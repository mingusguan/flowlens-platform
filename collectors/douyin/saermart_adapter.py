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
import gzip
import json
import logging
import os
import re
import signal
import sys
import time
import unicodedata
import urllib.parse
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Callable


LOG = logging.getLogger("flowlens.saermart_adapter")

HTTP_URL_PATTERN = re.compile(r"https?://[^\s]+", re.IGNORECASE)
LIVE_URL_PATTERN = re.compile(r"live\.douyin\.com/(\d+)", re.IGNORECASE)
REFLOW_URL_PATTERN = re.compile(r"/(?:douyin/)?webcast/reflow/(\d+)", re.IGNORECASE)
NUMBER_PATTERN = re.compile(r"^\d{6,}$")
REFLOW_ROOM_ID_MIN_LENGTH = 16
DESKTOP_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0"
)
UNSAFE_TEXT_CHARS = {"\ufffc", "\ufffd"}
GIFT_METHOD_KEYWORDS = ("gift", "combo", "tray", "ticket", "profit", "reward", "commerce", "asset")
SOCIAL_METHOD_KEYWORDS = ("social", "follow")


def configure_stdio_encoding() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if callable(reconfigure):
            reconfigure(encoding="utf-8", errors="replace")


def print_probe_result(result: dict[str, Any]) -> None:
    print(json.dumps(result, ensure_ascii=True), flush=True)


@dataclass(frozen=True)
class ResolvedLiveInput:
    live_id: str | None = None
    room_id: str | None = None
    live_title: str | None = None
    room_status: int | None = None
    live: bool | None = None
    source: str | None = None


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


def clean_event_text(value: Any, default: str | None = None) -> str | None:
    if value is None:
        return default
    text = str(value).replace("\r", " ").replace("\n", " ")
    chars: list[str] = []
    for char in text:
        if char in UNSAFE_TEXT_CHARS or unicodedata.category(char).startswith("C"):
            chars.append(" ")
            continue
        chars.append(char)
    cleaned = " ".join("".join(chars).split())
    return cleaned or default


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


def extract_candidate(value: str | None) -> str | None:
    if not value:
        return None
    trimmed = value.strip()
    if not trimmed:
        return None
    match = HTTP_URL_PATTERN.search(trimmed)
    if match:
        return strip_url_tail(match.group(0))
    return trimmed


def strip_url_tail(value: str) -> str:
    cleaned = value.strip()
    while cleaned and cleaned[-1] in "。，,)）]】\"'":
        cleaned = cleaned[:-1]
    return cleaned


def is_douyin_url(value: str) -> bool:
    try:
        host = (urllib.parse.urlparse(value).hostname or "").lower()
    except ValueError:
        return False
    return host.endswith("douyin.com") or host.endswith("amemv.com")


