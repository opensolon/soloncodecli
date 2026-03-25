@echo off
setlocal enabledelayedexpansion

:: =============================================
::  Solon Code Installer (Windows)
::  Run as Administrator required
:: =============================================

echo.
echo ============================================
echo    Solon Code Installer
echo ============================================
echo.

:: Check admin privileges
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [Error] Please run as Administrator!
    echo Right-click -^> Run as administrator
    pause
    exit /b 1
)

:: Get install directory
set "INSTALL_DIR=%~dp0"
if "%INSTALL_DIR:~-1%"=="\" set "INSTALL_DIR=%INSTALL_DIR:~0,-1%"

:: Check jar file
echo [1/4] Checking installation files...
if not exist "%INSTALL_DIR%\soloncode-cli.jar" (
    echo [Error] soloncode-cli.jar not found in %INSTALL_DIR%
    pause
    exit /b 1
)
echo       Found soloncode-cli.jar

:: Set SOLONCODE_HOME env var
echo.
echo [2/4] Setting environment variables...
setx SOLONCODE_HOME "%INSTALL_DIR%" /M >nul 2>&1
if %errorLevel% equ 0 (
    echo       SOLONCODE_HOME = %INSTALL_DIR%
) else (
    echo [Error] Failed to set SOLONCODE_HOME
    pause
    exit /b 1
)

:: Add to system PATH (use PowerShell to avoid setx 1024-char limit)
echo.
echo [3/4] Adding to system PATH...

powershell -NoProfile -Command "$p=[Environment]::GetEnvironmentVariable('Path','Machine');if($p -notlike '*%INSTALL_DIR%*'){[Environment]::SetEnvironmentVariable('Path',$p+';%INSTALL_DIR%','Machine');exit 0}else{exit 1}" >nul 2>&1
if %errorLevel% equ 0 (
    echo       Added to PATH
) else (
    echo       PATH already contains %INSTALL_DIR%
)

:: Setup global config
echo.
echo [4/5] Setting up global config...
set "GLOBAL_DIR=%USERPROFILE%\.soloncode"
set "GLOBAL_CONFIG=%GLOBAL_DIR%\config.yml"

if not exist "%GLOBAL_DIR%" (
    mkdir "%GLOBAL_DIR%"
)

if not exist "%GLOBAL_CONFIG%" (
    if exist "%INSTALL_DIR%\.soloncode\config.yml" (
        copy "%INSTALL_DIR%\.soloncode\config.yml" "%GLOBAL_CONFIG%" >nul
        echo       Copied config template to %GLOBAL_CONFIG%
    ) else if exist "%INSTALL_DIR%\config.yml" (
        copy "%INSTALL_DIR%\config.yml" "%GLOBAL_CONFIG%" >nul
        echo       Copied config template to %GLOBAL_CONFIG%
    ) else (
        echo       [Warning] No config template found, will use jar embedded config
    )
) else (
    echo       Global config already exists: %GLOBAL_CONFIG%
)

:: Create launcher script
echo.
echo [5/5] Creating launch script...
set "LAUNCHER=%INSTALL_DIR%\soloncode.cmd"

(
echo @echo off
echo setlocal
echo set "SOLONCODE_JAR=%%SOLONCODE_HOME%%\soloncode-cli.jar"
echo if not exist "%%SOLONCODE_JAR%%" ^(
echo     echo [Error] soloncode-cli.jar not found
echo     echo Please check SOLONCODE_HOME: %%SOLONCODE_HOME%%
echo     exit /b 1
echo ^)
echo chcp 65001 ^> nul 2^>nul
echo java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dstdin.encoding=UTF-8 -jar "%%SOLONCODE_JAR%%" %%*
) > "%LAUNCHER%"

echo       Created: %LAUNCHER%

:: Done
echo.
echo ============================================
echo    Installation Complete!
echo ============================================
echo.
echo   SOLONCODE_HOME = %INSTALL_DIR%
echo.
echo   Global config: %%USERPROFILE%%\.soloncode\config.yml
echo.
echo   Usage:
echo     1. Close this window
echo     2. Open a new command prompt
echo     3. Run: soloncode
echo.
echo   Uninstall:
echo     Run: %INSTALL_DIR%\uninstall.cmd
echo.
pause
