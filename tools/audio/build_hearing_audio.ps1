$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ffmpeg = (Get-Command ffmpeg -ErrorAction Stop).Source
$repository = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$hearingSource = Join-Path $PSScriptRoot 'source\user\ear_ringing_sfx.m4a'
$output = Join-Path $repository 'src\main\resources\assets\war_mod\sounds\hearing'
if (-not (Test-Path -LiteralPath $hearingSource -PathType Leaf)) {
    throw "User-supplied hearing source is missing: $hearingSource"
}
New-Item -ItemType Directory -Force -Path $output | Out-Null

function Convert-Ringing {
    param([string] $Name, [double] $Volume)
    $culture = [Globalization.CultureInfo]::InvariantCulture
    $volumeText = $Volume.ToString('0.###', $culture)
    # The supplied file rises slowly for most of its duration. Reverse its clean
    # sustained section and time-stretch it so gameplay gets an immediate tone
    # followed by the long natural decay the recording contains.
    $filter = 'atrim=start=1.65:end=16.50,asetpts=PTS-STARTPTS,areverse,atempo=0.5,' +
        "highpass=f=180,lowpass=f=9200,volume=${volumeText}," +
        'afade=t=in:st=0:d=0.04,afade=t=out:st=28.2:d=1.5,' +
        'alimiter=limit=0.94:attack=5:release=80:level=false'
    & $ffmpeg -y -hide_banner -loglevel error -i $hearingSource -af $filter `
        -map_metadata -1 -ac 2 -ar 48000 -c:a libvorbis -q:a 8 `
        (Join-Path $output ($Name + '.ogg'))
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed while building $Name" }
}

Convert-Ringing 'ear_ringing_light' 2.45
Convert-Ringing 'ear_ringing_medium' 2.65
Convert-Ringing 'ear_ringing_heavy' 2.85
Write-Host "Built shockwave hearing profiles in $output"
