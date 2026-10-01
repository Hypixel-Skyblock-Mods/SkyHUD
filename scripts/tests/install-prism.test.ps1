param(
    [Parameter(Mandatory = $true)]
    [string]$BuiltJar,
    [string]$Installer = (Join-Path $PSScriptRoot '../install-prism.ps1')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repository = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$root = Join-Path $repository ('build/installer-regression-' + [guid]::NewGuid().ToString('N'))
$mods = Join-Path $root 'minecraft/mods'
New-Item -ItemType Directory -Path $mods -Force | Out-Null
$oldJar = Join-Path $mods 'SkyHUD-old.jar'
$archive = [System.IO.Compression.ZipFile]::Open($oldJar, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $entry = $archive.CreateEntry('fabric.mod.json')
    $writer = [System.IO.StreamWriter]::new($entry.Open())
    try { $writer.Write('{"id":"skyhud","version":"0.0.0","environment":"client"}') } finally { $writer.Dispose() }
} finally { $archive.Dispose() }
$originalHash = (Get-FileHash -LiteralPath $oldJar).Hash
$global:installerMockMode = 'stopped'
$global:installerMockCalls = 0
function Get-CimInstance {
    param($ClassName, $Filter)
    $global:installerMockCalls++
    if ($global:installerMockMode -eq 'running' -or
        ($global:installerMockMode -eq 'race' -and $global:installerMockCalls -gt 1)) {
        [pscustomobject]@{CommandLine='java org.prismlauncher.EntryPoint'; ProcessId=12345}
    }
}
function Assert-Unchanged {
    if ((Get-FileHash -LiteralPath $oldJar).Hash -ne $originalHash) { throw 'Installed file changed during a rejected update.' }
    if (@(Get-ChildItem -LiteralPath $mods -Filter '*.tmp').Count -ne 0) { throw 'Staging files were left behind.' }
}
foreach ($mode in @('running', 'race')) {
    $global:installerMockMode = $mode
    $global:installerMockCalls = 0
    $rejected = $false
    try { & $Installer -JarPath $BuiltJar -InstancePath $root | Out-Null }
    catch { if ($_.Exception.Message -notmatch 'Close Minecraft') { throw }; $rejected = $true }
    if (-not $rejected) { throw "The $mode process guard did not reject the install." }
    Assert-Unchanged
    Write-Output "PASS: $mode game guard leaves the original JAR intact"
}
$badJar = Join-Path $root 'invalid.jar'
[System.IO.File]::WriteAllBytes($badJar, [byte[]]@(1,2,3,4))
$global:installerMockMode = 'stopped'
$rejected = $false
try { & $Installer -JarPath $badJar -InstancePath $root | Out-Null } catch { $rejected = $true }
if (-not $rejected) { throw 'Invalid archive was accepted.' }
Assert-Unchanged
Write-Output 'PASS: unreadable archive leaves the original JAR intact'
$result = & $Installer -JarPath $BuiltJar -InstancePath $root
if ((Get-FileHash -LiteralPath $oldJar).Hash -ne (Get-FileHash -LiteralPath $BuiltJar).Hash) { throw 'Installed payload mismatch.' }
if ((Get-FileHash -LiteralPath $result.Backup).Hash -ne $originalHash) { throw 'Backup mismatch.' }
if (@(Get-ChildItem -LiteralPath $mods -Filter '*.jar').Count -ne 1) { throw 'Duplicate installed mod.' }
if (@(Get-ChildItem -LiteralPath $mods -Filter '*.tmp').Count -ne 0) { throw 'Staging file remains.' }
Write-Output 'PASS: verified atomic install keeps an exact backup and one mod JAR'
