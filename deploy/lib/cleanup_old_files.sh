# 共享的"清理 N 天前的旧文件"逻辑，被 deploy.sh（清理部署日志）和 deploy/backup.sh
# （清理 MySQL/MinIO 备份、以及统计还剩几个备份文件）复用。
#
# 这个函数本身只负责"按模式找、按天数删"，不替调用方决定删除失败算不算整体失败——
# 这两个调用方对这件事的要求并不一样：deploy.sh 的清理发生在 up -d 已经成功之后，
# 删除失败不该把一次已经成功的部署打成"异常退出"；backup.sh 则是定时任务，backups/
# 目录装的是全机构学生/账单真实数据，清理持续性失败（权限变化等）本身就是一个该让
# 整个 backup.sh 退出非零、从而被 cron/运维监控感知到的问题（这也是改之前、还没抽
# 成共享函数时 backup.sh 自己的行为）。所以统一交给调用方自己决定：想让失败拖累
# 整体结果就直接裸调用，想吞掉就套一层 `if ! cleanup_old_files ...; then ...; fi`。
#
# 用法：cleanup_old_files <目录> <保留天数> <find -name 模式>...
#   cleanup_old_files "$LOG_DIR" 14 'deploy_*.log'
#   cleanup_old_files "$BACKUP_DIR" "$RETENTION_DAYS" 'mysql_*.sql.gz' 'minio_*.tar.gz'
#
# 另外导出 _build_find_name_args，把"若干个 -name 模式拼成 find 的 \( -name a -o
# -name b \) 参数数组"这个小逻辑单独暴露出来，供 backup.sh 统计"还剩几个备份文件"
# 时复用，不用再手写一遍一样的拼接循环。结果写进全局数组 FIND_NAME_ARGS（没有用
# `local -n` nameref 间接赋值，是因为 nameref 要 bash 4.3+，这个文件两处都是内部
# 工具函数，没必要为了省一个全局变量名去抬高 bash 版本门槛）。
_build_find_name_args() {
    FIND_NAME_ARGS=()
    local pattern
    for pattern in "$@"; do
        if [[ ${#FIND_NAME_ARGS[@]} -gt 0 ]]; then
            FIND_NAME_ARGS+=(-o)
        fi
        FIND_NAME_ARGS+=(-name "$pattern")
    done
}

cleanup_old_files() {
    if [[ "$#" -lt 2 ]]; then
        echo "cleanup_old_files: 用法错误，至少需要 <目录> <保留天数>，收到的参数是: $*" >&2
        return 1
    fi

    local dir="$1"
    local retention_days="$2"
    shift 2

    # 至少要有一个模式才有意义；同时避免下面 "${FIND_NAME_ARGS[@]}" 展开一个空
    # 数组——bash 4.4 之前的版本在 `set -u` 下对空数组做 "${arr[@]}" 展开会报
    # unbound variable（比如 macOS 系统自带的 /bin/bash 还是 3.2），当前两个调用
    # 方都传了至少一个模式，不会触发，但作为共享函数不该留这个坑给以后的调用方。
    if [[ "$#" -eq 0 ]]; then
        return 0
    fi

    _build_find_name_args "$@"
    find "$dir" -maxdepth 1 -type f \( "${FIND_NAME_ARGS[@]}" \) -mtime "+$retention_days" -print -delete
}
