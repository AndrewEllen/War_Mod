$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $scriptDirectory '..\..'))
$sourceAudio = Join-Path $repositoryRoot 'tools\audio\source\rocket_thrust_reference.mp3'
$workDirectory = Join-Path $repositoryRoot 'tools\audio\work\missile'
$missileDirectory = Join-Path $repositoryRoot 'src\main\resources\assets\war_mod\sounds\missile'
$warheadDirectory = Join-Path $repositoryRoot 'src\main\resources\assets\war_mod\sounds\warhead'
$explosionSource = Join-Path $repositoryRoot 'src\main\resources\assets\war_mod\sounds\explosion\prototype\explosion_extreme.ogg'

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) { throw 'FFmpeg was not found on PATH.' }
if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) { throw 'FFprobe was not found on PATH.' }
if (-not (Test-Path -LiteralPath $sourceAudio -PathType Leaf)) { throw "Required supplied source is missing: $sourceAudio" }
if (-not (Test-Path -LiteralPath $explosionSource -PathType Leaf)) { throw "Explosion source is missing: $explosionSource" }
New-Item -ItemType Directory -Path $workDirectory,$missileDirectory,$warheadDirectory -Force | Out-Null

function Run-Ffmpeg([string]$Name, [string[]]$Arguments) {
    Write-Host "Building $Name"
    & ffmpeg -hide_banner -loglevel error @Arguments
    if ($LASTEXITCODE -ne 0) { throw "FFmpeg failed while building $Name (exit $LASTEXITCODE)." }
}
function Output-Args([string]$Path) {
    return @('-y','-map_metadata','-1','-vn','-ar','48000','-ac','1','-c:a','libvorbis','-q:a','5',$Path)
}
function Encode([string]$Name, [string]$InputPath, [string]$Filter, [string]$OutputPath) {
    Run-Ffmpeg $Name (@('-y','-i',$InputPath,'-af',$Filter) + (Output-Args $OutputPath))
}

$sourceDuration = [double](& ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 $sourceAudio)
if ($LASTEXITCODE -ne 0 -or $sourceDuration -lt 6.1) { throw 'The supplied thrust source must contain at least 6.1 seconds of audio.' }

$cleanMaster = Join-Path $workDirectory 'thrust_clean.wav'
$ignitionMaster = Join-Path $workDirectory 'engine_ignition_master.wav'
$stableMaster = Join-Path $workDirectory 'engine_stable_4s.wav'
$sustainMaster = Join-Path $workDirectory 'engine_sustain_loop_master.wav'
$shutdownMaster = Join-Path $workDirectory 'engine_shutdown_master.wav'
$rushRaw = Join-Path $workDirectory 'terminal_rush_raw.wav'
$rushLoop = Join-Path $workDirectory 'terminal_rush_loop_master.wav'
$rushTail = Join-Path $workDirectory 'terminal_rush_tail_master.wav'
$boomMaster = Join-Path $workDirectory 'sonic_boom_master.wav'
$thudMaster = Join-Path $workDirectory 'impact_thud_master.wav'

Run-Ffmpeg 'clean supplied thrust reference' @('-y','-i',$sourceAudio,'-map_metadata','-1','-vn','-af','highpass=f=20,lowpass=f=11500,acompressor=threshold=0.22:ratio=2.6:attack=8:release=150,alimiter=limit=0.84','-ar','48000','-ac','1','-c:a','pcm_f32le',$cleanMaster)
Run-Ffmpeg 'engine ignition master' @('-y','-i',$cleanMaster,'-af','atrim=start=0:end=3.4,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.07,afade=t=out:st=3.18:d=0.22','-ar','48000','-ac','1','-c:a','pcm_f32le',$ignitionMaster)
Run-Ffmpeg 'stable thrust selection' @('-y','-i',$cleanMaster,'-af','atrim=start=2:end=6,asetpts=PTS-STARTPTS','-ar','48000','-ac','1','-c:a','pcm_f32le',$stableMaster)
Run-Ffmpeg 'seamless sustain master' @('-y','-i',$stableMaster,'-filter_complex','[0:a]atrim=start=0.55:end=3.45,asetpts=PTS-STARTPTS[mid];[0:a]atrim=start=3.45:end=4,asetpts=PTS-STARTPTS[end];[0:a]atrim=start=0:end=0.55,asetpts=PTS-STARTPTS[start];[end][start]acrossfade=d=0.55:c1=qsin:c2=iqsin[x];[mid][x]concat=n=2:v=0:a=1[out]','-map','[out]','-ar','48000','-ac','1','-c:a','pcm_f32le',$sustainMaster)
Run-Ffmpeg 'engine shutdown master' @('-y','-i',$cleanMaster,'-af','atrim=start=3.4:end=6,asetpts=PTS-STARTPTS,volume=1.02,afade=t=out:st=0.25:d=2.35','-ar','48000','-ac','1','-c:a','pcm_f32le',$shutdownMaster)

