$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $scriptDirectory '..\..'))
$sourceDirectory = Join-Path $repositoryRoot 'third_party\audio\open_game_art'
$workDirectory = Join-Path $repositoryRoot 'tools\audio\work'
$bangWorkDirectory = Join-Path $workDirectory 'bang_pack'
$outputDirectory = Join-Path $repositoryRoot 'src\main\resources\assets\war_mod\sounds'
$largeExplosionDirectory = Join-Path $outputDirectory 'explosion\large'

$requiredSources = @(
	'25-CC0-bang-sfx.zip',
	'explosion1.ogg',
	'explosion2.ogg',
	'explosion3.ogg',
	'explosions4.ogg',
	'muffled-distant-explosion.wav',
	'chunky-explosion.mp3',
	'synthetic-explosion.flac'
)

foreach ($source in $requiredSources) {
	$sourcePath = Join-Path $sourceDirectory $source
	if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
		throw "Required source audio is missing: $sourcePath"
	}
}

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
	throw 'FFmpeg was not found on PATH. Install a reputable FFmpeg package and rerun this script.'
}
if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
	throw 'FFprobe was not found on PATH. Install a matching FFmpeg package and rerun this script.'
}

New-Item -ItemType Directory -Path $workDirectory, $bangWorkDirectory, $largeExplosionDirectory -Force | Out-Null
Expand-Archive -LiteralPath (Join-Path $sourceDirectory '25-CC0-bang-sfx.zip') -DestinationPath $bangWorkDirectory -Force

function Invoke-FfmpegOutput {
	param(
		[string]$Name,
		[string[]]$Arguments
	)

	Write-Host "Building $Name"
	& ffmpeg @Arguments
	if ($LASTEXITCODE -ne 0) {
		throw "FFmpeg failed while building $Name (exit code $LASTEXITCODE)."
	}
}

function OutputArguments {
	param([string]$Path)
	return @(
		'-y',
		'-v', 'error',
		'-map_metadata', '-1',
		'-ar', '48000',
		'-ac', '1',
		'-c:a', 'libvorbis',
		'-q:a', '5',
		$Path
	)
}

$explosion1 = Join-Path $sourceDirectory 'explosion1.ogg'
$explosion2 = Join-Path $sourceDirectory 'explosion2.ogg'
$explosion3 = Join-Path $sourceDirectory 'explosion3.ogg'
$explosions4 = Join-Path $sourceDirectory 'explosions4.ogg'
$muffled = Join-Path $sourceDirectory 'muffled-distant-explosion.wav'
$chunky = Join-Path $sourceDirectory 'chunky-explosion.mp3'
$synthetic = Join-Path $sourceDirectory 'synthetic-explosion.flac'

$crack1 = Join-Path $largeExplosionDirectory 'large_explosion_1_crack.ogg'
Invoke-FfmpegOutput 'large_explosion_1_crack.ogg' @(
	'-i', $explosion2,
	'-filter_complex', '[0:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=start=0:end=0.38,highpass=f=500,lowpass=f=10500,afade=t=out:st=0.30:d=0.08,alimiter=limit=0.90[out]',
	'-map', '[out]'
	(OutputArguments $crack1)
)

$body1 = Join-Path $largeExplosionDirectory 'large_explosion_1_body.ogg'
Invoke-FfmpegOutput 'large_explosion_1_body.ogg' @(
	'-i', $explosion1,
	'-i', $chunky,
	'-filter_complex', '[0:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=duration=1.80,highpass=f=35,lowpass=f=5500,volume=0.75[a];[1:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=duration=1.80,highpass=f=35,lowpass=f=5000,volume=0.45[b];[a][b]amix=inputs=2:duration=longest:dropout_transition=0:normalize=0,acompressor=threshold=0.50:ratio=3:attack=5:release=100,alimiter=limit=0.90,afade=t=out:st=1.55:d=0.25[out]',
	'-map', '[out]'
	(OutputArguments $body1)
)

$low1 = Join-Path $largeExplosionDirectory 'large_explosion_1_low.ogg'
Invoke-FfmpegOutput 'large_explosion_1_low.ogg' @(
	'-i', $chunky,
	'-i', $synthetic,
	'-filter_complex', '[0:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=duration=2.40,highpass=f=28,lowpass=f=220,volume=0.85[a];[1:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=duration=2.40,highpass=f=30,lowpass=f=220,volume=0.70[b];[a][b]amix=inputs=2:duration=longest:dropout_transition=0:normalize=0,acompressor=threshold=0.40:ratio=2.5:attack=15:release=180,alimiter=limit=0.88,afade=t=out:st=2.05:d=0.35[out]',
	'-map', '[out]'
	(OutputArguments $low1)
)

$tail1 = Join-Path $largeExplosionDirectory 'large_explosion_1_tail.ogg'
Invoke-FfmpegOutput 'large_explosion_1_tail.ogg' @(
	'-i', $muffled,
	'-filter_complex', '[0:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=start=0.05:end=4.35,highpass=f=30,lowpass=f=1650,volume=0.82,afade=t=out:st=3.85:d=0.45,alimiter=limit=0.90[out]',
	'-map', '[out]'
	(OutputArguments $tail1)
)

