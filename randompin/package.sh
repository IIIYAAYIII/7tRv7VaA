#!/bin/bash
# 快速打包脚本 - 无需编译Java
# 仅打包基本文件，PIN乱序功能需要LSPosed/Xposed支持

VERSION="v1.0.0"
MODULE_NAME="RandomPIN-${VERSION}"
BUILD_DIR="./release"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "=== RandomPIN Module Package Script ==="
echo "Version: $VERSION"
echo ""

# 清理旧的构建
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/$MODULE_NAME"

# 复制必要文件
echo "Copying files..."

# 核心文件
cp module.prop "$BUILD_DIR/$MODULE_NAME/"
cp uninstall.sh "$BUILD_DIR/$MODULE_NAME/"

# 服务脚本
cp service.sh "$BUILD_DIR/$MODULE_NAME/"
cp post-fs-data.sh "$BUILD_DIR/$MODULE_NAME/"

# 系统属性
cp system.prop "$BUILD_DIR/$MODULE_NAME/"

# Xposed配置
cp xposed_init "$BUILD_DIR/$MODULE_NAME/"

# META-INF
cp -r META-INF "$BUILD_DIR/$MODULE_NAME/"

# common目录
cp -r common "$BUILD_DIR/$MODULE_NAME/"

# 创建空的system/framework目录 (用户可自行添加dex)
mkdir -p "$BUILD_DIR/$MODULE_NAME/system/framework"

# 创建Zygisk目录结构
mkdir -p "$BUILD_DIR/$MODULE_NAME/zygisk"

# 设置权限
echo "Setting permissions..."
find "$BUILD_DIR/$MODULE_NAME" -type f -name "*.sh" -exec chmod 755 {} \;

# 打包
echo "Creating zip package..."
cd "$BUILD_DIR"
zip -r "../${MODULE_NAME}.zip" "$MODULE_NAME"
cd ..

# 清理
rm -rf "$BUILD_DIR"

echo ""
echo "=== Package Complete ==="
echo "Created: ${MODULE_NAME}.zip"
echo ""
echo "Installation Instructions:"
echo "1. Copy the zip file to your device"
echo "2. Open Magisk Manager"
echo "3. Go to Modules -> Install from storage"
echo "4. Select the zip file"
echo "5. Reboot your device"
echo ""
echo "Note: For PIN randomization feature, you need:"
echo "  - LSPosed/Xposed framework installed"
echo "  - Compile the xposed_src Java files to DEX and put in system/framework/"
echo "  - Or use the Zygisk version (requires compilation)"
