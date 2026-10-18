param(
    [switch]$Background,
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $projectRoot '.runtime'
$settingsFile = Join-Path $runtime 'local-settings.json'
$configFile = Join-Path $runtime 'application-local.properties'
if (-not (Test-Path -LiteralPath $settingsFile) -or -not (Test-Path -LiteralPath $configFile)) {
    throw 'Local setup is missing. See docs/local-setup.md before starting.'
}
$settings = Get-Content -Raw -LiteralPath $settingsFile | ConvertFrom-Json
$java = Join-Path $settings.javaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw 'The configured local JDK is missing.' }
$javaRelease = Get-Content -Raw -LiteralPath (Join-Path $settings.javaHome 'release')
if ($javaRelease -notmatch 'JAVA_VERSION="21\.') { throw 'This project requires JDK 21.' }

$appDirectory = Join-Path $projectRoot 'app\housekeeping-management-system'
$jar = Join-Path $appDirectory 'target\housekeeping-management-system-0.0.1-SNAPSHOT.jar'
$pidFile = Join-Path $runtime 'app.pid'
$url = 'http://localhost:' + $settings.port + '/'
if (Test-Path -LiteralPath $pidFile) {
    $savedProcessId = [int](Get-Content -LiteralPath $pidFile)
    $running = Get-CimInstance Win32_Process -Filter "ProcessId=$savedProcessId" -ErrorAction SilentlyContinue
    if ($running -and $running.CommandLine -like ('*' + $jar + '*')) {
        Write-Host "Application is already running: $url (PID $savedProcessId)"
        exit 0
    }
}

$env:JAVA_HOME = $settings.javaHome
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
if (-not $NoBuild) {
    Push-Location $appDirectory
    try {
        $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        if ($maven) { & $maven.Source -B '-DskipTests' package }
        else { & (Join-Path $appDirectory 'mvnw.cmd') -B '-DskipTests' package }
        if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
    } finally { Pop-Location }
}
if (-not (Test-Path -LiteralPath $jar)) { throw 'Application JAR is missing. Run without -NoBuild.' }

$configUri = ([System.Uri]($runtime + [System.IO.Path]::DirectorySeparatorChar)).AbsoluteUri
$arguments = @('-jar', ('"' + $jar + '"'), '--spring.profiles.active=local',
    ('"--spring.config.additional-location=' + $configUri + '"'), ('--server.port=' + $settings.port))
if ($Background) {
    $outputLog = Join-Path $appDirectory 'target\local-app.out.log'
    $errorLog = Join-Path $appDirectory 'target\local-app.err.log'
    $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $appDirectory `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput $outputLog -RedirectStandardError $errorLog
    Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ascii
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $process.Refresh()
        if ($process.HasExited) {
            Get-Content -LiteralPath $outputLog -Tail 25
            throw "Application stopped during startup. See $outputLog"
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri ('http://127.0.0.1:' + $settings.port + '/login') -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                Write-Host "Application is ready: $url (PID $($process.Id))"
                Write-Host "Log: $outputLog"
                exit 0
            }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "Application did not become ready. See $outputLog"
} else {
    Write-Host "Application: $url -- press Ctrl+C to stop."
    Push-Location $appDirectory
    try { & $java -jar $jar '--spring.profiles.active=local' "--spring.config.additional-location=$configUri" "--server.port=$($settings.port)" }
    finally { Pop-Location }
}
