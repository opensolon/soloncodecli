#
# SolonCode CLI Installer for Windows
# Usage: irm https://solon.noear.org/soloncode/setup.ps1 | iex
#

$ErrorActionPreference = "Stop"

$VERSION = "v2026.4.1"
$PACKAGE_URL = "https://gitee.com/opensolon/soloncode/releases/download/$VERSION/soloncode-cli-bin-$VERSION.tar.gz"
$TEMP_DIR = Join-Path $env:TEMP "soloncode-install"

function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] " -ForegroundColor Green -NoNewline
    Write-Host $Message
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] " -ForegroundColor Red -NoNewline
    Write-Host $Message
}

# Cleanup temp directory
if (Test-Path "$TEMP_DIR") {
    Remove-Item -Recurse -Force "$TEMP_DIR"
}
New-Item -ItemType Directory -Path "$TEMP_DIR" | Out-Null

try {
    Write-Info "Downloading SolonCode CLI $VERSION..."

    $packageFile = Join-Path $TEMP_DIR "package.tar.gz"
    Invoke-WebRequest -Uri $PACKAGE_URL -OutFile "$packageFile" -UseBasicParsing

    Write-Info "Extracting package..."

    # Extract tar.gz using built-in tar (Windows 10+)
    # Use quotes to handle paths with spaces
    tar -xzf "$packageFile" -C "$TEMP_DIR"

    # Find install.cmd
    $installScript = Get-ChildItem -Path "$TEMP_DIR" -Filter "install.cmd" -Recurse | Select-Object -First 1

    if (-not $installScript) {
        Write-Error "install.cmd not found in package"
        exit 1
    }

    Write-Info "Running installer..."

    # Run installer using .NET Process class for reliable path handling
    $installPath = $installScript.FullName
    $installDir = Split-Path $installPath -Parent
    
    Write-Host "Install path: $installPath" -ForegroundColor Gray
    
    # Use .NET Process class to execute cmd.exe with proper quoting
    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = "cmd.exe"
    $processInfo.Arguments = "/c `"$installPath`""
    $processInfo.WorkingDirectory = $installDir
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $false
    
    $process = [System.Diagnostics.Process]::Start($processInfo)
    $process.WaitForExit()
    $exitCode = $process.ExitCode
    
    if ($exitCode -ne 0) {
        Write-Error "Installer failed with exit code: $exitCode"
        Write-Host ""
        Write-Host "Press any key to exit..." -ForegroundColor Yellow
        $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
        exit 1
    }

    Write-Info "Installation completed successfully!"
    Write-Host ""
    Write-Host "Press any key to exit..." -ForegroundColor Yellow
    $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')

} catch {
    Write-Error $_.Exception.Message
    Write-Host ""
    Write-Host "Press any key to exit..." -ForegroundColor Yellow
    $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
    exit 1
}