#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}===== 配置服务器系统代理 =====${NC}"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${BLUE}1. 配置用户级环境变量${NC}"

# 备份原有配置
if [ -f ~/.bashrc ]; then
    cp ~/.bashrc ~/.bashrc.backup.$(date +%s)
    echo "已备份 ~/.bashrc"
fi

# 检查是否已配置代理
if grep -q "# Clash Proxy Configuration" ~/.bashrc; then
    echo -e "${YELLOW}代理配置已存在，更新配置...${NC}"
    # 删除旧配置
    sed -i '/# Clash Proxy Configuration/,/# End Clash Proxy/d' ~/.bashrc
fi

# 添加代理配置到 .bashrc
cat >> ~/.bashrc << 'EOF'

# Clash Proxy Configuration
export http_proxy=http://127.0.0.1:7890
export https_proxy=http://127.0.0.1:7890
export all_proxy=socks5://127.0.0.1:7891
export no_proxy=localhost,127.0.0.1,192.168.0.0/16,10.0.0.0/8,172.16.0.0/12
# End Clash Proxy
EOF

echo -e "${GREEN}✓ 已添加代理配置到 ~/.bashrc${NC}"

echo -e "${BLUE}2. 配置 profile 环境变量${NC}"

# 配置 /etc/profile.d/ (需要sudo)
cat > /tmp/clash-proxy.sh << 'EOF'
# Clash Proxy Configuration
export http_proxy=http://127.0.0.1:7890
export https_proxy=http://127.0.0.1:7890
export all_proxy=socks5://127.0.0.1:7891
export no_proxy=localhost,127.0.0.1,192.168.0.0/16,10.0.0.0/8,172.16.0.0/12
EOF

echo "skysi" | sudo -S cp /tmp/clash-proxy.sh /etc/profile.d/clash-proxy.sh 2>/dev/null
if [ $? -eq 0 ]; then
    echo "skysi" | sudo -S chmod +x /etc/profile.d/clash-proxy.sh
    echo -e "${GREEN}✓ 已配置系统级代理 /etc/profile.d/clash-proxy.sh${NC}"
else
    echo -e "${YELLOW}⚠ 无法配置系统级代理（需要sudo权限）${NC}"
fi

echo -e "${BLUE}3. 配置 APT 代理（Ubuntu/Debian）${NC}"

cat > /tmp/apt-proxy.conf << 'EOF'
Acquire::http::Proxy "http://127.0.0.1:7890";
Acquire::https::Proxy "http://127.0.0.1:7890";
EOF

echo "skysi" | sudo -S cp /tmp/apt-proxy.conf /etc/apt/apt.conf.d/95clash-proxy 2>/dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 已配置APT代理${NC}"
else
    echo -e "${YELLOW}⚠ 无法配置APT代理${NC}"
fi

echo -e "${BLUE}4. 配置 Git 代理${NC}"

git config --global http.proxy http://127.0.0.1:7890
git config --global https.proxy http://127.0.0.1:7890

echo -e "${GREEN}✓ 已配置Git代理${NC}"

echo -e "${BLUE}5. 配置 Docker 代理${NC}"

# 创建Docker代理配置目录
echo "skysi" | sudo -S mkdir -p /etc/systemd/system/docker.service.d 2>/dev/null

cat > /tmp/docker-proxy.conf << 'EOF'
[Service]
Environment="HTTP_PROXY=http://127.0.0.1:7890"
Environment="HTTPS_PROXY=http://127.0.0.1:7890"
Environment="NO_PROXY=localhost,127.0.0.1,192.168.0.0/16,10.0.0.0/8,172.16.0.0/12"
EOF

echo "skysi" | sudo -S cp /tmp/docker-proxy.conf /etc/systemd/system/docker.service.d/http-proxy.conf 2>/dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 已配置Docker代理${NC}"
    echo -e "${YELLOW}需要重启Docker服务: sudo systemctl daemon-reload && sudo systemctl restart docker${NC}"
else
    echo -e "${YELLOW}⚠ 无法配置Docker代理${NC}"
fi

echo -e "${BLUE}6. 立即生效当前会话${NC}"

# 立即生效环境变量
export http_proxy=http://127.0.0.1:7890
export https_proxy=http://127.0.0.1:7890
export all_proxy=socks5://127.0.0.1:7891
export no_proxy=localhost,127.0.0.1,192.168.0.0/16,10.0.0.0/8,172.16.0.0/12

echo -e "${GREEN}✓ 当前会话代理已生效${NC}"

echo -e "${BLUE}7. 测试代理${NC}"

echo "测试HTTP代理..."
if curl -s --connect-timeout 5 https://www.google.com > /dev/null 2>&1; then
    echo -e "${GREEN}✓ HTTP代理工作正常${NC}"
    PROXY_IP=$(curl -s https://api.ipify.org 2>/dev/null)
    echo -e "  当前IP: ${PROXY_IP}"
else
    echo -e "${YELLOW}⚠ HTTP代理测试失败${NC}"
fi

echo ""
echo -e "${GREEN}===== 代理配置完成 =====${NC}"
echo ""
echo -e "${BLUE}配置说明:${NC}"
echo "1. 用户级代理: ~/.bashrc"
echo "2. 系统级代理: /etc/profile.d/clash-proxy.sh"
echo "3. APT代理: /etc/apt/apt.conf.d/95clash-proxy"
echo "4. Git代理: ~/.gitconfig"
echo "5. Docker代理: /etc/systemd/system/docker.service.d/http-proxy.conf"
echo ""
echo -e "${BLUE}使配置生效:${NC}"
echo "  source ~/.bashrc"
echo ""
echo -e "${BLUE}临时禁用代理:${NC}"
echo "  unset http_proxy https_proxy all_proxy"
echo ""
echo -e "${BLUE}永久禁用代理:${NC}"
echo "  编辑 ~/.bashrc 并注释掉代理配置"
echo ""
echo -e "${BLUE}查看当前代理:${NC}"
echo "  echo \$http_proxy"

ENDSSH

echo -e "${GREEN}===== 完成 =====${NC}"
