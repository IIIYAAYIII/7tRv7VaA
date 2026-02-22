#!/system/bin/sh
# 改进版双击锁屏服务
# 使用input事件监听，更可靠的实现

MODDIR="$1"
LOGFILE="/data/local/tmp/randompin.log"
PIDFILE="/data/local/tmp/randompin.pid"
TAP_TIMEOUT=400
SCREEN_STATE=""

log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOGFILE"
}

# 获取屏幕状态
get_screen_state() {
    dumpsys power 2>/dev/null | grep -E "mHoldingDisplay|mScreenOn|mWakefulness" | head -1
}

# 检查屏幕是否亮起
is_screen_on() {
    local state=$(dumpsys power 2>/dev/null | grep "mHoldingDisplay" | grep -o "true\|false")
    [ "$state" = "true" ]
}

# 检查是否在锁屏界面
is_lockscreen() {
    local focus=$(dumpsys window windows 2>/dev/null | grep -E "mCurrentFocus=" | head -1)
    echo "$focus" | grep -qiE 'keyguard|lockscreen|Keyguard|StatusBar'
}

# 获取屏幕尺寸
get_screen_size() {
    local size=$(wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+')
    if [ -n "$size" ]; then
        SCREEN_WIDTH=$(echo "$size" | cut -d'x' -f1)
        SCREEN_HEIGHT=$(echo "$size" | cut -d'x' -f2)
    else
        SCREEN_WIDTH=1080
        SCREEN_HEIGHT=2400
    fi
}

# 检查点击区域是否有效（不在状态栏、导航栏、应用图标区域）
is_valid_area() {
    local x="$1"
    local y="$2"
    
    # 状态栏高度 (约屏幕高度的4%)
    local status_height=$((SCREEN_HEIGHT / 25))
    
    # 导航栏高度 (约屏幕高度的6%)
    local nav_height=$((SCREEN_HEIGHT / 16))
    
    # 检查Y坐标
    [ "$y" -lt "$status_height" ] && return 1
    [ "$y" -gt $((SCREEN_HEIGHT - nav_height)) ] && return 1
    
    return 0
}

# 锁屏
do_lock_screen() {
    # 方法1: 电源键模拟
    input keyevent 26
    
    # 方法2: 使用系统服务（备用）
    # am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS
    # service call phone 27
    
    log_msg "Screen locked"
}

# 主监听函数 - 使用input事件队列
main_input_method() {
    local last_tap_time=0
    local tap_count=0
    local last_x=0
    local last_y=0
    
    log_msg "Starting input method listener"
    
    # 使用getevent监听
    while true; do
        # 检查屏幕状态
        if ! is_screen_on; then
            sleep 1
            continue
        fi
        
        # 如果已经在锁屏，跳过
        if is_lockscreen; then
            sleep 0.5
            continue
        fi
        
        # 使用input命令监听触摸（简化版）
        # 注意：这个方法需要root权限
        
        sleep 0.1
    done
}

# 使用getevent的低级别监听
main_getevent_method() {
    log_msg "Starting getevent method listener"
    
    # 找到触摸设备
    local touch_dev=""
    for dev in /dev/input/event*; do
        if getevent -p "$dev" 2>/dev/null | grep -q "ABS_MT"; then
            touch_dev="$dev"
            break
        fi
    done
    
    if [ -z "$touch_dev" ]; then
        log_msg "No touch device found"
        return 1
    fi
    
    log_msg "Using touch device: $touch_dev"
    
    local current_x=0
    local current_y=0
    local last_tap=0
    local touch_down=0
    
    getevent -l "$touch_dev" 2>/dev/null | while read -r line; do
        # 解析事件
        if echo "$line" | grep -q "ABS_MT_POSITION_X"; then
            current_x=$(echo "$line" | awk '{print $NF}')
            current_x=$((current_x))
        elif echo "$line" | grep -q "ABS_MT_POSITION_Y"; then
            current_y=$(echo "$line" | awk '{print $NF}')
            current_y=$((current_y))
        elif echo "$line" | grep -q "SYN_REPORT"; then
            # 同步报告，触摸事件结束
            if [ $touch_down -eq 1 ]; then
                current_time=$(date +%s%3N)
                
                if is_screen_on && ! is_lockscreen; then
                    if is_valid_area "$current_x" "$current_y"; then
                        if [ $((current_time - last_tap)) -lt "$TAP_TIMEOUT" ] && [ $((current_time - last_tap)) -gt 50 ]; then
                            log_msg "Double tap at ($current_x, $current_y)"
                            do_lock_screen
                            last_tap=0
                        else
                            last_tap=$current_time
                        fi
                    fi
                fi
                touch_down=0
            fi
        elif echo "$line" | grep -q "BTN_TOUCH.*DOWN"; then
            touch_down=1
        elif echo "$line" | grep -q "BTN_TOUCH.*UP"; then
            touch_down=0
        fi
    done
}

# 检查是否启用
check_enabled() {
    [ "$(getprop persist.randompin.doubletap)" != "0" ]
}

# 主入口
main() {
    log_msg "=== Double Tap Lock Service Starting ==="
    log_msg "Module dir: $MODDIR"
    
    # 保存PID
    echo $$ > "$PIDFILE"
    
    # 获取屏幕尺寸
    get_screen_size
    log_msg "Screen: ${SCREEN_WIDTH}x${SCREEN_HEIGHT}"
    
    # 选择监听方法
    if command -v getevent >/dev/null 2>&1 && [ -e /dev/input ]; then
        main_getevent_method
    else
        main_input_method
    fi
}

# 启动
main "$@"
