param(
    [string]$ExportDirectory = 'data\exports\akniga'
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$projectAbsolute = [IO.Path]::GetFullPath($projectDirectory)
$exportAbsolute = [IO.Path]::GetFullPath((Join-Path $projectDirectory $ExportDirectory))
if (-not $exportAbsolute.StartsWith($projectAbsolute + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'ExportDirectory must be inside the project.'
}
$distribution = Join-Path $projectDirectory 'server\build\install\server\lib'
if (-not (Test-Path -LiteralPath (Join-Path $distribution 'server-0.1.0.jar'))) {
    throw 'Build the exporter first: .\scripts\gradle.ps1 :server:installDist'
}
$jdkCandidates = @($env:JAVA_HOME, (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr'))
$jdkCache = Join-Path $env:USERPROFILE '.gradle\jdks'
if (Test-Path -LiteralPath $jdkCache) {
    $jdkCandidates += Get-ChildItem -LiteralPath $jdkCache -Directory | Select-Object -ExpandProperty FullName
}
$jdk = $jdkCandidates | Where-Object {
    $_ -and (Test-Path -LiteralPath (Join-Path $_ 'release')) -and
    (Select-String -LiteralPath (Join-Path $_ 'release') -Pattern '^JAVA_VERSION="21[.\"]' -Quiet)
} | Select-Object -First 1
if (-not $jdk) { throw 'JDK 21 not found. Set JAVA_HOME.' }

New-Item -ItemType Directory -Path $exportAbsolute -Force | Out-Null
$fileSystem = New-Object -ComObject Scripting.FileSystemObject
$java = Join-Path ($fileSystem.GetFolder($jdk).ShortPath) 'bin\java.exe'
$classPath = Join-Path ($fileSystem.GetFolder($distribution).ShortPath) '*'
$exportShort = $fileSystem.GetFolder($exportAbsolute).ShortPath
$temporaryDirectory = Join-Path $projectDirectory '.tmp'
New-Item -ItemType Directory -Path $temporaryDirectory -Force | Out-Null
$temporaryShort = $fileSystem.GetFolder($temporaryDirectory).ShortPath
$checkpoint = Join-Path $exportAbsolute 'progress.json'
if (Test-Path -LiteralPath $checkpoint) {
    $state = Get-Content -LiteralPath $checkpoint -Raw | ConvertFrom-Json
    $existingProcess = Get-Process -Id $state.pid -ErrorAction SilentlyContinue
    if ($existingProcess -and $state.status -eq 'RUNNING') { throw "Export is already running: PID $($state.pid)" }
    if ($state.status -eq 'COMPLETED') { throw 'This export is already complete. Choose a new ExportDirectory for a new run.' }
}
$logSuffix = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
$process = Start-Process -FilePath $java -ArgumentList @('-Dfile.encoding=UTF-8', "-Djdk.net.unixdomain.tmpdir=$temporaryShort", '-cp', $classPath,
    'com.onebook.server.infrastructure.providers.akniga.AknigaExportMainKt', $exportShort) `
    -WorkingDirectory $projectDirectory -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $exportAbsolute "run-$logSuffix.log") `
    -RedirectStandardError (Join-Path $exportAbsolute "run-$logSuffix.err.log") -PassThru
[PSCustomObject]@{ ProcessId = $process.Id; Directory = $exportAbsolute; ProgressFile = $checkpoint }
