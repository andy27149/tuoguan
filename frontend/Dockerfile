FROM node:20-alpine AS build
WORKDIR /app
# 真正的根因（已在服务器构建日志里实锤确认）：package-lock.json 曾经在一台连得到
# 公司内网 npm 源的机器上生成过，导致里面 29 个
# 包的 "resolved" 字段被永久写死成了这个内网地址。npm ci 装包时是严格按锁文件里
# 每个包的 resolved URL 去抓的，只有该 URL 命中"官方 registry.npmjs.org"这个模式
# 时才会被 --registry/NPM_REGISTRY 参数替换掉——写死成内网域名的这些包不会被替换，
# 服务器当然连不上，于是这些包必然装不出来（装出来哪个包缺，取决于锁文件生成时
# 哪些包命中了内网源，不是随机的）。frontend/.npmrc 已经把这个项目的 registry
# 锁定成公共源，避免以后在这类装了公司内网 npm 源的机器上 `npm install` 时又被
# 带偏；package-lock.json 也已经用公共源重新生成过，不应该再出现这个问题。
# 下面这段"装完校验 tsc 是否存在、不在就重试、重试 3 次还不行就让构建真正失败"
# 的逻辑仍然保留——这是防御 npm 自身另一个已知 bug（npm error "Exit handler never
# called!"，https://github.com/npm/cli/issues/8404）的安全网：只要某次包下载请求
# 因为真正的网络抖动（不是内网域名不可达这种必然失败）失败，npm 有概率把该包静默
# 装成空目录却仍然汇报退出码 0，直到 npm run build 才会炸出来。重试对"内网域名
# 不可达"这种必然失败没用（会正确地 3 次都失败然后让构建报错），但对偶发的网络
# 抖动有用，留着比去掉更安全。
ENV NPM_CONFIG_UPDATE_NOTIFIER=false
ARG NPM_REGISTRY=https://registry.npmjs.org
COPY package.json package-lock.json ./
RUN set -e; \
    ok=0; \
    for i in 1 2 3; do \
      echo "===== npm ci 第 ${i} 次尝试 ====="; \
      rm -rf node_modules; \
      npm ci --no-audit --no-fund --registry="${NPM_REGISTRY}" || true; \
      if [ -x node_modules/.bin/tsc ]; then \
        ok=1; \
        break; \
      fi; \
      echo "node_modules 不完整（tsc 缺失），准备重试"; \
    done; \
    if [ "${ok}" != "1" ]; then \
      echo "===== npm ci 重试 3 次仍未装出 tsc，打印 debug log ====="; \
      cat /root/.npm/_logs/*-debug-0.log 2>/dev/null; \
      exit 1; \
    fi
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
