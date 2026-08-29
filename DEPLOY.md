# 部署手册（Docker + Nginx 反向代理）

本手册面向"一台 Linux 服务器 + Docker + 你自己维护的 Nginx"的部署方式。数据库（MySQL）
和对象存储（MinIO）都跑在 Docker 容器里，只对本机（127.0.0.1）开放端口；对外暴露的
只有宿主机上的 Nginx，由它做 HTTPS 终止和反向代理。

## 目录结构速览

| 文件 | 作用 |
|---|---|
| `bootstrap.sh` | **一条命令完成首次部署**：装依赖、生成 `.env`、配置 Nginx、签证书、构建启动，见下方"零、一键部署" |
| `docker-compose.yml` | 基础编排：mysql / minio / backend / frontend 四个服务 |
| `docker-compose.prod.yml` | 生产环境覆盖：所有端口只绑定 `127.0.0.1`，容器改为 `restart: always`，backend 的 `MINIO_ENDPOINT` 改成公网地址 |
| `.env.example` | 环境变量模板，部署前复制成 `.env` 并填入真实的强密码 |
| `deploy.sh` | 日常更新部署脚本（`bootstrap.sh` 内部也会调用它） |
| `deploy/nginx/app.conf.example` | 主站 Nginx 配置示例（前端 + `/api/`） |
| `deploy/nginx/files.conf.example` | MinIO 独立子域名的 Nginx 配置示例 |

---

## 零、一键部署（推荐）

如果只是想在一台全新服务器上把整套系统跑起来，不需要逐条执行下面的手动步骤——先满足
三个前置条件，再跑一个命令就够了：

1. 一台全新的 Ubuntu/Debian 或 CentOS/RHEL/Fedora 服务器，80/443 端口已对公网开放
2. 两个域名（主域名 + MinIO 子域名，原因见第二节）的 DNS A 记录都已指向服务器公网 IP
3. 代码已经 clone 到服务器上（全新服务器可能连 git 都还没有，先装一下：
   Ubuntu/Debian 用 `sudo apt-get update && sudo apt-get install -y git`，
   CentOS/RHEL/Fedora 用 `sudo dnf install -y git`）

> CentOS 8 用户注意：官方镜像源已于 2022 年停止维护，如果 `dnf`/`yum` 报仓库不可用，
> 需要先把仓库指向 `vault.centos.org`，脚本运行到装依赖那一步失败时会打印具体命令。

```bash
git clone <你的仓库地址> tuoguan && cd tuoguan
sudo ./bootstrap.sh --domain example.com --files-domain files.example.com --email <EMAIL_ADDRESS>
```

不传参数直接 `sudo ./bootstrap.sh` 也可以，脚本会在执行时交互式询问这三项。

脚本会自动完成：安装 Docker/Nginx/Certbot → 生成 `.env`（所有密码/密钥用 `openssl rand`
随机生成，不需要手动编辑）→ 用你的域名渲染 Nginx 配置并启用 → 用 certbot 签发 HTTPS
证书 → 构建并启动所有容器。全程约几分钟，结束后会打印验证用的 `curl` 命令。

脚本可以重复运行：已存在的 `.env` 不会被覆盖，已装好的依赖不会重装，已签发的证书 certbot
会自动跳过/续期。

之后的日常更新部署仍然用 `./deploy.sh`，见第四节。如果想理解每一步具体做了什么、或者需要
定制化配置（比如 apt/dnf 之外的系统、多台服务器分离部署等），继续看下面的手动步骤。

---

## 一、前置条件

- 一台已开放 80/443 端口的 Linux 服务器（下文命令以 Ubuntu/Debian 为例）
- 已安装 Docker Engine 和 Docker Compose 插件（`docker compose version` 能正常输出）
- 已安装 Nginx（`apt install nginx`）
- **两个域名**（缺一不可，原因见下文第二节）：
  - 主域名，例如 `example.com`，给前端 + 后端 API 用
  - 一个子域名，例如 `files.example.com`，专门给 MinIO 用
  - 两个域名的 DNS A 记录都指向服务器公网 IP

---

## 二、为什么 MinIO 需要单独一个子域名

