package de.robv.android.xposed;

/**
 * 方法Hook回调类
 */
public abstract class XC_MethodHook {
    
    public XC_MethodHook() {}
    
    public XC_MethodHook(int priority) {}
    
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
    
    public static class MethodHookParam {
        public Object thisObject;
        public Member method;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        
        public Object getResult() {
            return result;
        }
        
        public void setResult(Object result) {
            this.result = result;
        }
        
        public Throwable getThrowable() {
            return throwable;
        }
        
        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }
        
        public boolean hasThrowable() {
            return throwable != null;
        }
        
        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) {
                throw throwable;
            }
            return result;
        }
    }
    
    public static class Unhook {
        public void unhook() {}
    }
}
