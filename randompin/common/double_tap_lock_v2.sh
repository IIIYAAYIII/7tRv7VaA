#!/system/bin/sh
# 改进版双击锁屏服务 (纯Shell精简版)
# 专为高版本安卓(Android 15/16)重写，解决getevent失效问题

MODDIR="$1"
LOGFILE="/data/local/tmp/randompin.log"
PIDFILE="/data/local/tmp/randompin.pid"

# 连续下按(DOWN)的最大间隔毫秒数
TAP_TIMEOUT=400 

log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOGFILE"
}

get_screen_size() {
    local size=$(wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | head -1)
    if [ -n "$size" ]; then
        SCREEN_WIDTH=$(echo "$size" | cut -d'x' -f1)
        SCREEN_HEIGHT=$(echo "$size" | cut -d'x' -f2)
    else
        # Fallback value
        SCREEN_WIDTH=1080
        SCREEN_HEIGHT=2400
    fi
}

is_screen_on() {
    # 极速判断是否亮屏
    dumpsys power 2>/dev/null | grep -q "mWakefulness=Awake"
}

is_lockscreen() {
    # 极速判断是否在锁屏
    dumpsys window windows 2>/dev/null | grep -E "mCurrentFocus=" | head -1 | grep -qiE 'keyguard|lockscreen|StatusBar'
}

is_valid_area() {
    local y="$1"
    
    # 顶部状态栏高度估值 (屏幕的4%)
    local status_height=$((SCREEN_HEIGHT / 25))
    # 底部导航栏高度估值 (屏幕的6%)
    local nav_height=$((SCREEN_HEIGHT / 16))
    
    [ "$y" -lt "$status_height" ] && return 1
    [ "$y" -gt $((SCREEN_HEIGHT - nav_height)) ] && return 1
    
    return 0
}

do_lock() {
    log_msg "Double Tap Triggered! Locking..."
    input keyevent 26
}

main_listen() {
    # 自动探测支持 ABS_MT_POSITION_X 的主力触摸屏设备
    local touch_dev=""
    for dev in /dev/input/event*; do
        if getevent -p "$dev" 2>/dev/null | grep -q "ABS_MT_POSITION_X"; then
            touch_dev="$dev"
            break
        fi
    done

    if [ -z "$touch_dev" ]; then
        log_msg "Fatal: No Multi-Touch device found in /dev/input/event*"
        return 1
    fi

    log_msg "Using touch device: $touch_dev"

    local current_y=0
    local touch_down=0
    local last_tap_time=0
    
    # 使用getevent循环逐行解析
    getevent -l "$touch_dev" 2>/dev/null | while read -r line; do
        if echo "$line" | grep -q "ABS_MT_POSITION_Y"; then
            # 记录触摸Y坐标 (十六进制转十进制)
            # 输出格式如: ABS_MT_POSITION_Y    000003e8
            local val_hex=$(echo "$line" | awk '{print $NF}')
            current_y=$((16#$val_hex))
            
        elif echo "$line" | grep -qE "BTN_TOUCH.*DOWN"; then
            touch_down=1
            
        elif echo "$line" | grep -q "SYN_REPORT"; then
            if [ $touch_down -eq 1 ]; then
                local current_time=$(date +%s%3N)
                
                # 双击判定逻辑
                if is_screen_on && ! is_lockscreen && is_valid_area "$current_y"; then
                    local diff=$((current_time - last_tap_time))
                    
                    if [ $diff -lt $TAP_TIMEOUT ] && [ $diff -gt 50 ]; then
                        do_lock
                        last_tap_time=0  # 重置以防连点锁屏后立刻亮屏
                    else
                        last_tap_time=$current_time
                    fi
                else
                    last_tap_time=0
                fi
                
                touch_down=0
            fi
        fi
    done
}

main() {
    log_msg "=== RandomPIN Double Tap Service Started ==="
    echo $$ > "$PIDFILE"
    get_screen_size
    log_msg "Screen Size: ${SCREEN_WIDTH}x${SCREEN_HEIGHT}"
    
    main_listen
}

main "$@"
