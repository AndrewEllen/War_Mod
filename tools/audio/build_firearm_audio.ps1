$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ffmpeg = (Get-Command ffmpeg -ErrorAction Stop).Source
$repository = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$source = Join-Path $PSScriptRoot 'source\user'
$output = Join-Path $repository 'src\main\resources\assets\war_mod\sounds\firearm'
New-Item -ItemType Directory -Force -Path $output | Out-Null

$required = @(
    'far_away_gunshot.m4a', 'bullet_impact_dirt.m4a', 'bullet_impact_body.m4a',
    'pistol_shooting.m4a', 'sniper_shot_close.m4a',
    'sniper_shot_medium_distance.m4a', 'automatic_rifle_shot.m4a',
    'bullet_pass_by_hit_metal.m4a', 'bullet_pass_by.m4a', 'bullet_ricochet.m4a'
)
foreach ($name in $required) {
    $path = Join-Path $source $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "User-supplied firearm source is missing: $path"
    }
}

function Convert-Clip {
    param(
        [Parameter(Mandatory = $true)][string] $InputFile,
        [Parameter(Mandatory = $true)][string] $OutputFile,
        [Parameter(Mandatory = $true)][double] $Start,
        [Parameter(Mandatory = $true)][double] $End,
        [Parameter(Mandatory = $true)][int] $LowPass,
        [Parameter(Mandatory = $true)][double] $Volume,
        [double] $FadeOut = 0.12,
        [ValidateSet('average', 'left', 'right')][string] $MonoChannel = 'average',
        [int] $HighPass = 24
    )
    if ($End -le $Start) { throw "Invalid clip bounds for $OutputFile" }
    $duration = $End - $Start
    $fade = [Math]::Min($FadeOut, $duration * 0.45)
    $fadeStart = [Math]::Max(0.0, $duration - $fade)
    $culture = [Globalization.CultureInfo]::InvariantCulture
    $startText = $Start.ToString('0.###', $culture)
    $endText = $End.ToString('0.###', $culture)
    $fadeText = $fade.ToString('0.###', $culture)
    $fadeStartText = $fadeStart.ToString('0.###', $culture)
    $volumeText = $Volume.ToString('0.###', $culture)
    $monoFilter = switch ($MonoChannel) {
        'left' { 'pan=mono|c0=c0' }
        'right' { 'pan=mono|c0=c1' }
        default { 'pan=mono|c0=0.5*c0+0.5*c1' }
    }
    $filter = "atrim=start=${startText}:end=${endText},asetpts=PTS-STARTPTS," +
        "${monoFilter},highpass=f=${HighPass},lowpass=f=${LowPass}," +
        "volume=${volumeText},afade=t=out:st=${fadeStartText}:d=${fadeText}," +
        'alimiter=limit=0.97:attack=2:release=40:level=false'
    & $ffmpeg -y -hide_banner -loglevel error -i $InputFile -af $filter `
        -map_metadata -1 -ac 1 -ar 48000 -c:a libvorbis -q:a 8 $OutputFile
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed while building $OutputFile" }
}

function Output-Name {
    param([string] $Stem, [int] $Index)
    $suffix = if ($Index -eq 0) { '' } else { '_' + ($Index + 1) }
    return Join-Path $output ($Stem + $suffix + '.ogg')
}

function Build-DistanceFamily {
    param(
        [string] $InputFile,
        [string] $Stem,
        [double] $Start,
        [double] $End,
        [double] $Volume,
        [double] $FadeOut = 0.14,
        [ValidateSet('average', 'left', 'right')][string] $MonoChannel = 'average',
        [int[]] $LowPasses = @(16000, 10500, 5600, 3100),
        [int] $HighPass = 24
    )
    $profiles = @(
        @{ Name = 'near'; LowPass = $LowPasses[0]; Volume = 1.00 },
        @{ Name = 'medium'; LowPass = $LowPasses[1]; Volume = 0.98 },
        @{ Name = 'far'; LowPass = $LowPasses[2]; Volume = 0.96 },
        @{ Name = 'extreme'; LowPass = $LowPasses[3]; Volume = 0.94 }
    )
    foreach ($profile in $profiles) {
        Convert-Clip $InputFile (Join-Path $output ($Stem + '_' + $profile.Name + '.ogg')) `
            $Start $End $profile.LowPass ($Volume * $profile.Volume) $FadeOut `
            $MonoChannel $HighPass
    }
}

$farGunshot = Join-Path $source 'far_away_gunshot.m4a'
$pistol = Join-Path $source 'pistol_shooting.m4a'
$sniperClose = Join-Path $source 'sniper_shot_close.m4a'
$sniperMedium = Join-Path $source 'sniper_shot_medium_distance.m4a'
$automaticRifle = Join-Path $source 'automatic_rifle_shot.m4a'

# Weapon reports retain the supplied natural perspectives. The common far report
# is reused for weapons without their own distant take; per-weapon pitch lives in sounds.json.
Convert-Clip $pistol (Join-Path $output 'pistol_near.ogg') 0.0 1.93 17000 0.84 0.18
Convert-Clip $farGunshot (Join-Path $output 'pistol_medium.ogg') 0.0 2.95 10500 0.92 0.16
Convert-Clip $farGunshot (Join-Path $output 'pistol_far.ogg') 0.0 2.95 6200 0.94 0.16
Convert-Clip $farGunshot (Join-Path $output 'pistol_extreme.ogg') 0.0 2.95 3200 0.98 0.16

$rifleCuts = @(
    @{ Start = 0.12; End = 1.42 }, @{ Start = 2.20; End = 3.50 },
    @{ Start = 4.19; End = 5.13 }, @{ Start = 5.72; End = 7.25 },
    @{ Start = 8.47; End = 9.78 }, @{ Start = 11.05; End = 12.55 },
    @{ Start = 15.73; End = 17.25 }, @{ Start = 18.63; End = 19.70 }
)
for ($index = 0; $index -lt $rifleCuts.Count; $index++) {
    $cut = $rifleCuts[$index]
    Convert-Clip $automaticRifle (Output-Name 'rifle_near' $index) `
        $cut.Start $cut.End 17000 0.82 0.18
}
Convert-Clip $farGunshot (Join-Path $output 'rifle_medium.ogg') 0.0 2.95 11000 0.94 0.16
Convert-Clip $farGunshot (Join-Path $output 'rifle_far.ogg') 0.0 2.95 6500 0.96 0.16
Convert-Clip $farGunshot (Join-Path $output 'rifle_extreme.ogg') 0.0 2.95 3300 1.00 0.16

