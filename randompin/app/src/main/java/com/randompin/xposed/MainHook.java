package com.randompin.xposed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Random PIN Xposed模块
 * 实现锁屏PIN码乱序显示
 * 支持安卓10-16 (API 29-35+)
 */
public class MainHook implements IXposedHookLoadPackage {
    
    private static final String TAG = "RandomPIN";
    private static final String[] DIGITS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 我们需要 Hook 两个包: systemui (锁屏密码) 和 android (系统服务, 用于双击壁纸)
        if (lpparam.packageName.equals("android")) {
            // Hook 系统服务处理双击壁纸逻辑
            DoubleTapLock.hookWallpaperTap(lpparam.classLoader);
            return;
        }

        // Hook 模块自身的 UI界面，用于向用户展示“已激活”状态
        if (lpparam.packageName.equals("com.randompin.xposed")) {
            XposedHelpers.findAndHookMethod(
                "com.randompin.xposed.SettingsActivity",
                lpparam.classLoader,
                "isModuleActive",
                de.robv.android.xposed.XC_MethodReplacement.returnConstant(true)
            );
            return;
        }

        if (!lpparam.packageName.equals("com.android.systemui")) {
            return;
        }
        
        int sdk = Build.VERSION.SDK_INT;
        XposedBridge.log("[" + TAG + "] Hooking SystemUI, SDK=" + sdk);
        
