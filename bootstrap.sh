#!/usr/bin/env bash
# 一键初始化脚本：在一台全新的 Ubuntu/Debian 或 CentOS/RHEL/Fedora 服务器上，把 DEPLOY.md
# 里的所有手动步骤（装 Docker/Nginx/Certbot、生成 .env、配置 Nginx、签发 HTTPS 证书、
# 构建启动容器）合并成这一个命令跑完。
#
# 前置条件（脚本做不到的，必须你自己先准备好）：
#   1. 代码已经在服务器上：先装好 git、git clone 好这个仓库，cd 进去再跑本脚本
#      （全新服务器可能连 git 都没有，先手动装：apt-get install -y git 或
#       dnf install -y git）。
#   2. 两个域名的 DNS A 记录已经指向这台服务器的公网 IP（主域名 + MinIO 子域名）。
#   3. 服务器的 80/443 端口能被公网访问到（安全组/防火墙已放行）。
#
# 用法：
#   sudo ./bootstrap.sh --domain example.com --files-domain files.example.com --email <EMAIL_ADDRESS>
#   缺哪个参数，脚本会在执行时交互式提问，所以也可以直接：
#   sudo ./bootstrap.sh
#
# 支持的发行版：Ubuntu/Debian（apt）、CentOS/RHEL/Fedora（dnf，没有 dnf 时退回 yum）。
#
# 幂等性：可以重复运行。已存在的 .env 不会被覆盖；已签发的证书 certbot 会自动跳过/续期；
# 已装好的 docker/nginx/certbot 不会重复安装。

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

DOMAIN=""
FILES_DOMAIN=""
EMAIL=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --domain) DOMAIN="$2"; shift 2 ;;
        --files-domain) FILES_DOMAIN="$2"; shift 2 ;;
        --email) EMAIL="$2"; shift 2 ;;
        *) echo "未知参数: $1" >&2; exit 1 ;;
    esac
done

if [[ "$EUID" -ne 0 ]]; then
    echo "错误：请用 root 权限运行（sudo ./bootstrap.sh ...）。" >&2
    exit 1
fi

if command -v apt-get >/dev/null 2>&1; then
    PKG_MANAGER="apt"
elif command -v dnf >/dev/null 2>&1; then
    PKG_MANAGER="dnf"
elif command -v yum >/dev/null 2>&1; then
    PKG_MANAGER="yum"
else
    echo "错误：本脚本目前只支持 apt 系（Ubuntu/Debian）或 dnf/yum 系（CentOS/RHEL/Fedora）发行版。" >&2
    exit 1
fi
echo "==> 检测到包管理器: $PKG_MANAGER"

read -rp "主域名（前端 + API，例如 example.com）: " -e -i "$DOMAIN" DOMAIN
read -rp "MinIO 子域名（例如 files.example.com）: " -e -i "$FILES_DOMAIN" FILES_DOMAIN
read -rp "证书到期提醒邮箱（certbot 用）: " -e -i "$EMAIL" EMAIL

if [[ -z "$DOMAIN" || -z "$FILES_DOMAIN" || -z "$EMAIL" ]]; then
    echo "错误：主域名、MinIO 子域名、邮箱都不能为空。" >&2
    exit 1
fi
if [[ "$DOMAIN" == "$FILES_DOMAIN" ]]; then
    echo "错误：主域名和 MinIO 子域名不能相同（原因见 DEPLOY.md 第二节）。" >&2
    exit 1
fi

echo
echo "==> 主域名:      $DOMAIN"
echo "==> MinIO 子域名: $FILES_DOMAIN"
echo "==> 证书邮箱:     $EMAIL"
echo
read -rp "确认信息无误，回车继续（Ctrl+C 取消）..." _

echo "==> 安装系统依赖（docker / nginx / certbot / git / openssl）"
case "$PKG_MANAGER" in
    apt)
        apt-get update -qq
        if ! command -v docker >/dev/null 2>&1; then
            curl -fsSL https://get.docker.com | sh
        fi
        apt-get install -y -qq nginx certbot python3-certbot-nginx git openssl >/dev/null
        ;;
    dnf|yum)
        if ! "$PKG_MANAGER" install -y -q epel-release >/dev/null 2>&1; then
            echo "错误：epel-release 安装失败（certbot 依赖 EPEL 仓库）。" >&2
            echo "如果这是已停止官方维护的 CentOS 8，官方镜像源已下线，需要先把仓库指向" >&2
            echo "vault.centos.org，再重新运行本脚本：" >&2
            echo '  sed -i "s/mirrorlist/#mirrorlist/g; s|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g" /etc/yum.repos.d/CentOS-*.repo' >&2
            exit 1
        fi
        if ! command -v docker >/dev/null 2>&1; then
            curl -fsSL https://get.docker.com | sh
        fi
        "$PKG_MANAGER" install -y -q nginx certbot python3-certbot-nginx git openssl >/dev/null
        ;;
