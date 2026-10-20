# 共享的"清理 N 天前的旧文件"逻辑，被 deploy.sh（清理部署日志）和 deploy/backup.sh
# （清理 MySQL/MinIO 备份）复用。两处原本各自实现了一份同构的 find -delete，其中
# deploy.sh 那份在 code review 里被发现了一个问题：在调用方普遍开着 `set -euo
# pipefail` 的前提下，find 对某个文件删除失败（权限变化、文件被并发进程占用等）
# 时会返回非零，导致这一步清理失败就让调用它的整个部署/备份脚本被判定为失败——
# 清理旧文件只是锦上添花，不应该有这个副作用。但也不能无条件 `|| true` 把所有
# 失败都当作"偶发、可忽略"：如果是权限之类的持续性错误，旧文件会无限堆积在
# backups/（里面是全机构学生/账单真实数据）或 logs/ 目录直到把磁盘写满，却完全
# 没有任何痕迹——这和这次改动本身"不能让错误信息悄悄消失在 scrollback 里"的初衷
# 矛盾。所以这里改成显式判断 find 的退出码，失败时打一条醒目的警告（不影响调用方
# 的整体结果），而不是彻底吞掉。
#
# 用法：cleanup_old_files <目录> <保留天数> <find -name 模式>...
#   cleanup_old_files "$LOG_DIR" 14 'deploy_*.log'
#   cleanup_old_files "$BACKUP_DIR" "$RETENTION_DAYS" 'mysql_*.sql.gz' 'minio_*.tar.gz'
cleanup_old_files() {
    local dir="$1"
    local retention_days="$2"
    shift 2

    # 至少要有一个模式才有意义；同时避免下面 "${name_args[@]}" 展开一个空数组——
    # bash 4.4 之前的版本在 `set -u` 下对空数组做 "${arr[@]}" 展开会报
    # unbound variable（比如 macOS 系统自带的 /bin/bash 还是 3.2），当前两个调用
    # 方都传了至少一个模式，不会触发，但作为共享函数不该留这个坑给以后的调用方。
    if [[ "$#" -eq 0 ]]; then
        return 0
    fi

    local -a name_args=()
    local pattern
    for pattern in "$@"; do
        if [[ ${#name_args[@]} -gt 0 ]]; then
            name_args+=(-o)
        fi
        name_args+=(-name "$pattern")
    done

    if ! find "$dir" -maxdepth 1 -type f \( "${name_args[@]}" \) -mtime "+$retention_days" -print -delete; then
        echo "警告：清理 $dir 下 $retention_days 天前的旧文件时失败（权限变化等），本次清理可能不完整，不影响本次结果，但建议手动检查一下该目录的磁盘占用和权限。" >&2
    fi
}
