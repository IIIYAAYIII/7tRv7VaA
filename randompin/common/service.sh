#!/system/bin/sh
# 服务启动脚本

MODDIR=${0%/*}
MODDIR=${MODDIR%/common}

# 等待系统启动
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

sleep 3

# 启动双击锁屏监听
if [ -f "$MODDIR/common/double_tap_lock.sh" ]; then
    nohup sh "$MODDIR/common/double_tap_lock.sh" "$MODDIR" &
fi
