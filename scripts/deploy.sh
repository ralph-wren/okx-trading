#!/bin/bash

## 检查MySQL服务是否运行
#echo -e "\033[0;36m检查本地MySQL服务是否运行...\033[0m"
#if nc -z localhost 3306; then
#    echo -e "\033[0;32mMySQL服务正在运行\033[0m"
#else
#    echo -e "\033[0;31m警告: 本地MySQL服务未运行。请先启动MySQL服务后再部署应用\033[0m"
#    echo -e "\033[0;33m可以使用以下命令启动MySQL服务: sudo service mysql start\033[0m"
#    exit 1
#fi
#
## 检查Redis服务是否运行
#echo -e "\033[0;36m检查本地Redis服务是否运行...\033[0m"
#if nc -z localhost 6379; then
#    echo -e "\033[0;32mRedis服务正在运行\033[0m"
#else
#    echo -e "\033[0;31m警告: 本地Redis服务未运行。请先启动Redis服务后再部署应用\033[0m"
#    echo -e "\033[0;33m可以使用以下命令启动Redis服务: sudo service redis-server start\033[0m"
#    exit 1
#fi

# 构建应用
echo -e "\033[0;36m正在构建应用...\033[0m"
mvn clean package -DskipTests

# 停止并删除旧容器
echo -e "\033[0;36m正在停止和移除旧容器...\033[0m"
docker-compose down

# 启动新容器
echo -e "\033[0;36m正在启动新容器...\033[0m"
docker-compose up -d

# 查看容器状态
echo -e "\033[0;36m查看容器状态...\033[0m"
docker-compose ps

# 查看日志
echo -e "\033[0;36m查看应用程序日志...\033[0m"
docker-compose logs -f app
