$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $projectRoot '.runtime'
$settings = Get-Content -Raw -LiteralPath (Join-Path $runtime 'local-settings.json') | ConvertFrom-Json
$java = Join-Path $settings.javaHome 'bin\java.exe'
$jar = Join-Path $projectRoot 'app\housekeeping-management-system\target\housekeeping-management-system-0.0.1-SNAPSHOT.jar'
$libraries = Join-Path $runtime 'seed-libraries'
New-Item -ItemType Directory -Path $libraries -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^BOOT-INF/lib/(spring-security-crypto-[^/]+|commons-logging-[^/]+|mssql-jdbc-[^/]+)\.jar$') {
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $libraries $entry.Name), $true)
        }
    }
} finally { $archive.Dispose() }
$classpath = (Get-ChildItem -LiteralPath $libraries -Filter '*.jar' | ForEach-Object { $_.FullName }) -join ';'
& $java --class-path $classpath (Join-Path $PSScriptRoot 'SeedLocalData.java') $runtime
if ($LASTEXITCODE -ne 0) { throw 'Local demo setup failed. Existing data was not reset.' }
