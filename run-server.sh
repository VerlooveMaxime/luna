#!/usr/bin/env bash
# Launches the Luna #377 server (port 43594, see data/luna.jsonc).
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p data/game/bots/saved_bots data/game/saved_players data/logs
exec ./gradlew run --console=plain -q "$@"
