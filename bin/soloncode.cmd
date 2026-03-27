@echo off
setlocal
set "SOLONCODE_JAR=%SOLONCODE_HOME%\soloncode-cli.jar"
if not exist "%SOLONCODE_JAR%" (
    echo [Error] soloncode-cli.jar not found
    echo Please check SOLONCODE_HOME: %SOLONCODE_HOME%
    exit /b 1
)
chcp 65001 > nul 2>nul
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dstdin.encoding=UTF-8 -jar "%SOLONCODE_JAR%" %*