Convert-Clip $sniperClose (Join-Path $output 'sniper_near.ogg') 0.10 4.18 17000 0.84 0.24
Convert-Clip $sniperMedium (Join-Path $output 'sniper_medium.ogg') 0.29 2.75 11500 1.34 0.20
Convert-Clip $farGunshot (Join-Path $output 'sniper_far.ogg') 0.0 2.95 6800 0.98 0.16
Convert-Clip $farGunshot (Join-Path $output 'sniper_extreme.ogg') 0.0 2.95 3400 1.02 0.16

# Supersonic pass-by reel: eight separated variations for every acoustic band.
$passBy = Join-Path $source 'bullet_pass_by.m4a'
$passByCuts = @(
    @{ Start = 0.00; End = 0.36 }, @{ Start = 0.50; End = 1.03 },
    @{ Start = 1.12; End = 1.43 }, @{ Start = 1.49; End = 2.19 },
    @{ Start = 2.20; End = 3.24 }, @{ Start = 3.45; End = 3.79 },
    @{ Start = 3.94; End = 4.25 }, @{ Start = 4.27; End = 4.52 }
)
$passProfiles = @(
    @{ Name = 'near'; LowPass = 16500; Volume = 1.08 },
    @{ Name = 'medium'; LowPass = 10500; Volume = 1.04 },
    @{ Name = 'far'; LowPass = 5700; Volume = 1.00 },
    @{ Name = 'extreme'; LowPass = 3200; Volume = 0.98 }
)
foreach ($profile in $passProfiles) {
    for ($index = 0; $index -lt $passByCuts.Count; $index++) {
        $cut = $passByCuts[$index]
        Convert-Clip $passBy (Output-Name ('bullet_crack_' + $profile.Name) $index) `
            $cut.Start $cut.End $profile.LowPass $profile.Volume 0.08
    }
}

# Dirt is the deliberate fallback for non-hard blocks; body remains separate.
Build-DistanceFamily (Join-Path $source 'bullet_impact_dirt.m4a') `
    'bullet_impact' 0.08 0.72 1.10 0.10 'right' @(19000, 14000, 8000, 4500) 18
Build-DistanceFamily (Join-Path $source 'bullet_impact_body.m4a') `
    'bullet_impact_body' 0.30 0.88 0.98 0.10

# Hard surfaces use separated ricochet/metal takes rather than the dirt fallback.
$ricochet = Join-Path $source 'bullet_ricochet.m4a'
$ricochetCuts = @(
    @{ Start = 0.80; End = 1.58 }, @{ Start = 2.12; End = 2.80 },
    @{ Start = 3.35; End = 4.03 }, @{ Start = 4.44; End = 5.32 },
    @{ Start = 6.68; End = 7.32 }, @{ Start = 9.60; End = 10.24 },
    @{ Start = 12.42; End = 13.12 }, @{ Start = 17.47; End = 18.05 },
    @{ Start = 20.52; End = 21.23 }, @{ Start = 23.54; End = 24.27 }
)
$impactProfiles = @(
    @{ Name = 'near'; LowPass = 16000; Volume = 0.90 },
    @{ Name = 'medium'; LowPass = 9200; Volume = 0.88 },
    @{ Name = 'far'; LowPass = 5100; Volume = 0.86 },
    @{ Name = 'extreme'; LowPass = 2900; Volume = 0.84 }
)
foreach ($profile in $impactProfiles) {
    for ($index = 0; $index -lt $ricochetCuts.Count; $index++) {
        $cut = $ricochetCuts[$index]
        Convert-Clip $ricochet (Output-Name ('bullet_impact_metal_' + $profile.Name) $index) `
            $cut.Start $cut.End $profile.LowPass $profile.Volume 0.12
    }
}

# Four supplied combined flyby-then-hard-impact takes. These are selected only
# when a bullet crosses close to a listener and hits a hard surface on that segment.
$passMetal = Join-Path $source 'bullet_pass_by_hit_metal.m4a'
$passMetalCuts = @(
    @{ Start = 0.00; End = 0.64 }, @{ Start = 1.18; End = 1.65 },
    @{ Start = 1.79; End = 2.54 }, @{ Start = 3.10; End = 3.73 }
)
for ($index = 0; $index -lt $passMetalCuts.Count; $index++) {
    $cut = $passMetalCuts[$index]
    Convert-Clip $passMetal (Join-Path $output ('bullet_pass_metal_' + ($index + 1) + '.ogg')) `
        $cut.Start $cut.End 16500 1.20 0.10
}

Write-Host "Built user-supplied firearm, flyby, body, dirt and hard-impact profiles in $output"
