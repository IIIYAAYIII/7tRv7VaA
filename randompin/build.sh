#!/bin/bash
# 编译Xposed模块的脚本
# 需要Android SDK和Java JDK

# 配置
ANDROID_SDK="${ANDROID_SDK:-$HOME/Android/Sdk}"
ANDROID_JAR="$ANDROID_SDK/platforms/android-35/android.jar"
BUILD_DIR="./build"
SRC_DIR="./xposed_src"
DEX_FILE="./system/framework/randompin.dex"

echo "=== RandomPIN Module Build Script ==="

# 检查依赖
check_dependencies() {
    echo "Checking dependencies..."
    
    if ! command -v javac &> /dev/null; then
        echo "Error: javac not found. Please install JDK."
        exit 1
    fi
    
    if ! command -v d8 &> /dev/null; then
        if [ -f "$ANDROID_SDK/build-tools/35.0.0/d8" ]; then
            D8="$ANDROID_SDK/build-tools/35.0.0/d8"
        elif [ -f "$ANDROID_SDK/build-tools/34.0.0/d8" ]; then
            D8="$ANDROID_SDK/build-tools/34.0.0/d8"
        else
            echo "Warning: d8 not found. Will try dx as fallback."
            D8=""
        fi
    else
        D8="d8"
    fi
    
    if [ ! -f "$ANDROID_JAR" ]; then
        # 尝试其他版本
        for version in 35 34 33 32 31 30 29; do
            if [ -f "$ANDROID_SDK/platforms/android-$version/android.jar" ]; then
                ANDROID_JAR="$ANDROID_SDK/platforms/android-$version/android.jar"
                echo "Found Android SDK platform $version"
                break
            fi
        done
    fi
    
    if [ ! -f "$ANDROID_JAR" ]; then
        echo "Warning: android.jar not found. Will compile without Android SDK."
    fi
}

# 编译Java源码
compile_java() {
    echo "Compiling Java sources..."
    
    mkdir -p "$BUILD_DIR/classes"
    
    # 编译
    if [ -f "$ANDROID_JAR" ]; then
        javac -source 1.8 -target 1.8 \
            -bootclasspath "$ANDROID_JAR" \
            -d "$BUILD_DIR/classes" \
            "$SRC_DIR"/*.java
    else
        javac -source 1.8 -target 1.8 \
            -d "$BUILD_DIR/classes" \
            "$SRC_DIR"/*.java
    fi
    
    if [ $? -ne 0 ]; then
        echo "Error: Compilation failed."
        exit 1
    fi
    
    echo "Compilation successful."
}

# 转换为DEX
convert_to_dex() {
    echo "Converting to DEX..."
    
    mkdir -p "$(dirname "$DEX_FILE")"
    
    if [ -n "$D8" ] && command -v $D8 &> /dev/null; then
        $D8 --output "$BUILD_DIR" "$BUILD_DIR/classes"/*.class
        cp "$BUILD_DIR/classes.dex" "$DEX_FILE"
    elif command -v dx &> /dev/null; then
        dx --dex --output="$DEX_FILE" "$BUILD_DIR/classes"
    else
        echo "Error: Neither d8 nor dx found. Cannot create DEX file."
        exit 1
    fi
    
    if [ $? -eq 0 ]; then
        echo "DEX file created: $DEX_FILE"
    else
        echo "Error: DEX conversion failed."
        exit 1
    fi
}

# 打包Magisk模块
package_module() {
    echo "Packaging Magisk module..."
    
    MODULE_NAME="randompin-$(date +%Y%m%d).zip"
    
    # 创建临时目录
    mkdir -p "$BUILD_DIR/module"
    
    # 复制文件
    cp module.prop "$BUILD_DIR/module/"
    cp uninstall.sh "$BUILD_DIR/module/"
    cp service.sh "$BUILD_DIR/module/"
    cp post-fs-data.sh "$BUILD_DIR/module/"
    cp xposed_init "$BUILD_DIR/module/"
    
    # 复制目录
    cp -r META-INF "$BUILD_DIR/module/"
    cp -r common "$BUILD_DIR/module/"
    
    if [ -f "$DEX_FILE" ]; then
        mkdir -p "$BUILD_DIR/module/system/framework"
        cp "$DEX_FILE" "$BUILD_DIR/module/system/framework/"
    fi
    
    # 打包
    cd "$BUILD_DIR/module"
    zip -r "../$MODULE_NAME" .
    cd ../..
    
    mv "$BUILD_DIR/$MODULE_NAME" .
    
    echo "Module created: $MODULE_NAME"
}

# 主流程
main() {
    check_dependencies
    compile_java
    convert_to_dex
    package_module
    
    echo ""
    echo "=== Build Complete ==="
    echo "Module zip file is ready for installation via Magisk."
    echo ""
    echo "Note: Make sure you have LSPosed or Xposed framework installed"
    echo "for PIN randomization feature."
}

main "$@"
