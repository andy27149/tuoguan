# 部署手册（Docker + Nginx 反向代理）

本手册面向"一台 Linux 服务器 + Docker + 你自己维护的 Nginx"的部署方式。数据库（MySQL）
和对象存储（MinIO）都跑在 Docker 容器里，只对本机（127.0.0.1）开放端口；对外暴露的
只有宿主机上的 Nginx，由它做 HTTPS 终止和反向代理。

## 目录结构速览

| 文件 | 作用 |
|---|---|
| `docker-compose.yml` | 基础编排：mysql / minio / backend / frontend 四个服务 |
| `docker-compose.prod.yml` | 生产环境覆盖：所有端口只绑定 `127.0.0.1`，容器改为 `restart: always`，backend 的 `MINIO_ENDPOINT` 改成公网地址 |
| `.env.example` | 环境变量模板，部署前复制成 `.env` 并填入真实的强密码 |
| `deploy.sh` | 一键部署/更新脚本 |
| `deploy/nginx/app.conf.example` | 主站 Nginx 配置示例（前端 + `/api/`） |
| `deploy/nginx/files.conf.example` | MinIO 独立子域名的 Nginx 配置示例 |

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
