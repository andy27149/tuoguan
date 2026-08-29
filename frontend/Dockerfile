FROM node:20-alpine AS build
WORKDIR /app
# 默认走官方源；国内网络访问 registry.npmjs.org 不稳定时容易触发 npm 自身的
# "Exit handler never called!" bug（安装过程被网络抖动打断导致 npm 内部崩溃，
# 不是报错退出而是静默留下不完整的 node_modules，tsc 等 devDependencies 里的
# 二进制就会缺失），构建时传 --build-arg NPM_REGISTRY=https://registry.npmmirror.com
# 即可切换到国内镜像，见 DEPLOY.md 常见问题排查。
ARG NPM_REGISTRY=https://registry.npmjs.org
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund --registry="${NPM_REGISTRY}"
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
