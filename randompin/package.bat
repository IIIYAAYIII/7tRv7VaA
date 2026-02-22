@echo off
REM Windows快速打包脚本

set VERSION=v1.0.0
set MODULE_NAME=RandomPIN-%VERSION%
set BUILD_DIR=release

echo === RandomPIN Module Package Script ===
echo Version: %VERSION%
echo.

REM 清理
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
mkdir "%BUILD_DIR%\%MODULE_NAME%"

REM 复制文件
echo Copying files...

copy module.prop "%BUILD_DIR%\%MODULE_NAME%\"
copy uninstall.sh "%BUILD_DIR%\%MODULE_NAME%\"
copy service.sh "%BUILD_DIR%\%MODULE_NAME%\"
copy post-fs-data.sh "%BUILD_DIR%\%MODULE_NAME%\"
copy system.prop "%BUILD_DIR%\%MODULE_NAME%\"
copy xposed_init "%BUILD_DIR%\%MODULE_NAME%\"

xcopy /E /I /Y META-INF "%BUILD_DIR%\%MODULE_NAME%\META-INF"
xcopy /E /I /Y common "%BUILD_DIR%\%MODULE_NAME%\common"

mkdir "%BUILD_DIR%\%MODULE_NAME%\system\framework"
mkdir "%BUILD_DIR%\%MODULE_NAME%\zygisk"

REM 打包
echo Creating zip package...
cd "%BUILD_DIR%"
powershell Compress-Archive -Path "%MODULE_NAME%" -DestinationPath "..\%MODULE_NAME%.zip" -Force
cd ..

REM 清理
rmdir /s /q "%BUILD_DIR%"

echo.
echo === Package Complete ===
echo Created: %MODULE_NAME%.zip
echo.
echo 请使用Magisk Manager安装此zip文件
echo.
pause
