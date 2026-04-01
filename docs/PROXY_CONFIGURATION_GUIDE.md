# 服务器代理配置指南

## ✅ 配置完成状态

服务器 `192.168.54.68` 已成功配置系统级代理，所有网络请求将通过Clash代理。

---

## 代理信息

### Clash服务
- **HTTP代理**: http://127.0.0.1:7890
- **SOCKS5代理**: socks5://127.0.0.1:7891
- **控制面板**: http://192.168.54.68:9090
- **代理IP**: 141.11.146.55 (香港节点)
- **状态**: ✅ 运行中

### 已配置的组件

1. ✅ **用户环境变量** (~/.bashrc)
2. ✅ **系统环境变量** (/etc/profile.d/clash-proxy.sh)
3. ✅ **APT包管理器** (/etc/apt/apt.conf.d/95clash-proxy)
4. ✅ **Git全局配置** (~/.gitconfig)
5. ✅ **Docker服务** (/etc/systemd/system/docker.service.d/http-proxy.conf)

---

## 使用说明

### 自动生效
新的SSH会话会自动使用代理，无需额外配置。

### 当前会话生效
如果在已有会话中，执行：
```bash
source ~/.bashrc
```

### 验证代理
```bash
# 查看环境变量
echo $http_proxy

# 测试访问
curl https://www.google.com

# 查看当前IP
curl https://api.ipify.org
```

---

## 管理命令

### 启动Clash
```bash
cd ~/clash && ./start.sh
```

### 停止Clash
```bash
pkill -f './clash'
```

### 查看Clash日志
```bash
tail -f ~/clash/clash.log
```

### 查看Clash状态
```bash
ps aux | grep clash
```

---

## 临时控制代理

### 临时禁用代理（当前会话）
```bash
unset http_proxy https_proxy all_proxy
```

### 临时启用代理（当前会话）
```bash
export http_proxy=http://127.0.0.1:7890
export https_proxy=http://127.0.0.1:7890
export all_proxy=socks5://127.0.0.1:7891
```

---

## 永久禁用代理

### 方法1：注释配置
编辑 `~/.bashrc`，注释掉代理配置：
```bash
nano ~/.bashrc
# 找到 "Clash Proxy Configuration" 部分并注释掉
```

### 方法2：删除配置文件
```bash
# 删除用户配置
sed -i '/# Clash Proxy Configuration/,/# End Clash Proxy/d' ~/.bashrc

# 删除系统配置（需要sudo）
sudo rm /etc/profile.d/clash-proxy.sh
sudo rm /etc/apt/apt.conf.d/95clash-proxy

# 删除Git代理
git config --global --unset http.proxy
git config --global --unset https.proxy
```

---

## Docker代理配置

Docker已配置代理，但需要重启服务才能生效：

```bash
sudo systemctl daemon-reload
sudo systemctl restart docker
```

验证Docker代理：
```bash
docker info | grep -i proxy
```

---

## 不走代理的地址

以下地址不会走代理（no_proxy配置）：
- localhost
- 127.0.0.1
- 192.168.0.0/16 (内网)
- 10.0.0.0/8 (内网)
- 172.16.0.0/12 (内网)

---

## 测试脚本

### 验证代理配置
```bash
./verify-proxy-config.sh
```

### 测试新会话代理
```bash
./test-new-session-proxy.sh
```

### 测试Clash代理
```bash
./test-clash-proxy.sh
```

---

## 故障排查

### 问题1：代理不生效
```bash
# 检查Clash是否运行
ps aux | grep clash

# 如果未运行，启动Clash
cd ~/clash && ./start.sh

# 重新加载环境变量
source ~/.bashrc
```

### 问题2：无法访问外网
```bash
# 检查Clash日志
tail -50 ~/clash/clash.log

# 测试代理端口
curl -x http://127.0.0.1:7890 https://www.google.com
```

### 问题3：部分命令不走代理
某些命令可能需要显式指定代理：
```bash
# curl
curl -x http://127.0.0.1:7890 https://example.com

# wget
wget -e use_proxy=yes -e http_proxy=127.0.0.1:7890 https://example.com

# git
git config --global http.proxy http://127.0.0.1:7890
```

---

## 配置文件位置

| 组件 | 配置文件 |
|------|---------|
| 用户环境 | ~/.bashrc |
| 系统环境 | /etc/profile.d/clash-proxy.sh |
| APT | /etc/apt/apt.conf.d/95clash-proxy |
| Git | ~/.gitconfig |
| Docker | /etc/systemd/system/docker.service.d/http-proxy.conf |
| Clash | ~/clash/config.yaml |

---

## 注意事项

1. ⚠️ Clash需要保持运行状态，代理才能工作
2. ⚠️ 服务器重启后需要手动启动Clash
3. ⚠️ 建议配置Clash为系统服务实现自动启动
4. ✅ 内网地址不会走代理
5. ✅ 新SSH会话自动使用代理

---

## 下一步建议

1. 配置Clash为systemd服务，实现开机自动启动
2. 设置Clash配置文件自动更新
3. 配置日志轮转
4. 监控Clash运行状态

需要帮助配置这些功能，请告诉我！
