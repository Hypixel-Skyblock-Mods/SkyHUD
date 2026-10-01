[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$JarPath,
    [Parameter(Mandatory = $true)]
    [string]$InstancePath
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Assert-MinecraftStopped {
    $games = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
        Where-Object { $_.CommandLine -match 'org\.prismlauncher\.EntryPoint|net\.minecraft\.client\.main\.Main|net\.fabricmc\.loader\..*KnotClient' })
    if ($games.Count -gt 0) {
        throw "Close Minecraft before installing SkyHUD. Running game JVMs: $($games.ProcessId -join ', ')."
    }
}

function Read-ModMetadata([System.IO.Stream]$Stream, [bool]$VerifyEntries = $false) {
    $archive = [System.IO.Compression.ZipArchive]::new($Stream, [System.IO.Compression.ZipArchiveMode]::Read, $true)
    try {
        $metadataEntry = $archive.GetEntry('fabric.mod.json')
        if ($null -eq $metadataEntry) { return $null }
        $reader = [System.IO.StreamReader]::new($metadataEntry.Open())
        try { $metadata = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($VerifyEntries) {
            foreach ($entry in $archive.Entries) {
                $entryStream = $entry.Open()
                try { $entryStream.CopyTo([System.IO.Stream]::Null) } finally { $entryStream.Dispose() }
            }
        }
        return $metadata
    } finally { $archive.Dispose() }
}

Assert-MinecraftStopped
$source = (Resolve-Path -LiteralPath $JarPath).Path
$instance = (Resolve-Path -LiteralPath $InstancePath).Path
$mods = Join-Path $instance 'minecraft/mods'
if (-not (Test-Path -LiteralPath $mods -PathType Container)) { throw "Prism mods directory not found: $mods" }

# Keep one immutable, verified payload even if the build output changes during installation.
$payload = [System.IO.File]::ReadAllBytes($source)
$sourceStream = [System.IO.MemoryStream]::new($payload, $false)
try { $metadata = Read-ModMetadata $sourceStream $true } finally { $sourceStream.Dispose() }
if ($metadata.id -ne 'skyhud' -or $metadata.environment -ne 'client') {
    throw 'The source must be a client-side SkyHUD production JAR.'
}
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try { $sourceHash = [BitConverter]::ToString($sha256.ComputeHash($payload)).Replace('-', '') } finally { $sha256.Dispose() }

$installed = @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File | Where-Object {
    $stream = [System.IO.File]::OpenRead($_.FullName)
    try { (Read-ModMetadata $stream).id -eq 'skyhud' } finally { $stream.Dispose() }
})
if ($installed.Count -gt 1) { throw 'Multiple SkyHUD JARs are installed; resolve the duplicates before updating.' }
# Preserve an existing filename; Fabric uses the version inside fabric.mod.json.
$destination = if ($installed.Count -eq 1) { $installed[0].FullName } else { Join-Path $mods ([System.IO.Path]::GetFileName($source)) }
$staged = Join-Path $mods ('.skyhud-install-' + [guid]::NewGuid().ToString('N') + '.tmp')
$backup = $null

try {
    [System.IO.File]::WriteAllBytes($staged, $payload)
    if ((Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash -ne $sourceHash) { throw 'Staged JAR does not match the verified build.' }
    Assert-MinecraftStopped
    if (Test-Path -LiteralPath $destination) {
        $backupDirectory = Join-Path $instance ('.codex-transfer-backup/skyhud-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $backupDirectory | Out-Null
        $backup = Join-Path $backupDirectory ([System.IO.Path]::GetFileName($destination))
        # Replace the file atomically with a backup; never overwrite a JAR's bytes in place.
        [System.IO.File]::Replace($staged, $destination, $backup)
    } else {
        [System.IO.File]::Move($staged, $destination)
    }
    if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $sourceHash) { throw 'Installed JAR does not match the verified build.' }
    [pscustomobject]@{ Version = $metadata.version; Installed = $destination; Backup = $backup; SHA256 = $sourceHash }
} finally {
    if (Test-Path -LiteralPath $staged) { Remove-Item -LiteralPath $staged }
}
