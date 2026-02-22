#!/system/bin/sh
# 模块加载后执行

MODDIR=${0%/*}
MODDIR=${MODDIR%/post-fs-data.sh}

# 设置SELinux权限
if [ -f "$MODDIR/common/double_tap_lock.sh" ]; then
    chmod 755 "$MODDIR/common/double_tap_lock.sh"
    chown root:root "$MODDIR/common/double_tap_lock.sh"
fi

# 设置系统属性
setprop persist.randompin.enabled 1
