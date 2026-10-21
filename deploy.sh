#!/usr/bin/env bash
# 一键部署/更新脚本。用法：
#   ./deploy.sh              # 拉取最新代码 + 重新构建 + 启动
#   ./deploy.sh --no-pull    # 跳过 git pull，只重新构建 + 启动（适合本地已改好代码的情况）
#   ./deploy.sh --no-cache   # 构建时不用 Docker 缓存（排查"缓存了不完整构建产物"类问题时用）
#
# 每次运行的完整输出（含 docker build 的全部过程）会落盘保存到
# logs/deploy_<时间戳>.log（可用 LOG_DIR 覆盖目录、LOG_RETENTION_DAYS 覆盖保留
# 天数，默认 14 天，0 表示不清理）。日志文件是这个脚本自己的 stdout/stderr 直接
# `exec >>` 追加写入，不经过任何中间进程转发；另起一个 `tail -f` 子进程把内容
# 镜像回原来的 stdout（不管那是真终端、还是调用方自己设置的重定向/管道），它只是
# 锦上添花的旁路——哪怕 SSH 连接意外断开、这个镜像进程先一步被杀掉，已经落盘的
# 内容和脚本退出时 EXIT trap 打的最后一行都不会因此丢失（早期版本用
# `exec > >(tee ...) 2>&1`，实测验证过 SIGHUP 场景下 tee 可能先于主脚本死掉，
# 导致 trap 自己那行诊断信息因为管道另一端已经没人读而丢失——这恰好是本脚本最
# 需要保留的那一行，所以改成现在这种直接写文件的方式）。同一次运行同一时间只
# 允许存在一个实例（flock），冲突时会报错退出。
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

source deploy/lib/cleanup_old_files.sh
source deploy/lib/single_instance_lock.sh

LOG_DIR="${LOG_DIR:-$(pwd)/logs}"
LOG_RETENTION_DAYS="${LOG_RETENTION_DAYS:-14}"
validate_retention_days "$LOG_RETENTION_DAYS" "LOG_RETENTION_DAYS"
# `-m 700` 让目录在刚被创建出来的那一刻就是 700，不依赖运行时的 umask，也不给
# "mkdir 和 chmod 是两条独立指令"留出窗口——如果部署账号/cron 的 umask 比较宽松
# （比如 002 甚至 000），光靠事后再补一次 chmod，中间仍然有一段目录已经建好、还
# 没来得及收紧权限的间隙，本地攻击者理论上可以抢在这个间隙里把锁文件名提前占成
# 指向任意文件的符号链接。下面这行 chmod 不是多余：`-m` 只在目录第一次被创建时
# 生效，如果目录是上一个版本的脚本建的、权限不对，`mkdir -p` 碰到已存在的目录
# 不会重新应用 `-m`，还是需要显式 chmod 兜底修正。跟 deploy/backup.sh 对
# BACKUP_DIR 的做法一致。chmod 失败时不直接中止（`||` 兜底）：如果这个目录是
# 之前一次以 root 身份跑、又没能成功转交属主（比如直接以 root 登录、仓库本身也是
# root clone 的，见 _chown_to_invoking_user_if_root 的注释），现在这个非 root
# 用户既不是属主也不是 root，chmod 必然失败——这种情况下目录权限已经不是这个脚本
# 能单方面纠正的了，打个警告让人知道去手动检查，总比直接把部署拦死要好。
mkdir -p -m 700 "$LOG_DIR"
chmod 700 "$LOG_DIR" 2>/dev/null || echo "警告：无法修改 $LOG_DIR 的权限（不是属主也不是 root？），继续使用现有权限，建议手动检查一下属主。" >&2

# logs/ 目录可能先被 root 创建——bootstrap.sh 一次性初始化时以 root 身份运行，
# 内部会调用一次 `./deploy.sh --no-pull`——也可能先被日常跑 deploy.sh 的普通
# 用户创建（DEPLOY.md「日常更新部署」一节写的就是不带 sudo）。早期版本为了让
# 两种身份都能用，把这个目录和锁文件权限放宽成所有人可写/可读写——但这会让任何
# 本地非特权用户提前把锁文件换成指向任意文件（比如 /root/.ssh/authorized_keys）
# 的符号链接，等下次 root 身份跑到这里直接截断、重新打开、chmod 成可写，变成一个
# 本地提权原语（已用符号链接实测复现）。正确做法不是放宽权限，而是用 root 身份跑
# 的时候把属主转给真实操作者，权限继续收紧，详见 deploy/lib/single_instance_
# lock.sh 里 _chown_to_invoking_user_if_root 的注释。
_chown_to_invoking_user_if_root "$LOG_DIR"