esac
systemctl enable --now docker
systemctl enable --now nginx

echo "==> 准备 .env"
if [[ -f .env ]]; then
    echo "    .env 已存在，跳过生成（如需重新生成随机密钥，先手动删除或备份 .env）。"
else
    cp .env.example .env
    sed -i "s#^MYSQL_ROOT_PASSWORD=.*#MYSQL_ROOT_PASSWORD=$(openssl rand -base64 24 | tr -d '=+/')#" .env
    sed -i "s#^MYSQL_PASSWORD=.*#MYSQL_PASSWORD=$(openssl rand -base64 24 | tr -d '=+/')#" .env
    sed -i "s#^MINIO_ROOT_PASSWORD=.*#MINIO_ROOT_PASSWORD=$(openssl rand -base64 24 | tr -d '=+/')#" .env
    sed -i "s#^JWT_SECRET=.*#JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')#" .env
    sed -i "s#^MINIO_PUBLIC_ENDPOINT=.*#MINIO_PUBLIC_ENDPOINT=https://${FILES_DOMAIN}#" .env
    echo "    已生成 .env，密码/密钥均为随机值（内容见 .env，注意妥善保管，不要提交到 git）。"
fi

echo "==> 配置 Nginx"
if [[ "$PKG_MANAGER" == "apt" ]]; then
    NGINX_AVAILABLE=/etc/nginx/sites-available
    NGINX_ENABLED=/etc/nginx/sites-enabled
else
    # CentOS/RHEL 的 nginx 包没有 Debian 那套 sites-available/sites-enabled 约定，
    # nginx.conf 默认只 include conf.d/*.conf，两者合一。
    NGINX_AVAILABLE=/etc/nginx/conf.d
    NGINX_ENABLED=/etc/nginx/conf.d
fi
mkdir -p "$NGINX_AVAILABLE" "$NGINX_ENABLED"
sed "s/example\.com/${DOMAIN}/g" deploy/nginx/app.conf.example > "${NGINX_AVAILABLE}/tuoguan.conf"
sed "s/files\.example\.com/${FILES_DOMAIN}/g" deploy/nginx/files.conf.example > "${NGINX_AVAILABLE}/tuoguan-files.conf"
if [[ "$NGINX_AVAILABLE" != "$NGINX_ENABLED" ]]; then
    ln -sf "${NGINX_AVAILABLE}/tuoguan.conf" "${NGINX_ENABLED}/tuoguan.conf"
    ln -sf "${NGINX_AVAILABLE}/tuoguan-files.conf" "${NGINX_ENABLED}/tuoguan-files.conf"
fi
nginx -t
systemctl reload nginx

if command -v getenforce >/dev/null 2>&1 && [[ "$(getenforce)" == "Enforcing" ]]; then
    echo "==> 配置 SELinux（放行 nginx 反向代理到本机端口，否则会报 502）"
    setsebool -P httpd_can_network_connect 1
fi

echo "==> 签发 HTTPS 证书（certbot）"
certbot --nginx -d "$DOMAIN" -d "$FILES_DOMAIN" --non-interactive --agree-tos -m "$EMAIL" --redirect
nginx -t
systemctl reload nginx

if command -v ufw >/dev/null 2>&1; then
    echo "==> 配置防火墙（ufw：只放行 22/80/443）"
    ufw allow 22/tcp >/dev/null || true
    ufw allow 80/tcp >/dev/null || true
    ufw allow 443/tcp >/dev/null || true
    if ufw status | grep -q "Status: active"; then
        : # 已启用，规则已追加
    else
        echo "    ufw 当前未启用，本脚本不会自动开启（避免误把你 SSH 连接锁死）；"
        echo "    确认 22 端口规则无误后，可自行执行: ufw enable"
    fi
elif command -v firewall-cmd >/dev/null 2>&1; then
    echo "==> 配置防火墙（firewalld：只放行 22/80/443）"
    firewall-cmd --permanent --add-service=ssh >/dev/null || true
    firewall-cmd --permanent --add-service=http >/dev/null || true
    firewall-cmd --permanent --add-service=https >/dev/null || true
    firewall-cmd --reload >/dev/null || true
fi

echo "==> 构建并启动容器"
./deploy.sh --no-pull

echo
echo "==================== 部署完成 ===================="
echo "前端 + API: https://${DOMAIN}"
echo "MinIO:      https://${FILES_DOMAIN}"
echo
echo "验证："
echo "  curl -I https://${DOMAIN}/api/health"
echo "  curl -I https://${DOMAIN}/"
echo
echo "生成的密码/密钥保存在 ./.env，请自行备份到安全的地方（该文件不会被提交到 git）。"
echo "===================================================="
