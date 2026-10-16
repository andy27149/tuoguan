#!/usr/bin/env bash
# 一键部署/更新脚本。用法：
#   ./deploy.sh              # 拉取最新代码 + 重新构建 + 启动
#   ./deploy.sh --no-pull    # 跳过 git pull，只重新构建 + 启动（适合本地已改好代码的情况）
#   ./deploy.sh --no-cache   # 构建时不用 Docker 缓存（排查"缓存了不完整构建产物"类问题时用）
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose -f docker-compose.yml -f docker-compose.prod.yml"
else
    echo "错误：找不到 docker compose（v2 插件）或 docker-compose（v1 独立命令），请先安装其中一个。" >&2
    exit 1
fi

if [[ ! -f .env ]]; then
    echo "错误：找不到 .env 文件。" >&2
    echo "请先执行: cp .env.example .env 然后编辑 .env 填入生产环境的强密码/密钥。" >&2
    exit 1
fi

PULL=1
NO_CACHE=0
for arg in "$@"; do
    case "$arg" in
        --no-pull) PULL=0 ;;
        --no-cache) NO_CACHE=1 ;;
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
if [[ "$NO_CACHE" -eq 1 ]]; then
    $COMPOSE build --no-cache
else
    $COMPOSE build
fi

# 产品诊断 #59：backend 容器启动时 Flyway 会自动跑数据库迁移，历史上出现过 V15/V17
# 这种清空旧数据重录的破坏性迁移。只要 mysql 的数据 volume 已经存在（说明不是首次
# 部署，里面有历史数据），在 `up -d` 真正触发这次迁移之前，先做一次 MySQL-only 的
# 快照——万一这次迁移中途失败或者逻辑有问题，可以直接回滚这个备份，而不是依赖上一次
# cron 定时备份（可能是几小时甚至一天前的数据）。用 volume 是否存在判断而不是解析
# `docker compose ps` 的文字输出，是因为后者的格式在 v1/v2 之间不一致，容易误判。
if docker volume ls --format '{{.Name}}' | grep -q '_mysql_data$'; then
    echo "==> 迁移前自动备份 MySQL（即将执行的 up -d 会触发 Flyway 迁移）"
    ./deploy/backup.sh --mysql-only --tag=predeploy
else
    echo "==> 跳过迁移前备份（mysql 数据 volume 尚不存在，视为首次部署，没有历史数据）"
fi

echo "==> 启动/更新容器"
$COMPOSE up -d

echo "==> 当前容器状态"
$COMPOSE ps

echo
echo "部署完成。查看后端启动日志: $COMPOSE logs -f backend"
