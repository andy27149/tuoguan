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
# 用法：acquire_single_instance_lock <锁文件路径> <失败提示里要显示的脚本名>
#   acquire_single_instance_lock "$LOG_DIR/.deploy.lock" "deploy.sh"
acquire_single_instance_lock() {
    local lock_file="$1"
    local script_name="$2"

    if ! command -v flock >/dev/null 2>&1; then
        return 0
    fi

    exec 9>"$lock_file"
    chmod 600 "$lock_file"
    if ! flock -n 9; then
        echo "错误：已有另一个 ${script_name} 在运行（锁文件 ${lock_file}），本次退出，避免并发执行。" >&2
        exit 1
    fi
}
