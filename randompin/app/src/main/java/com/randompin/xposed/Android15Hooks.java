package com.randompin.xposed;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 针对安卓15/16重写的全新Hook逻辑
 * 不再依赖外部包裹层(Bouncer)，直接劫持 NumPad 控制器底层核心布局
 */
public class Android15Hooks {
    
    private static final String TAG = "RandomPIN-A16";
    private static final int TAG_RANDOMIZED = 0x52414E44; // "RAND" 标记
    private static final int TAG_CONTAINER_TRACKED = 0x54524143; // "TRAC" 标记 - 追踪中的容器
    
    public static void hook(ClassLoader classLoader) {
        hookKeyguardBouncer(classLoader);
        hookLockIconViewController(classLoader);
        hookNumPadAnimationController(classLoader);
    }

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
                        
                        // 延迟获取父容器，确保父容器已经完全构建
                        keyObj.post(new Runnable() {
                            @Override
                            public void run() {
                                ViewGroup parent = (ViewGroup) keyObj.getParent();
                                if (parent == null) {
                                    // 继续向上查找
                                    return;
                                }
                                
                                // 找到PIN键盘的根容器（通常包含10个数字键）
                                ViewGroup pinContainer = findPINContainer(parent);
                                if (pinContainer == null) {
                                    XposedBridge.log("[" + TAG + "] PIN container not found yet, will retry");
                                    return;
                                }
                                
                                // 检查是否已经洗牌过
                                if (pinContainer.getTag(TAG_RANDOMIZED) != null) {
                                    return;
                                }
                                
                                // 避免重复添加监听器
                                if (pinContainer.getTag(TAG_CONTAINER_TRACKED) != null) {
                                    return;
                                }
                                
                                // 标记为追踪中
                                pinContainer.setTag(TAG_CONTAINER_TRACKED, Boolean.TRUE);
                                
                                // 使用 ViewTreeObserver 监听全局布局完成
                                final ViewGroup containerRef = pinContainer;
                                ViewTreeObserver.OnGlobalLayoutListener layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
                                    private int lastChildCount = 0;
                                    private int stableCount = 0;
                                    
                                    @Override
                                    public void onGlobalLayout() {
                                        int childCount = countDigitButtons(containerRef);
                                        
                                        // 等待子View数量稳定（连续两次检测到相同数量且>=10）
                                        if (childCount >= 10) {
                                            if (childCount == lastChildCount) {
                                                stableCount++;
                                                if (stableCount >= 2) {
                                                    // 布局稳定，执行洗牌
                                                    if (containerRef.getTag(TAG_RANDOMIZED) == null) {
                                                        doHardRandomize(containerRef);
                                                    }
                                                    // 移除监听器
                                                    containerRef.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                                    containerRef.setTag(TAG_CONTAINER_TRACKED, null);
                                                }
                                            } else {
                                                stableCount = 0;
                                            }
                                            lastChildCount = childCount;
                                        }
                                    }
                                };
                                
                                containerRef.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
                                
                                // 同时也执行一次立即检查（某些设备可能不需要等待）
                                containerRef.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (containerRef.getTag(TAG_RANDOMIZED) == null) {
                                            int digitCount = countDigitButtons(containerRef);
                                            if (digitCount >= 10) {
                                                doHardRandomize(containerRef);
                                                containerRef.setTag(TAG_CONTAINER_TRACKED, null);
                                            }
                                        }
                                    }
                                }, 300);
                            }
                        });
                    }
                }
            );

            // 监听键盘区域被销毁或隐藏，重置洗牌状态以便下次亮屏再次洗牌
            try {
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
                            XposedBridge.log("[" + TAG + "] Bouncer hidden.");
                        }
                    }
                );
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] KeyguardBouncer.hide hook failed (may not exist): " + t.getMessage());
            }
            
            // Hook KeyguardSecurityContainer 作为备选方案
            try {
                Class<?> securityContainerClass = XposedHelpers.findClass(
                    "com.android.keyguard.KeyguardSecurityContainer",
                    classLoader
                );
                XposedHelpers.findAndHookMethod(
                    securityContainerClass,
                    "onFinishInflate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            ViewGroup container = (ViewGroup) param.thisObject;
                            XposedBridge.log("[" + TAG + "] KeyguardSecurityContainer inflated");
                            
                            // 延迟搜索并处理PIN容器
                            container.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    processPINContainers(container);
                                }
                            }, 500);
                        }
                    }
                );
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] KeyguardSecurityContainer hook failed: " + t.getMessage());
            }
            
            XposedBridge.log("[" + TAG + "] Hooked NumPadKey successfully for Android 16.");
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] NumPadKey hook failed: " + t.getMessage());
        }
    }
    
    /**
     * 递归查找包含PIN键盘的容器
     */
    private static ViewGroup findPINContainer(View view) {
        if (view == null) return null;
        
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int digitCount = countDigitButtons(group);
            
            // 如果直接包含足够的数字按钮，返回此容器
            if (digitCount >= 10) {
                return group;
            }
            
            // 递归查找子View
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof ViewGroup) {
                    ViewGroup result = findPINContainer(child);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 递归处理所有PIN容器
     */
    private static void processPINContainers(ViewGroup root) {
        if (root == null) return;
        
        // 检查当前容器
        if (root.getTag(TAG_RANDOMIZED) == null) {
            int digitCount = countDigitButtons(root);
            if (digitCount >= 10) {
                doHardRandomize(root);
            }
        }
        
        // 递归处理子容器
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof ViewGroup) {
                processPINContainers((ViewGroup) child);
            }
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
                Integer digit = getDigitValue(child);
                if (digit != null && digit >= 0 && digit <= 9) {
                    digitButtons.add(child);
                    XposedBridge.log("[" + TAG + "] Found digit button: " + digit);
                } else {
                    otherButtons.add(child);
                }
            }
            
            // 如果数字按键不足10个，记录并退出
            if (digitButtons.size() < 10) {
                XposedBridge.log("[" + TAG + "] Only found " + digitButtons.size() + " digit buttons, skipping shuffle");
                return;
            }
            
            // 第二步：洗牌
            Collections.shuffle(digitButtons);
            XposedBridge.log("[" + TAG + "] Shuffled " + digitButtons.size() + " digit buttons");
            
            // 第三步：重组 - 保持原有索引位置的含义
            // 记录原始子View的位置信息
            List<View> originalChildren = new ArrayList<>();
            for (int i = 0; i < container.getChildCount(); i++) {
                originalChildren.add(container.getChildAt(i));
            }
            
            container.removeAllViews();
            
            // 按原始顺序重新添加，但用洗牌后的数字按钮替换原来的数字按钮
            int digitIndex = 0;
            for (View original : originalChildren) {
                Integer digit = getDigitValue(original);
                if (digit != null && digit >= 0 && digit <= 9 && digitIndex < digitButtons.size()) {
                    // 这是数字按钮位置，用洗牌后的按钮替换
                    container.addView(digitButtons.get(digitIndex));
                    digitIndex++;
                } else {
                    // 非数字按钮，保持原样
                    container.addView(original);
                }
            }
            
            container.setTag(TAG_RANDOMIZED, Boolean.TRUE);
            container.setTag(TAG_CONTAINER_TRACKED, null);
            XposedBridge.log("[" + TAG + "] Hard Randomized " + digitButtons.size() + " PIN Buttons successfully!");
            
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Error in doHardRandomize: " + t.getMessage());
            t.printStackTrace();
        }
    }
    
    /**
     * 计算容器中的数字按钮数量
     */
    private static int countDigitButtons(ViewGroup container) {
        int count = 0;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            Integer digit = getDigitValue(child);
            if (digit != null && digit >= 0 && digit <= 9) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 获取View的数字值（增强版，兼容多种情况）
     */
    private static Integer getDigitValue(View view) {
        if (view == null) return null;
        
        // 方法1: 尝试多种可能的字段名
        String[] possibleFieldNames = {"mDigit", "digit", "mNumber", "number", "mDigitValue", "digitValue"};
        for (String fieldName : possibleFieldNames) {
            try {
                Object digitObj = XposedHelpers.getObjectField(view, fieldName);
                if (digitObj instanceof Integer) {
                    return (Integer) digitObj;
                }
            } catch (Throwable ignored) {}
        }
        
        // 方法2: 通过反射遍历所有字段查找int类型的digit相关字段
        try {
            Field[] fields = view.getClass().getDeclaredFields();
            for (Field field : fields) {
                String name = field.getName().toLowerCase();
                if ((name.contains("digit") || name.contains("number")) && field.getType() == int.class) {
                    field.setAccessible(true);
                    int value = field.getInt(view);
                    if (value >= 0 && value <= 9) {
                        return value;
                    }
                }
            }
        } catch (Throwable ignored) {}
        
        // 方法3: 检查类名是否为NumPadKey
        String className = view.getClass().getName();
        if (className.contains("NumPadKey")) {
            // 这是NumPadKey，但找不到digit字段，尝试从TextView获取
            try {
                if (view instanceof TextView) {
                    String text = ((TextView) view).getText().toString().trim();
                    if (text.matches("^[0-9]$")) {
                        return Integer.parseInt(text);
                    }
                }
                
                // NumPadKey通常包含一个TextView子View
                if (view instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) view;
                    for (int i = 0; i < vg.getChildCount(); i++) {
                        View child = vg.getChildAt(i);
                        if (child instanceof TextView) {
                            String text = ((TextView) child).getText().toString().trim();
                            if (text.matches("^[0-9]$")) {
                                return Integer.parseInt(text);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
            
            // 即使找不到具体digit值，这也应该是一个数字键
            // 返回一个标记值让调用者知道这是数字键
            return 0; // 假设至少是个数字键
        }
        
        // 方法4: 检查TextView内容
        if (view instanceof TextView) {
            try {
                String text = ((TextView) view).getText().toString().trim();
                if (text.matches("^[0-9]$")) {
                    return Integer.parseInt(text);
                }
            } catch (Throwable ignored) {}
        }
        
        // 方法5: 检查content description
        try {
            CharSequence contentDesc = view.getContentDescription();
            if (contentDesc != null) {
                String desc = contentDesc.toString().trim();
                if (desc.matches("^[0-9]$")) {
                    return Integer.parseInt(desc);
                }
            }
        } catch (Throwable ignored) {}
        
        return null;
    }
    
    /**
     * 精确判断是否为数字键（兼容方法）
     */
    private static boolean isDigit(View view) {
        Integer digit = getDigitValue(view);
        return digit != null;
    }
}
