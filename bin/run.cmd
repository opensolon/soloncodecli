@echo off
chcp 65001 > nul

:: Solon Code - Local run script (compatibility mode)
:: For global install, run install.cmd then use: soloncode

if exist "%~dp0\soloncode-cli.jar" (
    java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dstdin.encoding=UTF-8 -jar "%~dp0\soloncode-cli.jar" %*
) else (
    echo [Error] soloncode-cli.jar not found.
    echo Please run install.cmd first for global installation.
)

if not defined SOLONCODE_HOME pause
