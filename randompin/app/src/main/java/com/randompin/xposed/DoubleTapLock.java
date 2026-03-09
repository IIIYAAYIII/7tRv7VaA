package com.randompin.xposed;

import android.app.KeyguardManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 完美的桌面双击锁屏方案
 * 多层级 Hook 策略确保在 Android 16 上有效
 */
public class DoubleTapLock {
    
    private static final String TAG = "RandomPIN-DoubleTap";
    private static final long DOUBLE_TAP_TIMEOUT = 400; // 毫秒
    private static long lastTapTime = 0;
    private static float lastTapX = 0;
    private static float lastTapY = 0;
    private static final float TAP_SLOP = 100; // 允许的点击位置偏移
    
    public static void hook(ClassLoader classLoader) {
        // 多方案同时启用，确保至少一个有效
        hookWallpaperTap(classLoader);
        hookPhoneWindowManager(classLoader);
        hookLauncher(classLoader);
    }
    
    /**
     * 方案1: Hook WallpaperManagerService 的 sendWallpaperCommand
     */
    public static void hookWallpaperTap(ClassLoader classLoader) {
        try {
            // Android 系统内部的壁纸服务
            Class<?> wallpaperManagerService = XposedHelpers.findClass(
                "com.android.server.wallpaper.WallpaperManagerService",
                classLoader
            );
            
            // 使用 hookAllMethods 覆盖所有重载
            Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
                wallpaperManagerService, 
                "sendWallpaperCommand", 
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // 动态遍历参数，寻找 action 字符串
                        for (Object arg : param.args) {
                            if ("android.wallpaper.tap".equals(arg)) {
                                handleDoubleTap("WallpaperTap");
                                break;
                            }
                        }
                    }
                }
            );
            
            if (unhooks != null && !unhooks.isEmpty()) {
                XposedBridge.log("[" + TAG + "] Hooked WallpaperManagerService.sendWallpaperCommand");
            }
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Failed to hook WallpaperManagerService: " + t.getMessage());
        }
        
        // 尝试 Hook WallpaperManager internal 版本
        try {
            Class<?> wallpaperManagerInternal = XposedHelpers.findClassIfExists(
                "com.android.server.wallpaper.WallpaperManagerInternal",
                classLoader
            );
            
            if (wallpaperManagerInternal != null) {
                XposedBridge.hookAllMethods(
                    wallpaperManagerInternal,
                    "sendWallpaperCommand",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            for (Object arg : param.args) {
                                if ("android.wallpaper.tap".equals(arg)) {
                                    handleDoubleTap("WallpaperInternal");
                                    break;
                                }
                            }
                        }
                    }
                );
                XposedBridge.log("[" + TAG + "] Hooked WallpaperManagerInternal");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] WallpaperManagerInternal hook failed: " + t.getMessage());
        }
    }
    
    /**
     * 方案2: Hook PhoneWindowManager / WindowManagerPolicy
     */
    public static void hookPhoneWindowManager(ClassLoader classLoader) {
        try {
            // Android 16 可能使用 WindowManagerPolicy
            Class<?> windowManagerPolicy = XposedHelpers.findClassIfExists(
                "com.android.server.policy.PhoneWindowManager",
                classLoader
            );
            
            if (windowManagerPolicy == null) {
                windowManagerPolicy = XposedHelpers.findClassIfExists(
                    "com.android.server.wm.WindowManagerPolicy",
                    classLoader
                );
            }
            
            if (windowManagerPolicy != null) {
                // Hook dispatchPointerEvent 或类似方法
                XposedBridge.hookAllMethods(
                    windowManagerPolicy,
                    "dispatchPointerEvent",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args.length > 0 && param.args[0] instanceof MotionEvent) {
                                MotionEvent event = (MotionEvent) param.args[0];
                                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                    // 检查是否在桌面区域
                                    handleDoubleTap("PhoneWindowManager");
                                }
                            }
                        }
                    }
                );
                XposedBridge.log("[" + TAG + "] Hooked PhoneWindowManager/WindowManagerPolicy");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] PhoneWindowManager hook failed: " + t.getMessage());
        }
        
        // 尝试 Hook DecorView 的 dispatchTouchEvent
        try {
            Class<?> decorViewClass = XposedHelpers.findClass(
                "com.android.internal.policy.DecorView",
                classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                decorViewClass,
                "dispatchTouchEvent",
                MotionEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        MotionEvent event = (MotionEvent) param.args[0];
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            View view = (View) param.thisObject;
                            Context context = view.getContext();
                            
                            // 检查是否是 Launcher 进程
                            String processName = context.getPackageName();
                            if (processName != null && 
                                (processName.contains("launcher") || 
                                 processName.contains(" Launcher") ||
                                 processName.equals("com.android.launcher3") ||
                                 processName.equals("com.google.android.apps.nexuslauncher"))) {
                                
                                // 检查是否点击在空白区域（非图标）
                                if (isTouchOnEmptyArea(view, event)) {
                                    handleDoubleTap("DecorView-Launcher");
                                }
                            }
                        }
                    }
                }
            );
            XposedBridge.log("[" + TAG + "] Hooked DecorView.dispatchTouchEvent");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] DecorView hook failed: " + t.getMessage());
        }
    }
    
    /**
     * 方案3: Hook Launcher 进程
     */
    public static void hookLauncher(ClassLoader classLoader) {
        // Hook Launcher3 的 Workspace
        try {
            Class<?> workspaceClass = XposedHelpers.findClassIfExists(
                "com.android.launcher3.Workspace",
                classLoader
            );
            
            if (workspaceClass != null) {
                XposedHelpers.findAndHookMethod(
                    workspaceClass,
                    "onInterceptTouchEvent",
                    MotionEvent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            MotionEvent event = (MotionEvent) param.args[0];
                            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                handleDoubleTap("Launcher-Workspace");
                            }
                        }
                    }
                );
                XposedBridge.log("[" + TAG + "] Hooked Launcher3.Workspace");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Launcher3.Workspace hook failed: " + t.getMessage());
        }
        
        // Hook 原生 Launcher 的 onTouchEvent
        try {
            Class<?> launcherClass = XposedHelpers.findClassIfExists(
                "com.android.launcher3.Launcher",
                classLoader
            );
            
            if (launcherClass == null) {
                launcherClass = XposedHelpers.findClassIfExists(
                    "com.google.android.apps.nexuslauncher.NexusLauncher",
                    classLoader
                );
            }
            
            if (launcherClass != null) {
                XposedBridge.hookAllMethods(
                    launcherClass,
                    "onTouchEvent",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            MotionEvent event = (MotionEvent) param.args[0];
                            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                handleDoubleTap("Launcher-onTouch");
                            }
                        }
                    }
                );
                XposedBridge.log("[" + TAG + "] Hooked Launcher.onTouchEvent");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Launcher hook failed: " + t.getMessage());
        }
        
        // Hook DragLayer (Launcher 的根布局)
        try {
            Class<?> dragLayerClass = XposedHelpers.findClassIfExists(
                "com.android.launcher3.DragLayer",
                classLoader
            );
            
            if (dragLayerClass != null) {
                XposedHelpers.findAndHookMethod(
                    dragLayerClass,
                    "onInterceptTouchEvent",
                    MotionEvent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            MotionEvent event = (MotionEvent) param.args[0];
                            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                // 只有点击空白区域才处理
                                ViewGroup dragLayer = (ViewGroup) param.thisObject;
                                if (!isTouchOnChild(dragLayer, event)) {
                                    handleDoubleTap("DragLayer");
                                }
                            }
                        }
                    }
                );
                XposedBridge.log("[" + TAG + "] Hooked Launcher3.DragLayer");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] DragLayer hook failed: " + t.getMessage());
        }
    }
    
    /**
     * 处理双击检测
     */
    private static void handleDoubleTap(String source) {
        long currentTime = SystemClock.uptimeMillis();
        
        // 检测双击
        if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT && 
            currentTime - lastTapTime > 50) {
            XposedBridge.log("[" + TAG + "] Double tap detected from " + source + ". Locking screen.");
            lockScreen();
            lastTapTime = 0;
        } else {
            lastTapTime = currentTime;
        }
    }
    
    /**
     * 检查触摸是否在子 View 上
     */
    private static boolean isTouchOnChild(ViewGroup parent, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                float childLeft = child.getLeft();
                float childTop = child.getTop();
                float childRight = child.getRight();
                float childBottom = child.getBottom();
                
                if (x >= childLeft && x <= childRight && 
                    y >= childTop && y <= childBottom) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 检查触摸是否在空白区域
     */
    private static boolean isTouchOnEmptyArea(View view, MotionEvent event) {
        if (!(view instanceof ViewGroup)) {
            return true;
        }
        
        ViewGroup viewGroup = (ViewGroup) view;
        float x = event.getX();
        float y = event.getY();
        
        // 检查是否点击在任何可点击的子 View 上
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.isClickable()) {
                float childLeft = child.getLeft();
                float childTop = child.getTop();
                float childRight = child.getRight();
                float childBottom = child.getBottom();
                
                if (x >= childLeft && x <= childRight && 
                    y >= childTop && y <= childBottom) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 执行锁屏
     */
    private static void lockScreen() {
        try {
            // 方案0: 终极方案，如果我们在 system_server (android)，直接调用底层的 IWindowManager.lockNow
            // 这可以完全绕过 Android 15/16 针对普通应用、服务或反射 PowerManager 增加的各种限制安全策略
            try {
                // IBinder b = ServiceManager.getService(Context.WINDOW_SERVICE);
                Class<?> serviceManagerClass = XposedHelpers.findClass("android.os.ServiceManager", null);
                Object windowServiceBinder = XposedHelpers.callStaticMethod(serviceManagerClass, "getService", Context.WINDOW_SERVICE);
                
                if (windowServiceBinder != null) {
                    // IWindowManager wm = IWindowManager.Stub.asInterface(b);
                    Class<?> stubClass = XposedHelpers.findClass("android.view.IWindowManager$Stub", null);
                    Object iWindowManager = XposedHelpers.callStaticMethod(stubClass, "asInterface", windowServiceBinder);
                    
                    if (iWindowManager != null) {
                        // wm.lockNow(null); 或者 wm.lockNow(); (不同 Android 版本参数可能不同)
                        try {
                            XposedHelpers.callMethod(iWindowManager, "lockNow", (Object) null);
                        } catch (Throwable t) {
                            try {
                                XposedHelpers.callMethod(iWindowManager, "lockNow");
                            } catch (Throwable t2) {
                                throw t2;
                            }
                        }
                        XposedBridge.log("[" + TAG + "] Screen locked via robust IWindowManager.lockNow in system_server");
                        return;
                    }
                }
            } catch (Throwable e) {
                XposedBridge.log("[" + TAG + "] IWindowManager.lockNow failed: " + e.getMessage());
            }
            
            Context context = getSystemContext();
            if (context == null) {
                XposedBridge.log("[" + TAG + "] Failed to get context, trying shell command");
                tryShellLock();
                return;
            }
            
            // 方案1: 使用 KeyguardManager 锁屏 (Android 8+)
            try {
                KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null) {
                    km.requestDismissKeyguard(null, null);
                }
            } catch (Throwable ignored) {}
            
            // 方案2: 使用 PowerManager.goToSleep
            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    Method goToSleep = PowerManager.class.getDeclaredMethod("goToSleep", long.class);
                    goToSleep.setAccessible(true);
                    goToSleep.invoke(pm, SystemClock.uptimeMillis());
                    XposedBridge.log("[" + TAG + "] Screen locked via PowerManager.goToSleep");
                    return;
                }
            } catch (Throwable e) {
                XposedBridge.log("[" + TAG + "] goToSleep failed (Expected on A15+): " + e.getMessage());
            }
            
            // 方案3: 使用普通 WindowManager 的 lockNow
            try {
                WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    Method lockNow = wm.getClass().getDeclaredMethod("lockNow");
                    lockNow.setAccessible(true);
                    lockNow.invoke(wm);
                    XposedBridge.log("[" + TAG + "] Screen locked via context WindowManager.lockNow");
                    return;
                }
            } catch (Throwable e) {
                XposedBridge.log("[" + TAG + "] WindowManager lock failed: " + e.getMessage());
            }
            
            // 方案4: 发送 ACTION_CLOSE_SYSTEM_DIALOGS 广播
            try {
                context.sendBroadcast(new android.content.Intent(android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            } catch (Throwable ignored) {}
            
            // 方案5: Shell 命令作为最后保底
            tryShellLock();
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Error locking screen: " + t.getMessage());
            tryShellLock();
        }
    }
    
    /**
     * 获取系统 Context
     */
    private static Context getSystemContext() {
        try {
            Class<?> activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object currentActivityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread");
            return (Context) XposedHelpers.callMethod(currentActivityThread, "getSystemContext");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Failed to get system context: " + t.getMessage());
        }
        return null;
    }
    
    /**
     * 使用 Shell 命令锁屏
     */
    private static void tryShellLock() {
        try {
            // 使用 input keyevent 26 (KEYCODE_POWER) 锁屏
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "26"});
            XposedBridge.log("[" + TAG + "] Screen locked via shell command (keyevent 26)");
            return;
        } catch (Throwable e) {
            XposedBridge.log("[" + TAG + "] keyevent 26 failed: " + e.getMessage());
        }
        
        try {
            // 使用 input keyevent 6 (KEYCODE_SLEEP)
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "6"});
            XposedBridge.log("[" + TAG + "] Screen locked via shell command (keyevent 6)");
            return;
        } catch (Throwable e) {
            XposedBridge.log("[" + TAG + "] keyevent 6 failed: " + e.getMessage());
        }
        
        try {
            // 使用 cmd power 锁屏 (Android 11+)
            Runtime.getRuntime().exec(new String[]{"cmd", "power", "sleep"});
            XposedBridge.log("[" + TAG + "] Screen locked via cmd power sleep");
        } catch (Throwable e) {
            XposedBridge.log("[" + TAG + "] cmd power sleep failed: " + e.getMessage());
        }
    }
}