#!/bin/bash
# =============================================
#  Solon Code Uninstall Script (Linux / macOS)
# =============================================

echo ""
echo "============================================"
echo "   Solon Code Uninstaller"
echo "============================================"
echo ""

INSTALL_DIR="$(cd "$(dirname "$0")" && pwd)"

# 确认
read -p "Uninstall Solon Code from $INSTALL_DIR ? (Y/N): " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 0
fi

# 检测 shell 配置文件
if [ -n "$ZSH_VERSION" ]; then
    SHELL_CONFIG="$HOME/.zshrc"
elif [ -n "$BASH_VERSION" ]; then
    SHELL_CONFIG="$HOME/.bashrc"
else
    SHELL_CONFIG="$HOME/.profile"
fi

# 从 shell 配置移除
echo "[1/2] Removing from $SHELL_CONFIG..."
if [ -f "$SHELL_CONFIG" ]; then
    cp "$SHELL_CONFIG" "${SHELL_CONFIG}.bak"
    # 移除 Solon Code 相关行
    sed -i.tmp '/# Solon Code/d' "$SHELL_CONFIG"
    sed -i.tmp '/SOLONCODE_HOME/d' "$SHELL_CONFIG"
    # 清理空行
    sed -i.tmp '/^$/N;/^\n$/d' "$SHELL_CONFIG" 2>/dev/null
    rm -f "${SHELL_CONFIG}.tmp"
    echo "      Cleaned $SHELL_CONFIG"
fi

# 删除软链接
echo "[2/2] Removing command symlink..."
if [ "$(id -u)" -eq 0 ]; then
    rm -f /usr/local/bin/soloncode 2>/dev/null && echo "      Removed /usr/local/bin/soloncode"
elif command -v sudo &> /dev/null; then
    sudo rm -f /usr/local/bin/soloncode 2>/dev/null && echo "      Removed /usr/local/bin/soloncode"
fi

# 完成
echo ""
echo "============================================"
echo "   Uninstall Complete!"
echo "============================================"
echo ""
echo "  Note: Config and session data preserved at:"
echo "        ~/.soloncode/"
echo "  To fully remove: rm -rf ~/.soloncode"
echo ""
