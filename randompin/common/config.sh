#!/system/bin/sh
# 配置管理脚本
# 用于启用/禁用模块功能

MODDIR=${0%/*}
MODDIR=${MODDIR%/common}
CONFIG_FILE="$MODDIR/common/config"

# 默认配置
DEFAULT_CONFIG="# RandomPIN Configuration
# PIN乱序功能 (需要LSPosed/Xposed)
PIN_RANDOMIZE=1
# 双击锁屏功能
DOUBLE_TAP_LOCK=1
# 双击间隔时间(毫秒)
DOUBLE_TAP_TIMEOUT=400"

# 初始化配置文件
init_config() {
    if [ ! -f "$CONFIG_FILE" ]; then
        echo "$DEFAULT_CONFIG" > "$CONFIG_FILE"
    fi
}

# 读取配置
get_config() {
    local key="$1"
    if [ -f "$CONFIG_FILE" ]; then
        grep "^$key=" "$CONFIG_FILE" 2>/dev/null | cut -d'=' -f2
    else
        echo ""
    fi
}

# 设置配置
set_config() {
    local key="$1"
    local value="$2"
    
    if [ -f "$CONFIG_FILE" ]; then
        if grep -q "^$key=" "$CONFIG_FILE"; then
            sed -i "s/^$key=.*/$key=$value/" "$CONFIG_FILE"
        else
            echo "$key=$value" >> "$CONFIG_FILE"
        fi
    fi
    
    # 更新系统属性
    case "$key" in
        "PIN_RANDOMIZE")
            setprop persist.randompin.randomize "$value"
            ;;
        "DOUBLE_TAP_LOCK")
            setprop persist.randompin.doubletap "$value"
            # 重启双击锁屏服务
            if [ "$value" = "1" ]; then
                pkill -f double_tap_lock.sh 2>/dev/null
                if [ -f "$MODDIR/common/double_tap_lock.sh" ]; then
                    nohup sh "$MODDIR/common/double_tap_lock.sh" "$MODDIR" &
                fi
            else
                pkill -f double_tap_lock.sh 2>/dev/null
            fi
            ;;
        "DOUBLE_TAP_TIMEOUT")
            setprop persist.randompin.timeout "$value"
            ;;
    esac
}

# 显示当前配置
show_config() {
    echo "=== RandomPIN Configuration ==="
    echo "PIN Randomize: $(get_config PIN_RANDOMIZE) (need LSPosed/Xposed)"
    echo "Double Tap Lock: $(get_config DOUBLE_TAP_LOCK)"
    echo "Double Tap Timeout: $(get_config DOUBLE_TAP_TIMEOUT) ms"
    echo ""
    echo "System Properties:"
    echo "  persist.randompin.enabled: $(getprop persist.randompin.enabled)"
    echo "  persist.randompin.xposed: $(getprop persist.randompin.xposed)"
    echo "  persist.randompin.doubletap: $(getprop persist.randompin.doubletap)"
}

# 主入口
case "$1" in
    "init")
        init_config
        ;;
    "get")
        get_config "$2"
        ;;
    "set")
        set_config "$2" "$3"
        ;;
    "show")
        show_config
        ;;
    *)
        echo "Usage: $0 {init|get <key>|set <key> <value>|show}"
        ;;
esac
