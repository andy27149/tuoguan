#!/usr/bin/env bash
# 一键部署/更新脚本。用法：
#   ./deploy.sh              # 拉取最新代码 + 重新构建 + 启动
#   ./deploy.sh --no-pull    # 跳过 git pull，只重新构建 + 启动（适合本地已改好代码的情况）
#   ./deploy.sh --no-cache   # 构建时不用 Docker 缓存（排查"缓存了不完整构建产物"类问题时用）
#
# 每次运行的完整输出（含 docker build 的全部过程）会落盘保存到
# logs/deploy_<时间戳>.log（可用 LOG_DIR 覆盖目录、LOG_RETENTION_DAYS 覆盖保留
# 天数，默认 14 天）。日志文件是这个脚本自己的 stdout/stderr 直接 `exec >>`
# 追加写入，不经过任何中间进程转发；另起一个 `tail -f` 子进程把内容镜像回终端，
# 方便交互式运行时实时看效果，但它只是锦上添花的旁路——哪怕 SSH 连接意外断开、
# 这个镜像进程先一步被杀掉，已经落盘的内容和脚本退出时 EXIT trap 打的最后一行
# 都不会因此丢失（早期版本用 `exec > >(tee ...) 2>&1`，实测验证过 SIGHUP 场景下
# tee 可能先于主脚本死掉，导致 trap 自己那行诊断信息因为管道另一端已经没人读而
# 丢失——这恰好是本脚本最需要保留的那一行，所以改成现在这种直接写文件的方式）。
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

LOG_DIR="${LOG_DIR:-$(pwd)/logs}"
LOG_RETENTION_DAYS="${LOG_RETENTION_DAYS:-14}"
mkdir -p "$LOG_DIR"

# 防止两次 deploy.sh 并发跑：不仅日志文件名按秒级时间戳会撞车、互相追加写乱掉，
# 更根本的是 git pull / docker build / up -d（触发 Flyway 迁移）本身就不允许并发
# 执行。flock 是 Ubuntu/Debian/CentOS 生产服务器自带的 util-linux 标配命令，跟
# deploy/backup.sh 的做法一致；本机没有也不影响功能，只是失去这层互斥保护。
LOCK_FILE="$LOG_DIR/.deploy.lock"
if command -v flock >/dev/null 2>&1; then
    exec 9>"$LOCK_FILE"
    if ! flock -n 9; then
        echo "错误：已有另一个 deploy.sh 在运行（锁文件 $LOCK_FILE），本次退出，避免并发执行 git pull/docker build/up -d。" >&2
        exit 1
    fi
fi

LOG_FILE="$LOG_DIR/deploy_$(date +%Y-%m-%d_%H%M%S).log"
: > "$LOG_FILE"
tail -f "$LOG_FILE" &
TAIL_PID=$!
exec >> "$LOG_FILE" 2>&1

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

trap '
rc=$?
if [[ "$rc" -eq 0 ]]; then
    log "部署脚本正常结束"
else
    log "部署脚本异常退出，退出码：${rc}（如果上面没看到\"部署完成\"字样，说明流程中断/失败，需要排查）"
fi
kill "$TAIL_PID" 2>/dev/null || true
wait "$TAIL_PID" 2>/dev/null || true
' EXIT

source deploy/lib/cleanup_old_files.sh

log "==> 部署开始，完整日志：$LOG_FILE"

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
    log "==> 拉取最新代码"
    git pull
fi

log "==> 构建镜像"
if [[ "$NO_CACHE" -eq 1 ]]; then
    $COMPOSE build --no-cache
else
    $COMPOSE build
fi
log "==> 镜像构建完成"

# 产品诊断 #59：backend 容器启动时 Flyway 会自动跑数据库迁移，历史上出现过 V15/V17
# 这种清空旧数据重录的破坏性迁移。mysql 容器当前确实在运行时（说明不是首次部署，
# 里面有历史数据可以在线 dump），在 `up -d` 真正触发这次迁移之前，先做一次
# MySQL-only 的快照——万一这次迁移中途失败或者逻辑有问题，可以直接回滚这个备份，
# 而不是依赖上一次 cron 定时备份（可能是几小时甚至一天前的数据）。
# 用 `$COMPOSE ps -q` 拿容器 ID 再 `docker inspect .State.Running` 判断是否在跑，
# 比解析 `docker compose ps` 的文字输出更可靠（后者的格式在 v1/v2 之间不一致）；
# 也比只看数据 volume 是否存在更准确——volume 存在但容器被手动停过（没有 -v）的
# 情况下，mysql 并不在线，backup.sh 里的 mysqldump 必然连不上，不如在这里就明确
# 跳过并提示，而不是让构建完镜像、还没 up -d 的部署流程因为这个失败而诡异中止。
MYSQL_CONTAINER_ID="$($COMPOSE ps -q mysql 2>/dev/null || true)"
if [[ -n "$MYSQL_CONTAINER_ID" ]] && [[ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER_ID" 2>/dev/null)" == "true" ]]; then
    log "==> 迁移前自动备份 MySQL（即将执行的 up -d 会触发 Flyway 迁移）"
    ./deploy/backup.sh --mysql-only --tag=predeploy
elif [[ -n "$($COMPOSE ps -aq mysql 2>/dev/null || true)" ]]; then
    # 用 `ps -aq`（包含已停止的容器）判断"这个 compose 项目是否曾经创建过 mysql
    # 容器"，而不是像上面判断 MinIO volume 时提醒过的那样去猜 volume 名字/做无
    # 限定的全局模糊匹配——那种写法在同机跑着别的项目、卷名碰巧也以 _mysql_data
    # 结尾时，会把"首次部署"误判成"容器被停过"，纯粹是这条提示文案说错原因，
    # 不影响后面 up -d 是否继续执行，但没必要留着这个不一致。
    log "==> 跳过迁移前备份：mysql 容器存在但当前未运行（可能被手动停过），无法在线备份。"
    echo "    up -d 启动、确认服务恢复正常后，建议手动跑一次 ./deploy/backup.sh 补一份快照。"
else
    log "==> 跳过迁移前备份（mysql 容器尚未创建过，视为首次部署，没有历史数据）"
fi

log "==> 启动/更新容器"
$COMPOSE up -d
log "==> 容器启动命令执行完成"

log "==> 当前容器状态"
$COMPOSE ps

log "==> 清理 $LOG_RETENTION_DAYS 天之前的部署日志文件"
cleanup_old_files "$LOG_DIR" "$LOG_RETENTION_DAYS" 'deploy_*.log'

echo
log "部署完成。查看后端启动日志: $COMPOSE logs -f backend"
log "本次完整部署日志：$LOG_FILE"
