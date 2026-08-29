FROM node:20-alpine AS build
WORKDIR /app
# update-notifier 会在 npm ci 快结束时固定去连 registry.npmjs.org 检查 npm 自身
# 有没有新版本，这个请求走的是硬编码地址，跟下面 --registry 参数配的镜像源无关；
# 国内网络访问不到/超时会触发 npm 自身的 "Exit handler never called!" bug，导致
# node_modules 装到一半就崩，tsc 等 devDependencies 二进制缺失（报 sh: tsc: not
# found），见 https://github.com/npm/cli/issues/8404。关掉这个检查即可。
ENV NPM_CONFIG_UPDATE_NOTIFIER=false
# 包本身的下载源默认走官方源，国内也可以用 --build-arg 切换到镜像加速，见 DEPLOY.md。
ARG NPM_REGISTRY=https://registry.npmjs.org
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund --registry="${NPM_REGISTRY}"
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
