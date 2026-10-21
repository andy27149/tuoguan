# 共享的"用 flock 防止同一个脚本并发跑两份"逻辑，被 deploy.sh（防止 git pull/
# docker build/up -d 互相打架）和 deploy/backup.sh（防止并发读写同一份 mysqldump/
# tar 产物）复用。两边原本各自写了一份几乎一样的代码，结果已经在 review 里验证出
# 真实的分叉：backup.sh 那份记得 `chmod 600 "$LOCK_FILE"`，deploy.sh 那份漏了——
# 这正是不该有两份同构逻辑的原因，以后改锁的策略（比如加超时、识别陈旧锁）也只需
# 要改一处。
#
# flock 是 Ubuntu/Debian/CentOS 生产服务器自带的 util-linux 标配命令；本机没有也
# 不影响功能，只是失去这层互斥保护。
#
# 这把锁要在两种身份下都能正常工作——`bootstrap.sh`（一次性初始化，以 root 身份
# 跑，内部会调用一次 `./deploy.sh --no-pull`）和日常的 `./deploy.sh`（文档里写的
# 是不带 sudo、以普通用户身份跑）。早期版本为了解决这个问题，把锁文件/日志目录
# 权限放宽成所有人可写（666/777）——但这是个真实可复现的本地提权漏洞：锁文件所在
# 目录一旦所有人可写，任何本地非特权用户都能提前把锁文件名换成指向任意文件（比如
# /root/.ssh/authorized_keys）的符号链接；`exec N>"$lock_file"` 和 `chmod` 默认都
# 会跟随符号链接操作到真正的目标文件，下次 `sudo ./bootstrap.sh` 用 root 身份跑到
# 这里，就会把攻击者选定的任意文件截断、以 root 身份重新打开、再 chmod 成所有人
# 可写——相当于一个本地提权/任意文件篡改原语。已经用符号链接实测复现过。
#
# 正确的修复方式不是放宽权限，而是：谁用 root 身份创建了这把锁，就把它的属主转给
# 真正日常操作的那个人（优先从 sudo 设置的 SUDO_USER 环境变量拿到），权限继续
# 收紧在 600，不给其他本地账户任何可乘之机。
#
# SUDO_USER 只覆盖"用 sudo 跑"这一种情况——如果是直接以 root 身份登录执行（比如
# DEPLOY.md 里 backup.sh 的 cron 示例没有限定用哪个账户的 crontab，配成 root 的
# crontab 也是可能的），不会有 SUDO_USER，这种情况退而求其次，用仓库目录本身的
# 属主：不管 root 是怎么拿到的，这个目录通常就是真正日常操作的那个人 clone 下来
# 的，是比"有没有设置 SUDO_USER"更通用的信号。两边的 cd 发生在调用这个函数之前，
# 所以这里的 `.` 就是仓库根目录。`stat` 的 `-c`（GNU）和 `-f`（BSD/macOS）参数
# 不兼容，两种都试一下——生产目标是 Linux，但本地在 macOS 上开发/调试时也不该
# 悄悄失败。
#
# 如果连这个兜底都解析不出一个非 root 的属主（比如真的是以 root 身份登录、仓库
# 本身也是 root clone 的——不符合 DEPLOY.md 文档的用法，但没法完全排除），就没有
# 办法知道"真正日常操作的人"是谁，索性不改属主；这种情况下后面 chmod 可能会因为
# 不是属主/root 而失败，调用方的 chmod 调用本身已经做了容错（见 deploy.sh /
# deploy/backup.sh 里的注释），失败只是打印警告，不会让整个脚本直接崩溃。
#
# 用法：acquire_single_instance_lock <锁文件路径> <失败提示里要显示的脚本名> [冲突时的动作词，默认"退出"]
#   acquire_single_instance_lock "$LOG_DIR/.deploy.lock" "deploy.sh"
#   acquire_single_instance_lock "$BACKUP_DIR/.backup.lock" "backup.sh" "跳过"
#
# 冲突时的退出码固定是 2（不是通用的 1），方便调用方区分"真的失败"和"只是被
# 另一个实例占着、属于预期内的跳过"——deploy.sh 调用 backup.sh 做迁移前快照时就是
# 靠这个区分：锁冲突（2）只打警告继续，其它任何失败都要老老实实中止部署。
_chown_to_invoking_user_if_root() {
    local path="$1"

    if [[ "$EUID" -ne 0 ]]; then
        return 0
    fi

    local target_user="${SUDO_USER:-}"
    if [[ -z "$target_user" ]]; then
        target_user="$(stat -c '%U' . 2>/dev/null || stat -f '%Su' . 2>/dev/null || true)"
    fi
    if [[ -n "$target_user" ]] && [[ "$target_user" != "root" ]]; then
        chown "$target_user" "$path" 2>/dev/null || true
    fi
}

