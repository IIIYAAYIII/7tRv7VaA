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
            
            // 拦截发送给壁纸的命令 (当在桌面上点击空白处时，Launcher会发送该命令)
            XposedHelpers.findAndHookMethod(
                wallpaperManagerService,
                "sendWallpaperCommand",
                String.class,  // callingPackage
                String.class,  // action (e.g. "android.wallpaper.tap")
                int.class,     // x
                int.class,     // y
                int.class,     // z
                android.os.Bundle.class, // extras
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String action = (String) param.args[1];
                        if ("android.wallpaper.tap".equals(action)) {
                            // 检测到了一次纯粹的桌面空白处点击
                            long currentTime = SystemClock.uptimeMillis();
                            if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT && currentTime - lastTapTime > 50) {
                                // 触发双击锁屏
                                XposedBridge.log("[" + TAG + "] Double tap on wallpaper detected. Locking screen.");
                                lockScreen(param.thisObject);
                                lastTapTime = 0; // 重置
                            } else {
                                lastTapTime = currentTime;
                            }
                        }
                    }
                }
            );
            
            XposedBridge.log("[" + TAG + "] Successfully hooked WallpaperManagerService for double tap.");
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Failed to hook WallpaperManagerService: " + t.getMessage());
            // 备用方案：Hook SystemUI 的 Wallpaper 触摸回调
            hookSystemUIWallpaperTap(classLoader);
        }
    }
    
    /**
     * 作为备用方案，在 SystemUI 层级拦截 ImageWallpaper 的触摸事件
     */
    private static void hookSystemUIWallpaperTap(ClassLoader classLoader) {
        try {
            Class<?> imageWallpaperClass = XposedHelpers.findClass(
                "com.android.systemui.wallpapers.ImageWallpaper$GLEngine",
                classLoader
            );
            
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
            // 尝试从服务上下文中获取 Context
            Context mContext = null;
            try {
                mContext = (Context) XposedHelpers.getObjectField(serviceContext, "mContext");
            } catch (Throwable e) {
                // Ignore
            }
            
            if (mContext != null) {
                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    XposedHelpers.callMethod(pm, "goToSleep", SystemClock.uptimeMillis());
                    return;
                }
            }
            
            // 如果获取不到 Context，执行底层的 shell 锁屏命令作为终极保底
            Runtime.getRuntime().exec("input keyevent 26");
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Error locking screen: " + t.getMessage());
        }
    }
}
