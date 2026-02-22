# Zygisk模块说明
#
# Zygisk是Magisk的进程注入框架，可以在不依赖Xposed的情况下Hook系统进程
#
# 使用方法：
# 1. 安装Magisk并启用Zygisk功能
# 2. 编译main.cpp为so库：
#    - 需要Android NDK
#    - 编译目标：arm64-v8a, armeabi-v7a
# 3. 将编译好的so文件放入zygisk目录，命名为：
#    - zygisk/arm64-v8a.so
#    - zygisk/armeabi-v7a.so
#
# Zygisk版本的优点：
# - 不需要额外安装Xposed/LSPosed框架
# - 更轻量级
# - 更好的兼容性
#
# 注意：当前提供的是C++源码，需要使用NDK编译
