#!/system/bin/sh
# 不要在此处修改，修改请去common文件夹内修改
# 双击锁屏服务启动脚本

MODDIR=${0%/*}
MODDIR=${MODDIR%/service.sh}

# 等待系统启动完成
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

sleep 5

# 启动双击锁屏服务
if [ -f "$MODDIR/common/double_tap_lock.sh" ]; then
    nohup sh "$MODDIR/common/double_tap_lock.sh" "$MODDIR" > /dev/null 2>&1 &
fi

# 检查是否安装了LSPosed/Xposed
if [ -d /data/adb/lspd ] || [ -d /data/adb/edxposed ] || [ -d /data/adb/taichi ]; then
    # Xposed框架已安装，PIN乱序功能可用
    setprop persist.randompin.xposed 1
else
    # 未检测到Xposed框架
    setprop persist.randompin.xposed 0
fi
