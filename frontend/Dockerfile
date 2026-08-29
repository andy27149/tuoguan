FROM node:20-alpine AS build
WORKDIR /app
# npm ci 有个长期存在的已知 bug（npm error "Exit handler never called!"，见
# https://github.com/npm/cli/issues/8404 等多个 issue）：只要安装过程中有任意一个
# 包的下载请求失败（不管是超时、连接被拒、还是证书校验失败——本机实测触发原因是
# SELF_SIGNED_CERT_IN_CHAIN，跟"国内连不上 registry.npmjs.org"完全是两回事，
# 但表现的 bug 是同一个），npm 内部有概率把某些包静默装成空目录，自己却仍然
# 汇报退出码 0——不会让 npm ci 这一步失败，只会在下一步 `npm run build` 时才炸出
# `sh: tsc: not found` 或类似报错。调超时/重试次数/npm 版本/Node 版本都试过，
# 都不能保证这次安装请求不失败，所以没法从"防止触发"这个方向根治。
# 真正管用的办法是反过来：不管这次 npm ci 内部有没有偷偷丢包，装完之后都去检查
# 关键产物（tsc 二进制）在不在，不在就整个重装，重试几次还不行就让构建真正失败
# （而不是带着残缺的 node_modules 悄悄往下走）。
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
