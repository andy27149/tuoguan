# 共享的"清理 N 天前的旧文件"逻辑，被 deploy.sh（清理部署日志）和 deploy/backup.sh
# （清理 MySQL/MinIO 备份）复用。两处原本各自实现了一份同构的 find -delete，其中
# deploy.sh 那份在 code review 里被发现了一个问题：在调用方普遍开着 `set -euo
# pipefail` 的前提下，find 对某个文件删除失败（权限变化、文件被并发进程占用等）
# 时会返回非零，导致这一步清理失败就让调用它的整个部署/备份脚本被判定为失败——
# 清理旧文件只是锦上添花，不应该有这个副作用，所以这里统一兜底 `|| true`。
#
# 用法：cleanup_old_files <目录> <保留天数> <find -name 模式>...
#   cleanup_old_files "$LOG_DIR" 14 'deploy_*.log'
#   cleanup_old_files "$BACKUP_DIR" "$RETENTION_DAYS" 'mysql_*.sql.gz' 'minio_*.tar.gz'
cleanup_old_files() {
    local dir="$1"
    local retention_days="$2"
    shift 2

    local -a name_args=()
    local pattern
    for pattern in "$@"; do
        if [[ ${#name_args[@]} -gt 0 ]]; then
            name_args+=(-o)
        fi
        name_args+=(-name "$pattern")
    done

    find "$dir" -maxdepth 1 -type f \( "${name_args[@]}" \) -mtime "+$retention_days" -print -delete || true
}