        // 根据安卓版本选择不同的Hook方式
        if (sdk >= 35) {
            // 安卓15+ (包含安卓 16, SDK 36) 使用新的Hook方式
            hookAndroid15Plus(lpparam);
            // 作为 Nubia 等深度定制安卓 16 系统的兜底，依然挂载传统的 PIN 视图
            hookKeyguardPINView(lpparam);
        } else {
            // 通用Hook (安卓10-14)
            hookKeyguardPINView(lpparam);
            hookLockPatternView(lpparam);
            hookNumPadKey(lpparam);
        }
    }
    
    /**
     * 安卓15+ 专用Hook
     */
    private void hookAndroid15Plus(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log("[" + TAG + "] Applying Android 15+ hooks");
        Android15Hooks.hookKeyguardBouncer(lpparam.classLoader);
        Android15Hooks.hookNumPadAnimationController(lpparam.classLoader);
        Android15Hooks.hookLockIconViewController(lpparam.classLoader);
    }
    
    /**
     * Hook KeyguardPINView (安卓10-14) 包括 SIM 视图
     */
    private void hookKeyguardPINView(XC_LoadPackage.LoadPackageParam lpparam) {
        // 挂载普通 PIN 码视图
        hookGenericPINView(lpparam, "com.android.keyguard.KeyguardPINView");
        // 挂载 SIM PIN 码视图
        hookGenericPINView(lpparam, "com.android.keyguard.KeyguardSimPinView");
        // 挂载 SIM PUK 码视图
        hookGenericPINView(lpparam, "com.android.keyguard.KeyguardSimPukView");
    }

    private void hookGenericPINView(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        try {
            Class<?> keyguardPINViewClass = XposedHelpers.findClass(
                "com.android.keyguard.KeyguardPINView",
                lpparam.classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                keyguardPINViewClass,
                "onFinishInflate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        ViewGroup view = (ViewGroup) param.thisObject;
                        randomizePINButtons(view);
                    }
                }
            );
            
            // Hook 密码验证方法，需要正确映射乱序后的按钮
            XposedHelpers.findAndHookMethod(
                keyguardPINViewClass,
                "verifyPasswordAndUnlock",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // 处理密码验证
                    }
                }
            );
            
            XposedBridge.log("[" + TAG + "] Successfully hooked " + className);
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Failed to hook " + className + ": " + t.getMessage());
        }
    }
    
    /**
     * Hook LockPatternView (部分设备)
     */
    private void hookLockPatternView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> lockPatternViewClass = XposedHelpers.findClass(
                "com.android.internal.widget.LockPatternView",
                lpparam.classLoader
            );
            
            XposedBridge.log("[" + TAG + "] Found LockPatternView class");
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] LockPatternView not found (expected on most devices)");
        }
    }
    
    /**
     * Hook NumPadKey (安卓12+)
     */
    private void hookNumPadKey(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 安卓12+ 使用NumPadKey
            Class<?> numPadKeyClass = XposedHelpers.findClass(
                "com.android.keyguard.NumPadKey",
                lpparam.classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                numPadKeyClass,
                "onFinishInflate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // NumPadKey已加载，父容器会在之后处理
                    }
                }
            );
            
            XposedBridge.log("[" + TAG + "] Successfully hooked NumPadKey");
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Failed to hook NumPadKey: " + t.getMessage());
        }
    }
    
    /**
     * 随机化PIN按钮顺序
     */
    private void randomizePINButtons(ViewGroup container) {
        try {
            // 查找数字按钮容器
            ViewGroup pinContainer = findPINContainer(container);
            
            if (pinContainer == null) {
                XposedBridge.log("[" + TAG + "] PIN container not found");
                return;
            }
            
            // 收集所有数字按钮
            List<View> digitButtons = new ArrayList<>();
            List<View> otherButtons = new ArrayList<>();
            
            for (int i = 0; i < pinContainer.getChildCount(); i++) {
                View child = pinContainer.getChildAt(i);
                
                if (isDigitButton(child)) {
                    digitButtons.add(child);
                } else {
                    otherButtons.add(child);
                }
            }
            
            if (digitButtons.isEmpty()) {
                XposedBridge.log("[" + TAG + "] No digit buttons found");
                return;
            }
            
            // 随机打乱数字按钮顺序
            Collections.shuffle(digitButtons, new Random());
            
            // 重新排列按钮
            pinContainer.removeAllViews();
            
            for (View button : digitButtons) {
                pinContainer.addView(button);
            }
            for (View button : otherButtons) {
                pinContainer.addView(button);
            }
            
            XposedBridge.log("[" + TAG + "] PIN buttons randomized successfully");
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Error randomizing PIN buttons: " + t.getMessage());
        }
    }
    
    /**
     * 查找PIN码容器
     */
    private ViewGroup findPINContainer(ViewGroup parent) {
        // 尝试通过ID查找
        int containerId = parent.getResources().getIdentifier(
            "key_enter", "id", "com.android.systemui"
        );
        
        if (containerId != 0) {
            View container = parent.findViewById(containerId);
            if (container instanceof ViewGroup) {
                return (ViewGroup) container.getParent() instanceof ViewGroup 
                    ? (ViewGroup) container.getParent() 
                    : (ViewGroup) container;
            }
        }
        
        // 递归查找包含多个按钮的容器
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            
            if (child instanceof ViewGroup) {
                ViewGroup childGroup = (ViewGroup) child;
                
                // 检查是否包含10个以上按钮（数字键盘）
                int buttonCount = countDigitButtons(childGroup);
                if (buttonCount >= 10) {
                    return childGroup;
                }
                
                // 递归查找
                ViewGroup result = findPINContainer(childGroup);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 计算容器中的数字按钮数量
     */
    private int countDigitButtons(ViewGroup container) {
        int count = 0;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (isDigitButton(child)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 判断是否为数字按钮
     */
    private boolean isDigitButton(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            String text = button.getText().toString();
            return text.matches("[0-9]");
        }
        
        // 检查是否有digit属性或tag
        Object digit = view.getTag();
        if (digit instanceof Integer && (Integer) digit >= 0 && (Integer) digit <= 9) {
            return true;
        }
        
        // 检查content description
        CharSequence contentDesc = view.getContentDescription();
        if (contentDesc != null && contentDesc.toString().matches("[0-9]")) {
            return true;
        }
        
        return false;
    }
}
