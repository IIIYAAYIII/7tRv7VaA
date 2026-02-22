package de.robv.android.xposed;

import java.lang.reflect.Member;

/**
 * Xposed桥接类
 */
public class XposedBridge {
    
    public static final String TAG = "Xposed";
    
    public static void log(String text) {
        android.util.Log.i(TAG, text);
    }
    
    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        return null;
    }
    
    public static void unhookMethod(Member hookMethod, XC_MethodHook callback) {
    }
    
    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args)
            throws NullPointerException, IllegalAccessException, IllegalArgumentException {
        return null;
    }
    
    public static void hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
    }
    
    public static void hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
    }
    
    public static class ClassNotFoundError extends Error {
        public ClassNotFoundError(Throwable cause) {
            super(cause);
        }
    }
}
