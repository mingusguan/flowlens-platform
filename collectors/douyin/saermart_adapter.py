#!/usr/bin/env python3
"""
Cloud adapter for saermart/DouyinLiveWebFetcher.

This script is launched by the Java backend when a live session needs cloud
fallback collection. It imports saermart's DouyinLiveWebFetcher at runtime,
converts parsed live messages into FlowLens events, and posts them back to:

    POST /api/live/report/cloud/events

Usage example:

    python collectors/douyin/saermart_adapter.py \
      --fetcher-path /opt/DouyinLiveWebFetcher \
      --backend-url http://127.0.0.1:8088 \
      --anchor-id 123 \
      --token xxx \
      --live-id 510200350291

Probe example:

    python collectors/douyin/saermart_adapter.py \
      --probe \
      --fetcher-path /opt/DouyinLiveWebFetcher \
      --live-id 510200350291
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import signal
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any


LOG = logging.getLogger("flowlens.saermart_adapter")


class EventReporter:
    def __init__(self, backend_url: str, anchor_id: int, report_token: str, live_id: str | None, room_id: str | None):
        self.backend_url = backend_url.rstrip("/")
        self.anchor_id = anchor_id
        self.report_token = report_token
        self.live_id = live_id
        self.room_id = room_id

    def post_event(self, payload: dict[str, Any]) -> None:
        body = {
            "anchorId": self.anchor_id,
            "reportToken": self.report_token,
            "liveId": payload.pop("liveId", self.live_id),
            "roomId": payload.pop("roomId", self.room_id),
            **payload,
        }
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            f"{self.backend_url}/api/live/report/cloud/events",
            data=data,
            headers={"Content-Type": "application/json; charset=utf-8"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                if response.status >= 300:
                    LOG.warning("event post returned status=%s body=%s", response.status, response.read().decode("utf-8", "ignore"))
        except urllib.error.URLError as exc:
            LOG.warning("event post failed: %s", exc)


def iso_now() -> str:
    return datetime.now().isoformat(timespec="seconds")


def scalar(value: Any, default: Any = None) -> Any:
    if value is None:
        return default
    if isinstance(value, (str, int, float, bool)):
        return value
    return default


def nested(obj: Any, *names: str, default: Any = None) -> Any:
    current = obj
    for name in names:
        if current is None:
            return default
        current = getattr(current, name, None)
    return current if current is not None else default


def user_id(user: Any) -> str | None:
    return str(scalar(nested(user, "id"), None) or scalar(nested(user, "shortId"), None) or "") or None


def nickname(user: Any) -> str | None:
    return scalar(nested(user, "nick_name"), None) or scalar(nested(user, "nickName"), None) or scalar(nested(user, "nickname"), None)


def douyin_account(user: Any) -> str | None:
    value = (
        scalar(nested(user, "display_id"), None)
        or scalar(nested(user, "displayId"), None)
        or scalar(nested(user, "unique_id"), None)
        or scalar(nested(user, "uniqueId"), None)
        or scalar(nested(user, "short_id"), None)
        or scalar(nested(user, "shortId"), None)
    )
    return str(value) if value else None


def msg_id(msg: Any) -> str | None:
    value = (
        scalar(getattr(msg, "msg_id", None), None)
        or scalar(getattr(msg, "msgId", None), None)
        or scalar(nested(msg, "common", "msg_id"), None)
        or scalar(nested(msg, "common", "msgId"), None)
    )
    return str(value) if value else None


def as_jsonable(obj: Any) -> str:
    try:
        return json.dumps(obj, default=lambda item: getattr(item, "__dict__", str(item)), ensure_ascii=False)
    except TypeError:
        return str(obj)


def build_fetcher_class(fetcher_path: Path):
    if not fetcher_path.exists():
        raise FileNotFoundError(f"fetcher path not found: {fetcher_path}")
    local_deps = fetcher_path / "_deps"
    use_local_deps = os.getenv("FLOWLENS_DOUYIN_USE_LOCAL_DEPS", "true").lower() not in {"0", "false", "no"}
    if use_local_deps and local_deps.exists():
        sys.path.insert(0, str(local_deps))
    sys.path.insert(0, str(fetcher_path))
    try:
        import liveMan  # type: ignore
    except ImportError as exc:
        raise ImportError(f"cannot import liveMan.DouyinLiveWebFetcher from {fetcher_path}") from exc
    return liveMan.DouyinLiveWebFetcher, liveMan


def int_or_none(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def set_fetcher_room_id(fetcher: Any, room_id: str | None) -> None:
    if not room_id:
        return
    cleaned = room_id.strip()
    if cleaned:
        setattr(fetcher, "_DouyinLiveWebFetcher__room_id", cleaned)


def apply_session_timeout(fetcher: Any, timeout_seconds: int = 10) -> None:
    original_request = fetcher.session.request

    def request_with_timeout(method: str, url: str, **kwargs: Any) -> Any:
        kwargs.setdefault("timeout", timeout_seconds)
        return original_request(method, url, **kwargs)

    fetcher.session.request = request_with_timeout


def resolve_room_id(fetcher: Any) -> str | None:
    try:
        value = fetcher.room_id
    except Exception as exc:
        LOG.warning("probe cannot resolve room_id: %s", exc)
        return None
    if value is None:
        return None
    value = str(value).strip()
    return value or None


def compact_text(value: str, limit: int = 240) -> str:
    cleaned = " ".join(value.replace("\r", " ").replace("\n", " ").split())
    return cleaned[:limit]


def response_json(response: Any) -> dict[str, Any]:
    try:
        return response.json()
    except ValueError as exc:
        content_type = response.headers.get("Content-Type", "")
        body = compact_text(response.text or "")
        raise ValueError(f"response_not_json status={response.status_code} contentType={content_type} body={body}") from exc


def probe_room(fetcher: Any, douyin_module: Any) -> dict[str, Any]:
    room_id = resolve_room_id(fetcher)
    if not room_id:
        return {"live": False, "roomStatus": None, "roomId": None, "error": "room_id_not_found"}

    last_error: Exception | None = None
    for attempt in range(1, 4):
        try:
            return probe_room_once(fetcher, douyin_module, room_id)
        except Exception as exc:
            last_error = exc
            LOG.warning("probe attempt=%s failed live_id=%s room_id=%s error=%s", attempt, fetcher.live_id, room_id, exc)
            if attempt < 3:
                time.sleep(0.8)
    return {
        "live": False,
        "roomStatus": None,
        "roomId": str(room_id),
        "error": "probe_failed",
        "message": compact_text(str(last_error or "")),
    }


def probe_room_once(fetcher: Any, douyin_module: Any, room_id: str) -> dict[str, Any]:
    ms_token = douyin_module.generateMsToken()
    nonce = fetcher.get_ac_nonce()
    signature = fetcher.get_ac_signature(nonce)
    url = (
        "https://live.douyin.com/webcast/room/web/enter/?aid=6383"
        "&app_name=douyin_web&live_id=1&device_platform=web&language=zh-CN&enter_from=page_refresh"
        "&cookie_enabled=true&screen_width=5120&screen_height=1440&browser_language=zh-CN&browser_platform=Win32"
        "&browser_name=Edge&browser_version=140.0.0.0"
        f"&web_rid={fetcher.live_id}"
        f"&room_id_str={room_id}"
        "&enter_source=&is_need_double_stream=false&insert_task_id=&live_reason=&msToken="
        + ms_token
    )
    query = douyin_module.parse_url(url).query
    params = {
        item[0]: item[1]
        for item in [part.split("=", 1) for part in query.split("&") if "=" in part]
    }
    a_bogus = fetcher.get_a_bogus(params)
    url += f"&a_bogus={a_bogus}"
    headers = fetcher.headers.copy()
    headers.update({
        "Referer": f"https://live.douyin.com/{fetcher.live_id}",
        "Cookie": f"ttwid={fetcher.ttwid};__ac_nonce={nonce}; __ac_signature={signature}",
    })
    response = fetcher.session.get(url, headers=headers)
    response.raise_for_status()
    data = (response_json(response).get("data") or {})
    room = data.get("room") or {}
    user = data.get("user") or {}
    room_status = int_or_none(data.get("room_status"))
    return {
        "live": room_status == 0,
        "roomStatus": room_status,
        "roomId": str(room_id),
        "userId": scalar(user.get("id_str"), None) or scalar(user.get("id"), None),
        "nickname": scalar(user.get("nickname"), None),
        "liveTitle": scalar(data.get("title"), None) or scalar(room.get("title"), None),
    }


def make_reporting_fetcher(base_class: type, douyin_module: Any, reporter: EventReporter):
    class ReportingDouyinLiveWebFetcher(base_class):  # type: ignore[misc, valid-type]
        def _parseChatMsg(self, payload: Any) -> None:  # noqa: N802
            message = douyin_module.ChatMessage().parse(payload)
            super()._parseChatMsg(payload)
            user = getattr(message, "user", None)
            reporter.post_event({
                "eventType": "COMMENT",
                "msgId": msg_id(message),
                "userId": user_id(user),
                "douyinAccount": douyin_account(user),
                "nickname": nickname(user),
                "content": scalar(getattr(message, "content", None), ""),
                "eventTime": iso_now(),
                "rawPayload": as_jsonable(message),
            })

        def _parseGiftMsg(self, payload: Any) -> None:  # noqa: N802
            message = douyin_module.GiftMessage().parse(payload)
            super()._parseGiftMsg(payload)
            user = getattr(message, "user", None)
            gift = getattr(message, "gift", None)
            gift_count = (
                scalar(getattr(message, "combo_count", None), 0)
                or scalar(getattr(message, "comboCount", None), 0)
                or scalar(getattr(message, "repeat_count", None), 0)
                or scalar(getattr(message, "repeatCount", None), 0)
                or 0
            )
            gift_value = (
                scalar(getattr(gift, "diamond_count", None), 0)
                or scalar(getattr(gift, "diamondCount", None), 0)
                or 0
            )
            reporter.post_event({
                "eventType": "GIFT",
                "msgId": msg_id(message),
                "userId": user_id(user),
                "douyinAccount": douyin_account(user),
                "nickname": nickname(user),
                "giftId": str(scalar(getattr(gift, "id", None), "")) or None,
                "giftName": scalar(getattr(gift, "name", None), None),
                "giftCount": int(gift_count),
                "giftValue": int(gift_value) * int(gift_count or 1),
                "eventTime": iso_now(),
                "rawPayload": as_jsonable(message),
            })

        def _parseLikeMsg(self, payload: Any) -> None:  # noqa: N802
            message = douyin_module.LikeMessage().parse(payload)
            super()._parseLikeMsg(payload)
            user = getattr(message, "user", None)
            like_count = scalar(getattr(message, "count", None), 0) or scalar(getattr(message, "total", None), 0) or 0
            reporter.post_event({
                "eventType": "LIKE",
                "msgId": msg_id(message),
                "userId": user_id(user),
                "douyinAccount": douyin_account(user),
                "nickname": nickname(user),
                "likeCount": int(like_count),
                "eventTime": iso_now(),
                "rawPayload": as_jsonable(message),
            })

        def _parseControlMsg(self, payload: Any) -> None:  # noqa: N802
            message = douyin_module.ControlMessage().parse(payload)
            status = scalar(getattr(message, "status", None), None)
            if status == 3:
                reporter.post_event({
                    "eventType": "LIVE_END",
                    "msgId": msg_id(message),
                    "eventTime": iso_now(),
                    "rawPayload": as_jsonable(message),
                })
            super()._parseControlMsg(payload)

    return ReportingDouyinLiveWebFetcher


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="FlowLens Douyin cloud collector adapter")
    parser.add_argument("--probe", action="store_true", help="Only detect whether the live room is currently live")
    parser.add_argument("--fetcher-path", required=True, help="Path to saermart/DouyinLiveWebFetcher checkout")
    parser.add_argument("--backend-url", default=None, help="FlowLens backend base URL, for example http://127.0.0.1:8088")
    parser.add_argument("--anchor-id", default=None, type=int)
    parser.add_argument("--token", default=None, help="Anchor report token generated by FlowLens")
    parser.add_argument("--live-id", required=True, help="Douyin live_id, usually the URL suffix")
    parser.add_argument("--room-id", default=None)
    parser.add_argument("--log-level", default="INFO")
    args = parser.parse_args()
    if not args.probe:
        missing = [
            name
            for name, value in [
                ("--backend-url", args.backend_url),
                ("--anchor-id", args.anchor_id),
                ("--token", args.token),
            ]
            if value is None or value == ""
        ]
        if missing:
            parser.error(f"collector mode requires: {', '.join(missing)}")
    return args


def main() -> int:
    args = parse_args()
    logging.basicConfig(level=getattr(logging, args.log_level.upper(), logging.INFO), format="%(asctime)s %(levelname)s %(message)s")
    base_class, douyin_module = build_fetcher_class(Path(args.fetcher_path).resolve())
    os.chdir(Path(args.fetcher_path).resolve())
    if args.probe:
        room = base_class(args.live_id)
        set_fetcher_room_id(room, args.room_id)
        apply_session_timeout(room)
        result = probe_room(room, douyin_module)
        print(json.dumps(result, ensure_ascii=False), flush=True)
        return 0

    reporter = EventReporter(args.backend_url, args.anchor_id, args.token, args.live_id, args.room_id)
    fetcher_class = make_reporting_fetcher(base_class, douyin_module, reporter)
    room = fetcher_class(args.live_id)

    should_stop = False

    def handle_signal(signum: int, _frame: Any) -> None:
        nonlocal should_stop
        LOG.info("received signal=%s, stopping collector", signum)
        should_stop = True
        if hasattr(room, "stop"):
            room.stop()

    signal.signal(signal.SIGTERM, handle_signal)
    signal.signal(signal.SIGINT, handle_signal)

    LOG.info("starting cloud collector anchor_id=%s live_id=%s", args.anchor_id, args.live_id)
    try:
        room.start()
        while not should_stop:
            time.sleep(1)
    except KeyboardInterrupt:
        LOG.info("collector interrupted")
    except Exception:
        LOG.exception("collector crashed")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
