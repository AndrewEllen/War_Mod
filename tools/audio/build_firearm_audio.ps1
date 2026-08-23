$ErrorActionPreference = 'Stop'

$ffmpeg = (Get-Command ffmpeg -ErrorAction Stop).Source
$repository = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$source = Join-Path $PSScriptRoot 'source\firearm'
$output = Join-Path $repository 'src\main\resources\assets\war_mod\sounds\firearm'
New-Item -ItemType Directory -Force -Path $output | Out-Null

function Convert-FirearmSound {
    param(
        [Parameter(Mandatory = $true)][string] $InputFile,
        [Parameter(Mandatory = $true)][string] $OutputFile,
        [Parameter(Mandatory = $true)][string] $Filter
    )
    & $ffmpeg -y -hide_banner -loglevel error -i $InputFile -af $Filter `
        -ac 1 -ar 48000 -c:a libvorbis -q:a 8 $OutputFile
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed while building $OutputFile" }
}

$nearFilter = 'highpass=f=38,alimiter=limit=0.97'
$mediumFilter = 'highpass=f=38,lowpass=f=11500,volume=0.96,alimiter=limit=0.97'
$farFilter = 'highpass=f=44,lowpass=f=5200,volume=0.84,aecho=0.82:0.28:110:0.10,alimiter=limit=0.97'
$extremeFilter = 'highpass=f=40,lowpass=f=2700,volume=0.70,aecho=0.82:0.36:180|340:0.12|0.05,alimiter=limit=0.97'

$weapons = @(
    @{ Name = 'pistol'; Near = 'walther_ppq_near_master.wav'; Mid = 'walther_ppq_mid_master.wav' },
    @{ Name = 'rifle'; Near = 'ar15_near_master.wav'; Mid = 'ar15_mid_master.wav' },
    @{ Name = 'sniper'; Near = 'tikka_t3_near_master.wav'; Mid = 'tikka_t3_mid_master.wav' }
)

foreach ($weapon in $weapons) {
    Convert-FirearmSound (Join-Path $source $weapon.Near) `
        (Join-Path $output ($weapon.Name + '_near.ogg')) $nearFilter
    Convert-FirearmSound (Join-Path $source $weapon.Mid) `
        (Join-Path $output ($weapon.Name + '_medium.ogg')) $mediumFilter
    Convert-FirearmSound (Join-Path $source $weapon.Mid) `
        (Join-Path $output ($weapon.Name + '_far.ogg')) $farFilter
    Convert-FirearmSound (Join-Path $source $weapon.Mid) `
        (Join-Path $output ($weapon.Name + '_extreme.ogg')) $extremeFilter
}

$crack = Join-Path $source 'bullet_crackle_cc0.wav'
Convert-FirearmSound $crack (Join-Path $output 'bullet_crack_near.ogg') `
    'highpass=f=180,alimiter=limit=0.96'
Convert-FirearmSound $crack (Join-Path $output 'bullet_crack_medium.ogg') `
    'highpass=f=140,lowpass=f=11000,volume=0.90,alimiter=limit=0.96'
Convert-FirearmSound $crack (Join-Path $output 'bullet_crack_far.ogg') `
    'highpass=f=100,lowpass=f=5500,volume=0.76,aecho=0.82:0.22:70:0.08,alimiter=limit=0.96'
Convert-FirearmSound $crack (Join-Path $output 'bullet_crack_extreme.ogg') `
    'highpass=f=80,lowpass=f=3000,volume=0.62,aecho=0.82:0.28:120:0.10,alimiter=limit=0.96'

Write-Host "Built real-recording firearm distance profiles in $output"
