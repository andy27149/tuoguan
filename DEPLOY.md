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
| `deploy.sh` | 日常更新部署脚本（`bootstrap.sh` 内部也会调用它），每次 `up -d` 前会自动做一次迁移前 MySQL 备份 |
| `deploy/backup.sh` | MySQL/MinIO 备份脚本，配 cron 做日常定时备份，`deploy.sh` 也会调用它 |
| `deploy/nginx/app.conf.example` | 主站 Nginx 配置示例（前端 + `/api/`），独占整个域名时用 |
| `deploy/nginx/app-subpath.conf.example` | 域名根路径被别的系统占用、需要把本系统挂在子路径下时用，见下方"七、常见问题排查" |
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

每次运行的完整输出（含 docker build 全过程）会落盘保存到 `logs/deploy_<时间戳>.log`，
不管 SSH 连接中途有没有断开都能看到完整记录，排查部署问题时直接看这份文件即可：

```bash
LOG_DIR=/data/deploy-logs ./deploy.sh      # 改用别的目录存日志，默认是仓库根目录下的 logs/
LOG_RETENTION_DAYS=30 ./deploy.sh          # 自动清理这个天数之前的旧日志，默认 14 天，0 表示不清理
```

同一时间只允许一个 `./deploy.sh` 实例运行（跟 `deploy/backup.sh` 一样用 `flock`），
重复执行会报错退出，见下方"常见问题排查"。

---

## 五、备份与恢复

用到的两个持久化 Docker volume 是 `mysql_data` 和 `minio_data`（实际名字会带项目名前缀，
用 `docker volume ls` 确认，通常是 `tuoguan_mysql_data` / `tuoguan_minio_data`）。

### 自动化：`deploy/backup.sh`

统一用这一个脚本做 MySQL 逻辑备份（`mysqldump --all-databases | gzip`）和 MinIO 文件级
备份（打包整个 volume），都写到仓库根目录下的 `backups/`（已 `.gitignore`，不会被
误提交；可以用 `BACKUP_DIR` 环境变量改成挂载的额外磁盘路径）：

```bash
./deploy/backup.sh                       # MySQL + MinIO 都备份
./deploy/backup.sh --mysql-only          # 只备份 MySQL（deploy.sh 迁移前自动调用的模式）
./deploy/backup.sh --retention-days=30   # 自动清理这个天数之前的旧备份，默认 14 天
```

**两种触发方式，建议都配上，各自解决不同的问题**：

1. **迁移前自动备份（已经生效，不需要你额外配置）**：`deploy.sh` 每次执行 `up -d`
   前，只要 mysql 容器当前确实在运行（不是首次部署），就会自动跑一次 `--mysql-only`
   备份，文件名带 `predeploy` 标签。这是专门为了兜底 Flyway 迁移失败的场景（见下面
   "迁移失败应急流程"），保证每次有 schema 变更的部署，手上至少有一份"迁移开始前
   一刻"的数据，而不用依赖上一次定时备份可能是几小时甚至一天前的数据。如果 mysql
   容器之前已经创建过、但当前没在运行（比如手动 `stop` 过服务），这一步会跳过并
   打印提示，等 `up -d` 把服务拉起来、确认恢复正常后，记得手动补跑一次
   `./deploy/backup.sh`。

2. **日常定时备份（需要你在服务器上手动配一次 cron，出于安全考虑这一步必须你自己
   执行，见下方命令）**：覆盖"平时没有部署动作、但数据本身需要定期备份"的场景。
   在服务器上执行 `crontab -e`，加入（按你的服务器时区调整时间，这里是每天凌晨
   3:17，刻意避开整点减少和其他定时任务撞在一起）：

   ```cron
   17 3 * * * cd /path/to/tuoguan && ./deploy/backup.sh >> /var/log/tuoguan-backup.log 2>&1
   ```

   配完用 `run-parts`/手动触发一次 `./deploy/backup.sh` 确认能正常生成文件，再检查
   `/var/log/tuoguan-backup.log` 确认没有报错。