LOCK_CONFLICT_EXIT_CODE=2

acquire_single_instance_lock() {
    local lock_file="$1"
    local script_name="$2"
    local conflict_verb="${3:-退出}"

    if ! command -v flock >/dev/null 2>&1; then
        return 0
    fi

    # `exec N>"$lock_file"` 和后面的 chmod/chown 默认都会跟随符号链接操作到真正
    # 的目标文件——这是上几轮已经修过、也实测复现过的提权漏洞的根本原因。光把
    # 目录权限收紧到 700 只能防住"以后"被人在这个目录里新建符号链接，防不住
    # "目录权限收紧之前就已经存在"的残留符号链接（比如这个目录是旧版本脚本建的，
    # 那时候权限还没收紧；或者仓库根目录本身一度是组/所有人可写的）。所以每次用
    # 这把锁之前都显式查一下目标路径是不是符号链接，是就直接拒绝，而不是悄悄
    # 跟着链接走下去。
    if [[ -L "$lock_file" ]]; then
        echo "错误：锁文件 ${lock_file} 是一个符号链接，出于安全考虑拒绝使用——正常情况下不会出现这个文件，请检查是否有人篡改，确认安全后手动删除它再重试。" >&2
        exit 1
    fi

    exec 9>"$lock_file"
    chmod 600 "$lock_file" 2>/dev/null || echo "警告：无法修改锁文件 ${lock_file} 的权限（不是属主也不是 root？），继续使用现有权限。" >&2
    _chown_to_invoking_user_if_root "$lock_file"
    if ! flock -n 9; then
        echo "错误：已有另一个 ${script_name} 在运行（锁文件 ${lock_file}），本次${conflict_verb}，避免并发执行。" >&2
        exit "$LOCK_CONFLICT_EXIT_CODE"
    fi
}

# fd 9 是上面这把锁用的文件描述符。执行会 fork 外部进程、且可能跑很久（甚至卡住）
# 的命令时用这个包一层显式关掉继承来的 fd 9：flock 的锁绑定在"打开文件描述"上，
# 如果不关，一旦调用方脚本被 SIGKILL/OOM killer 杀掉，这些子进程变成孤儿继续
# 运行，就会一直攥着这把锁不放，导致锁永远不会释放，必须人工找到并杀掉残留进程
# 才能恢复。deploy.sh（git pull/docker build/up -d/backup.sh）和 backup.sh
# （mysqldump/docker run tar）两边所有符合条件的调用都应该套这个；已经用 lsof
# 实测验证过子进程确实不再继承这个 fd。
#
# 这是一个刻意的取舍，不是没考虑到副作用：用 run_unlocked 包过的子进程一旦变成
# 孤儿，相当于在"锁一定会释放"和"孤儿还在跑的时候锁已经不保护它"之间选了前者——
# 如果这时候有人手动发起第二次部署/备份，是可能和这个还没跑完的孤儿子进程并发
# 操作同一份代码/容器/数据库的。这里认为"锁被一个卡住的孤儿永久占住、所有后续
# 部署都失败、必须人工介入才能恢复"是更坏的结果（已经实测复现过），所以接受这个
# 更小的残留风险，并在 DEPLOY.md 的排查指引里提醒操作者：强制中断卡住的
# deploy.sh/backup.sh 时要连子进程一起杀（`pkill -9 -P <pid>` 或杀整个进程组），
# 不要只 kill 父进程自己，从源头上避免孤儿子进程出现。
run_unlocked() {
    "$@" 9>&-
}