# 锁文件固定放在仓库根目录（这个脚本一开始就 `cd` 到这里了），不跟着 LOG_DIR 走：
# LOG_DIR 是特意设计成可以被 LOG_DIR 环境变量覆盖的（比如挪到另一块盘），如果
# 锁文件路径也跟着 LOG_DIR 变，两次用不同 LOG_DIR 启动的 deploy.sh（比如一次
# 手动跑用默认路径、一次被套了 LOG_DIR=/data/deploy-logs 的 systemd timer/cron
# 跑）就会分别锁住两个不同的文件，互相看不见对方、锁形同虚设——这恰好是加这把锁
# 本来要防的并发场景。
acquire_single_instance_lock "./.deploy.lock" "deploy.sh"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

LOG_FILE="$LOG_DIR/deploy_$(date +%Y-%m-%d_%H%M%S).log"
: > "$LOG_FILE"
# 跟 logs/ 目录一样：这个文件也可能是 root 身份（bootstrap.sh）创建的，不转交
# 属主的话，普通用户虽然能进 logs/ 目录、却会在 cat/tail 这份具体文件时碰到
# Permission denied——目录权限只管能不能列目录项，单个文件自己的权限是另一回事。
_chown_to_invoking_user_if_root "$LOG_FILE"

# 一律起 tail -f 做镜像，不按"是不是接在真终端上"（`[[ -t 1 ]]`）区分：这个
# 判断区分不出"真的没人看，可以省掉这个进程"和"调用方自己把 deploy.sh 的输出
# 重定向到了文件/管道（cron、systemd、外层包装脚本），就是要在那个目标里看到
# 输出"——这两种情况 `-t 1` 都是 false。bootstrap.sh 内部调用 `./deploy.sh
# --no-pull` 时也是同理，不能假设"不是交互式终端=没人关心输出"。tail 在这里
# fork 出来的时机还在 `exec >>`（下面）改写当前 stdout 之前，所以它镜像的是
# 调用方原本设置的那个目标，不管究竟是真终端、文件、还是管道，都能保留跟改造
# 之前一样"调用方在哪里重定向，输出就去哪里"的行为。
# `9>&-` 显式关掉这个后台 tail 继承到的锁文件描述符：flock 的锁绑定在"打开文件
# 描述"上而不是绑定在进程上，如果不关掉，一旦 deploy.sh 被 SIGKILL（人工
# kill -9、OOM killer）来不及走 EXIT trap 去杀这个 tail，残留的 tail 进程会
# 一直攥着这份锁的文件描述符不放，导致锁永远不会释放，后面所有 deploy.sh 都
# 会被误判成"已有实例在跑"，必须人工找到并杀掉这个残留进程才能恢复——这是
# review 里实测会发生的问题。
tail -f "$LOG_FILE" 9>&- &
TAIL_PID=$!

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
kill "$TAIL_PID" 2>/dev/null || true
wait "$TAIL_PID" 2>/dev/null || true
' EXIT

exec >> "$LOG_FILE" 2>&1

log "==> 部署开始，完整日志：$LOG_FILE"

if [[ "$PULL" -eq 1 ]]; then
    if [[ -n "$(run_unlocked git status --porcelain)" ]]; then
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
    # 只有"backup.sh 自己的锁被占用"（比如跟 DEPLOY.md 配的 cron 定时备份撞车）
    # 这种瞬时冲突才该放过，继续往下跑——backup.sh 用固定的 LOCK_CONFLICT_EXIT_
    # CODE（2）标识这种情况，跟其它任何原因（磁盘满、mysqldump 认证失败、备份本身
    # 损坏……）的失败区分开。除了这个退出码之外的任何失败，都必须照常让 set -e
    # 中止整个部署——不然就是在没有可回滚快照的情况下，盲目去跑 up -d 触发的
    # Flyway 迁移，而上面的注释已经提到历史上出现过 V15/V17 这种破坏性迁移。
    BACKUP_RC=0
    run_unlocked ./deploy/backup.sh --mysql-only --tag=predeploy || BACKUP_RC=$?
    if [[ "$BACKUP_RC" -eq "$LOCK_CONFLICT_EXIT_CODE" ]]; then
        log "警告：迁移前自动备份跳过（backup.sh 自己的锁被占用，大概率是撞上 cron 定时备份），继续执行 up -d。建议部署完成后手动确认一下最近有没有可用的备份快照。"
    elif [[ "$BACKUP_RC" -ne 0 ]]; then
        echo "错误：迁移前自动备份失败（退出码 ${BACKUP_RC}），为避免在没有可回滚快照的情况下执行可能有破坏性的 Flyway 迁移，本次部署中止。" >&2
        exit 1
    fi
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
