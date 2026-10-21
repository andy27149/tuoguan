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
# 真正日常操作的那个人（从 sudo 设置的 SUDO_USER 环境变量拿到），权限继续收紧在
# 600，不给其他本地账户任何可乘之机。
#
# 用法：acquire_single_instance_lock <锁文件路径> <失败提示里要显示的脚本名> [冲突时的动作词，默认"退出"]
#   acquire_single_instance_lock "$LOG_DIR/.deploy.lock" "deploy.sh"
#   acquire_single_instance_lock "$BACKUP_DIR/.backup.lock" "backup.sh" "跳过"
_chown_to_invoking_user_if_root() {
    local path="$1"
    if [[ "$EUID" -eq 0 ]] && [[ -n "${SUDO_USER:-}" ]]; then
        chown "$SUDO_USER" "$path" 2>/dev/null || true
    fi
}

acquire_single_instance_lock() {
    local lock_file="$1"
    local script_name="$2"
    local conflict_verb="${3:-退出}"

    if ! command -v flock >/dev/null 2>&1; then
        return 0
    fi

    exec 9>"$lock_file"
    chmod 600 "$lock_file"
    _chown_to_invoking_user_if_root "$lock_file"
    if ! flock -n 9; then
        echo "错误：已有另一个 ${script_name} 在运行（锁文件 ${lock_file}），本次${conflict_verb}，避免并发执行。" >&2
        exit 1
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