def resolve_redirect_url(url: str) -> str | None:
    request = urllib.request.Request(url, headers={"User-Agent": DESKTOP_USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=8) as response:
            return response.geturl()
    except urllib.error.URLError as exc:
        LOG.warning("resolve douyin redirect failed url=%s error=%s", url, exc)
        return None


def json_get(data: dict[str, Any], *keys: str) -> Any:
    current: Any = data
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def text_or_none(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def first_text(*values: Any) -> str | None:
    for value in values:
        text = text_or_none(value)
        if text:
            return text
    return None


def has_stream_url(room: dict[str, Any]) -> bool:
    stream_url = room.get("stream_url")
    if not isinstance(stream_url, dict):
        return False
    return bool(
        stream_url.get("rtmp_pull_url")
        or stream_url.get("hls_pull_url")
        or stream_url.get("flv_pull_url")
    )


def live_from_pc_room_status(room_status: int | None) -> bool | None:
    if room_status is None:
        return None
    return room_status == 0


def live_from_reflow_room_status(room_status: int | None) -> bool | None:
    if room_status is None:
        return None
    if room_status == 2:
        return True
    if room_status == 4:
        return False
    return None


def resolve_reflow_room(room_id: str) -> ResolvedLiveInput:
    url = (
        "https://webcast.amemv.com/webcast/room/reflow/info/"
        f"?type_id=0&live_id=1&room_id={urllib.parse.quote(room_id)}&app_id=1128"
    )
    request = urllib.request.Request(url, headers={"User-Agent": DESKTOP_USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=8) as response:
            data = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, json.JSONDecodeError, UnicodeDecodeError) as exc:
        LOG.warning("resolve reflow room failed room_id=%s error=%s", room_id, exc)
        return ResolvedLiveInput(room_id=room_id, source="reflow")

    room = json_get(data, "data", "room")
    if not isinstance(room, dict):
        return ResolvedLiveInput(room_id=room_id, source="reflow")

    resolved_room_id = first_text(room.get("id_str"), room.get("id"), room_id)
    live_id = first_text(json_get(room, "owner", "web_rid"))
    live_title = first_text(room.get("title"))
    room_status = int_or_none(room.get("status"))
    stream_available = has_stream_url(room)
    live = live_from_reflow_room_status(room_status)
    LOG.info(
        "resolved reflow room room_id=%s live_id=%s room_status=%s live=%s stream_available=%s title=%s",
        resolved_room_id,
        live_id,
        room_status,
        live,
        stream_available,
        live_title,
    )
    return ResolvedLiveInput(
        live_id=live_id,
        room_id=resolved_room_id,
        live_title=live_title,
        room_status=room_status,
        live=live,
        source="reflow",
    )


def resolve_live_input(live_id: str | None, room_id: str | None) -> ResolvedLiveInput:
    candidate = extract_candidate(live_id) or extract_candidate(room_id)
    cleaned_room_id = extract_candidate(room_id)
    if candidate:
        match = LIVE_URL_PATTERN.search(candidate)
        if match:
            return ResolvedLiveInput(live_id=match.group(1), room_id=cleaned_room_id, source="pc_live")
        match = REFLOW_URL_PATTERN.search(candidate)
        if match:
            return resolve_reflow_room(match.group(1))
        if NUMBER_PATTERN.match(candidate):
            if len(candidate) >= REFLOW_ROOM_ID_MIN_LENGTH:
                return resolve_reflow_room(candidate)
            return ResolvedLiveInput(live_id=candidate, room_id=cleaned_room_id, source="live_id")
        if is_douyin_url(candidate):
            redirected = resolve_redirect_url(candidate)
            if redirected and redirected != candidate:
                return resolve_live_input(redirected, room_id)
    if cleaned_room_id and NUMBER_PATTERN.match(cleaned_room_id) and len(cleaned_room_id) >= REFLOW_ROOM_ID_MIN_LENGTH:
        return resolve_reflow_room(cleaned_room_id)
    return ResolvedLiveInput(live_id=candidate, room_id=cleaned_room_id, source="raw")


def merge_resolved_inputs(primary: ResolvedLiveInput, fallback: ResolvedLiveInput) -> ResolvedLiveInput:
    source = primary.source
    if primary.live is None and fallback.live is not None:
        source = fallback.source
    return ResolvedLiveInput(
        live_id=first_text(primary.live_id, fallback.live_id),
        room_id=first_text(primary.room_id, fallback.room_id),
        live_title=first_text(primary.live_title, fallback.live_title),
        room_status=primary.room_status if primary.room_status is not None else fallback.room_status,
        live=primary.live if primary.live is not None else fallback.live,
        source=first_text(source, fallback.source),
    )


def resolve_effective_live_input(live_id: str | None, room_id: str | None) -> ResolvedLiveInput:
    live_result = resolve_live_input(live_id, room_id)
    room_result = resolve_live_input(None, room_id) if room_id else ResolvedLiveInput()
    return merge_resolved_inputs(live_result, room_result)


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


def payload_text_fragments(payload: Any, limit: int = 8) -> list[str]:
    if not isinstance(payload, (bytes, bytearray)):
        return []
    decoded = bytes(payload).decode("utf-8", "ignore")
    fragments: list[str] = []
    current: list[str] = []

    def flush_current() -> None:
        if not current:
            return
        text = clean_event_text("".join(current), "")
        current.clear()
        if text and len(text) >= 2 and text not in fragments:
            fragments.append(text)

    for char in decoded:
        if char.isprintable() and not unicodedata.category(char).startswith("C"):
            current.append(char)
            continue
        flush_current()
    flush_current()
    return fragments[:limit]


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
        return {"live": False, "roomStatus": None, "liveId": str(fetcher.live_id), "roomId": None, "error": "room_id_not_found"}

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
        "liveId": str(fetcher.live_id),
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
    base_cookie = ""
    if hasattr(fetcher, "session") and hasattr(fetcher.session, "headers"):
        base_cookie = fetcher.session.headers.get("Cookie", "") or ""
    if not base_cookie:
        base_cookie = headers.get("Cookie", "") or ""
    signed_cookie = merge_cookie_header(base_cookie, f"ttwid={fetcher.ttwid};__ac_nonce={nonce}; __ac_signature={signature}")
    headers.update({
        "Referer": f"https://live.douyin.com/{fetcher.live_id}",
        "Cookie": signed_cookie,
    })
    response = fetcher.session.get(url, headers=headers)
    response.raise_for_status()
    data = (response_json(response).get("data") or {})
    room = data.get("room") or {}
    user = data.get("user") or {}
    room_status = int_or_none(data.get("room_status"))
    return {
        "live": live_from_pc_room_status(room_status) is True,
        "roomStatus": room_status,
        "statusSource": "pc",
        "liveId": str(fetcher.live_id),
        "roomId": str(room_id),
        "userId": scalar(user.get("id_str"), None) or scalar(user.get("id"), None),
        "nickname": scalar(user.get("nickname"), None),
        "liveTitle": scalar(data.get("title"), None) or scalar(room.get("title"), None),
    }


def merge_cookie_header(*values: str | None) -> str:
    pairs: dict[str, str] = {}
    order: list[str] = []
    for value in values:
        if not value:
            continue
        for part in value.split(";"):
            item = part.strip()
            if not item or "=" not in item:
                continue
            name, cookie_value = item.split("=", 1)
            key = name.strip()
            if not key:
                continue
            if key not in pairs:
                order.append(key)
            pairs[key] = cookie_value.strip()
    return "; ".join(f"{key}={pairs[key]}" for key in order)


def reflow_probe_result(resolved: ResolvedLiveInput, error: str | None = None) -> dict[str, Any]:
    raw_room_status = resolved.room_status
    return {
        "live": bool(resolved.live),
        "roomStatus": 0 if resolved.live is True else raw_room_status,
        "rawRoomStatus": raw_room_status,
        "statusSource": resolved.source,
        "liveId": resolved.live_id,
        "roomId": resolved.room_id,
        "liveTitle": resolved.live_title,
        "error": error,
    }


def merge_probe_result(result: dict[str, Any], resolved: ResolvedLiveInput) -> dict[str, Any]:
    merged = result.copy()
    if not merged.get("liveId") and resolved.live_id:
        merged["liveId"] = resolved.live_id
    if not merged.get("roomId") and resolved.room_id:
        merged["roomId"] = resolved.room_id
    if not merged.get("liveTitle") and resolved.live_title:
        merged["liveTitle"] = resolved.live_title
    if merged.get("roomStatus") is None and resolved.room_status is not None:
        merged["roomStatus"] = resolved.room_status
        merged["rawRoomStatus"] = resolved.room_status
        merged["statusSource"] = resolved.source
    return merged


def make_reporting_fetcher(base_class: type, douyin_module: Any, reporter: EventReporter, request_stop: Callable[[], None] | None = None):
    class ReportingDouyinLiveWebFetcher(base_class):  # type: ignore[misc, valid-type]
        def _wsOnMessage(self, ws: Any, message: Any) -> None:  # noqa: N802
            try:
                package = douyin_module.PushFrame().parse(message)
                response = douyin_module.Response().parse(gzip.decompress(package.payload))
            except Exception:
                LOG.exception("cannot decode websocket message")
                return

            self._send_ack_if_needed(ws, package, response)
            for item in getattr(response, "messages_list", []) or []:
                self._dispatch_message(item)

        def _send_ack_if_needed(self, ws: Any, package: Any, response: Any) -> None:
            if not getattr(response, "need_ack", False):
                return
            try:
                ack = douyin_module.PushFrame(
                    log_id=package.log_id,
                    payload_type="ack",
                    payload=response.internal_ext.encode("utf-8"),
                ).SerializeToString()
                ws.send(ack, 2)
            except Exception:
                LOG.exception("cannot send websocket ack")

        def _dispatch_message(self, item: Any, source: str = "ws") -> None:
            method = getattr(item, "method", "")
            payload = getattr(item, "payload", b"")
            handlers = {
                "WebcastChatMessage": self._parseChatMsg,
                "WebcastGiftMessage": self._parseGiftMsg,
                "WebcastLikeMessage": self._parseLikeMsg,
                "WebcastSocialMessage": self._parseSocialMsg,
                "WebcastRoomUserSeqMessage": self._parseRoomUserSeqMsg,
                "WebcastControlMessage": self._parseControlMsg,
            }
            handler = handlers.get(method)
            if handler is None:
                self._inspect_unhandled_message(method, payload)
                return
            try:
                handler(payload)
            except Exception:
                LOG.exception("cannot parse douyin message source=%s method=%s", source, method)
                self._inspect_unhandled_message(method, payload)

        def _parse_unknown_important_messages(self, message: Any) -> None:
            try:
                package = douyin_module.PushFrame().parse(message)
                response = douyin_module.Response().parse(gzip.decompress(package.payload))
                for item in getattr(response, "messages_list", []):
                    method = getattr(item, "method", "")
                    lower_method = method.lower()
                    if method not in {"WebcastGiftMessage", "WebcastSocialMessage"} and any(
                        keyword in lower_method for keyword in GIFT_METHOD_KEYWORDS
                    ):
                        LOG.info("fallback parsing gift-like douyin message method=%s", method)
                        self._parseGiftMsg(item.payload)
                    if method != "WebcastSocialMessage" and any(keyword in lower_method for keyword in SOCIAL_METHOD_KEYWORDS):
                        LOG.info("fallback parsing social-like douyin message method=%s", method)
                        self._parseSocialMsg(item.payload)
            except Exception:
                LOG.debug("fallback douyin message inspection failed", exc_info=True)

        def _inspect_unhandled_message(self, method: str, payload: Any) -> None:
            lower_method = method.lower()
            fragments = payload_text_fragments(payload)
            gift_like = any(keyword in lower_method for keyword in GIFT_METHOD_KEYWORDS)
            social_like = any(keyword in lower_method for keyword in SOCIAL_METHOD_KEYWORDS)
            if gift_like or social_like:
                LOG.info(
                    "unhandled important douyin message method=%s payloadText=%s",
                    method,
                    " | ".join(fragments)[:300],
                )

        def _parseChatMsg(self, payload: Any) -> None:  # noqa: N802
            message = douyin_module.ChatMessage().parse(payload)
            user = getattr(message, "user", None)
            reporter.post_event({
                "eventType": "COMMENT",
                "msgId": msg_id(message),
                "userId": user_id(user),
                "douyinAccount": douyin_account(user),
                "nickname": clean_event_text(nickname(user)),
                "content": clean_event_text(scalar(getattr(message, "content", None), ""), "") or "",
                "eventTime": iso_now(),
            })
            try:
                super()._parseChatMsg(payload)
            except Exception:
                LOG.debug("original chat message printing failed", exc_info=True)

        def _parseGiftMsg(self, payload: Any) -> None:  # noqa: N802
            message = douyin_module.GiftMessage().parse(payload)
            user = getattr(message, "user", None)
            gift = getattr(message, "gift", None)
            gift_count = (
                scalar(getattr(message, "combo_count", None), 0)
                or scalar(getattr(message, "comboCount", None), 0)
                or scalar(getattr(message, "repeat_count", None), 0)
                or scalar(getattr(message, "repeatCount", None), 0)
                or scalar(getattr(message, "total_count", None), 0)
                or scalar(getattr(message, "totalCount", None), 0)
                or 1
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
                "nickname": clean_event_text(nickname(user)),
                "giftId": str(scalar(getattr(gift, "id", None), "")) or None,
                "giftName": clean_event_text(scalar(getattr(gift, "name", None), None)),
                "giftCount": int(gift_count),
                "giftValue": int(gift_value) * int(gift_count or 1),
                "eventTime": iso_now(),
            })
            try:
                super()._parseGiftMsg(payload)
            except Exception:
                LOG.debug("original gift message printing failed", exc_info=True)

        def _parseLikeMsg(self, payload: Any) -> None:  # noqa: N802
            message = douyin_module.LikeMessage().parse(payload)
            user = getattr(message, "user", None)
            like_count = scalar(getattr(message, "count", None), 0) or scalar(getattr(message, "total", None), 0) or 0
            reporter.post_event({
                "eventType": "LIKE",
                "msgId": msg_id(message),
                "userId": user_id(user),
                "douyinAccount": douyin_account(user),
                "nickname": clean_event_text(nickname(user)),
                "likeCount": int(like_count),
                "eventTime": iso_now(),
            })
            try:
                super()._parseLikeMsg(payload)
            except Exception:
                LOG.debug("original like message printing failed", exc_info=True)

        def _parseSocialMsg(self, payload: Any) -> None:  # noqa: N802
            message = douyin_module.SocialMessage().parse(payload)
            user = getattr(message, "user", None)
            reporter.post_event({
                "eventType": "FOLLOW",
                "msgId": msg_id(message),
                "userId": user_id(user),
                "douyinAccount": douyin_account(user),
                "nickname": clean_event_text(nickname(user)),
                "content": clean_event_text(scalar(getattr(message, "share_target", None), None)),
                "eventTime": iso_now(),
            })
            try:
                super()._parseSocialMsg(payload)
            except Exception:
                LOG.debug("original social message printing failed", exc_info=True)

        def _parseRoomUserSeqMsg(self, payload: Any) -> None:  # noqa: N802
            message = douyin_module.RoomUserSeqMessage().parse(payload)
            viewer_count = (
                int_or_none(getattr(message, "total_pv_for_anchor", None))
                or int_or_none(getattr(message, "total_user", None))
                or int_or_none(getattr(message, "total", None))
                or 0
            )
            reporter.post_event({
                "eventType": "ROOM_STATS",
                "msgId": msg_id(message),
                "viewerCount": int(viewer_count),
                "eventTime": iso_now(),
            })
            try:
                super()._parseRoomUserSeqMsg(payload)
            except Exception:
                LOG.debug("original room user seq message printing failed", exc_info=True)

        def _parseControlMsg(self, payload: Any) -> None:  # noqa: N802
            message = douyin_module.ControlMessage().parse(payload)
            status = scalar(getattr(message, "status", None), None)
            if status == 3:
                reporter.post_event({
                    "eventType": "LIVE_END",
                    "msgId": msg_id(message),
                    "eventTime": iso_now(),
                })
                if request_stop is not None:
                    LOG.info("live end control message received, stopping collector")
                    request_stop()
            try:
                super()._parseControlMsg(payload)
            except Exception:
                LOG.debug("original control message printing failed", exc_info=True)

    return ReportingDouyinLiveWebFetcher


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="FlowLens Douyin cloud collector adapter")
    parser.add_argument("--probe", action="store_true", help="Only detect whether the live room is currently live")
    parser.add_argument("--fetcher-path", required=True, help="Path to saermart/DouyinLiveWebFetcher checkout")
    parser.add_argument("--backend-url", default=None, help="FlowLens backend base URL, for example http://127.0.0.1:8088")
    parser.add_argument("--anchor-id", default=None, type=int)
    parser.add_argument("--token", default=None, help="Anchor report token generated by FlowLens")
    parser.add_argument("--live-id", default=None, help="Douyin live_id, PC URL, mobile share text, or v.douyin.com link")
    parser.add_argument("--room-id", default=None)
    parser.add_argument("--log-level", default="INFO")
    args = parser.parse_args()
    if not args.live_id and not args.room_id:
        parser.error("requires at least one of: --live-id, --room-id")
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
    configure_stdio_encoding()
    args = parse_args()
    logging.basicConfig(level=getattr(logging, args.log_level.upper(), logging.INFO), format="%(asctime)s %(levelname)s %(message)s")
    base_class, douyin_module = build_fetcher_class(Path(args.fetcher_path).resolve())
    os.chdir(Path(args.fetcher_path).resolve())
    resolved_input = resolve_effective_live_input(args.live_id, args.room_id)
    live_id = first_text(resolved_input.live_id, args.live_id)
    room_id = first_text(resolved_input.room_id, args.room_id)
    LOG.info(
        "resolved live input source=%s live_id=%s room_id=%s room_status=%s live=%s",
        resolved_input.source,
        live_id,
        room_id,
        resolved_input.room_status,
        resolved_input.live,
    )
    if args.probe:
        if resolved_input.source == "reflow" and resolved_input.live is False:
            print_probe_result(reflow_probe_result(resolved_input))
            return 0
        if not live_id:
            print_probe_result(reflow_probe_result(resolved_input, "live_id_not_found"))
            return 0
        room = base_class(live_id)
        set_fetcher_room_id(room, room_id)
        apply_session_timeout(room)
        result = probe_room(room, douyin_module)
        reflow_result = None
        if result.get("roomId") and not result.get("liveTitle"):
            reflow_result = resolve_reflow_room(str(result["roomId"]))
            result = merge_probe_result(result, reflow_result)
        if not result.get("live") and resolved_input.source == "reflow" and resolved_input.live is True:
            result = reflow_probe_result(resolved_input, result.get("error") or "pc_probe_failed")
        elif not result.get("live") and reflow_result is not None and reflow_result.live is True:
            result = reflow_probe_result(reflow_result, result.get("error") or "pc_probe_failed")
        else:
            result = merge_probe_result(result, resolved_input)
        print_probe_result(result)
        return 0

    if not live_id:
        LOG.error("live_id cannot be resolved from input live_id=%s room_id=%s", args.live_id, args.room_id)
        return 1
    if not room_id:
        resolved_for_room = resolve_live_input(live_id, None)
        room_id = first_text(resolved_for_room.room_id, room_id)
    should_stop = False

    def request_stop() -> None:
        nonlocal should_stop
        should_stop = True
        if hasattr(room, "stop"):
            room.stop()

    reporter = EventReporter(args.backend_url, args.anchor_id, args.token, live_id, room_id)
    fetcher_class = make_reporting_fetcher(base_class, douyin_module, reporter, request_stop)
    room = fetcher_class(live_id)
    set_fetcher_room_id(room, room_id)

    def handle_signal(signum: int, _frame: Any) -> None:
        LOG.info("received signal=%s, stopping collector", signum)
        request_stop()

    signal.signal(signal.SIGTERM, handle_signal)
    signal.signal(signal.SIGINT, handle_signal)

    LOG.info("starting cloud collector anchor_id=%s live_id=%s room_id=%s", args.anchor_id, live_id, room_id)
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
