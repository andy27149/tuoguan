#!/usr/bin/env bash
# 排查前端 Docker 构建时 "sh: tsc: not found" 问题用的一次性诊断脚本。
# 用跟 frontend/Dockerfile 构建阶段完全相同的基础镜像（node:20-alpine），在一个
# 一次性容器里重新跑一遍 npm ci，把 npm 配置、环境变量、npm ci 的完整输出都打印出来，
# 方便判断到底是 npm 配置把 devDependencies 跳过了，还是别的原因。
#
# 用法：在仓库根目录执行
#   ./diagnose-frontend-build.sh

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [[ ! -d frontend ]]; then
    echo "错误：没找到 frontend 目录，请在仓库根目录下运行本脚本。" >&2
    exit 1
fi

docker run --rm -v "$(pwd)/frontend":/app -w /app node:20-alpine sh -c '
    echo "==================== npm config list ===================="
    npm config list
    echo
    echo "==================== 环境变量（npm/node_env 相关） ===================="
    env | grep -iE "npm|node_env" || echo "(无匹配的环境变量)"
    echo
    echo "==================== npm ci ===================="
    npm ci
    echo
    echo "==================== node_modules/.bin 里跟 ts 相关的内容 ===================="
    ls node_modules/.bin 2>/dev/null | grep -i ts || echo "找不到 tsc，node_modules/.bin 是空的或者压根没生成"
    echo
    echo "==================== package.json 里声明的 typescript 版本 ===================="
    node -e "const p=require(\"./package.json\"); console.log(\"devDependencies.typescript =\", (p.devDependencies||{}).typescript)"
'
