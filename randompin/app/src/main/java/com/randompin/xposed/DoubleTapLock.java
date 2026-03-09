package com.randompin.xposed;

import android.os.SystemClock;
import android.os.PowerManager;
import android.content.Context;
import android.view.MotionEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 完美的桌面双击锁屏方案
 * 专门拦截安卓框架层 WallpaperManagerService 接收到的 "android.wallpaper.tap" 事件
 * 确保只有点击真正的桌面空白处才会触发锁屏
 */
public class DoubleTapLock {
    
    private static final String TAG = "RandomPIN-DoubleTap";
    private static final long DOUBLE_TAP_TIMEOUT = 400; // 毫秒
    private static long lastTapTime = 0;
    
    public static void hookWallpaperTap(ClassLoader classLoader) {
        try {
            // Android 系统内部的壁纸服务
            Class<?> wallpaperManagerService = XposedHelpers.findClass(
                "com.android.server.wallpaper.WallpaperManagerService",
                classLoader
            );
            
            // 安卓16更改了此方法的签名（加入了 displayId 或 IBinder），我们改用无视签名的全面挂载
            // hookAllMethods 能够自动挂载所有叫这个名字的方法，即使它在子类或有多个重载
            java.util.Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
                wallpaperManagerService, 
                "sendWallpaperCommand", 
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // 动态遍历参数，寻找 action 字符串
                        for (Object arg : param.args) {
                            if ("android.wallpaper.tap".equals(arg)) {
                                // 检测到了一次纯粹的桌面空白处点击
                                long currentTime = SystemClock.uptimeMillis();
                                if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT && currentTime - lastTapTime > 50) {
                                    XposedBridge.log("[" + TAG + "] Double tap on wallpaper detected. Locking screen.");
                                    lockScreen(param.thisObject);
                                    lastTapTime = 0; // 重置
                                } else {
                                    lastTapTime = currentTime;
                                }
                                break;
                            }
                        }
                    }
                }
            );
            
            if (unhooks != null && !unhooks.isEmpty()) {
                XposedBridge.log("[" + TAG + "] Successfully dynamically hooked WallpaperManagerService for double tap.");
            } else {
                XposedBridge.log("[" + TAG + "] Could not find sendWallpaperCommand using hookAllMethods!");
            }
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Failed to hook WallpaperManagerService: " + t.getMessage());
        }
    }
    
    /**
     * 作为备用方案，在 SystemUI 层级拦截 ImageWallpaper 的触摸事件
     */
    public static void hookSystemUIWallpaperTap(ClassLoader classLoader) {
        try {
            // Android 16 可能重构了壁纸引擎的内部类，做安全捕获
            Class<?> imageWallpaperClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.wallpapers.ImageWallpaper$GLEngine",
                classLoader
            );
            
            if (imageWallpaperClass == null) {
                XposedBridge.log("[" + TAG + "] (Fallback) ImageWallpaper$GLEngine class not found in SystemUI, skipping fallback.");
                return;
            }
            
            XposedHelpers.findAndHookMethod(
                imageWallpaperClass,
                "onTouchEvent",
                MotionEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        MotionEvent event = (MotionEvent) param.args[0];
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            long currentTime = SystemClock.uptimeMillis();
                            if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT && currentTime - lastTapTime > 50) {
                                XposedBridge.log("[" + TAG + "] (Fallback) Double tap on SystemUI wallpaper detected.");
                                lockScreen(param.thisObject);
                                lastTapTime = 0;
                            } else {
                                lastTapTime = currentTime;
                            }
                        }
                    }
                }
            );
            XposedBridge.log("[" + TAG + "] Successfully hooked SystemUI ImageWallpaper fallback.");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Fallback hook also failed: " + t.getMessage());
        }
    }
    
    private static void lockScreen(Object serviceContext) {
        try {
            Context mContext = null;
            
            // 尝试多种方式获取 Context
            try {
                mContext = (Context) XposedHelpers.getObjectField(serviceContext, "mContext");
            } catch (Throwable e1) {
                try {
                    // 尝试调用 getContext() 方法
                    mContext = (Context) XposedHelpers.callMethod(serviceContext, "getContext");
                } catch (Throwable e2) {
                    // 使用静态方式获取 SystemUI Context
                    try {
                        Class<?> activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null);
                        Object currentActivityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread");
                        mContext = (Context) XposedHelpers.callMethod(currentActivityThread, "getSystemContext");
                    } catch (Throwable e3) {
                        XposedBridge.log("[" + TAG + "] Failed to get Context: " + e3.getMessage());
                    }
                }
            }
            
            if (mContext != null) {
                // 方案1: 使用 PowerManager.goToSleep (需要 DEVICE_POWER 权限，在 SystemUI 进程中有效)
                try {
                    PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                    if (pm != null) {
                        XposedHelpers.callMethod(pm, "goToSleep", SystemClock.uptimeMillis());
                        XposedBridge.log("[" + TAG + "] Screen locked via PowerManager.goToSleep");
                        return;
                    }
                } catch (Throwable e) {
                    XposedBridge.log("[" + TAG + "] goToSleep failed: " + e.getMessage());
                }
                
                // 方案2: 发送休眠广播 (Android 10+ 部分设备有效)
                try {
                    mContext.sendBroadcast(new android.content.Intent("android.intent.action.CLOSE_SYSTEM_DIALOGS"));
                    android.os.Handler handler = new android.os.Handler(mContext.getMainLooper());
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Runtime.getRuntime().exec("input keyevent 26");
                            } catch (Throwable ignored) {}
                        }
                    }, 100);
                    XposedBridge.log("[" + TAG + "] Screen lock triggered via broadcast + keyevent");
                    return;
                } catch (Throwable e) {
                    XposedBridge.log("[" + TAG + "] Broadcast method failed: " + e.getMessage());
                }
            }
            
            // 方案3: 终极保底 - 执行 shell 命令锁屏
            try {
                Runtime.getRuntime().exec("input keyevent 26");
                XposedBridge.log("[" + TAG + "] Screen locked via input keyevent 26");
            } catch (Throwable e) {
                XposedBridge.log("[" + TAG + "] All lock methods failed: " + e.getMessage());
            }
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Error locking screen: " + t.getMessage());
        }
    }
}
