@echo off
setlocal enabledelayedexpansion

:: =============================================
::  Solon Code Uninstaller (Windows)
::  Run as Administrator required
:: =============================================

echo.
echo ============================================
echo    Solon Code Uninstaller
echo ============================================
echo.

:: Check admin privileges
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [Error] Please run as Administrator!
    pause
    exit /b 1
)

:: Get install directory
set "INSTALL_DIR=%~dp0"
if "%INSTALL_DIR:~-1%"=="\" set "INSTALL_DIR=%INSTALL_DIR:~0,-1%"

:: Confirm uninstall
set /p CONFIRM="Uninstall Solon Code from: %INSTALL_DIR% ? (Y/N): "
if /i not "%CONFIRM%"=="Y" (
    echo Cancelled.
    pause
    exit /b 0
)

:: Remove from PATH (use PowerShell to avoid setx 1024-char limit)
echo.
echo [1/2] Removing from system PATH...

powershell -NoProfile -Command "$p=[Environment]::GetEnvironmentVariable('Path','Machine');$p=$p -replace '[;]*%INSTALL_DIR%[;]*','';$p=$p.TrimEnd(';');[Environment]::SetEnvironmentVariable('Path',$p,'Machine')" >nul 2>&1
echo       Removed from PATH

:: Delete SOLONCODE_HOME
echo.
echo [2/2] Removing environment variables...
reg delete "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v SOLONCODE_HOME /f >nul 2>&1
echo       Removed SOLONCODE_HOME

:: Delete launcher script
if exist "%INSTALL_DIR%\soloncode.cmd" (
    del "%INSTALL_DIR%\soloncode.cmd"
    echo       Removed soloncode.cmd
)

:: Done
echo.
echo ============================================
echo    Uninstall Complete!
echo ============================================
echo.
echo   Note: Config and session data preserved at:
echo         %%USERPROFILE%%\.soloncode\
echo   To fully remove, manually delete that directory.
echo.
pause