**务必把 `backups/` 目录同步到服务器之外的地方**（另一台机器、对象存储、或者
`rclone`/`rsync` 到异地），脚本本身只解决"本机有份备份"，不解决"服务器整机故障、
备份跟着一起没了"（产品诊断 #58），这一步没有通用脚本，取决于你用什么异地存储，
常见做法是在上面那条 cron 命令后面再接一条 `rclone sync backups/ remote:tuoguan-backups/`
之类的命令。

### 手动备份 / 恢复

临时需要手动备份（比如做高风险操作前）直接跑 `./deploy/backup.sh` 即可。恢复：

```bash
# 恢复 MySQL（会覆盖当前数据库，操作前务必确认）
gunzip -c backups/mysql_2026-10-16_031700_predeploy.sql.gz \
  | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'

# 恢复 MinIO（会覆盖当前数据，操作前务必确认，且需要先停掉 minio 容器）
docker compose stop minio
docker run --rm -v tuoguan_minio_data:/data -v "$(pwd)/backups":/backup alpine \
  sh -c "cd /data && tar xzf /backup/minio_2026-10-16_031700.tar.gz"
docker compose start minio
```

密码始终从容器自身的环境变量里取，不会出现在你本机的终端历史或日志里。

### 恢复演练（产品诊断 #55：备份从未验证过能恢复）

"配了备份"和"备份真的能用"是两回事，建议**每月一次**，在一台独立的机器或临时
服务器上（不要在生产机上做，避免误操作影响线上数据）跑一遍完整演练：

1. 准备一台干净的机器，`git clone` 仓库、`cp .env.example .env` 填入任意密码（这台
   机器只是验证恢复流程，密码不需要和生产一致）。
2. `docker compose up -d mysql minio`，等两个容器都 healthy。
3. 把生产服务器上最近一次的 `backups/mysql_*.sql.gz` 和 `backups/minio_*.tar.gz`
   拷贝过来，按上面"手动备份 / 恢复"的命令分别恢复。
4. 启动 backend（`docker compose up -d backend`），用 `curl /api/health` 确认数据库
   连接正常，再登录一个真实机构账号，抽查学生/账单数据是否和生产一致。
5. 记录这次演练花了多长时间（这个数字就是真实发生灾难时的预期 RTO 下限），演练
   完直接整台机器销毁，不要把这台临时机器留着继续跑。

### Flyway 迁移失败应急流程（产品诊断 #59）

Flyway 社区版不支持 down migration（没有"自动回滚到上一个版本"这种命令），`V15`/
`V17` 历史上已经是破坏性迁移（清空旧数据强制重录），下一次类似迁移如果在生产执行
中途失败，处理步骤：

1. **停服，不要重试部署**：`docker compose stop backend`，避免 backend 用不完整的
   schema 反复启动/崩溃循环，也避免用户在数据结构中间态下继续写入数据。
2. **评估失败原因和影响范围**：`docker compose logs backend | grep -A30 "Migration .* failed"`
   能看到 Flyway 报错的具体 SQL 和失败原因。判断这是"迁移脚本本身有 bug，数据没有
   被破坏，修好脚本重跑就行"还是"迁移脚本已经执行到一半，数据已经处于不一致状态"。
3. **分支处理**：
   - 如果数据没有被破坏（比如迁移是新增字段但建索引失败）：修复迁移脚本，`mvn test`
     本地验证通过后正常走 PR 流程修复，`./deploy.sh` 重新部署让 Flyway 重跑。
   - 如果数据已经不一致：用 `deploy.sh` 在这次部署前自动生成的 `*_predeploy.sql.gz`
     备份（上面"自动化"一节），按"手动备份 / 恢复"的命令整库回滚，回滚期间这次
     部署引入的其他代码改动也会一起不生效，需要之后重新评估要不要单独修复迁移脚本
     再部署一次。
   - 极少数"迁移已经跑完但发现业务逻辑有问题"的情况（不是迁移本身失败），评估是否
     能写一条补充的 Flyway 迁移（新版本号）去修正数据，而不是回滚整库——回滚会丢失
     这次部署之后产生的所有新数据（机构当天录入的学生/账单等），只有在补 SQL 明显
     比回滚更难、或者数据破坏范围说不清楚的时候才选择整库回滚。