$crack2 = Join-Path $largeExplosionDirectory 'large_explosion_2_crack.ogg'
Invoke-FfmpegOutput 'large_explosion_2_crack.ogg' @(
	'-i', $explosions4,
	'-filter_complex', '[0:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=start=0:end=0.42,highpass=f=600,lowpass=f=11000,volume=0.92,afade=t=out:st=0.33:d=0.09,alimiter=limit=0.88[out]',
	'-map', '[out]'
	(OutputArguments $crack2)
)

$body2 = Join-Path $largeExplosionDirectory 'large_explosion_2_body.ogg'
Invoke-FfmpegOutput 'large_explosion_2_body.ogg' @(
	'-i', $explosion3,
	'-i', $synthetic,
	'-filter_complex', '[0:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=duration=1.65,highpass=f=40,lowpass=f=4500,volume=0.78[a];[1:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=duration=1.65,highpass=f=35,lowpass=f=4200,volume=0.52[b];[a][b]amix=inputs=2:duration=longest:dropout_transition=0:normalize=0,acompressor=threshold=0.48:ratio=3:attack=8:release=120,alimiter=limit=0.88,afade=t=out:st=1.38:d=0.27[out]',
	'-map', '[out]'
	(OutputArguments $body2)
)

$low2 = Join-Path $largeExplosionDirectory 'large_explosion_2_low.ogg'
Invoke-FfmpegOutput 'large_explosion_2_low.ogg' @(
	'-i', $explosion1,
	'-i', $muffled,
	'-filter_complex', '[0:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=duration=2.40,highpass=f=30,lowpass=f=200,volume=0.70[a];[1:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=duration=2.40,highpass=f=28,lowpass=f=200,volume=0.78[b];[a][b]amix=inputs=2:duration=longest:dropout_transition=0:normalize=0,acompressor=threshold=0.42:ratio=2.5:attack=15:release=180,alimiter=limit=0.88,afade=t=out:st=2.05:d=0.35[out]',
	'-map', '[out]'
	(OutputArguments $low2)
)

$tail2 = Join-Path $largeExplosionDirectory 'large_explosion_2_tail.ogg'
Invoke-FfmpegOutput 'large_explosion_2_tail.ogg' @(
	'-i', $muffled,
	'-filter_complex', '[0:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=start=0.25:end=4.35,highpass=f=35,lowpass=f=1250,volume=0.72[a];[0:a]aformat=sample_rates=48000:channel_layouts=mono,atrim=start=0.05:end=3.70,highpass=f=45,lowpass=f=950,volume=0.20,adelay=110|110[b];[a][b]amix=inputs=2:duration=longest:dropout_transition=0:normalize=0,afade=t=out:st=3.55:d=0.30,alimiter=limit=0.88[out]',
	'-map', '[out]'
	(OutputArguments $tail2)
)

$silent = Join-Path $outputDirectory 'silent.ogg'
Invoke-FfmpegOutput 'silent.ogg' @(
	'-f', 'lavfi',
	'-i', 'anullsrc=r=48000:cl=mono',
	'-t', '0.10',
	'-filter_complex', '[0:a]afade=t=in:st=0:d=0.005,afade=t=out:st=0.095:d=0.005[out]',
	'-map', '[out]'
	(OutputArguments $silent)
)

$expectedOutputs = @(
	$crack1, $body1, $low1, $tail1,
	$crack2, $body2, $low2, $tail2,
	$silent
)
foreach ($output in $expectedOutputs) {
	if (-not (Test-Path -LiteralPath $output -PathType Leaf)) {
		throw "Expected generated audio is missing: $output"
	}

	$probeJson = (& ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,sample_rate,channels,duration -show_entries format=duration -of json -- $output | Out-String)
	if ($LASTEXITCODE -ne 0) {
		throw "FFprobe failed for $output."
	}
	$probe = $probeJson | ConvertFrom-Json
	if (-not $probe.streams -or $probe.streams.Count -lt 1) {
		throw "No audio stream found in $output."
	}
	$stream = $probe.streams[0]
	$durationText = $stream.duration
	if ([string]::IsNullOrWhiteSpace([string]$durationText)) {
		$durationText = $probe.format.duration
	}
	$duration = [double]$durationText
	if ($stream.codec_name -ne 'vorbis') {
		throw "Generated file is not Vorbis: $output"
	}
	if ([int]$stream.sample_rate -ne 48000) {
		throw "Generated file is not 48 kHz: $output"
	}
	if ([int]$stream.channels -ne 1) {
		throw "Generated file is not mono: $output"
	}
	if ([double]::IsNaN($duration) -or [double]::IsInfinity($duration) -or $duration -le 0.0) {
		throw "Generated file has no positive duration: $output"
	}
	Write-Host ("{0} | Codec: {1} | Sample rate: {2} Hz | Channels: {3} | Duration: {4:F3} s" -f (Split-Path $output -Leaf), $stream.codec_name, $stream.sample_rate, $stream.channels, $duration)
}

Write-Host ("Generated and verified {0} OGG files." -f $expectedOutputs.Count)
