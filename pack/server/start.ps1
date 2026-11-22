# Random Item Skyblock dedicated server starter for Windows (PowerShell 5.1+).
# Needs nothing else: it downloads and verifies NeoForge, the server mods and, when
# Java 21 is not installed, a portable Eclipse Temurin 21 runtime into .\runtime.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-Location -LiteralPath $PSScriptRoot
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$NeoForge = '21.1.265'
$JreUrl = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jre_x64_windows_hotspot_21.0.13.1_2.zip'
$JreSha256 = 'e15ee1e54a5a3b38027a09cedfa87bc3348d7dd3ce50866dd3d9671d7fffc6c1'
$UserAgent = 'zzythe/random-item-skyblock-server/0.6.1'

function Get-Verified([string]$Url, [string]$Target, [string]$Algorithm, [string]$Expected) {
    if (Test-Path -LiteralPath $Target) {
        if ((Get-FileHash -LiteralPath $Target -Algorithm $Algorithm).Hash -eq $Expected.ToUpper()) { return }
        throw "$Target is different from the version this pack expects. Move it out of the way and start again."
    }
    $dir = Split-Path -Parent $Target
    if ($dir -and !(Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    $part = "$Target.part"
    Write-Host "  downloading $(Split-Path -Leaf $Target)"
    $client = New-Object System.Net.WebClient
    $client.Headers.Add('User-Agent', $UserAgent)
    try { $client.DownloadFile($Url, (Join-Path $PWD $part)) } finally { $client.Dispose() }
    if ((Get-FileHash -LiteralPath $part -Algorithm $Algorithm).Hash -ne $Expected.ToUpper()) {
        Remove-Item -LiteralPath $part -Force
        throw "Download check failed for $(Split-Path -Leaf $Target). Try again later."
    }
    Move-Item -LiteralPath $part -Destination $Target
}

function Test-Java21([string]$Path) {
    if (!$Path -or !(Test-Path -LiteralPath $Path)) { return $false }
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $Path; $info.Arguments = '-version'
    $info.RedirectStandardError = $true; $info.RedirectStandardOutput = $true; $info.UseShellExecute = $false
    try {
        $p = [System.Diagnostics.Process]::Start($info)
        $text = $p.StandardError.ReadToEnd() + $p.StandardOutput.ReadToEnd(); $p.WaitForExit()
        return ($text -match 'version "21[."]') -and ($text -match '64-Bit')
    } catch { return $false }
}

function Find-Java {
    $local = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'runtime') -Filter java.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    $candidates = @()
    if ($local) { $candidates += $local.FullName }
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }
    foreach ($c in $candidates) { if (Test-Java21 $c) { return $c } }
    if (!([Environment]::Is64BitOperatingSystem)) { throw 'This server needs 64-bit Windows.' }
    Write-Host 'Java 21 was not found. Getting a portable Eclipse Temurin 21 runtime (about 50 MB) into the runtime folder.'
    $zip = Join-Path $PSScriptRoot 'runtime\temurin-21-jre.zip'
    Get-Verified $JreUrl 'runtime\temurin-21-jre.zip' 'SHA256' $JreSha256
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $PSScriptRoot 'runtime') -Force
    Remove-Item -LiteralPath $zip -Force
    $java = (Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'runtime') -Filter java.exe -Recurse | Select-Object -First 1).FullName
    if (!(Test-Java21 $java)) { throw 'The downloaded Java runtime did not start.' }
    return $java
}

function Confirm-Eula {
    if ((Test-Path eula.txt) -and (Select-String -Path eula.txt -Pattern '^\s*eula\s*=\s*true\s*$' -Quiet)) { return $true }
    Write-Host ''
    Write-Host 'Minecraft servers require you to accept the Minecraft End User License Agreement:'
    Write-Host '  https://aka.ms/MinecraftEULA'
    Write-Host ''
    Write-Host -NoNewline 'Have you read it, and do you accept it? Type yes to accept: '
    $answer = [Console]::In.ReadLine()
    if ($answer -and $answer.Trim().ToLower() -in @('yes', 'y')) {
        Set-Content -Path eula.txt -Encoding ASCII -Value @("# Accepted by the server owner at the start-up prompt on $(Get-Date -Format s)", 'eula=true')
        return $true
    }
    Write-Host 'The EULA was not accepted, so the server will not start.'
    return $false
}

try {
    Write-Host 'Random Item Skyblock server'
    if (!(Confirm-Eula)) { exit 2 }
    $java = Find-Java
    Write-Host "Using Java: $java"
    Write-Host 'Checking server files...'
    $entries = Get-Content -LiteralPath 'server-files.tsv' | Where-Object { $_ -and !$_.StartsWith('#') }
    foreach ($line in $entries) {
        $kind, $name, $url, $sha512 = $line.Split("`t")
        if ($name -match '[\\/]|\.\.') { throw "Invalid file name in server-files.tsv: $name" }
        if ($kind -eq 'mod') { Get-Verified $url "mods\$name" 'SHA512' $sha512 }
        elseif ($kind -eq 'installer') { $installer = @{ Name = $name; Url = $url; Sha = $sha512 } }
    }
    $argsFile = "libraries\net\neoforged\neoforge\$NeoForge\win_args.txt"
    if (!(Test-Path -LiteralPath $argsFile)) {
        Write-Host "Installing NeoForge $NeoForge (first start only)..."
        Get-Verified $installer.Url $installer.Name 'SHA512' $installer.Sha
        & $java -jar $installer.Name --installServer | Out-Null
        if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $argsFile)) { throw 'The NeoForge installer failed. See the installer .log file.' }
        Remove-Item -LiteralPath $installer.Name -Force
    }
    # The NeoForge installer writes a user_jvm_args.txt with every line commented out.
    if (!((Test-Path user_jvm_args.txt) -and (Select-String -Path user_jvm_args.txt -Pattern '^\s*-Xmx' -Quiet))) {
        $gb = [math]::Floor((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
        $max = [math]::Max(2, [math]::Min(6, $gb - 2))
        Add-Content -Path user_jvm_args.txt -Encoding ASCII -Value @('', '# Random Item Skyblock default. Change -Xmx to give the server more or less memory.', '-Xms2G', "-Xmx$($max)G")
    }
    Write-Host 'Starting the server. Type stop and press Enter to save and shut down.'
    & $java '@user_jvm_args.txt' "@$argsFile" nogui @args
    exit $LASTEXITCODE
} catch {
    Write-Host ''
    Write-Host "Could not start: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