4. **恢复服务**：`docker compose start backend`，`curl /api/health` 确认正常。
5. 事后把这次故障的原因、处理过程记录下来（哪怕只是写在 commit message 或者给自己
   的笔记里），避免下一次类似迁移又踩同样的坑。

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
- [ ] 已经在服务器上用 crontab 配好 `deploy/backup.sh` 的日常定时任务（见"五、备份
      与恢复"），并且做过至少一次恢复演练验证备份真的能用，不是"配置了但没试过"
- [ ] `backups/` 目录已经同步到服务器之外的地方（另一台机器/对象存储），不是只存在
      同一台服务器本地磁盘上
- [ ] `backups/` 目录和里面的备份文件权限是 `700`/`600`（`deploy/backup.sh` 已经用
      `umask 077` + 显式 `chmod` 保证了这一点），不要用更宽松的权限手动覆盖——备份
      文件是全机构学生/账单等真实数据的全量 dump，同机其他账户不应该能直接读到
- [ ] 生产环境的手机号/密码等测试账号（如果有）已经清理或替换成真实数据
- [ ] `docker-compose.prod.yml` 已给 4 个容器配置了内存上限（`mem_limit`+`memswap_limit`
      关闭 swap）和日志轮转（`max-size`/`max-file`），防止单个容器异常吃满内存或日志
      无限增长拖垮整机（产品诊断 #62/#63）。当前数字是按 4 核 8G 服务器分配的：mysql 2g /
      backend 1g / minio 512m / frontend 256m——**是否适用取决于实际服务器配置**，
      服务器规格不同或变了，部署前要核对并调整这几个数字再打勾。这个日志轮转同时
      覆盖应用代码自己打的业务日志（账单生成/消课/登录等，产品诊断 #56），不是只管
      框架启动日志——backend 容器所有写到 stdout 的内容都算在同一份 `max-size`×
      `max-file` 额度里，不需要为业务日志单独配置

---

## 七、常见问题排查

- **怎么查看应用日志**（产品诊断 #56）：账单生成失败、消课、充值、登录这几条关键
  业务路径都打了日志（见 `BillGenerationService`/`CourseConsumptionService`/
  `CourseAccountService`/`AuthService`），走的是 Spring Boot 默认的控制台输出，
  没有写到任何独立的日志文件，所以查看方式和看其它容器日志完全一样：
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend        # 实时跟随
  docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --since 2h backend # 只看最近2小时
  ```
  **不会无限增长撑爆磁盘**：backend 容器的日志（框架启动日志 + 这些业务日志，走的
  是同一个 stdout）受"六、安全检查清单"里那条 `max-size: 10m` / `max-file: 5` 的
  轮转配置约束，上限固定在 50MB，写满后自动删最老的文件——这是部署时就该配好的
  Docker 层面限制，不需要为这几条新日志单独处理。副作用：轮转会删旧日志，这套方案
  只保证"不撑爆磁盘"，不是长期留痕/审计，如果需要查几个月前的问题，现在这套本地
  日志方案做不到（更完整的方案是接入外部日志归集/错误上报服务，目前评估后暂缓，
  见产品诊断 #56 的决策记录）。
- **前端能打开，但登录后接口全部报错**：先看 `docker compose logs -f backend`，
  多数是 `.env` 里数据库密码/JWT_SECRET 之类配置错误，或者 MySQL 容器还没就绪就被
  backend 连接（`docker-compose.yml` 里已经用 `depends_on: condition: service_healthy`
  保证了顺序，一般不会遇到）。
- **头像上传后显示不出来 / 图片链接打不开**：检查 `.env` 里的 `MINIO_PUBLIC_ENDPOINT`
  是否配置正确、对应的 nginx 子域名配置和证书是否生效（`curl -I https://files.example.com`
  应该能拿到 MinIO 的响应而不是连接失败）。
