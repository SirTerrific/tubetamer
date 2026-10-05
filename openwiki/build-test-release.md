---
type: "Guide"
title: "Build, test and release"
openwiki_generated: true
verified:
  - by: openwiki/0.7.0
    at: 2026-10-05T04:15:14.909Z
---

# Build, test and release

How TubeTamer is packaged as a Docker image, deployed, tested and released. For the runtime settings these files pass in, see [Configuration](configuration.md). To get a first instance running, see [Quickstart](quickstart.md).

## Docker image

The `Dockerfile` builds on `python:3.14-slim`:

1. Installs `requirements.txt` with `pip install --no-cache-dir`.
2. Installs the runtime dependencies `gosu`, `ffmpeg` and `deno` with apt (`--no-install-recommends`). yt-dlp needs Deno as its JavaScript runtime. Deno is downloaded from the GitHub `latest` release and matched to the build architecture (`dpkg --print-architecture`: `amd64` → `x86_64`, `arm64` → `aarch64`). If no Deno binary exists for the architecture, the build prints a warning and keeps going.
3. Creates a non-root `appuser` and the `/app/db/videos` and `/app/db/logs` directories.
4. Copies the app and `entrypoint.sh`.

The default command runs `python main.py -c /app/config.yaml` when that file exists. Otherwise it runs `python main.py`, and configuration then comes only from `BRG_*` environment variables.

`entrypoint.sh` changes the owner of the mounted volumes to `appuser` (UID 999). This is needed because host directories mounted by Unraid, NAS boxes or Docker volumes are often owned by root. It then drops privileges with `gosu`.

## Deployment

- **docker-compose**: `docker-compose.yml` defines one `tubetamer` service. It uses the `tubetamer` image, restarts `unless-stopped`, maps port `8080:8080`, mounts `./config.yaml` read-only and the `brg_db` and `brg_videos` volumes, and passes `BRG_BOT_TOKEN`, `BRG_ADMIN_CHAT_ID` and `BRG_PIN` from the host environment.
- **Unraid**: `unraid-template.xml` points at the published image `ghcr.io/sirterrific/tubetamer:latest`.
- **Remote host**: `deploy.sh` packs the source into a tarball (leaving out `__pycache__`, `.pyc`, `.env`, `.log` and similar), copies it over `ssh`/`scp` (default `/opt/tubetamer`), creates `config.yaml` from `config.example.yaml` the first time, and runs `docker compose build` and `docker compose up -d`.

## Tests

The suite lives in `tests/` and runs with pytest (plus `pytest-asyncio` for async tests). `tests/conftest.py` provides these shared fixtures:

- `video_store`: a `VideoStore` backed by a temporary SQLite file
- `child_store`: a `ChildStore` for the default profile, built on `video_store`
- `config_yaml`: writes a minimal config file

Test modules cover one area each: bot (`test_bot_*`, `test_callback_router.py`), stores (`test_video_store.py`, `test_child_store.py`), the extractor (`test_extractor_*`), i18n, utils, the web layer (`test_web_*`) and full web integration (`test_web_integration.py`).

Run everything with `pytest`, or one file with `pytest tests/test_config.py`. Without a local Python, run it in a container:

```bash
docker run --rm -v "$PWD:/src:ro" -w /work python:3.14-slim bash -c \
  "cp -r /src/. /work/ && pip install -q -r requirements.txt pytest pytest-asyncio && python -m pytest -q"
```

## Versioning and changelog

- `version.py` holds the version string (`__version__`). The web templates display it, and the release workflow checks it.
- `CHANGELOG.md` has one `## vX.Y.Z - YYYY-MM-DD` section per release, with `### Added` / `### Changed` / `### Fixed` subsections. Add new entries at the top.
- Releases are tagged `vX.Y.Z` on `main`.

## Publish workflow (GHCR)

`.github/workflows/docker-publish.yaml` runs on a pushed `main`, on a published GitHub release, and on manual dispatch. It:

1. On release events, checks that the tag (`GITHUB_REF_NAME` without the `v`) matches `__version__` in `version.py`. If they differ, the job fails.
2. Sets up QEMU and Buildx, then logs in to `ghcr.io` with `GITHUB_TOKEN`.
3. Generates tags with `docker/metadata-action`: semver `{{version}}`, `{{major}}.{{minor}}` and `{{major}}`, plus `latest`.
4. Builds and pushes a multi-arch image for `linux/amd64` and `linux/arm64`.

The workflow sets `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24` so the actions run on Node 24.

Release steps:

1. Bump `version.py` and add the `CHANGELOG.md` entry.
2. Commit and push to `main`.
3. Run `gh release create vX.Y.Z`.
4. Watch the run with `gh run watch`.
5. Check the published image with `docker buildx imagetools inspect ghcr.io/sirterrific/tubetamer:X.Y.Z`.