Run-Ffmpeg 'terminal rush raw' @('-y','-f','lavfi','-i','anoisesrc=color=pink:amplitude=0.68:sample_rate=48000:duration=2.85','-f','lavfi','-i','anoisesrc=color=brown:amplitude=0.42:sample_rate=48000:duration=2.85','-filter_complex','[0:a]highpass=f=135,lowpass=f=7200,volume=0.64[a];[1:a]highpass=f=30,lowpass=f=950,volume=0.38[b];[a][b]amix=inputs=2:duration=longest:normalize=0,acompressor=threshold=0.28:ratio=2.5:attack=4:release=100,alimiter=limit=0.84[out]','-map','[out]','-ar','48000','-ac','1','-c:a','pcm_f32le',$rushRaw)
Run-Ffmpeg 'seamless terminal rush loop' @('-y','-i',$rushRaw,'-filter_complex','[0:a]atrim=start=0.55:end=2.3,asetpts=PTS-STARTPTS[mid];[0:a]atrim=start=2.3:end=2.85,asetpts=PTS-STARTPTS[end];[0:a]atrim=start=0:end=0.55,asetpts=PTS-STARTPTS[start];[end][start]acrossfade=d=0.55:c1=qsin:c2=iqsin[x];[mid][x]concat=n=2:v=0:a=1[out]','-map','[out]','-ar','48000','-ac','1','-c:a','pcm_f32le',$rushLoop)
Run-Ffmpeg 'terminal rush tail' @('-y','-f','lavfi','-i','anoisesrc=color=pink:amplitude=0.62:sample_rate=48000:duration=1.2','-f','lavfi','-i','anoisesrc=color=brown:amplitude=0.38:sample_rate=48000:duration=1.2','-filter_complex','[0:a]highpass=f=130,lowpass=f=6500,volume=0.55[a];[1:a]highpass=f=30,lowpass=f=800,volume=0.34[b];[a][b]amix=inputs=2:duration=longest:normalize=0,afade=t=out:st=0.18:d=1.02,alimiter=limit=0.84[out]','-map','[out]','-ar','48000','-ac','1','-c:a','pcm_f32le',$rushTail)

Run-Ffmpeg 'source-derived sonic boom' @('-y','-i',$explosionSource,'-af','atrim=start=0:end=1.6,asetpts=PTS-STARTPTS,asetrate=45120,aresample=48000,atempo=1.06383,highpass=f=28,lowpass=f=4200,equalizer=f=75:t=q:w=1:g=4,volume=0.88,afade=t=out:st=0.72:d=0.88,alimiter=limit=0.84','-ar','48000','-ac','1','-c:a','pcm_f32le',$boomMaster)
Run-Ffmpeg 'source-derived impact thud' @('-y','-i',$explosionSource,'-af','atrim=start=0:end=0.55,asetpts=PTS-STARTPTS,highpass=f=24,lowpass=f=850,equalizer=f=72:t=q:w=1:g=6,volume=0.92,afade=t=out:st=0.10:d=0.45,alimiter=limit=0.84','-ar','48000','-ac','1','-c:a','pcm_f32le',$thudMaster)

