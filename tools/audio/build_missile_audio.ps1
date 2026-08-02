$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$scriptDirectory=Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot=[IO.Path]::GetFullPath((Join-Path $scriptDirectory '..\..'))
$workDirectory=Join-Path $repositoryRoot 'tools\audio\work\missile'
$missileDirectory=Join-Path $repositoryRoot 'src\main\resources\assets\war_mod\sounds\missile'
$warheadDirectory=Join-Path $repositoryRoot 'src\main\resources\assets\war_mod\sounds\warhead'
if(-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)){throw 'FFmpeg was not found on PATH.'}
if(-not (Get-Command ffprobe -ErrorAction SilentlyContinue)){throw 'FFprobe was not found on PATH.'}
New-Item -ItemType Directory -Path $workDirectory,$missileDirectory,$warheadDirectory -Force | Out-Null
function Run-Ffmpeg([string]$Name,[string[]]$Arguments){Write-Host "Building $Name";& ffmpeg -hide_banner -loglevel error @Arguments;if($LASTEXITCODE -ne 0){throw "FFmpeg failed while building $Name (exit $LASTEXITCODE)."}}
function Output-Args([string]$Path){@('-y','-map_metadata','-1','-vn','-ar','48000','-ac','1','-c:a','libvorbis','-q:a','5',$Path)}
$engineMaster=Join-Path $workDirectory 'engine_master.wav'
Run-Ffmpeg 'engine master' @('-y','-f','lavfi','-i','anoisesrc=color=brown:amplitude=0.55:sample_rate=48000:duration=1.45','-f','lavfi','-i','sine=frequency=38:sample_rate=48000:duration=1.45','-f','lavfi','-i','sine=frequency=57:sample_rate=48000:duration=1.45','-f','lavfi','-i','sine=frequency=82:sample_rate=48000:duration=1.45','-filter_complex','[0:a]highpass=f=25,lowpass=f=3500,tremolo=f=7:d=0.18,volume=0.42[n];[1:a]volume=0.34[s1];[2:a]volume=0.24[s2];[3:a]volume=0.17[s3];[n][s1][s2][s3]amix=inputs=4:duration=longest:normalize=0,acompressor=threshold=0.24:ratio=3:attack=8:release=120,alimiter=limit=0.86,afade=t=in:st=0:d=0.035,afade=t=out:st=1.30:d=0.15[out]','-map','[out]','-ar','48000','-ac','1',$engineMaster)
$rushMaster=Join-Path $workDirectory 'terminal_rush_master.wav'
Run-Ffmpeg 'terminal rush master' @('-y','-f','lavfi','-i','anoisesrc=color=pink:amplitude=0.72:sample_rate=48000:duration=1.05','-f','lavfi','-i','anoisesrc=color=brown:amplitude=0.42:sample_rate=48000:duration=1.05','-filter_complex','[0:a]highpass=f=145,lowpass=f=7000,volume=0.62[a];[1:a]highpass=f=35,lowpass=f=850,volume=0.38[b];[a][b]amix=inputs=2:duration=longest:normalize=0,acompressor=threshold=0.30:ratio=2.5:attack=3:release=90,alimiter=limit=0.84,afade=t=in:st=0:d=0.018,afade=t=out:st=0.76:d=0.29[out]','-map','[out]','-ar','48000','-ac','1',$rushMaster)
$boomMaster=Join-Path $workDirectory 'sonic_boom_master.wav'
Run-Ffmpeg 'sonic boom master' @('-y','-f','lavfi','-i','anoisesrc=color=white:amplitude=0.82:sample_rate=48000:duration=0.055','-f','lavfi','-i','anoisesrc=color=white:amplitude=0.78:sample_rate=48000:duration=0.050','-f','lavfi','-i','sine=frequency=66:sample_rate=48000:duration=1.8','-f','lavfi','-i','anoisesrc=color=brown:amplitude=0.48:sample_rate=48000:duration=1.8','-filter_complex','[0:a]highpass=f=180,lowpass=f=9000,afade=t=out:st=0.018:d=0.037[c1];[1:a]highpass=f=180,lowpass=f=8500,afade=t=out:st=0.016:d=0.034,adelay=85[c2];[2:a]lowpass=f=180,volume=0.62,afade=t=out:st=0.30:d=1.40[low];[3:a]highpass=f=32,lowpass=f=240,volume=0.40,afade=t=out:st=0.22:d=1.58[tail];[c1][c2][low][tail]amix=inputs=4:duration=longest:normalize=0,acompressor=threshold=0.30:ratio=3:attack=2:release=140,alimiter=limit=0.84[out]','-map','[out]','-ar','48000','-ac','1',$boomMaster)
$profiles=@(
	@($engineMaster,(Join-Path $missileDirectory 'engine_rumble_near.ogg'),'highpass=f=28,lowpass=f=3500,equalizer=f=90:t=q:w=1:g=2,alimiter=limit=0.88'),
	@($engineMaster,(Join-Path $missileDirectory 'engine_rumble_medium.ogg'),'highpass=f=28,lowpass=f=1800,equalizer=f=90:t=q:w=1:g=3,volume=0.94,alimiter=limit=0.86'),
	@($engineMaster,(Join-Path $missileDirectory 'engine_rumble_far.ogg'),'highpass=f=32,lowpass=f=750,equalizer=f=95:t=q:w=1:g=5,volume=0.82,alimiter=limit=0.84'),
	@($engineMaster,(Join-Path $missileDirectory 'engine_rumble_extreme.ogg'),'highpass=f=35,lowpass=f=400,equalizer=f=90:t=q:w=1:g=6,volume=0.66,alimiter=limit=0.82'),
	@($rushMaster,(Join-Path $warheadDirectory 'terminal_rush_near.ogg'),'highpass=f=150,lowpass=f=7000,alimiter=limit=0.88'),
	@($rushMaster,(Join-Path $warheadDirectory 'terminal_rush_medium.ogg'),'highpass=f=100,lowpass=f=3000,volume=0.92,alimiter=limit=0.86'),
	@($rushMaster,(Join-Path $warheadDirectory 'terminal_rush_far.ogg'),'highpass=f=45,lowpass=f=1200,equalizer=f=120:t=q:w=1:g=4,volume=0.78,alimiter=limit=0.84'),
	@($rushMaster,(Join-Path $warheadDirectory 'terminal_rush_extreme.ogg'),'highpass=f=38,lowpass=f=580,equalizer=f=105:t=q:w=1:g=5,volume=0.58,alimiter=limit=0.82'),
	@($boomMaster,(Join-Path $warheadDirectory 'sonic_boom_near.ogg'),'highpass=f=30,lowpass=f=9000,equalizer=f=72:t=q:w=1:g=3,alimiter=limit=0.88'),
	@($boomMaster,(Join-Path $warheadDirectory 'sonic_boom_medium.ogg'),'highpass=f=30,lowpass=f=3500,equalizer=f=72:t=q:w=1:g=4,volume=0.92,alimiter=limit=0.86'),
	@($boomMaster,(Join-Path $warheadDirectory 'sonic_boom_far.ogg'),'highpass=f=28,lowpass=f=900,equalizer=f=70:t=q:w=1:g=6,volume=0.78,alimiter=limit=0.84'),
	@($boomMaster,(Join-Path $warheadDirectory 'sonic_boom_extreme.ogg'),'highpass=f=28,lowpass=f=420,equalizer=f=68:t=q:w=1:g=7,volume=0.62,alimiter=limit=0.82')
)
foreach($profile in $profiles){Run-Ffmpeg ([IO.Path]::GetFileName($profile[1])) (@('-y','-i',$profile[0],'-af',$profile[2])+(Output-Args $profile[1]))}
foreach($profile in $profiles){$path=$profile[1];if(-not (Test-Path -LiteralPath $path -PathType Leaf)){throw "Expected output is missing: $path"};$probe=& ffprobe -v error -show_entries stream=codec_name,sample_rate,channels -show_entries format=duration -of json $path;if($LASTEXITCODE -ne 0){throw "ffprobe failed for $path"};$data=$probe|ConvertFrom-Json;$stream=$data.streams[0];$duration=[double]$data.format.duration;if($stream.codec_name -ne 'vorbis'-or [int]$stream.sample_rate -ne 48000-or [int]$stream.channels -ne 1-or $duration -le 0){throw "Invalid output format: $path"};Write-Host ("Verified {0}: codec={1}, rate={2}, channels={3}, duration={4:N3}s" -f ([IO.Path]::GetFileName($path)),$stream.codec_name,$stream.sample_rate,$stream.channels,$duration)}