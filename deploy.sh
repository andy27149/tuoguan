#!/usr/bin/env bash
# 一键部署/更新脚本。用法：
#   ./deploy.sh              # 拉取最新代码 + 重新构建 + 启动
#   ./deploy.sh --no-pull    # 跳过 git pull，只重新构建 + 启动（适合本地已改好代码的情况）
#   ./deploy.sh --no-cache   # 构建时不用 Docker 缓存（排查"缓存了不完整构建产物"类问题时用）
#
# 每次运行的完整输出（含 docker build 的全部过程）会落盘保存到
# logs/deploy_<时间戳>.log（可用 LOG_DIR 覆盖目录、LOG_RETENTION_DAYS 覆盖保留
# 天数，默认 14 天，0 表示不清理）。日志文件是这个脚本自己的 stdout/stderr 直接
# `exec >>` 追加写入，不经过任何中间进程转发；接在终端上跑的时候，另起一个
# `tail -f` 子进程把内容镜像回终端方便实时看效果（非交互场景——比如 bootstrap.sh
# 内部调用——不会起这个镜像进程），但它只是锦上添花的旁路——哪怕 SSH 连接意外
# 断开、这个镜像进程先一步被杀掉，已经落盘的内容和脚本退出时 EXIT trap 打的最后
# 一行都不会因此丢失（早期版本用 `exec > >(tee ...) 2>&1`，实测验证过 SIGHUP
# 场景下 tee 可能先于主脚本死掉，导致 trap 自己那行诊断信息因为管道另一端已经
# 没人读而丢失——这恰好是本脚本最需要保留的那一行，所以改成现在这种直接写文件
# 的方式）。同一次运行同一时间只允许存在一个实例（flock），冲突时会报错退出。
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

# 参数校验放在这里（任何有副作用的操作——加锁、建日志文件——之前），这样打错参数
# 时直接报错退出，不会先占了锁、建了日志文件又立刻释放，给同时启动的另一次正常
# 部署制造一个看起来像"已有实例在跑"、实际上是误触发的假警告。
PULL=1
NO_CACHE=0
for arg in "$@"; do
    case "$arg" in
        --no-pull) PULL=0 ;;
        --no-cache) NO_CACHE=1 ;;
        *) echo "未知参数: $arg" >&2; exit 1 ;;
    esac
done

LOG_DIR="${LOG_DIR:-$(pwd)/logs}"
LOG_RETENTION_DAYS="${LOG_RETENTION_DAYS:-14}"
if ! [[ "$LOG_RETENTION_DAYS" =~ ^[0-9]+$ ]]; then
    echo "错误：LOG_RETENTION_DAYS 必须是非负整数，收到的是: ${LOG_RETENTION_DAYS}" >&2
    exit 1
fi
mkdir -p "$LOG_DIR"
# logs/ 只装非敏感的部署过程日志（真正敏感的备份数据在 deploy/backup.sh 自己管
# 的 backups/，那边权限收紧到 700，跟这里不一样）。放宽成所有人可写，是因为这个
# 目录可能先被 root 创建——bootstrap.sh 一次性初始化时以 root 身份运行，内部会
# 调用一次 `./deploy.sh --no-pull`——也可能先被日常跑 deploy.sh 的普通用户创建
# （DEPLOY.md「日常更新部署」一节写的就是不带 sudo）。不管谁先创建，换另一个身份
# 跑都不该被目录权限卡住，这是 review 用 bootstrap.sh + DEPLOY.md 的实际流程交叉
# 验证出来的真实问题：目录/锁文件一旦先被 root 创建成收敛权限，后续所有非 root
# 的 deploy.sh 都会直接 Permission denied，永久失败。
chmod 777 "$LOG_DIR" 2>/dev/null || true

# 防止两次 deploy.sh 并发跑：不仅日志文件名按秒级时间戳会撞车、互相追加写乱掉，
# 更根本的是 git pull / docker build / up -d（触发 Flyway 迁移）本身就不允许并发
# 执行。
source deploy/lib/single_instance_lock.sh
acquire_single_instance_lock "$LOG_DIR/.deploy.lock" "deploy.sh"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

LOG_FILE="$LOG_DIR/deploy_$(date +%Y-%m-%d_%H%M%S).log"
: > "$LOG_FILE"

