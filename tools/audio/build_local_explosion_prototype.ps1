$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $scriptDirectory '..\..'))
$sourcePath = Join-Path $repositoryRoot 'local_audio_sources\explosion_reference.mp3'
$outputDirectory = Join-Path $repositoryRoot 'src\main\resources\assets\war_mod\sounds\explosion\prototype'
$workDirectory = Join-Path $repositoryRoot 'tools\audio\work\local_explosion_prototype'
$leadingSilenceSeconds = 0.222336

if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
	throw "Local explosion prototype source is missing: $sourcePath"
}
if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
	throw 'FFmpeg was not found on PATH.'
}
if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
	throw 'FFprobe was not found on PATH.'
}

New-Item -ItemType Directory -Path $outputDirectory, $workDirectory -Force | Out-Null

$profiles = @(
	@{
		Name = 'explosion_near'
		TargetPeakDb = -1.8
		Filter = "atrim=start=$leadingSilenceSeconds,asetpts=PTS-STARTPTS,highpass=f=22,lowpass=f=15000,equalizer=f=85:t=q:w=1.0:g=5"
	},
	@{
		Name = 'explosion_medium'
		TargetPeakDb = -1.3
		Filter = "atrim=start=$leadingSilenceSeconds,asetpts=PTS-STARTPTS,highpass=f=20,lowpass=f=3500,equalizer=f=95:t=q:w=1.0:g=6"
	},
	@{
		Name = 'explosion_far'
		TargetPeakDb = -1.5
		Filter = "atrim=start=$leadingSilenceSeconds,asetpts=PTS-STARTPTS,highpass=f=18,lowpass=f=900,equalizer=f=90:t=q:w=1.0:g=8"
	},
	@{
		Name = 'explosion_extreme'
		TargetPeakDb = -1.2
		Filter = "atrim=start=$leadingSilenceSeconds,asetpts=PTS-STARTPTS,highpass=f=17,lowpass=f=500,equalizer=f=78:t=q:w=0.9:g=10,acompressor=threshold=0.25:ratio=1.5:attack=2:release=80:makeup=1"
	}
)

function Invoke-CheckedFfmpeg {
	param(
		[string]$Description,
		[string[]]$Arguments
	)

	Write-Host $Description
	& ffmpeg @Arguments
	if ($LASTEXITCODE -ne 0) {
		throw "FFmpeg failed while $Description (exit code $LASTEXITCODE)."
	}
}

function Get-PeakLevelDb {
	param([string]$AudioPath)

	$previousErrorActionPreference = $ErrorActionPreference
	try {
		$ErrorActionPreference = 'Continue'
		$scan = (& ffmpeg -hide_banner -nostats -i $AudioPath -af volumedetect -f null NUL 2>&1 | Out-String)
		$scanExitCode = $LASTEXITCODE
	} finally {
		$ErrorActionPreference = $previousErrorActionPreference
	}
	if ($scanExitCode -ne 0) {
		throw "FFmpeg peak scan failed for $AudioPath."
	}
	$match = [regex]::Match($scan, 'max_volume:\s*(-?\d+(?:\.\d+)?)\s*dB')
	if (-not $match.Success) {
		throw "Could not read peak level for $AudioPath."
	}
	return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}

foreach ($profile in $profiles) {
	$name = [string]$profile.Name
	$filter = [string]$profile.Filter
	$targetPeakDb = [double]$profile.TargetPeakDb
	$filteredPath = Join-Path $workDirectory "$name.filtered.wav"
	$outputPath = Join-Path $outputDirectory "$name.ogg"

	Invoke-CheckedFfmpeg "Filtering $name from the aligned master: ffmpeg -i <source> -af `"$filter`" -ar 48000 -ac 1 <filtered.wav>" @(
		'-y', '-v', 'error',
		'-i', $sourcePath,
		'-map_metadata', '-1',
		'-af', $filter,
		'-ar', '48000',
		'-ac', '1',
		'-c:a', 'pcm_f32le',
		$filteredPath
	)

	$sourcePeakDb = Get-PeakLevelDb $filteredPath
	$gainDb = $targetPeakDb - $sourcePeakDb
	$gainText = $gainDb.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
	$limiterLevel = [Math]::Pow(10.0, $targetPeakDb / 20.0).ToString('0.######', [Globalization.CultureInfo]::InvariantCulture)
	$normalizationFilter = "volume=${gainText}dB,alimiter=limit=${limiterLevel}:attack=5:release=50:level=false"
	Invoke-CheckedFfmpeg "Encoding ${name}: ffmpeg -i <filtered.wav> -af `"$normalizationFilter`" -map_metadata -1 -ar 48000 -ac 1 -c:a libvorbis -q:a 6 <output.ogg>" @(
		'-y', '-v', 'error',
		'-i', $filteredPath,
		'-map_metadata', '-1',
		'-af', $normalizationFilter,
		'-ar', '48000',
		'-ac', '1',
		'-c:a', 'libvorbis',
		'-q:a', '6',
		$outputPath
	)
}

foreach ($profile in $profiles) {
	$outputPath = Join-Path $outputDirectory "$($profile.Name).ogg"
	$probeJson = (& ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,sample_rate,channels,duration -show_entries format=duration -of json -- $outputPath | Out-String)
	if ($LASTEXITCODE -ne 0) {
		throw "FFprobe failed for $outputPath."
	}
	$probe = $probeJson | ConvertFrom-Json
	$stream = $probe.streams[0]
	$durationText = if ([string]::IsNullOrWhiteSpace([string]$stream.duration)) { $probe.format.duration } else { $stream.duration }
	$duration = [double]::Parse([string]$durationText, [Globalization.CultureInfo]::InvariantCulture)
	$peakDb = Get-PeakLevelDb $outputPath

	if ($stream.codec_name -ne 'vorbis' -or [int]$stream.sample_rate -ne 48000 -or [int]$stream.channels -ne 1 -or $duration -le 0.0) {
		throw "Generated audio does not meet the required Vorbis/mono/48 kHz format: $outputPath"
	}
	Write-Host ("{0} | Codec: {1} | Channels: {2} | Sample rate: {3} Hz | Duration: {4:F3} s | Peak: {5:F1} dBFS" -f (Split-Path $outputPath -Leaf), $stream.codec_name, $stream.channels, $stream.sample_rate, $duration, $peakDb)
}
