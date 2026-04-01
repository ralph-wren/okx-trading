#!/bin/bash

REMOTE_HOST="192.168.54.68"
REMOTE_USER="skysi"
REMOTE_PASSWORD="skysi"

sshpass -p "$REMOTE_PASSWORD" ssh -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST bash << 'ENDSSH'

echo "===== Clash日志 ====="
cat ~/clash/clash.log
echo ""
echo "===== 配置文件检查 ====="
ls -lh ~/clash/
echo ""
echo "===== 尝试前台启动Clash（测试） ====="
cd ~/clash
timeout 5 ./clash -d . -t || echo "配置测试完成"

ENDSSH