后端给头像等文件生成的是 MinIO **预签名 URL**，签名是基于配置的 endpoint 直接算出来的，
请求路径的第一段必须是桶名（本项目桶名是 `avatars`）。如果想在主域名下用 `/files/` 这种
路径前缀转发，nginx 转发给 MinIO 时无论是保留前缀（MinIO 把 `files` 当成桶名，404/403）
还是去掉前缀（预签名 URL 里的签名跟实际收到的路径对不上，同样报错），都会失败。用独立
子域名可以完全绕开这个问题：nginx 原样转发到 MinIO 根路径，`MINIO_PUBLIC_ENDPOINT`
直接设成这个子域名即可，前后端访问、签名校验都能对上。详见
`deploy/nginx/files.conf.example` 里的完整注释。

这也是为什么 `docker-compose.prod.yml` 里 backend 的 `MINIO_ENDPOINT` 被覆盖成了
`${MINIO_PUBLIC_ENDPOINT}`（而不是容器间可达的 `http://minio:9000`）——预签名 URL 是
要发给浏览器/家长手机的，容器内部地址在外面根本访问不到。这个覆盖带来一个可接受的
副作用：后端自己上传文件到 MinIO 时，请求也会绕一圈经过公网域名和 Nginx 再回到本机
MinIO，而不是走容器内网直连。本项目里这条链路只有头像上传，流量很小，可以接受；如果
以后上传量变大，可以考虑把"生成预签名 URL 用的 endpoint"和"SDK 内部调用用的 endpoint"
拆成两个不同的 MinioClient，本手册暂不涉及。

---

## 三、首次部署步骤

### 1. 拉取代码、准备 `.env`

```bash
git clone <你的仓库地址> tuoguan && cd tuoguan
cp .env.example .env
```

编辑 `.env`，把所有 `xxx_change_me` 换成随机生成的强密码/密钥（**不要**把 `.env` 提交
到 git，仓库的 `.gitignore` 里应该已经排除了它，部署前确认一下）：

- `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD`：MySQL 的 root 密码和业务用户密码
- `MINIO_ROOT_PASSWORD`：MinIO 管理员密码
- `JWT_SECRET`：建议用 `openssl rand -base64 48` 生成一个随机值
- `MINIO_PUBLIC_ENDPOINT`：填 `https://` + 你的 MinIO 子域名，例如
  `https://files.example.com`（证书还没签发也先填上，下面签证书的步骤会用到同一个域名）

### 2. 配置 Nginx（先用 HTTP，证书稍后由 certbot 自动升级）

```bash
sudo cp deploy/nginx/app.conf.example /etc/nginx/sites-available/tuoguan.conf
sudo cp deploy/nginx/files.conf.example /etc/nginx/sites-available/tuoguan-files.conf
```

编辑这两个文件，把 `example.com` / `files.example.com` 替换成你的真实域名，然后启用：

```bash
sudo ln -s /etc/nginx/sites-available/tuoguan.conf /etc/nginx/sites-enabled/
sudo ln -s /etc/nginx/sites-available/tuoguan-files.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

此时后端/前端容器还没启动，`nginx -t` 只是校验语法，能通过就行。

### 3. 签发 HTTPS 证书（certbot）

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d example.com -d files.example.com
```

certbot 会自动检测到两个 server block（按 `server_name` 匹配），一次性给两个域名签证书，
并自动在对应的 nginx 配置里插入 `listen 443 ssl` 相关配置、配置好证书路径，同时默认会
加一条把 80 端口请求重定向到 443 的规则。签完之后再跑一次校验：

```bash
sudo nginx -t && sudo systemctl reload nginx
```

certbot 会自带一个定时任务自动续期，一般不需要手动管理。可以用
`sudo certbot renew --dry-run` 验证续期流程是否正常。

### 4. 构建并启动容器

```bash
./deploy.sh --no-pull
```

首次部署本地代码就是最新的，用 `--no-pull` 跳过 git pull。脚本会依次执行
`docker compose -f docker-compose.yml -f docker-compose.prod.yml build` 和 `up -d`，
并在最后打印容器状态。

### 5. 验证

```bash
curl -I https://example.com/api/health   # 期望 200
curl -I https://example.com/             # 期望 200，前端首页
```

再用浏览器实际登录一次，确认能正常看到看板首页、上传一次头像能正常显示（这一步顺带验证
了 MinIO 公网地址和预签名 URL 是否配置对了）。

---

## 四、日常更新部署

在服务器上（工作区必须是干净的，即没有本地未提交的改动）：

```bash
cd tuoguan
./deploy.sh
```

