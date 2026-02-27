# Videoahmatti (MVP)

Personal self-hosted video browser/player for mounted video files.

## Current MVP scope

- Scan mounted video directory on startup
- Store video metadata in SQLite
- List videos on a web page
- Play videos in-page
- Download videos
- No tagging yet
- No HTTP Range support yet

## Tech stack

- Clojure + `http-kit`
- Manual routing (`cond` / `case` style)
- `next.jdbc` + plain SQL
- SQLite
- `aero` config
- `malli` validation
- `core.async` (job plumbing)

## Prerequisites

- Java 21+ (or compatible JVM)
- Clojure CLI (`clojure`)
- Mounted video directory available on local filesystem

## Configuration

Config file: `resources/config.edn`

Environment variables:

- `VIDEOAHMATTI_HOST` (default: `0.0.0.0`)
- `VIDEOAHMATTI_PORT` (default: `8080`)
- `VIDEOAHMATTI_VIDEO_ROOT` (default: `./videos`)
- `VIDEOAHMATTI_JDBC_URL` (default: `jdbc:sqlite:./data/videoahmatti.db`)

Example:

```bash
export VIDEOAHMATTI_VIDEO_ROOT="/mnt/storagebox/videos"
export VIDEOAHMATTI_JDBC_URL="jdbc:sqlite:./data/videoahmatti.db"
```

## Run

Put your local values in `.env` (project root), then start the app:

```bash
clojure -M:run
```

## Deploy (no Docker Hub)

Build locally, transfer image via `docker save|load` over SSH, and restart container on remote host:

```bash
./scripts/deploy.sh --host user@your-vm
```

Example with custom paths/port:

```bash
./scripts/deploy.sh \
	--host user@your-vm \
	--remote-videos /srv/videoahmatti/videos \
	--remote-data /srv/videoahmatti/data \
	--port 8080
```

On startup the app will:

1. Run idempotent schema SQL (`resources/schema.sql`) to create missing tables/indexes
2. Scan `VIDEOAHMATTI_VIDEO_ROOT` for video files
3. Upsert discovered files into `videos` table
4. Start HTTP server

## Routes

- `GET /` — video list page
- `GET /videos/:id` — watch page
- `GET /api/videos` — videos JSON
- `GET /api/videos/:id` — single video JSON
- `GET /api/videos/:id/stream` — stream endpoint
- `GET /api/videos/:id/download` — download endpoint
- `GET /health` — health check

## Notes

- The stream endpoint currently serves full-file responses only.
- For large files, browser seek behavior may be limited until Range support is added.
