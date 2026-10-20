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
# 锁文件权限是 666 而不是更收敛的 600：这把锁要在两种身份下都能正常工作——
# `bootstrap.sh`（一次性初始化，以 root 身份跑，内部会调用一次 `./deploy.sh
# --no-pull`）和日常的 `./deploy.sh`（文档里写的是不带 sudo、以普通用户身份跑）。
# 如果锁文件是 600 且先被 root 创建，后面普通用户再跑 deploy.sh 就会在
# `exec 9>"$lock_file"` 这一步直接 Permission denied，把所有后续部署都卡死——
# 这是 review 里拿 bootstrap.sh + DEPLOY.md 的实际流程交叉验证出来的真实问题。
# 锁文件本身不装任何内容（只用来 flock），放宽权限不泄露数据。
#
# 用法：acquire_single_instance_lock <锁文件路径> <失败提示里要显示的脚本名> [冲突时的动作词，默认"退出"]
#   acquire_single_instance_lock "$LOG_DIR/.deploy.lock" "deploy.sh"
#   acquire_single_instance_lock "$BACKUP_DIR/.backup.lock" "backup.sh" "跳过"
acquire_single_instance_lock() {
    local lock_file="$1"
    local script_name="$2"
    local conflict_verb="${3:-退出}"

    if ! command -v flock >/dev/null 2>&1; then
        return 0
    fi

    exec 9>"$lock_file"
    chmod 666 "$lock_file" 2>/dev/null || true
    if ! flock -n 9; then
        echo "错误：已有另一个 ${script_name} 在运行（锁文件 ${lock_file}），本次${conflict_verb}，避免并发执行。" >&2
        exit 1
    fi
}
