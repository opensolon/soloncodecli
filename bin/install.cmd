@echo off
setlocal enabledelayedexpansion

:: =============================================
::  Solon Code Installer (Windows)
::  支持重复安装，保留已有 config.yml
:: =============================================

echo.
echo ============================================
echo    Solon Code Installer
echo ============================================
echo.

:: 设置源目录和目标目录
set "SOURCE_DIR=%~dp0"
if "%SOURCE_DIR:~-1%"=="\" set "SOURCE_DIR=%SOURCE_DIR:~0,-1%"
set "TARGET_DIR=%USERPROFILE%\.soloncode\bin"

:: [1/5] 检查并备份已有的 config.yml
echo [1/5] Checking for existing installation...
set "CONFIG_BACKUP="
set "TARGET_CONFIG=%TARGET_DIR%\config.yml"
if exist "%TARGET_CONFIG%" (
    set "CONFIG_BACKUP=%TEMP%\soloncode_config_backup_%RANDOM%.yml"
    copy "%TARGET_CONFIG%" "!CONFIG_BACKUP!" >nul 2>&1
    echo       Backing up existing config.yml
) else (
    echo       No existing config.yml found
)

:: [2/5] 创建/清空目标目录
echo.
echo [2/5] Preparing target directory...
if exist "%TARGET_DIR%" (
    :: 清空目录内容（保留目录本身）
    del /Q "%TARGET_DIR%\*" >nul 2>&1
    for /d %%d in ("%TARGET_DIR%\*") do rd "%%d" /S /Q >nul 2>&1
    echo       Cleaned existing directory
) else (
    mkdir "%TARGET_DIR%"
    echo       Created new directory
)

:: [3/5] 复制所有文件到目标目录
echo.
echo [3/5] Copying files to target directory...
for %%f in ("%SOURCE_DIR%\*") do (
    copy "%%f" "%TARGET_DIR%\" >nul 2>&1
    echo       Copied: %%~nxf
)
:: 复制子目录（如果有）
for /d %%d in ("%SOURCE_DIR%\*") do (
    xcopy "%%d" "%TARGET_DIR%\%%~nxd\" /E /I /Y >nul 2>&1
    echo       Copied directory: %%~nxd
)

:: [4/5] 解压 zip 文件（如果存在）
echo.
echo [4/5] Extracting zip files if any...
set "FOUND_ZIP=0"
for %%z in ("%TARGET_DIR%\soloncode-cli-bin-*.zip") do (
    set "FOUND_ZIP=1"
    echo       Found: %%~nxz
    
    :: 使用 PowerShell 解压
    powershell -NoProfile -Command "Expand-Archive -Path '%%z' -DestinationPath '%TARGET_DIR%' -Force" >nul 2>&1
    
    :: 处理子目录情况（解压后可能在子目录中）
    for /d %%d in ("%TARGET_DIR%\soloncode-cli-*") do (
        if exist "%%d\soloncode-cli.jar" (
            xcopy "%%d\*" "%TARGET_DIR%\" /E /Y >nul 2>&1
            rd "%%d" /S /Q >nul 2>&1
            echo       Extracted files from subdirectory
        )
    )
    echo       Extracted: %%~nxz
)

if "%FOUND_ZIP%"=="0" (
    echo       No zip files found, skipping extraction
)

:: 恢复 config.yml 备份（如果之前存在）
if not "!CONFIG_BACKUP!"=="" (
    if exist "!CONFIG_BACKUP!" (
        copy "!CONFIG_BACKUP!" "%TARGET_CONFIG%" >nul 2>&1
        del "!CONFIG_BACKUP!" >nul 2>&1
        echo.
        echo       Restored config.yml (preserved user config)
    )
)

:: 检查 jar 文件是否存在
if not exist "%TARGET_DIR%\soloncode-cli.jar" (
    echo.
    echo [Error] soloncode-cli.jar not found in %TARGET_DIR%
    pause
    exit /b 1
)
echo.
echo       Found soloncode-cli.jar

:: [5/5] 创建 soloncode 命令脚本并配置 PATH
echo.
echo [5/5] Setting up 'soloncode' command...

:: 创建启动脚本
set "LAUNCHER=%TARGET_DIR%\soloncode.cmd"
(
echo @echo off
echo setlocal
echo set "JAR_DIR=%%~dp0"
echo if "%%JAR_DIR:~-1%%"=="\" set "JAR_DIR=%%JAR_DIR:~0,-1%%"
echo set "JAR_FILE=%%JAR_DIR%%\soloncode-cli.jar"
echo if not exist "%%JAR_FILE%%" ^(
echo     echo [Error] soloncode-cli.jar not found
echo     echo Please check: %%JAR_FILE%%
echo     exit /b 1
echo ^)
echo chcp 65001 ^> nul 2^>nul
echo java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dstdin.encoding=UTF-8 -jar "%%JAR_FILE%%" %%*
) > "%LAUNCHER%"

echo       Created: %LAUNCHER%

:: 添加到 PATH（用户级别）
powershell -NoProfile -Command "$p=[Environment]::GetEnvironmentVariable('Path','User');if($p -notlike '*%TARGET_DIR%*'){$np=$p;if($p -ne ''){$np=$p+';'}[Environment]::SetEnvironmentVariable('Path',$np+'%TARGET_DIR%','User');Write-Host 'Added to PATH'}else{Write-Host 'Already in PATH'}"

:: 完成
echo.
echo ============================================
echo    Installation Complete!
echo ============================================
echo.
echo   Install location: %TARGET_DIR%
echo.
echo   Usage:
echo     1. Open a NEW command prompt or terminal
echo     2. Run: soloncode
echo.
echo   Note: Your existing config.yml has been preserved.
echo.
pause