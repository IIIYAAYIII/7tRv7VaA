#!/system/bin/sh
# 双击锁屏服务
# 通过监听触摸事件实现双击空白处锁屏

MODDIR="$1"
LOGFILE="/data/local/tmp/randompin.log"
TAP_TIMEOUT=400  # 双击间隔时间(毫秒)
LAST_TAP_TIME=0
TAP_COUNT=0

log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOGFILE"
}

# 使用input命令获取触摸事件
# 或使用getevent监听底层输入事件

# 方法1: 使用input命令和screen状态检测
# 方法2: 使用getevent监听触摸屏设备

# 获取触摸屏设备
get_touch_device() {
    for device in /dev/input/event*; do
        if getevent -p "$device" 2>/dev/null | grep -q "ABS_MT_POSITION"; then
            echo "$device"
            return
        fi
    done
}

# 锁定屏幕
lock_screen() {
    # 方法1: 使用power key
    input keyevent KEYCODE_POWER
    log_msg "Screen locked via power key"
}

# 方法2: 使用系统服务
lock_screen_admin() {
    if command -v service >/dev/null 2>&1; then
        service call phone 27 >/dev/null 2>&1 || \
        am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
    fi
    input keyevent KEYCODE_POWER
}

# 检查是否在锁屏界面
is_lockscreen() {
    # 检查当前窗口是否为锁屏
    local dump=$(dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp')
    if echo "$dump" | grep -qiE 'keyguard|lockscreen|Keyguard'; then
        return 0
    fi
    return 1
}

# 检查是否点击在状态栏或应用图标区域
is_valid_tap_area() {
    local x="$1"
    local y="$2"
    local screen_width=$(wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | cut -d'x' -f1)
    local screen_height=$(wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | cut -d'x' -f2)
    
    # 默认值
    [ -z "$screen_width" ] && screen_width=1080
    [ -z "$screen_height" ] && screen_height=2340
    
    # 状态栏高度约100像素（顶部区域）
    local status_bar_height=150
    
    # 底部导航栏高度约150像素
    local nav_bar_height=150
    
    # 检查是否在状态栏区域（顶部）
    if [ "$y" -lt "$status_bar_height" ]; then
        return 1
    fi
    
    # 检查是否在导航栏区域（底部）
    if [ "$y" -gt $((screen_height - nav_bar_height)) ]; then
        return 1
    fi
    
    return 0
}

# 主监听循环 - 使用getevent
main_getevent() {
    local touch_device=$(get_touch_device)
    
    if [ -z "$touch_device" ]; then
        log_msg "Error: No touch device found"
        return 1
    fi
    
    log_msg "Touch device: $touch_device"
    
    local tap_time=0
    local last_tap=0
    local tap_x=0
    local tap_y=0
    local last_x=0
    local last_y=0
    
    # 监听触摸事件
    getevent -l "$touch_device" 2>/dev/null | while read line; do
        # 解析触摸事件
        if echo "$line" | grep -q "ABS_MT_POSITION_X"; then
            tap_x=$(echo "$line" | awk '{print $NF}')
        elif echo "$line" | grep -q "ABS_MT_POSITION_Y"; then
            tap_y=$(echo "$line" | awk '{print $NF}')
        elif echo "$line" | grep -q "BTN_TOUCH"; then
            touch_state=$(echo "$line" | awk '{print $NF}')
            if [ "$touch_state" = "DOWN" ] || [ "$touch_state" = "00000001" ]; then
                current_time=$(date +%s%3N)
                
                # 检查双击间隔
                if [ $((current_time - last_tap)) -lt "$TAP_TIMEOUT" ]; then
                    # 双击检测到
                    if is_valid_tap_area "$tap_x" "$tap_y"; then
                        if ! is_lockscreen; then
                            log_msg "Double tap detected at ($tap_x, $tap_y), locking screen"
                            lock_screen
                        fi
                    fi
                    last_tap=0
                else
                    last_tap=$current_time
                fi
            fi
        fi
    done
}

# 备用方法 - 使用input tap检测（需要root）
main_input() {
    # 此方法需要配合其他工具使用
    # 这里使用一个简化的实现
    log_msg "Using input method fallback"
    
    while true; do
        sleep 1
    done
}

# 检查是否启用双击锁屏
is_enabled() {
    local enabled=$(getprop persist.randompin.doubletap 2>/dev/null)
    [ "$enabled" = "1" ] || [ -z "$enabled" ]
}

# 启动服务
log_msg "Double tap lock service starting..."
log_msg "Module directory: $MODDIR"

# 检查是否有getevent命令
if command -v getevent >/dev/null 2>&1; then
    main_getevent
else
    main_input
fi
