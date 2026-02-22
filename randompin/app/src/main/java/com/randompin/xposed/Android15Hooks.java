package com.randompin.xposed;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 针对安卓15/16重写的全新Hook逻辑
 * 不再依赖外部包裹层(Bouncer)，直接劫持 NumPad 控制器底层核心布局
 */
public class Android15Hooks {
    
    private static final String TAG = "RandomPIN-A16";
    private static boolean hasRandomized = false;
    
    public static void hookKeyguardBouncer(ClassLoader classLoader) {
        // 安卓16不再通过外部 Bouncer 触发，而是直接监听底部 NumPadKey 的生成
    }
    
    public static void hookLockIconViewController(ClassLoader classLoader) {
        // 略
    }
    
    /**
     * 安卓16终极Hook方案：挂载数字键盘本身的布局完成时刻
     */
    public static void hookNumPadAnimationController(ClassLoader classLoader) {
        try {
            // 挂按键本身的形成时机，适用于锁屏密码、SIM 卡密码和 PUK 解锁区域的共用底层按键
            Class<?> numPadKeyClass = XposedHelpers.findClass(
                "com.android.keyguard.NumPadKey",
                classLoader
            );
            
            // 当任何一个数字按键解析布局完成时
            XposedHelpers.findAndHookMethod(
                numPadKeyClass,
                "onFinishInflate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View keyObj = (View) param.thisObject;
                        ViewGroup parent = (ViewGroup) keyObj.getParent();
                        
                        if (parent != null && !hasRandomized) {
                            // 为了防抖，延迟一瞬间等所有兄弟节点都加进来后再洗牌
                            parent.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!hasRandomized) {
                                        doHardRandomize(parent);
                                    }
                                }
                            });
                        }
                    }
                }
            );

            // 监听键盘区域被销毁或隐藏，重置洗牌状态以便下次亮屏再次洗牌
            Class<?> bouncerClass = XposedHelpers.findClass(
                "com.android.keyguard.KeyguardBouncer",
                classLoader
            );
            XposedHelpers.findAndHookMethod(
                bouncerClass,
                "hide",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        hasRandomized = false;
                        XposedBridge.log("[" + TAG + "] Bouncer hidden, reset state.");
                    }
                }
            );
            
            XposedBridge.log("[" + TAG + "] Hooked NumPadKey successfully for Android 16.");
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] NumPadKey hook failed: " + t.getMessage());
        }
    }
    
    /**
     * 暴力洗牌算法：剥离出来直接重构父容器
     */
    private static void doHardRandomize(ViewGroup container) {
        if (container == null) return;
        
        try {
            List<View> digitButtons = new ArrayList<>();
            List<View> otherButtons = new ArrayList<>();
            
            // 第一步：分类
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (isDigit(child)) {
                    digitButtons.add(child);
                } else {
                    otherButtons.add(child);
                }
            }
            
            // 如果数字按键不足10个（可能不是真正的键盘区域，或还没完全加载完成），退出
            if (digitButtons.size() < 10) return;
            
            // 第二步：洗牌
            Collections.shuffle(digitButtons);
            
            // 第三步：重组 (安卓原生的NumPad排序通常是 1-3, 4-6, 7-9, 然后最下方两边是返回/指纹，中间是0)
            container.removeAllViews();
            
            // 假设键盘是依靠 index 位置来渲染界面的：
            // 将打乱后的0-8放回顶部，把原来的其他按钮（如删除、确认）按其原始索引位尝试插回
            int digitIndex = 0;
            int totalOriginal = digitButtons.size() + otherButtons.size();
            
            for (View btn : digitButtons) {
                container.addView(btn);
            }
            for (View btn : otherButtons) {
                container.addView(btn);
            }
            
            hasRandomized = true;
            XposedBridge.log("[" + TAG + "] Hard Randomized 10 PIN Buttons successfully!");
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Error in doHardRandomize: " + t.getMessage());
        }
    }
    
    /**
     * 精确判断是否为数字键
     */
    private static boolean isDigit(View view) {
        // 根据类名判断
        String name = view.getClass().getName();
        if (name.contains("NumPadKey")) {
            // 通过获取该 View 上的 digit 属性
            try {
                Object digitObj = XposedHelpers.getObjectField(view, "mDigit");
                if (digitObj != null && digitObj instanceof Integer) {
                    int d = (Integer) digitObj;
                    return (d >= 0 && d <= 9);
                }
            } catch (Throwable e) {
                // 如果没有 mDigit 字段则回退其他判断
            }
            return true;
        }
        
        // Android 16 Compose 或部分定制系统可能会用特殊的 TextView
        if (view instanceof TextView) {
            String text = ((TextView) view).getText().toString();
            return text.matches("^[0-9]$");
        }
        
        return false;
    }
}