脚本会自动 `git pull` 最新代码、重新 `build`、`up -d` 滚动更新容器。如果服务器上有本地
改动导致 `git pull` 被脚本拒绝，先在本地开发机改好、提交、推送，服务器上只做拉取和部署，
不要直接在服务器上改代码。

数据库 schema 变更由 Flyway 在 backend 容器启动时自动执行迁移，不需要额外手动操作。

---

## 五、备份与恢复

用到的两个持久化 Docker volume 是 `mysql_data` 和 `minio_data`（实际名字会带项目名前缀，
用 `docker volume ls` 确认，通常是 `tuoguan_mysql_data` / `tuoguan_minio_data`）。

### MySQL：逻辑备份（推荐）

比直接打包 volume 更安全，能保证数据一致性：

```bash
# 备份
docker compose exec mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --all-databases' \
  > "backup_mysql_$(date +%F).sql"

# 恢复（会覆盖当前数据库，操作前务必确认）
cat backup_mysql_YYYY-MM-DD.sql | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
```

密码始终从容器自身的环境变量里取，不会出现在你本机的终端历史或日志里。

### MinIO：文件级备份

MinIO 存的是头像等静态文件，直接打包 volume 即可：

```bash
# 备份
docker run --rm -v tuoguan_minio_data:/data -v "$(pwd)":/backup alpine \
  tar czf "/backup/backup_minio_$(date +%F).tar.gz" -C /data .

# 恢复（会覆盖当前数据，操作前务必确认，且需要先停掉 minio 容器）
docker compose stop minio
docker run --rm -v tuoguan_minio_data:/data -v "$(pwd)":/backup alpine \
  sh -c "cd /data && tar xzf /backup/backup_minio_YYYY-MM-DD.tar.gz"
docker compose start minio
```

建议写一个 cron 定时任务每天跑一次 MySQL 备份，并把备份文件同步到服务器之外的地方
（比如另一台机器或对象存储），避免"服务器本身出问题"和"备份也没了"同时发生。

---

## 六、安全检查清单

- [ ] `.env` 里所有密码/密钥都已替换成随机生成的强值，且 `.env` 没有被提交到 git
- [ ] `docker-compose.prod.yml` 确保 mysql/minio/backend 的端口只绑定在 `127.0.0.1`，
      没有对公网暴露（当前配置已经这样做，改动前留意别删掉 `127.0.0.1:` 前缀）
- [ ] MinIO 控制台端口 9001 **不要**在 nginx 里代理暴露到公网；需要管理界面时用
      SSH 端口转发在本地临时访问：`ssh -L 9001:127.0.0.1:9001 user@your-server`，
      然后浏览器打开 `http://127.0.0.1:9001`
- [ ] 主域名和 MinIO 子域名都已启用 HTTPS，且 80 端口的明文请求会被重定向到 443
      （certbot 默认会配置好这一条）
- [ ] 服务器防火墙（如 `ufw`）只放行 22 / 80 / 443，其余端口一律拒绝公网访问
- [ ] MySQL/MinIO 的备份策略已经建立并实际验证过能恢复成功，不是"配置了但没试过"
- [ ] 生产环境的手机号/密码等测试账号（如果有）已经清理或替换成真实数据

---

## 七、常见问题排查

- **前端能打开，但登录后接口全部报错**：先看 `docker compose logs -f backend`，
  多数是 `.env` 里数据库密码/JWT_SECRET 之类配置错误，或者 MySQL 容器还没就绪就被
  backend 连接（`docker-compose.yml` 里已经用 `depends_on: condition: service_healthy`
  保证了顺序，一般不会遇到）。
- **头像上传后显示不出来 / 图片链接打不开**：检查 `.env` 里的 `MINIO_PUBLIC_ENDPOINT`
  是否配置正确、对应的 nginx 子域名配置和证书是否生效（`curl -I https://files.example.com`
  应该能拿到 MinIO 的响应而不是连接失败）。
- **`./deploy.sh` 提示工作区有未提交改动，拒绝 `git pull`**：说明服务器上有本地改动，
  按前面"日常更新部署"一节的说明处理，或者临时用 `./deploy.sh --no-pull`。