- **`./deploy.sh` 提示工作区有未提交改动，拒绝 `git pull`**：说明服务器上有本地改动，
  按前面"日常更新部署"一节的说明处理，或者临时用 `./deploy.sh --no-pull`。
- **`./deploy.sh` 提示"已有另一个 deploy.sh 在运行"，但确认并没有别的部署在跑**：先用
  `ps aux | grep deploy.sh` 和 `ps aux | grep "tail -f.*logs/deploy_"` 确认是不是真的有
  残留进程（比如上一次部署被 `kill -9`/断电中断，子进程变成了孤儿还在跑），找到就手动
  `kill` 掉。**下次要强制中断卡住的 `deploy.sh` 时，建议用 `pkill -9 -P <deploy.sh 的
  pid>` 连它的子进程一起杀**（或者 `kill -9 -<进程组id>` 杀整个进程组），而不是只
  `kill -9` 它自己那一个 pid——`deploy.sh` 被单独杀掉时，正在跑的 `docker build`/
  `up -d`/`backup.sh` 子进程不会跟着一起死，会继续作为孤儿进程跑完，而这段时间内锁
  已经随 `deploy.sh` 一起释放了，如果这时候又手动发起一次新的部署，就会和那个还没
  跑完的孤儿子进程并发操作同一份代码/容器/数据库（这是刻意的取舍：比起锁可能被一个
  卡死的孤儿进程永久占住、所有后续部署都失败，更倾向于锁一定会释放，但这意味着强制
  中断时操作者需要自己保证不在孤儿子进程跑完之前又发起新部署）。
  如果上面都没发现残留进程、还是报这个错，检查一下 `logs/.deploy.lock` 的属主和权限
  （`ls -l logs/.deploy.lock`）——正常应该是 `-rw-------`（600）、属主是实际跑部署的
  那个账号；如果看到它是所有人可读写的，说明权限被不正常地改动过，需要排查，而不是
  当成正常状态。
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
  导致锁文件里约 29 个包的 `resolved` 字段被永久写死成了内网地址。`npm ci` 装包时是严格按锁文件里每个包的 `resolved`
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
- **域名根路径已经被别的系统占用，只能把托管班挂在子路径下（比如 `/tuoguan/`）**：
  1. `.env` 里加一行 `VITE_BASE_PATH=/tuoguan/`（前后都要带斜杠，见 `.env.example` 里的说明）。
  2. 重新构建前端镜像——子路径前缀是构建时打包进静态资源的，不是运行时配置，必须
     `--no-cache` 强制重新构建：`./deploy.sh --no-pull --no-cache`（或手动
     `docker compose -f docker-compose.yml -f docker-compose.prod.yml build --no-cache frontend`）。
  3. 把 `deploy/nginx/app-subpath.conf.example` 里的两个 `location` 块插入到域名现有的
     `server {}` 配置里（把示例里的 `/tuoguan/` 换成实际用的子路径），`nginx -t &&
     systemctl reload nginx`。注意 `bootstrap.sh` 生成的 `/etc/nginx/sites-available/tuoguan.conf`
     是首次部署时一次性生成的静态文件，后续 `git pull`/`deploy.sh` 不会自动更新它，
     子路径配置需要手动加进去。
  4. 家长分享链接、静态资源、`/api/` 请求都会自动带上这个子路径前缀，不需要改任何
     业务代码；`MINIO_PUBLIC_ENDPOINT` 走独立子域名（见上面"二、为什么 MinIO 需要
     单独一个子域名"），跟前端挂在哪个子路径下无关，不用跟着改。
