param(
    [ValidateSet('tiny', 'base')][string]$Model = 'tiny',
    [string]$Destination = (Join-Path $PSScriptRoot 'models')
)
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$checksums = @{
    tiny = 'bd577a113a864445d4c299885e0cb97d4ba92b5f'
    base = '465707469ff3a37a2b9b8d8f89f2f99de7299dac'
}
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
$fileName = "ggml-$Model.bin"
$targetPath = Join-Path $Destination $fileName
$partialPath = Join-Path $Destination ("$fileName." + [Guid]::NewGuid().ToString('N') + '.part')
try {
    $modelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${fileName}?download=true"
    Write-Host "Downloading multilingual Whisper $Model. This may take a few minutes."
    Invoke-WebRequest -UseBasicParsing -Uri $modelUrl -OutFile $partialPath
    if ((Get-Item -LiteralPath $partialPath).Length -gt (160 * 1024 * 1024)) { throw 'Unexpected model size.' }
    $actualHash = (Get-FileHash -LiteralPath $partialPath -Algorithm SHA1).Hash.ToLowerInvariant()
    if ($actualHash -ne $checksums[$Model]) { throw 'Model checksum mismatch. The existing model was not changed.' }
    Move-Item -LiteralPath $partialPath -Destination $targetPath -Force
    Write-Host "Verified model: $targetPath"
    Write-Host 'Copy this .bin file to the phone, then choose it in the local voice screen.'
} finally {
    if (Test-Path -LiteralPath $partialPath) { Remove-Item -LiteralPath $partialPath -Force }
}
