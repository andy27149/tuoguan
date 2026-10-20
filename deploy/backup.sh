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
source deploy/lib/cleanup_old_files.sh
source deploy/lib/single_instance_lock.sh

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

# 备份文件是全机构学生/账单等真实数据的全量 dump，不能让同机其他账户/进程随便读到。
# umask 只影响这个脚本之后新建的文件/目录，不影响 BACKUP_DIR 之前已经存在的权限，
# 所以下面 mkdir -p 之后还要再显式 chmod 一次兜底。
umask 077

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

if ! [[ "$RETENTION_DAYS" =~ ^[0-9]+$ ]]; then
    echo "错误：--retention-days 必须是非负整数，收到的是: ${RETENTION_DAYS}" >&2
    exit 1
fi

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

# 同一把锁保护 cron 定时备份和 deploy.sh 的迁移前备份不会并发争用同一个 mysqldump/
# tar 进程（两者理论上可能撞在同一时刻）。
acquire_single_instance_lock "$BACKUP_DIR/.backup.lock" "backup.sh"

TIMESTAMP="$(date +%F_%H%M%S)"
MYSQL_FILE=""
MINIO_FILE=""

# 任何一步中途失败（比如 mysql 容器没在跑），都不能把半成品文件留在 backups/ 目录里，
# 冒充一份"看起来存在、实际恢复出来是空库/空文件"的备份——这比完全没有备份更危险。
# 统一写到 .tmp 后缀，成功后才 rename 成正式文件名；失败时用 trap 兜底删掉 .tmp 残留。
cleanup_incomplete() {
    [[ -n "$MYSQL_FILE" ]] && rm -f "${MYSQL_FILE}.tmp"
    [[ -n "$MINIO_FILE" ]] && rm -f "${MINIO_FILE}.tmp"
}
trap cleanup_incomplete EXIT

if [[ "$DO_MYSQL" -eq 1 ]]; then
    MYSQL_FILE="$BACKUP_DIR/mysql_${TIMESTAMP}${TAG}.sql.gz"
    echo "==> 备份 MySQL 到 $MYSQL_FILE"
    # 密码始终从容器自身的环境变量里取，不会出现在本机终端历史或进程列表里。
    $COMPOSE exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --all-databases' \
        | gzip > "${MYSQL_FILE}.tmp"
    chmod 600 "${MYSQL_FILE}.tmp"
    mv "${MYSQL_FILE}.tmp" "$MYSQL_FILE"
    echo "    完成，大小：$(du -h "$MYSQL_FILE" | cut -f1)"
fi

if [[ "$DO_MINIO" -eq 1 ]]; then
    # 不去猜"compose 项目名是不是等于目录名"（clone 到别的目录名、或设置过
    # COMPOSE_PROJECT_NAME 时这个假设就不成立），也不做无限定的全局 volume 名模糊
    # 匹配（同一台机器上跑着别的项目、卷名碰巧也以 _minio_data 结尾时会选错，静默
    # 备份了完全无关的数据）。直接问 minio 容器自己挂载在 /data 的是哪个 volume，
    # 这个信息绑定到"这个 compose 项目实际在跑的容器"，不依赖任何命名约定。
    MINIO_CONTAINER="$($COMPOSE ps -q minio)"
    if [[ -z "$MINIO_CONTAINER" ]]; then
        echo "错误：minio 容器当前未运行，无法确定要备份哪个 volume（备份 MinIO 需要容器处于运行状态）。" >&2
        exit 1
    fi
    MINIO_VOLUME="$(docker inspect --format '{{ range .Mounts }}{{ if eq .Destination "/data" }}{{ .Name }}{{ end }}{{ end }}' "$MINIO_CONTAINER")"
    if [[ -z "$MINIO_VOLUME" ]]; then
        echo "错误：找不到 minio 容器挂载在 /data 路径的 volume。" >&2
        exit 1
    fi
    MINIO_FILE="$BACKUP_DIR/minio_${TIMESTAMP}${TAG}.tar.gz"
    echo "==> 备份 MinIO（volume: ${MINIO_VOLUME}）到 $MINIO_FILE"
    docker run --rm -v "$MINIO_VOLUME":/data -v "$BACKUP_DIR":/backup alpine \
        tar czf "/backup/$(basename "$MINIO_FILE").tmp" -C /data .
    # 这个文件是容器里的 root 用 tar 创建的，用的是容器自己的 umask（默认 022），
    # 不会继承宿主机这边 `umask 077` 的设置，所以必须显式 chmod，不能只靠 umask。
    chmod 600 "${MINIO_FILE}.tmp"
    mv "${MINIO_FILE}.tmp" "$MINIO_FILE"
    echo "    完成，大小：$(du -h "$MINIO_FILE" | cut -f1)"
fi

# 备份文件名的两种后缀模式只在这一处定义，清理和下面统计"还剩几个备份文件"都从
# 这个数组派生，不再各自重复写一遍——以前两处分别手写过一份一样的 -name 'xxx' -o
# -name 'yyy'，以后要是加第三种备份类型，容易改了一处忘了另一处，数量统计就会
# 悄悄算错。
BACKUP_FILE_PATTERNS=('mysql_*.sql.gz' 'minio_*.tar.gz')

if [[ "$RETENTION_DAYS" -gt 0 ]]; then
    echo "==> 清理 $RETENTION_DAYS 天之前的备份文件"
    cleanup_old_files "$BACKUP_DIR" "$RETENTION_DAYS" "${BACKUP_FILE_PATTERNS[@]}"
fi

_build_find_name_args "${BACKUP_FILE_PATTERNS[@]}"
BACKUP_FILE_COUNT="$(find "$BACKUP_DIR" -maxdepth 1 -type f \( "${FIND_NAME_ARGS[@]}" \) | wc -l | tr -d ' ')"
echo "==> 备份完成：$BACKUP_FILE_COUNT 个备份文件保留在 $BACKUP_DIR"
