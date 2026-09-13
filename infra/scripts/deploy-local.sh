#!/usr/bin/env bash
# 本机一键部署：构建镜像 → 应用到 Docker Desktop K8s
#
# 用法（仓库根目录）：
#   ./infra/scripts/deploy-local.sh                 # 全量构建全部服务
#   ./infra/scripts/deploy-local.sh order web       # 只构建并重启指定模块
#   ./infra/scripts/deploy-local.sh activity order  # 活动/订单相关改动常用
#
# 可用模块名：
#   gateway | user | activity | core | order | web
#   或完整名：seckill-gateway / seckill-web …
#
# 环境变量：
#   TAG=0.1.0 PLATFORM=linux/arm64 SKIP_BUILD=1  # 仅 apply + restart，不构建

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

PLATFORM="${PLATFORM:-linux/arm64}"
TAG="${TAG:-0.1.0}"
SKIP_BUILD="${SKIP_BUILD:-0}"

normalize() {
  case "$1" in
    gateway|user|activity|core|order) echo "seckill-$1" ;;
    web|seckill-web) echo "seckill-web" ;;
    seckill-gateway|seckill-user|seckill-activity|seckill-core|seckill-order) echo "$1" ;;
    all) echo "all" ;;
    *)
      echo "未知模块: $1" >&2
      echo "可用: gateway user activity core order web 或 all" >&2
      exit 1
      ;;
  esac
}

contains() {
  local needle="$1"
  shift
  local x
  for x in "$@"; do
    [[ "$x" == "$needle" ]] && return 0
  done
  return 1
}

JAVA_MODULES=""
BUILD_WEB=0
RESTART_LIST=""

add_restart() {
  if ! contains "$1" $RESTART_LIST; then
    RESTART_LIST="${RESTART_LIST} $1"
  fi
}

if [[ $# -eq 0 ]]; then
  JAVA_MODULES="seckill-gateway seckill-user seckill-activity seckill-core seckill-order"
  BUILD_WEB=1
  RESTART_LIST="seckill-user seckill-activity seckill-core seckill-order seckill-gateway seckill-web"
else
  for raw in "$@"; do
    m="$(normalize "$raw")"
    if [[ "$m" == "all" ]]; then
      JAVA_MODULES="seckill-gateway seckill-user seckill-activity seckill-core seckill-order"
      BUILD_WEB=1
      RESTART_LIST="seckill-user seckill-activity seckill-core seckill-order seckill-gateway seckill-web"
      break
    fi
    if [[ "$m" == "seckill-web" ]]; then
      BUILD_WEB=1
      add_restart "seckill-web"
    else
      JAVA_MODULES="${JAVA_MODULES} ${m}"
      add_restart "$m"
    fi
  done
fi

echo "==> $(date '+%F %T') deploy-local start"
echo "==> platform=${PLATFORM} tag=${TAG}"

if [[ "${SKIP_BUILD}" != "1" ]]; then
  for m in $JAVA_MODULES; do
    echo "==> building ${m}:${TAG}"
    docker build \
      --platform "${PLATFORM}" \
      -f infra/docker/Dockerfile.java \
      --build-arg "MODULE=${m}" \
      -t "${m}:${TAG}" \
      .
  done

  if [[ "${BUILD_WEB}" == "1" ]]; then
    echo "==> building seckill-web:${TAG}"
    docker build \
      --platform "${PLATFORM}" \
      -f infra/docker/Dockerfile.web \
      -t "seckill-web:${TAG}" \
      .
  fi
else
  echo "==> SKIP_BUILD=1，跳过镜像构建"
fi

echo "==> kubectl apply"
kubectl apply -f infra/k8s/

if [[ -n "$(echo "$RESTART_LIST" | tr -d '[:space:]')" ]]; then
  echo "==> rollout restart:${RESTART_LIST}"
  for d in $RESTART_LIST; do
    kubectl -n seckill rollout restart "deploy/${d}"
  done
  for d in $RESTART_LIST; do
    kubectl -n seckill rollout status "deploy/${d}" --timeout=240s
  done
fi

echo "==> pods:"
kubectl -n seckill get pods
echo "==> open http://localhost:30080"
echo "==> $(date '+%F %T') deploy-local done"
