#!/usr/bin/env bash
# 备份 MySQL（逻辑备份，mysqldump）和 MinIO（volume 文件级打包），供两种场景使用：
#   1. 配成 cron 定时任务做日常备份（见 DEPLOY.md「备份与恢复」一节）
#   2. deploy.sh 在每次 `up -d`（会触发 Flyway 自动迁移）之前调用，做一次迁移前快照
#
# 用法：
#   ./deploy/backup.sh                       # 备份 MySQL + MinIO
#   ./deploy/backup.sh --mysql-only          # 只备份 MySQL（deploy.sh 迁移前调用的模式）
#   ./deploy/backup.sh --minio-only          # 只备份 MinIO
#   ./deploy/backup.sh --tag=predeploy       # 文件名里加一段标签，方便区分来源
#   ./deploy/backup.sh --retention-days=30   # 自动清理这个天数之前的备份，默认 14 天
#
# 备份文件默认写到仓库根目录下的 backups/（已加入 .gitignore），可用 BACKUP_DIR
# 环境变量覆盖成其他路径（比如挂载了额外磁盘或异地同步工具监听的目录）。
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose -f docker-compose.yml -f docker-compose.prod.yml"
else
    echo "错误：找不到 docker compose（v2 插件）或 docker-compose（v1 独立命令）。" >&2
    exit 1
fi

if [[ ! -f .env ]]; then
    echo "错误：找不到 .env 文件，无法读取数据库密码。" >&2
    exit 1
fi

BACKUP_DIR="${BACKUP_DIR:-$(pwd)/backups}"
RETENTION_DAYS=14
TAG=""
DO_MYSQL=1
DO_MINIO=1

for arg in "$@"; do
    case "$arg" in
        --mysql-only) DO_MINIO=0 ;;
        --minio-only) DO_MYSQL=0 ;;
        --tag=*) TAG="_${arg#--tag=}" ;;
        --retention-days=*) RETENTION_DAYS="${arg#--retention-days=}" ;;
        *) echo "未知参数: ${arg}（标签/保留天数请用 --tag=xxx / --retention-days=N 的等号写法）" >&2; exit 1 ;;
    esac
done

mkdir -p "$BACKUP_DIR"
TIMESTAMP="$(date +%F_%H%M%S)"

if [[ "$DO_MYSQL" -eq 1 ]]; then
    MYSQL_FILE="$BACKUP_DIR/mysql_${TIMESTAMP}${TAG}.sql.gz"
    echo "==> 备份 MySQL 到 $MYSQL_FILE"
    # 密码始终从容器自身的环境变量里取，不会出现在本机终端历史或进程列表里。
    $COMPOSE exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --all-databases' \
        | gzip > "$MYSQL_FILE"
    echo "    完成，大小：$(du -h "$MYSQL_FILE" | cut -f1)"
fi

if [[ "$DO_MINIO" -eq 1 ]]; then
    MINIO_VOLUME="$(basename "$(pwd)")_minio_data"
    if ! docker volume inspect "$MINIO_VOLUME" >/dev/null 2>&1; then
        # docker compose 项目名默认取目录名，但可能被显式覆盖过，这里兜底扫描一次。
        MINIO_VOLUME="$(docker volume ls --format '{{.Name}}' | grep '_minio_data$' | head -n1)"
    fi
    if [[ -z "$MINIO_VOLUME" ]]; then
        echo "错误：找不到 MinIO 的 Docker volume（期望名字以 _minio_data 结尾）。" >&2
        exit 1
    fi
    MINIO_FILE="$BACKUP_DIR/minio_${TIMESTAMP}${TAG}.tar.gz"
    echo "==> 备份 MinIO（volume: ${MINIO_VOLUME}）到 $MINIO_FILE"
    docker run --rm -v "$MINIO_VOLUME":/data -v "$BACKUP_DIR":/backup alpine \
        tar czf "/backup/$(basename "$MINIO_FILE")" -C /data .
    echo "    完成，大小：$(du -h "$MINIO_FILE" | cut -f1)"
fi

if [[ "$RETENTION_DAYS" -gt 0 ]]; then
    echo "==> 清理 $RETENTION_DAYS 天之前的备份文件"
    find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'mysql_*.sql.gz' -o -name 'minio_*.tar.gz' \) \
        -mtime "+$RETENTION_DAYS" -print -delete
fi

echo "==> 备份完成：$(ls -1 "$BACKUP_DIR" | wc -l | tr -d ' ') 个文件保留在 $BACKUP_DIR"
