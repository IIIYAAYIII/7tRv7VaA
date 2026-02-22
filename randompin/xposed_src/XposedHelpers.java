package de.robv.android.xposed;

/**
 * Xposed辅助类
 */
public class XposedHelpers {
    
    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new XposedBridge.ClassNotFoundError(e);
        }
    }
    
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, 
            Object... parameterTypesAndCallback) {
        // 实际实现需要完整Xposed API
        return null;
    }
    
    public static Object callMethod(Object obj, String methodName, Object... args) {
        return null;
    }
    
    public static Object getObjectField(Object obj, String fieldName) {
        return null;
    }
    
    public static void setObjectField(Object obj, String fieldName, Object value) {
    }
    
    public static int getIntField(Object obj, String fieldName) {
        return 0;
    }
    
    public static void setIntField(Object obj, String fieldName, int value) {
    }
}
