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
        [Parameter(Mandatory = $true)][string] $Preparation,
        [Parameter(Mandatory = $true)][string] $Filter
    )
    & $ffmpeg -y -hide_banner -loglevel error -i $InputFile -af "$Preparation,$Filter" `
        -ac 1 -ar 48000 -c:a libvorbis -q:a 8 $OutputFile
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed while building $OutputFile" }
}

$gunPreparation = 'pan=mono|c0=c0,atrim=start=0.08,asetpts=PTS-STARTPTS'
$impactPreparation = 'pan=mono|c0=0.5*c0+0.5*c1'

$weapons = @(
    @{
        Name = 'pistol'; Near = 'walther_ppq_near_master.wav'; Mid = 'walther_ppq_mid_master.wav'
        Tone = 'equalizer=f=115:t=q:w=0.72:g=8,equalizer=f=240:t=q:w=0.90:g=3'
    },
    @{
        Name = 'rifle'; Near = 'ar15_near_master.wav'; Mid = 'ar15_mid_master.wav'
        Tone = 'equalizer=f=88:t=q:w=0.68:g=22,equalizer=f=175:t=q:w=0.90:g=4'
    },
    @{
        Name = 'sniper'; Near = 'tikka_t3_near_master.wav'; Mid = 'tikka_t3_mid_master.wav'
        Tone = 'equalizer=f=72:t=q:w=0.65:g=20,equalizer=f=145:t=q:w=0.85:g=4.5'
    }
)

foreach ($weapon in $weapons) {
    Convert-FirearmSound (Join-Path $source $weapon.Near) `
        (Join-Path $output ($weapon.Name + '_near.ogg')) $gunPreparation `
        "highpass=f=30,$($weapon.Tone),lowpass=f=15500,alimiter=limit=0.97"
    Convert-FirearmSound (Join-Path $source $weapon.Mid) `
        (Join-Path $output ($weapon.Name + '_medium.ogg')) $gunPreparation `
        "highpass=f=32,$($weapon.Tone),lowpass=f=11500,volume=0.96,alimiter=limit=0.97"
    Convert-FirearmSound (Join-Path $source $weapon.Mid) `
        (Join-Path $output ($weapon.Name + '_far.ogg')) $gunPreparation `
        "highpass=f=34,$($weapon.Tone),lowpass=f=5400,volume=0.86,aecho=0.82:0.28:110:0.08,alimiter=limit=0.97"
    Convert-FirearmSound (Join-Path $source $weapon.Mid) `
        (Join-Path $output ($weapon.Name + '_extreme.ogg')) $gunPreparation `
        "highpass=f=32,$($weapon.Tone),lowpass=f=2900,volume=0.74,aecho=0.82:0.34:180|340:0.10|0.04,alimiter=limit=0.97"
}

$crack = Join-Path $source 'bullet_crackle_cc0.wav'
Convert-FirearmSound $crack (Join-Path $output 'bullet_crack_near.ogg') $impactPreparation `
    'highpass=f=180,alimiter=limit=0.96'
Convert-FirearmSound $crack (Join-Path $output 'bullet_crack_medium.ogg') $impactPreparation `
    'highpass=f=140,lowpass=f=11000,volume=0.90,alimiter=limit=0.96'
Convert-FirearmSound $crack (Join-Path $output 'bullet_crack_far.ogg') $impactPreparation `
    'highpass=f=100,lowpass=f=5500,volume=0.76,aecho=0.82:0.22:70:0.08,alimiter=limit=0.96'
Convert-FirearmSound $crack (Join-Path $output 'bullet_crack_extreme.ogg') $impactPreparation `
    'highpass=f=80,lowpass=f=3000,volume=0.62,aecho=0.82:0.28:120:0.10,alimiter=limit=0.96'

$impact = Join-Path $source 'bullet_impact_cc0.wav'
Convert-FirearmSound $impact (Join-Path $output 'bullet_impact_near.ogg') $impactPreparation `
    'highpass=f=55,equalizer=f=150:t=q:w=0.75:g=4,equalizer=f=1200:t=q:w=1:g=2,lowpass=f=13500,alimiter=limit=0.96'
Convert-FirearmSound $impact (Join-Path $output 'bullet_impact_medium.ogg') $impactPreparation `
    'highpass=f=50,equalizer=f=135:t=q:w=0.75:g=4,lowpass=f=8500,volume=0.86,alimiter=limit=0.96'
Convert-FirearmSound $impact (Join-Path $output 'bullet_impact_far.ogg') $impactPreparation `
    'highpass=f=45,equalizer=f=120:t=q:w=0.75:g=4,lowpass=f=4700,volume=0.70,alimiter=limit=0.96'
Convert-FirearmSound $impact (Join-Path $output 'bullet_impact_extreme.ogg') $impactPreparation `
    'highpass=f=40,equalizer=f=105:t=q:w=0.75:g=4,lowpass=f=2600,volume=0.54,alimiter=limit=0.96'

Write-Host "Built real-recording firearm and bullet-impact distance profiles in $output"
