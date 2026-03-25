#!/bin/bash
# Solon Code - 本地运行脚本（兼容模式）
# 建议使用 install.sh 全局安装后，直接执行 soloncode 命令

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -f "$SCRIPT_DIR/soloncode-cli.jar" ]; then
    java -Dfile.encoding=UTF-8 -jar "$SCRIPT_DIR/soloncode-cli.jar" "$@"
else
    echo "[Error] soloncode-cli.jar not found."
    echo "Please run install.sh first for global installation."
    exit 1
fi
