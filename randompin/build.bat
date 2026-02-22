@echo off
REM Windows编译脚本
REM 需要Android SDK和Java JDK

setlocal enabledelayedexpansion

echo === RandomPIN Module Build Script ===
echo.

REM 配置
if "%ANDROID_SDK%"=="" (
    set "ANDROID_SDK=%LOCALAPPDATA%\Android\Sdk"
    if not exist "!ANDROID_SDK!" (
        set "ANDROID_SDK=C:\Android\Sdk"
    )
)

set "BUILD_DIR=build"
set "SRC_DIR=xposed_src"
set "DEX_FILE=system\framework\randompin.dex"

REM 检查javac
where javac >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: javac not found. Please install JDK and add to PATH.
    exit /b 1
)

REM 创建目录
if not exist "%BUILD_DIR%\classes" mkdir "%BUILD_DIR%\classes"
if not exist "system\framework" mkdir "system\framework"

REM 查找android.jar
set "ANDROID_JAR="
for %%v in (35 34 33 32 31 30 29) do (
    if exist "%ANDROID_SDK%\platforms\android-%%v\android.jar" (
        set "ANDROID_JAR=%ANDROID_SDK%\platforms\android-%%v\android.jar"
        echo Found Android SDK platform %%v
        goto :found_android
    )
)
:found_android

REM 编译Java源码
echo Compiling Java sources...
if defined ANDROID_JAR (
    javac -source 1.8 -target 1.8 -bootclasspath "%ANDROID_JAR%" -d "%BUILD_DIR%\classes" "%SRC_DIR%\*.java"
) else (
    echo Warning: android.jar not found, compiling without Android SDK
    javac -source 1.8 -target 1.8 -d "%BUILD_DIR%\classes" "%SRC_DIR%\*.java"
)

if %ERRORLEVEL% neq 0 (
    echo Error: Compilation failed.
    exit /b 1
)

echo Compilation successful.
echo.
echo Note: You need to convert class files to DEX manually using d8 or dx tool.
echo Example: d8 --output . build\classes\*.class
echo.
echo After creating the DEX file, package the module manually or use WSL to run build.sh

pause
