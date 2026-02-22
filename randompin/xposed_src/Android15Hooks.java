package com.randompin.xposed;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 安卓15+专用Hook
 * 处理新版KeyguardUI的变化
 */
public class Android15Hooks {
    
    private static final String TAG = "RandomPIN-Android15";
    
    /**
     * Hook新版KeyguardBouncer
     */
    public static void hookKeyguardBouncer(ClassLoader classLoader) {
        try {
            // 安卓15+ KeyguardBouncer实现
            Class<?> bouncerClass = XposedHelpers.findClass(
                "com.android.keyguard.KeyguardBouncer",
                classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                bouncerClass,
                "show",
                boolean.class,
                Runnable.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 当bouncer显示时，准备乱序PIN
                        Object view = XposedHelpers.getObjectField(param.thisObject, "mKeyguardView");
                        if (view != null) {
                            randomizePINInContainer((ViewGroup) view);
                        }
                    }
                }
            );
            
            XposedBridge.log("[" + TAG + "] Hooked KeyguardBouncer");
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Failed to hook KeyguardBouncer: " + t.getMessage());
        }
    }
    
    /**
     * Hook NumPadAnimationController (安卓15+)
     */
    public static void hookNumPadAnimationController(ClassLoader classLoader) {
        try {
            Class<?> controllerClass = XposedHelpers.findClass(
                "com.android.keyguard.NumPadAnimationController",
                classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                controllerClass,
                "startAppearAnimation",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // 在动画开始前乱序
                        Object key = XposedHelpers.getObjectField(param.thisObject, "mNumPadKey");
                        if (key != null) {
                            View view = (View) key;
                            ViewGroup parent = (ViewGroup) view.getParent();
                            if (parent != null) {
                                randomizePINInContainer(parent);
                            }
                        }
                    }
                }
            );
            
            XposedBridge.log("[" + TAG + "] Hooked NumPadAnimationController");
            
        } catch (Throwable t) {
            // 可能在某些设备上不存在
            XposedBridge.log("[" + TAG + "] NumPadAnimationController not found (expected)");
        }
    }
    
    /**
     * Hook LockIconViewController (安卓15+ 新锁屏界面)
     */
    public static void hookLockIconViewController(ClassLoader classLoader) {
        try {
            Class<?> lockIconClass = XposedHelpers.findClass(
                "com.android.keyguard.LockIconViewController",
                classLoader
            );
            
            // Hook更新方法
            XposedHelpers.findAndHookMethod(
                lockIconClass,
                "updateState",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 处理新锁屏状态更新
                    }
                }
            );
            
            XposedBridge.log("[" + TAG + "] Hooked LockIconViewController");
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] LockIconViewController not found");
        }
    }
    
    /**
     * 乱序PIN按钮容器
     */
    private static void randomizePINInContainer(ViewGroup container) {
        if (container == null) return;
        
        try {
            // 递归查找PIN输入区域
            findAndRandomizePINButtons(container);
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Error in randomizePINInContainer: " + t.getMessage());
        }
    }
    
    /**
     * 递归查找并乱序PIN按钮
     */
    private static void findAndRandomizePINButtons(ViewGroup parent) {
        // 查找包含NumPadKey的容器
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            
            String className = child.getClass().getName();
            
            // 检查是否为NumPadKey容器
            if (className.contains("NumPadKey") || className.contains("PasswordTextView")) {
                // 找到了，乱序同级元素
                randomizeSiblings(parent);
                return;
            }
            
            // 递归查找
            if (child instanceof ViewGroup) {
                findAndRandomizePINButtons((ViewGroup) child);
            }
        }
    }
    
    /**
     * 乱序同级按钮
     */
    private static void randomizeSiblings(ViewGroup container) {
        java.util.List<View> digitButtons = new java.util.ArrayList<>();
        java.util.List<View> otherButtons = new java.util.ArrayList<>();
        
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            
            if (isDigitButton(child)) {
                digitButtons.add(child);
            } else {
                otherButtons.add(child);
            }
        }
        
        if (digitButtons.size() >= 10) {
            java.util.Collections.shuffle(digitButtons);
            
            container.removeAllViews();
            for (View btn : digitButtons) {
                container.addView(btn);
            }
            for (View btn : otherButtons) {
                container.addView(btn);
            }
            
            XposedBridge.log("[" + TAG + "] PIN buttons randomized");
        }
    }
    
    /**
     * 判断是否为数字按钮
     */
    private static boolean isDigitButton(View view) {
        if (view instanceof android.widget.Button) {
            String text = ((android.widget.Button) view).getText().toString();
            return text.matches("[0-9]");
        }
        
        Object tag = view.getTag();
        if (tag instanceof Integer) {
            int digit = (Integer) tag;
            return digit >= 0 && digit <= 9;
        }
        
        if (view.getContentDescription() != null) {
            String desc = view.getContentDescription().toString();
            return desc.matches("[0-9]");
        }
        
        // 检查类名
        String className = view.getClass().getName();
        return className.contains("NumPadKey");
    }
}
