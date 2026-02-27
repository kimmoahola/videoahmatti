#!/usr/bin/env bash
set -euo pipefail

HOST="${DEPLOY_HOST:-}"
IMAGE="${DEPLOY_IMAGE:-videoahmatti:latest}"
CONTAINER="${DEPLOY_CONTAINER:-videoahmatti}"
REMOTE_VIDEOS="${DEPLOY_REMOTE_VIDEOS:-/mnt/storagebox}"
REMOTE_DATA="${DEPLOY_REMOTE_DATA:-/videoahmatti}"
PORT="${DEPLOY_PORT:-8080}"
LOCAL_CONTEXT="${DEPLOY_LOCAL_CONTEXT:-.}"

usage() {
  cat <<EOF
Usage: scripts/deploy.sh --host user@vm [options]

Options:
  --host <user@vm>            Remote SSH target (required)
  --image <name:tag>          Docker image name/tag (default: videoahmatti:latest)
  --container <name>          Container name (default: videoahmatti)
  --remote-videos <path>      Remote host path mounted to /app/videos (default: /srv/videoahmatti/videos)
  --remote-data <path>        Remote host path mounted to /app/data (default: /srv/videoahmatti/data)
  --port <port>               Host port mapped to container 8080 (default: 8080)
  --context <path>            Local docker build context (default: .)
  --help                      Show this help

Environment variable equivalents:
  DEPLOY_HOST, DEPLOY_IMAGE, DEPLOY_CONTAINER,
  DEPLOY_REMOTE_VIDEOS, DEPLOY_REMOTE_DATA, DEPLOY_PORT, DEPLOY_LOCAL_CONTEXT
EOF
}

while (($#)); do
  case "$1" in
    --host)
      HOST="$2"
      shift 2
      ;;
    --image)
      IMAGE="$2"
      shift 2
      ;;
    --container)
      CONTAINER="$2"
      shift 2
      ;;
    --remote-videos)
      REMOTE_VIDEOS="$2"
      shift 2
      ;;
    --remote-data)
      REMOTE_DATA="$2"
      shift 2
      ;;
    --port)
      PORT="$2"
      shift 2
      ;;
    --context)
      LOCAL_CONTEXT="$2"
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$HOST" ]]; then
  echo "Missing required --host (or DEPLOY_HOST)" >&2
  usage
  exit 1
fi

echo "[1/4] Building image: $IMAGE"
docker build -t "$IMAGE" "$LOCAL_CONTEXT"

echo "[2/4] Transferring image to $HOST"
docker save "$IMAGE" | ssh "$HOST" 'sudo docker load'

echo "[3/4] Uploading remote deploy script"
REMOTE_SCRIPT="/tmp/videoahmatti-deploy-$(date +%s).sh"

cat <<'REMOTE_DEPLOY' | ssh "$HOST" "cat > '$REMOTE_SCRIPT' && chmod +x '$REMOTE_SCRIPT'"
#!/usr/bin/env bash
set -euo pipefail

CONTAINER="$1"
IMAGE="$2"
REMOTE_VIDEOS="$3"
REMOTE_DATA="$4"
PORT="$5"

mkdir -p "$REMOTE_VIDEOS" "$REMOTE_DATA"

docker stop "$CONTAINER" >/dev/null 2>&1 || true
docker rm "$CONTAINER" >/dev/null 2>&1 || true

set -x
docker run -d \
  --name "$CONTAINER" \
  --restart unless-stopped \
  -p "$PORT:8080" \
  -v "$REMOTE_VIDEOS:/app/videos" \
  -v "$REMOTE_DATA:/app/data" \
  "$IMAGE"
REMOTE_DEPLOY

echo "[4/4] Executing remote deploy script with sudo"
ssh "$HOST" "sudo '$REMOTE_SCRIPT' '$CONTAINER' '$IMAGE' '$REMOTE_VIDEOS' '$REMOTE_DATA' '$PORT'; sudo rm -f '$REMOTE_SCRIPT'"

echo "Deploy complete."
