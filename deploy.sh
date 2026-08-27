#!/usr/bin/env bash
# 一键部署/更新脚本。用法：
#   ./deploy.sh          # 拉取最新代码 + 重新构建 + 启动
#   ./deploy.sh --no-pull  # 跳过 git pull，只重新构建 + 启动（适合本地已改好代码的情况）
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"

if [[ ! -f .env ]]; then
    echo "错误：找不到 .env 文件。" >&2
    echo "请先执行: cp .env.example .env 然后编辑 .env 填入生产环境的强密码/密钥。" >&2
    exit 1
fi

PULL=1
for arg in "$@"; do
    case "$arg" in
        --no-pull) PULL=0 ;;
        *) echo "未知参数: $arg" >&2; exit 1 ;;
    esac
done

if [[ "$PULL" -eq 1 ]]; then
    if [[ -n "$(git status --porcelain)" ]]; then
        echo "错误：工作区有未提交的改动，为避免覆盖，已取消自动 git pull。" >&2
        echo "请先 commit/stash，或使用 ./deploy.sh --no-pull 跳过拉取。" >&2
        exit 1
    fi
    echo "==> 拉取最新代码"
    git pull
fi

echo "==> 构建镜像"
$COMPOSE build

echo "==> 启动/更新容器"
$COMPOSE up -d

echo "==> 当前容器状态"
$COMPOSE ps

echo
echo "部署完成。查看后端启动日志: $COMPOSE logs -f backend"
