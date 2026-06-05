# Douyin Cloud Collector

`saermart_adapter.py` is the cloud collector launched by the Java backend.

It does not vendor or copy `saermart/DouyinLiveWebFetcher`. Deploy that project separately, then point `--fetcher-path` to its directory.

Example backend config:

```yaml
flowlens:
  live:
    collector:
      executable: python
      args:
        - E:/JAVA/flowlens-platform/collectors/douyin/saermart_adapter.py
        - --fetcher-path
        - E:/JAVA/DouyinLiveWebFetcher
        - --backend-url
        - http://127.0.0.1:8088
        - --anchor-id
        - "{anchorId}"
        - --token
        - "{reportToken}"
        - --live-id
        - "{liveId}"
```

The adapter posts normalized events to:

- `POST /api/live/report/cloud/events`

Supported public event types:

- `COMMENT`
- `LIKE`
- `MEMBER`
- `FOLLOW`
- `ROOM_STATS`
- `LIVE_END`

Probe mode:

```bash
python collectors/douyin/saermart_adapter.py --probe --fetcher-path E:/JAVA/DouyinLiveWebFetcher --live-id 510200350291
```

`--live-id` can also be a PC live URL, a mobile Douyin share text, or a `v.douyin.com` short link. The adapter follows mobile short links to `webcast/reflow/{roomId}`, resolves `owner.web_rid`, and uses that value as the PC `live_id`.

Probe mode prints one JSON line, for example `{"live": true, "roomStatus": 0, "roomId": "..."}`.
The Java scheduler uses this to auto-create a live session before starting the cloud collector.
