# Videoahmatti — LLM Project Overview

This document gives AI coding agents the default context for this repository.

## Project Purpose

Personal self-hosted web app for browsing and watching video files stored in Hetzner Storage Box.

Primary user flows:
- Show video thumbnails in a web page.
- Open a video and watch it on the page.
- Download a video file.
- Browse/filter videos by auto-generated tags (for example: animals, vehicles).

## Architecture (MVP)

- One Hetzner VM hosts the whole app.
- Video files are mounted as normal files on disk (no direct object-storage SDK access in app code).
- Backend is mainly Clojure.
- Some workloads run as external processes (for example `ffmpeg` and a tagging script/model process).
- SQLite stores metadata (videos, thumbnails).

## Backend Stack Decisions

- HTTP server: `http-kit`
- Routing: manual routing in Ring-style handler using plain Clojure conditionals (`cond`/`case`), no routing library
- Database: `next.jdbc` with plain SQL
- Config: `aero`
- Validation: `malli`
- Async/background coordination: `core.async`
- JSON: use a lightweight JSON library (prefer `jsonista`)

## Explicit Non-Goal (Current Scope)

- Do **not** implement HTTP Range support yet.
  - Keep playback/download handling simple for now.

## Data Responsibilities

SQLite tables should cover at least:
- videos (file metadata)
- thumbnails (thumbnail metadata and references)
- tags (detected labels + confidence)

## Coding Guidance for Agents

- Keep implementation minimal and practical (personal-use app, not enterprise-scale).
- Prefer explicit, readable code over framework-heavy abstractions.
- Use plain SQL files/strings instead of introducing ORM layers.
- Do not add extra product features beyond current scope unless requested.
- Preserve the decision to keep video files as mounted filesystem paths.

## Suggested Next Implementation Order

1. DB schema + migrations
2. Video scan/index job from mounted directory
3. Thumbnail listing API + simple gallery page
4. Video detail page with watch + download
5. Async workers for thumbnail generation
6. Async workers for tagging + tag filter UI
