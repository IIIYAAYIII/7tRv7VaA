#!/system/bin/sh
# 预启动脚本

MODDIR=${0%/*}
MODDIR=${MODDIR%/common}

# 复制dex文件到临时目录（如果存在）
if [ -f "$MODDIR/system/framework/randompin.dex" ]; then
    cp "$MODDIR/system/framework/randompin.dex" /data/local/tmp/
    chmod 644 /data/local/tmp/randompin.dex
fi

# 设置属性
setprop persist.randompin.enabled 1
