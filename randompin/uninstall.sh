#!/system/bin/sh
# 模块卸载脚本

MODDIR=${0%/*}
MODDIR=${MODDIR%/uninstall.sh}

# 停止双击锁屏服务
pkill -f "double_tap_lock.sh" 2>/dev/null
killall double_tap_lock.sh 2>/dev/null

# 清理临时文件
rm -f /data/local/tmp/randompin.dex
rm -f /data/local/tmp/randompin.log

# 重置属性
setprop persist.randompin.enabled 0
setprop persist.randompin.xposed 0
setprop persist.randompin.doubletap 0

exit 0
