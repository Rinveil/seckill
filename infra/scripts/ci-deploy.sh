#!/usr/bin/env bash
# push 到 GitHub 后由 self-hosted runner 调用：构建镜像 + 部署到本机 Docker Desktop K8s
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "==> $(date '+%F %T') ci-deploy start"

./infra/scripts/build-images.sh
./infra/scripts/deploy-k8s.sh

echo "==> $(date '+%F %T') ci-deploy done"