- **`docker compose build` 报 `dial tcp ...: connect: connection refused`（拉取
  `registry-1.docker.io` 镜像失败）**：国内服务器直连 Docker Hub 经常被拒，配置一个
  registry mirror 即可（阿里云/腾讯云等云厂商控制台里也能领一个账号专属的加速地址，
  比公共地址更稳定）：
  ```bash
  sudo mkdir -p /etc/docker
  sudo tee /etc/docker/daemon.json <<'EOF'
  { "registry-mirrors": ["https://docker.m.daocloud.io"] }
  EOF
  sudo systemctl restart docker
  ```
  如果 `/etc/docker/daemon.json` 已经存在且有其他配置，把 `registry-mirrors` 字段手动
  合并进去，不要整个覆盖。改完用 `docker info | grep -A3 "Registry Mirrors"` 确认生效，
  再重新跑 `./deploy.sh --no-pull`。
- **`docker compose is not a docker command`**：说明装的是旧版 docker（比如 CentOS/EPEL
  的 `moby-engine`）缺 compose v2 插件。`deploy.sh` 已经会自动识别并退回使用 `docker-compose`
  v1（如果已安装）；如果 v1 也没有，装一个即可（`pip3 install docker-compose` 或参考
  `bootstrap.sh` 里下载 v2 插件二进制的做法）。
- **前端构建报 `sh: tsc: not found`（`npm run build` 阶段失败）**：根因不是
  `package.json`/`typescript` 配置问题（`npm ci` 默认会把 `dependencies` 和
  `devDependencies` 都装上，`typescript` 放在 devDependencies 完全没问题）。

  **真正的根因（已在服务器构建日志里确认）**：`frontend/package-lock.json` 曾经在一台
  配置了公司内网 npm 源的开发机上生成过（该机器的全局 `~/.npmrc` 指向内网 Artifactory），
  导致锁文件里约 29 个包的 `resolved` 字段被永久写死成了内网地址
  `artifactory-corp.sddz.ebay.com`。`npm ci` 装包时是严格按锁文件里每个包的 `resolved`
  URL 去抓的，只有该 URL 命中"官方 `registry.npmjs.org`"这个模式时才会被
  `--registry`/`NPM_REGISTRY` 参数替换掉——写死成内网域名的包不会被替换，生产服务器
  当然连不上这个专属内网地址，这些包必然安装失败，重试 3 次也没用（不是偶发网络抖动，
  是永久不可达）。**修复方式**：新增了 `frontend/.npmrc` 把这个项目的 registry 锁定成
  公共源，避免以后在类似机器上 `npm install`/`npm update` 时又被带偏；
  `frontend/package-lock.json` 也已经用公共源整个重新生成，锁文件里所有 `resolved`
  URL 现在都指向 `registry.npmjs.org`，不需要额外配置，拉最新代码即可。

  另外，`npm ci` 自身还有一个已知的独立 bug（`npm error Exit handler never called!`，
  [npm/cli#8404](https://github.com/npm/cli/issues/8404)）：只要安装过程中任意一个包的
  下载请求因为真正的网络抖动失败（超时、连接被拒、证书校验失败等，跟上面"内网域名永久
  不可达"是两回事），npm 内部有概率把某个包静默装成空目录却仍然汇报退出码 0。
  `frontend/Dockerfile` 会在 `npm ci` 跑完之后检查 `node_modules/.bin/tsc` 在不在，
  不在就整个重装，最多重试 3 次；3 次都装不出 `tsc` 才会让构建真正失败（并打印 npm
  debug log）。这个逻辑作为防御偶发网络抖动的安全网继续保留，但它解决不了上面那种
  "域名永久不可达"的问题——那种情况必须修锁文件，重试只会正确地失败 3 次然后报错。

  包本身的下载速度如果觉得慢，仍然可以用 `NPM_REGISTRY` 切换到国内镜像（跟上面两个问题
  都是两回事，`docker-compose.yml` 已经把这个变量接到了前端构建的 `--build-arg`）：
  ```bash
  # .env 里有这一行就改值，没有就追加，避免出现重复的 NPM_REGISTRY
  if grep -q '^NPM_REGISTRY=' .env; then
    sed -i 's#^NPM_REGISTRY=.*#NPM_REGISTRY=https://registry.npmmirror.com#' .env
  else
    echo 'NPM_REGISTRY=https://registry.npmmirror.com' >> .env
  fi
  ./deploy.sh --no-pull --no-cache
  ```
  加 `--no-cache` 是因为之前失败的构建可能已经把不完整的 `node_modules` 缓存进了镜像层。
