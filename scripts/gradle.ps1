param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArguments = @('help')
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$fileSystem = New-Object -ComObject Scripting.FileSystemObject

# Keep Java argument files and Android tools compatible with non-ASCII user paths.
function Get-ShortDirectory([string]$Path) {
    return $fileSystem.GetFolder($Path).ShortPath
}

$jdkCandidates = @(
    $env:JAVA_HOME
    (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr')
)
$jdkCache = Join-Path $env:USERPROFILE '.gradle\jdks'
if (Test-Path -LiteralPath $jdkCache) {
    $jdkCandidates += Get-ChildItem -LiteralPath $jdkCache -Directory | Select-Object -ExpandProperty FullName
}
$jdk = $jdkCandidates | Where-Object {
    $_ -and (Test-Path -LiteralPath (Join-Path $_ 'release')) -and
    (Select-String -LiteralPath (Join-Path $_ 'release') -Pattern '^JAVA_VERSION="21[.\"]' -Quiet)
} | Select-Object -First 1
if (-not $jdk) { throw 'JDK 21 не найден. Укажите путь к JDK 21 в JAVA_HOME.' }

$cacheDirectory = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$temporaryDirectory = Join-Path $projectDirectory '.tmp'
New-Item -ItemType Directory -Path $cacheDirectory, $temporaryDirectory -Force | Out-Null
$sdkDirectory = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }

$previousEnvironment = @{}
foreach ($name in @('JAVA_HOME', 'JAVA_TOOL_OPTIONS', 'ANDROID_HOME', 'TEMP', 'TMP')) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    $env:JAVA_HOME = Get-ShortDirectory $jdk
    $env:TEMP = Get-ShortDirectory $temporaryDirectory
    $env:TMP = $env:TEMP
    $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' "-Djdk.net.unixdomain.tmpdir=' + $env:TEMP + '"').Trim()
    if (Test-Path -LiteralPath $sdkDirectory) { $env:ANDROID_HOME = Get-ShortDirectory $sdkDirectory }
    # Gradle 8 writes worker argument files in the daemon charset; the Windows
    # Java launcher reads them in the native charset, so keep those in agreement.
    & (Join-Path $projectDirectory 'gradlew.bat') -p (Get-ShortDirectory $projectDirectory) -g (Get-ShortDirectory $cacheDirectory) '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=COMPAT' @GradleArguments
    $result = $LASTEXITCODE
} finally {
    foreach ($name in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
}
exit $result