# 只有接在真正的终端上才起 tail -f 做实时镜像——bootstrap.sh 内部调用、或者以后
# 万一被 cron 调用时，根本没人盯着这个输出，起这个镜像进程纯粹是白白常驻一个
# 后台进程、多一份 trap 里的 kill/wait 记账。
TAIL_PID=""
if [[ -t 1 ]]; then
    # `9>&-` 显式关掉这个后台 tail 继承到的锁文件描述符：flock 的锁绑定在"打开
    # 文件描述"上而不是绑定在进程上，如果不关掉，一旦 deploy.sh 被 SIGKILL（人工
    # kill -9、OOM killer）来不及走 EXIT trap 去杀这个 tail，残留的 tail 进程会
    # 一直攥着这份锁的文件描述符不放，导致锁永远不会释放，后面所有 deploy.sh 都
    # 会被误判成"已有实例在跑"，必须人工找到并杀掉这个残留进程才能恢复——这是
    # review 里实测会发生的问题。
    tail -f "$LOG_FILE" 9>&- &
    TAIL_PID=$!
fi

# trap 要在下面这行有风险的 `exec >>`（万一磁盘满了、文件系统变只读）之前就注册
# 好，否则这个 exec 一失败，set -e 直接退出，TAIL_PID 对应的 tail 进程就没人去
# kill 了，变成一个孤儿进程（不会卡锁，但白白漏一个进程在后台跑）。
trap '
rc=$?
if [[ "$rc" -eq 0 ]]; then
    log "部署脚本正常结束"
else
    log "部署脚本异常退出，退出码：${rc}（如果上面没看到\"部署完成\"字样，说明流程中断/失败，需要排查）"
fi
if [[ -n "$TAIL_PID" ]]; then
    kill "$TAIL_PID" 2>/dev/null || true
    wait "$TAIL_PID" 2>/dev/null || true
fi
' EXIT

exec >> "$LOG_FILE" 2>&1

# 前面只给 tail 加了 `9>&-`，只解决了它一个人的问题——git pull / docker build
# （可能跑几十分钟甚至卡住）/ up -d / backup.sh 这些会 fork 外部进程的调用，一样
# 会继承这份锁的文件描述符：deploy.sh 一旦被 SIGKILL/OOM killer 杀掉、这些子
# 进程变成孤儿继续跑，就会复现跟 tail 完全一样的"锁永远不释放"问题，只是换了个
# 更容易被忽略的地方。统一用这个函数包一层来关掉继承来的 fd 9，以后新增会 fork
# 子进程的调用也套用它，不用每次都记得自己写一遍 `9>&-`。
run_unlocked() {
    "$@" 9>&-
}

source deploy/lib/cleanup_old_files.sh

log "==> 部署开始，完整日志：$LOG_FILE"

if [[ "$PULL" -eq 1 ]]; then
    if [[ -n "$(git status --porcelain)" ]]; then
        echo "错误：工作区有未提交的改动，为避免覆盖，已取消自动 git pull。" >&2
        echo "请先 commit/stash，或使用 ./deploy.sh --no-pull 跳过拉取。" >&2
        exit 1
    fi
    log "==> 拉取最新代码"
    run_unlocked git pull
fi

log "==> 构建镜像"
if [[ "$NO_CACHE" -eq 1 ]]; then
    run_unlocked $COMPOSE build --no-cache
else
    run_unlocked $COMPOSE build
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
MYSQL_CONTAINER_ID="$(run_unlocked $COMPOSE ps -q mysql 2>/dev/null || true)"
if [[ -n "$MYSQL_CONTAINER_ID" ]] && [[ "$(run_unlocked docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER_ID" 2>/dev/null)" == "true" ]]; then
    log "==> 迁移前自动备份 MySQL（即将执行的 up -d 会触发 Flyway 迁移）"
    run_unlocked ./deploy/backup.sh --mysql-only --tag=predeploy
elif [[ -n "$(run_unlocked $COMPOSE ps -aq mysql 2>/dev/null || true)" ]]; then
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
run_unlocked $COMPOSE up -d
log "==> 容器启动命令执行完成"

log "==> 当前容器状态"
run_unlocked $COMPOSE ps

if [[ "$LOG_RETENTION_DAYS" -gt 0 ]]; then
    log "==> 清理 $LOG_RETENTION_DAYS 天之前的部署日志文件"
    # 清理旧日志纯粹是锦上添花，发生在 up -d 已经成功之后，不该因为某个旧文件
    # 删除失败（权限变化等）就让 set -e 把这次已经成功的部署打成"异常退出"。
    if ! cleanup_old_files "$LOG_DIR" "$LOG_RETENTION_DAYS" 'deploy_*.log'; then
        log "警告：清理旧部署日志失败，不影响本次部署结果，建议手动检查 $LOG_DIR 的磁盘占用和权限"
    fi
fi

echo
log "部署完成。查看后端启动日志: $COMPOSE logs -f backend"
log "本次完整部署日志：$LOG_FILE"
