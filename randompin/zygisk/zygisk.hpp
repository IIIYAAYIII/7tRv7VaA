// Zygisk API头文件
// 来源: https://github.com/topjohnwu/zygisk-module-sample

#pragma once

#include <jni.h>

namespace zygisk {

class Api {
public:
    virtual void hookJniNativeMethods(const char *className, JNINativeMethod *methods, int numMethods) = 0;
    virtual void pltHookRegister(const char *libName, const char *funcName, void *newFunc, void **oldFunc) = 0;
    virtual void pltHookExclude(const char *libName, const char *funcName) = 0;
    virtual bool pltHookCommit() = 0;
    virtual int connectCompanion(const char *path) = 0;
    virtual size_t getModuleDir(const char *path) = 0;
    virtual int getModuleDirFd(int fd) = 0;
};

class ModuleBase {
public:
    virtual void onLoad(Api *api, JNIEnv *env) {}
    virtual void preAppSpecialize(AppSpecializeArgs *args) {}
    virtual void postAppSpecialize(const AppSpecializeArgs *args) {}
    virtual void preServerSpecialize(ServerSpecializeArgs *args) {}
    virtual void postServerSpecialize(const ServerSpecializeArgs *args) {}

    struct AppSpecializeArgs {
        int32_t &uid;
        int32_t &gid;
        int32_t *const gids;
        size_t gids_count;
        int32_t &runtime_flags;
        int64_t &permitted_capabilities;
        int64_t &effective_capabilities;
        jstring *const app_data_dir;
    };

    struct ServerSpecializeArgs {
        int32_t &uid;
        int32_t &gid;
        int32_t *const gids;
        size_t gids_count;
        int32_t &runtime_flags;
        int64_t &permitted_capabilities;
        int64_t &effective_capabilities;
    };
};

} // namespace zygisk

#define REGISTER_ZYGISK_MODULE(name) \
    void *zygisk_module_init(zygisk::Api *api, JNIEnv *env) { \
        auto mod = new name(); \
        mod->onLoad(api, env); \
        return mod; \
    }
