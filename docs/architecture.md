# Architecture

```mermaid
flowchart LR
    subgraph kid["Kid's device (no Google access needed)"]
        UI["Web UI / PWA"]
    end

    subgraph server["TubeTamer server"]
        API["FastAPI<br/>(search, catalog, watch, stream)"]
        THUMB["Thumbnail proxy<br/>/thumb — disk cache"]
        DL["Video downloader<br/>yt-dlp + ffmpeg"]
        BOT["Telegram bot<br/>(approvals, channels, limits)"]
        DB[("SQLite<br/>profiles, videos, watch log")]
    end

    PARENT["Parent's phone<br/>(Telegram)"]
    YT["YouTube"]

    UI -->|"search / request / watch"| API
    UI -->|"thumbnails"| THUMB
    UI <-->|"video stream (/api/stream)"| API
    API --> DB
    BOT --> DB
    API -->|"new request"| BOT
    BOT <-->|"approve / deny buttons"| PARENT
    DL -->|"download approved videos"| YT
    THUMB -->|"fetch once, cache on disk"| YT
    API -->|"yt-dlp search / metadata"| YT
```

The kid's device only ever talks to the TubeTamer server: pages, thumbnails (`/thumb`), and video streams (`/api/stream`) are all served locally. The server is the single component that contacts YouTube — which is what makes full DNS/IP blocking of Google possible on the kid's device.

## Request Flow

```mermaid
sequenceDiagram
    participant K as Kid's tablet
    participant S as TubeTamer server
    participant P as Parent (Telegram)
    participant Y as YouTube

    K->>S: Search query
    S->>Y: yt-dlp search
    Y-->>S: Results
    S-->>K: Results page (thumbnails via /thumb)
    K->>S: Request video
    S->>P: Notification + Approve/Deny buttons
    P->>S: Approve (Edu / Fun)
    S->>Y: Download video (yt-dlp, local playback mode)
    Y-->>S: Video file → db/videos
    K->>S: Pending page polls status
    S-->>K: Approved → play
    K->>S: GET /api/stream/{video_id}
    S-->>K: Video streamed from disk (HTTP Range)
```

In embed mode (`local_playback.enabled: false`), the last two steps are replaced by a YouTube iframe embed on the watch page — the tablet then needs direct YouTube access.