$profiles = @(
    @{Name='near'; Filter='highpass=f=20,lowpass=f=9000,equalizer=f=105:t=q:w=1:g=2,alimiter=limit=0.84'},
    @{Name='medium'; Filter='highpass=f=20,lowpass=f=4800,equalizer=f=95:t=q:w=1:g=3,volume=0.95,alimiter=limit=0.84'},
    @{Name='far'; Filter='highpass=f=24,lowpass=f=2100,equalizer=f=88:t=q:w=1:g=4,volume=0.84,alimiter=limit=0.84'},
    @{Name='extreme'; Filter='highpass=f=28,lowpass=f=950,equalizer=f=78:t=q:w=1:g=5,volume=0.72,alimiter=limit=0.84'}
)
$outputs = New-Object System.Collections.Generic.List[object]
foreach ($profile in $profiles) {
    $name = $profile.Name; $filter = $profile.Filter
    $ignition = Join-Path $missileDirectory "engine_ignition_$name.ogg"
    $sustain = Join-Path $missileDirectory "engine_sustain_$name.ogg"
    $shutdown = Join-Path $missileDirectory "engine_shutdown_$name.ogg"
    $loop = Join-Path $warheadDirectory "terminal_rush_loop_$name.ogg"
    $tail = Join-Path $warheadDirectory "terminal_rush_tail_$name.ogg"
    $boom = Join-Path $warheadDirectory "sonic_boom_$name.ogg"
    $thud = Join-Path $warheadDirectory "impact_thud_$name.ogg"
    Encode "engine ignition $name" $ignitionMaster $filter $ignition
    Encode "engine sustain $name" $sustainMaster $filter $sustain
    Encode "engine shutdown $name" $shutdownMaster $filter $shutdown
    Encode "terminal rush loop $name" $rushLoop $filter $loop
    Encode "terminal rush tail $name" $rushTail $filter $tail
    Encode "sonic boom $name" $boomMaster $filter $boom
    Encode "impact thud $name" $thudMaster $filter $thud
    $outputs.Add([pscustomobject]@{Path=$ignition; Kind='ignition'})
    $outputs.Add([pscustomobject]@{Path=$sustain; Kind='engine-loop'})
    $outputs.Add([pscustomobject]@{Path=$shutdown; Kind='shutdown'})
    $outputs.Add([pscustomobject]@{Path=$loop; Kind='rush-loop'})
    $outputs.Add([pscustomobject]@{Path=$tail; Kind='tail'})
    $outputs.Add([pscustomobject]@{Path=$boom; Kind='boom'})
    $outputs.Add([pscustomobject]@{Path=$thud; Kind='thud'})
}

function Boundary-Rms([string]$Path, [bool]$Ending) {
    $seek = if ($Ending) { @('-sseof','-0.12') } else { @('-ss','0') }
    $savedErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $lines = & ffmpeg -hide_banner @seek -t 0.12 -i $Path -af volumedetect -f null NUL 2>&1
    $ErrorActionPreference = $savedErrorActionPreference
    $match = $lines | Select-String 'mean_volume:\s*(-?[0-9.]+) dB' | Select-Object -Last 1
    if ($null -eq $match) { return $null }
    return [double]$match.Matches[0].Groups[1].Value
}

foreach ($output in $outputs) {
    $path = $output.Path
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Expected output is missing: $path" }
    $probe = & ffprobe -v error -show_entries stream=codec_name,sample_rate,channels -show_entries format=duration -of json $path
    if ($LASTEXITCODE -ne 0) { throw "ffprobe failed for $path" }
    $data = $probe | ConvertFrom-Json; $stream = $data.streams[0]; $duration = [double]$data.format.duration
    if ($stream.codec_name -ne 'vorbis' -or [int]$stream.sample_rate -ne 48000 -or [int]$stream.channels -ne 1 -or $duration -le 0) { throw "Invalid output format: $path" }
    if ($output.Kind -eq 'engine-loop' -and ($duration -lt 3.35 -or $duration -gt 3.55)) { throw "Unexpected engine loop duration: $duration" }
    if ($output.Kind -eq 'rush-loop' -and ($duration -lt 2.20 -or $duration -gt 2.40)) { throw "Unexpected rush loop duration: $duration" }
    Write-Host ("Verified {0}: codec={1}, rate={2}, channels={3}, duration={4:N3}s" -f ([IO.Path]::GetFileName($path)),$stream.codec_name,$stream.sample_rate,$stream.channels,$duration)
    if ($output.Kind -in @('engine-loop','rush-loop')) {
        $startRms = Boundary-Rms $path $false; $endRms = Boundary-Rms $path $true
        if ($null -ne $startRms -and $null -ne $endRms -and [Math]::Abs($startRms-$endRms) -gt 12) { Write-Warning "Loop boundary RMS differs by more than 12 dB for $path" }
    }
}

$resourceMp3 = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'src\main\resources') -Recurse -Filter '*.mp3' -File)
if ($resourceMp3.Count -gt 0) { throw 'Source MP3 files must not be packaged under resources.' }
if ($outputs.Count -ne 28) { throw "Expected 28 generated audio files, got $($outputs.Count)." }
Write-Host 'Generated and verified 28 missile/warhead audio resources.'
