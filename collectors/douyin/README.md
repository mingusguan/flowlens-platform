# Douyin Cloud Collector

`saermart_adapter.py` is the cloud fallback collector launched by the Java backend.

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

Supported event types:

- `COMMENT`
- `GIFT`
- `LIKE`
- `LIVE_END`

Probe mode:

```bash
python collectors/douyin/saermart_adapter.py --probe --fetcher-path E:/JAVA/DouyinLiveWebFetcher --live-id 510200350291
```

`--live-id` can also be a PC live URL, a mobile Douyin share text, or a `v.douyin.com` short link. The adapter follows mobile short links to `webcast/reflow/{roomId}`, resolves `owner.web_rid`, and uses that value as the PC `live_id`.

Probe mode prints one JSON line, for example `{"live": true, "roomStatus": 0, "roomId": "..."}`.
The Java scheduler uses this to auto-create a live session before starting the cloud collector.

## Local gift announcer

`local_gift_announcer.py` is a standalone local client for on-site gift voice announcements.
It listens for Douyin gift messages, can optionally announce chat comments, and plays speech
on the local machine. It does not send heartbeats, gift events, comments, or any other data to
the FlowLens backend.

Dry-run first to confirm the room and announcement text:

```bash
python collectors/douyin/local_gift_announcer.py --fetcher-path E:/JAVA/DouyinLiveWebFetcher --live-id 510200350291 --dry-run
```

Then run with local speech:

```bash
python collectors/douyin/local_gift_announcer.py --fetcher-path E:/JAVA/DouyinLiveWebFetcher --live-id 510200350291
```

`--live-id` accepts the same values as the cloud adapter: a PC live URL, mobile share text,
a `v.douyin.com` short link, or the URL suffix. Useful options:

- `--template "感谢 {nickname} 送出 {gift_name}{gift_count_text}"`
- `--announce-comments`
- `--comment-interval-seconds 1.5`
- `--min-gift-value 10`
- `--speech-rate 1`
- `--speech-volume 90`
- `--list-voices`
- `--voice "Microsoft Huihui Desktop"`
- `--speech-bitness 32 --voice "Ekho Mandarin"`
- `--speech-engine print`

### VoxCPM speech engine

For higher quality AI voice announcements, run VoxCPM as a local HTTP service
and point the gift announcer at it. Install VoxCPM in a Python 3.10+ environment
with a CUDA-capable PyTorch build first, then start:

```bash
python collectors/douyin/voxcpm_tts_server.py --host 127.0.0.1 --port 8710 --device cuda
```

Check the service:

```bash
curl http://127.0.0.1:8710/health
```

Then run the announcer with VoxCPM:

```bash
python collectors/douyin/local_gift_announcer.py --fetcher-path E:/JAVA/DouyinLiveWebFetcher --live-id 510200350291 --speech-engine voxcpm
```

Useful VoxCPM options:

- `--voxcpm-url http://127.0.0.1:8710/synthesize`
- `--voxcpm-voice-description "温暖、热情、直播间女主播风格"`
- `--voxcpm-prompt-wav E:/voice/anchor.wav --voxcpm-prompt-text "这是一段主播参考音频的逐字文本"`
- `--voxcpm-reference-wav E:/voice/anchor.wav`
- `--voxcpm-cache-dir E:/JAVA/flowlens-platform/collectors/douyin/.cache/voxcpm`
- `--voxcpm-no-cache`
- `--voxcpm-fallback-engine powershell`
- `--voxcpm-player-command "ffplay -nodisp -autoexit -loglevel error {path}"`

The VoxCPM engine caches generated wav files by text and voice options. If the
VoxCPM service fails, the announcer falls back to the local system speech engine
by default, so on-site announcements keep working.
