# BlockNum (屏蔽号码导入导出)

**中英双语说明 / Bilingual Documentation**

## 简介 (Introduction)
【中文】
BlockNum 是一个用于 Android 设备（支持 Android 8 至 Android 16，兼容 ARM32 和 ARM64）的屏蔽电话号码导入与导出工具。
本程序提供了干净的中英文可选界面（默认中文），并且能够通过多种方式智能读取和修改设备底层屏蔽记录。项目的自动编译（APK 产物）可通过 GitHub Actions 工作流完成。

【English】
BlockNum is an Android application (supports Android 8 to 16, ARM32/ARM64 compatible) designed to export and import blocked phone numbers. 
It features a clean dual-language interface (Chinese default, switchable to English) and uses multiple fallback methods to intelligently read and modify the device's underlying blocklist. Automated APK building is handled via GitHub Actions workflows.

## 核心功能 (Core Features)
【中文】
1. **多模式读取与写入 (Multi-mode Access)**:
   - **标准 API 模式 (Standard API)**: 引导应用成为系统的“默认拨号器”，安全、合法地调用 Android 官方 `BlockedNumberContract` 接口进行号码拦截表的读写。
   - **Root 模式 (Root Fallback)**: 当无法获取标准权限或处于非主用户空间时，若设备已 Root，将自动回退使用 `su` 和 `sqlite3`（或底层 DB 文件拷贝回写）直连底层系统数据库。
   - **不可用模式**: 两者都无权限时，安全拦截操作防止崩溃。
2. **号码导出 (Export)**:
   - 通过 Android SAF (Storage Access Framework)，将手机中的黑名单以 `.csv` 格式安全无残留地导出至用户自行选择的本地目录。
3. **号码导入 (Import)**:
   - 支持从本地 `.csv` 或 `.txt` 文件批量导入拦截号码。
   - 导入时可选“**合并**”（跳过已存在的号码）或“**替换**”（一键清空原有黑名单后载入新数据）操作。
4. **多语言与版本适配 (Language & Compatibility)**:
   - 一键无缝切换中英双语。
   - 包含底层探测逻辑，可正确识别并优雅降级适配不同系统版本（Android 8 Oreo 到 Android 16 Baklava）。

【English】
1. **Multi-mode Access**:
   - **Standard API**: Prompts to become the "Default Dialer" to legally and safely read/write via the official Android `BlockedNumberContract` API.
   - **Root Fallback**: If permissions fail or the app is not running as the primary user, it attempts to use `su` and `sqlite3` (or direct DB file manipulation) on rooted devices to edit the system database.
   - **Unavailable**: Guarantees safety when no valid permissions exist.
2. **Export Numbers**:
   - Securely exports the blocklist to a user-chosen `.csv` file via SAF (Storage Access Framework).
3. **Import Numbers**:
   - Batch import blocked numbers from `.csv` or `.txt` files.
   - Provides options to either **Merge** (skip duplicates) or **Replace** (clear current list and import new data completely).
4. **Language & Compatibility**:
   - Seamless one-tap switch between Chinese and English.
   - Intelligent version detection and backport support from Android 8 (Oreo) up to Android 16 (Baklava).

## 编译与构建 (Build & CI/CD)
【中文】
项目使用标准的 Gradle 构建（构建脚本为 `build.gradle` 及 `settings.gradle`）。如果您需要自动生成 APK 安装包，可以触发并操作仓库根目录下 `.github/workflows` 中的自动编译 Action。该自动化工作流将完成程序的编译和打包。

【English】
The local development uses standard Gradle build scripts (`build.gradle`, `settings.gradle`). If you need automated release APKs, please trigger the GitHub Actions workflows located in `.github/workflows` at the repository root. The automated CI/CD pipeline manages building and packaging the target application.
