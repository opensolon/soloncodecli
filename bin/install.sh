#!/bin/bash
# =============================================
#  Solon Code Installer (Linux / macOS)
# =============================================

set -e

echo ""
echo "============================================"
echo "   Solon Code Installer"
echo "============================================"
echo ""

# 获取安装目录（脚本所在目录）
INSTALL_DIR="$(cd "$(dirname "$0")" && pwd)"

# 检查 jar 文件
echo "[1/4] Checking installation files..."
if [ ! -f "$INSTALL_DIR/soloncode-cli.jar" ]; then
    echo "[Error] soloncode-cli.jar not found in $INSTALL_DIR"
    exit 1
fi
echo "      Found soloncode-cli.jar"

# 检测 shell 配置文件
echo ""
echo "[2/4] Configuring environment variables..."
if [ -n "$ZSH_VERSION" ]; then
    SHELL_CONFIG="$HOME/.zshrc"
elif [ -n "$BASH_VERSION" ]; then
    SHELL_CONFIG="$HOME/.bashrc"
else
    SHELL_CONFIG="$HOME/.profile"
fi

# 添加 SOLONCODE_HOME
if grep -q "SOLONCODE_HOME=" "$SHELL_CONFIG" 2>/dev/null; then
    echo "      SOLONCODE_HOME already configured in $SHELL_CONFIG"
else
    echo "" >> "$SHELL_CONFIG"
    echo "# Solon Code" >> "$SHELL_CONFIG"
    echo "export SOLONCODE_HOME=\"$INSTALL_DIR\"" >> "$SHELL_CONFIG"
    echo "      Added SOLONCODE_HOME to $SHELL_CONFIG"
fi

# 添加到 PATH
if echo "$PATH" | grep -q "$INSTALL_DIR"; then
    echo "      PATH already contains $INSTALL_DIR"
else
    echo 'export PATH="$PATH:$SOLONCODE_HOME"' >> "$SHELL_CONFIG"
    echo "      Added to PATH in $SHELL_CONFIG"
fi

# 创建启动脚本
echo ""
echo "[3/4] Creating launch script..."
cat > "$INSTALL_DIR/soloncode" << 'LAUNCHER_EOF'
#!/bin/bash
if [ -z "$SOLONCODE_HOME" ]; then
    echo "[Error] SOLONCODE_HOME not set. Run install.sh first."
    exit 1
fi
SOLONCODE_JAR="$SOLONCODE_HOME/soloncode-cli.jar"
if [ ! -f "$SOLONCODE_JAR" ]; then
    echo "[Error] soloncode-cli.jar not found at $SOLONCODE_JAR"
    exit 1
fi
java -Dfile.encoding=UTF-8 -jar "$SOLONCODE_JAR" "$@"
LAUNCHER_EOF

chmod +x "$INSTALL_DIR/soloncode"
echo "      Created: $INSTALL_DIR/soloncode"

# Setup global config
echo ""
echo "[4/5] Setting up global config..."
GLOBAL_DIR="$HOME/.soloncode"
GLOBAL_CONFIG="$GLOBAL_DIR/config.yml"

mkdir -p "$GLOBAL_DIR"

if [ ! -f "$GLOBAL_CONFIG" ]; then
    if [ -f "$INSTALL_DIR/.soloncode/config.yml" ]; then
        cp "$INSTALL_DIR/.soloncode/config.yml" "$GLOBAL_CONFIG"
        echo "      Copied config template to $GLOBAL_CONFIG"
    elif [ -f "$INSTALL_DIR/config.yml" ]; then
        cp "$INSTALL_DIR/config.yml" "$GLOBAL_CONFIG"
        echo "      Copied config template to $GLOBAL_CONFIG"
    else
        echo "      [Warning] No config template found, will use jar embedded config"
    fi
else
    echo "      Global config already exists: $GLOBAL_CONFIG"
fi

# Create symlink to /usr/local/bin
echo ""
echo "[5/5] Creating command symlink..."
if [ "$(id -u)" -eq 0 ]; then
    ln -sf "$INSTALL_DIR/soloncode" /usr/local/bin/soloncode 2>/dev/null && echo "      Created /usr/local/bin/soloncode"
elif command -v sudo &> /dev/null; then
    sudo ln -sf "$INSTALL_DIR/soloncode" /usr/local/bin/soloncode 2>/dev/null && echo "      Created /usr/local/bin/soloncode"
else
    echo "      [Skip] Need root/sudo for /usr/local/bin/soloncode"
    echo "      Run manually: sudo ln -sf \"$INSTALL_DIR/soloncode\" /usr/local/bin/soloncode"
fi

# 完成
echo ""
echo "============================================"
echo "   Installation Complete!"
echo "============================================"
echo ""
echo "  Shell config: $SHELL_CONFIG"
echo "  Global config: ~/.soloncode/config.yml"
echo ""
echo "  Usage:"
echo "    1. source $SHELL_CONFIG"
echo "    2. Or restart terminal"
echo "    3. Run: soloncode"
echo ""
echo "  Uninstall:"
echo "    Run: $INSTALL_DIR/uninstall.sh"
echo ""
