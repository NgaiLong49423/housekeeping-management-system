$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $projectRoot '.runtime\app.pid'
if (-not (Test-Path -LiteralPath $pidFile)) { Write-Host 'No background application PID is recorded.'; exit 0 }
$savedProcessId = [int](Get-Content -LiteralPath $pidFile)
$jar = Join-Path $projectRoot 'app\housekeeping-management-system\target\housekeeping-management-system-0.0.1-SNAPSHOT.jar'
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$savedProcessId" -ErrorAction SilentlyContinue
if ($process) {
    if ($process.CommandLine -notlike ('*' + $jar + '*')) { throw 'PID belongs to another program; refusing to stop it.' }
    Stop-Process -Id $savedProcessId
    Write-Host "Stopped this project application (PID $savedProcessId)."
} else { Write-Host 'The application has already stopped.' }
Remove-Item -LiteralPath $pidFile
