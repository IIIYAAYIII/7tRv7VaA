package de.robv.android.xposed;

/**
 * Xposed API 接口定义
 * 编译时需要
 */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
