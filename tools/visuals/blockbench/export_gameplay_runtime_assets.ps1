[CmdletBinding()]
param(
    [string]$CatalogRoot = (Join-Path $PSScriptRoot 'gameplay_catalog'),
    [string]$MissileCatalogRoot = (Join-Path $PSScriptRoot 'missiles'),
    [string]$ResourceRoot = (Join-Path $PSScriptRoot '..\..\..\src\main\resources\assets\war_mod'),
    [string]$ClientSourceRoot = (Join-Path $PSScriptRoot '..\..\..\src\client\java'),
    [switch]$LegacySupportsOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

try {
    Add-Type -AssemblyName System.Drawing.Common -ErrorAction Stop
} catch {
    Add-Type -AssemblyName System.Drawing -ErrorAction Stop
}

$CatalogRoot = [IO.Path]::GetFullPath($CatalogRoot)
$MissileCatalogRoot = [IO.Path]::GetFullPath($MissileCatalogRoot)
$ResourceRoot = [IO.Path]::GetFullPath($ResourceRoot)
$ClientSourceRoot = [IO.Path]::GetFullPath($ClientSourceRoot)
$ManifestPath = Join-Path $CatalogRoot 'gameplay_model_manifest.json'
$MissileManifestPath = Join-Path $MissileCatalogRoot 'missile_model_manifest.json'
$Utf8NoBom = [Text.UTF8Encoding]::new($false)
$Invariant = [Globalization.CultureInfo]::InvariantCulture

function Write-TextFile {
    param([string]$Path, [string]$Content)
    $parent = Split-Path -Parent $Path
    [IO.Directory]::CreateDirectory($parent) | Out-Null
    for ($attempt = 0; $attempt -lt 12; $attempt++) {
        try {
            [IO.File]::WriteAllText($Path, $Content + "`n", $Utf8NoBom)
            return
        } catch [IO.IOException] {
            if ($attempt -eq 11) { throw }
            [Threading.Thread]::Sleep(125)
        }
    }
}

function Write-JsonFile {
    param([string]$Path, [object]$Value)
    Write-TextFile -Path $Path -Content ($Value | ConvertTo-Json -Depth 32 -Compress)
}

function Copy-JsonValue {
    param([object]$Value)
    if ($null -eq $Value) { return $null }
    return ($Value | ConvertTo-Json -Depth 32 | ConvertFrom-Json)
}

function Export-DynamicMaterialAtlas {
    # Pack the nine ImageGen material samples into one neutral detail atlas.
    # Generated meshes tint these 16px tiles with each Blockbench cube colour.
    $sourceRoot = Join-Path $PSScriptRoot 'material_sources'
    $names = @('concrete','gunmetal','olive_paint','shaft_black','brushed_steel','brass','warning_red','radar_cross','soot_metal')
    $targetDirectory = Join-Path $ResourceRoot 'textures'
    [IO.Directory]::CreateDirectory($targetDirectory) | Out-Null
    $targetPath = Join-Path $targetDirectory 'blockbench_material_atlas.png'
    $atlas = [Drawing.Bitmap]::new(48, 48, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        for ($index = 0; $index -lt $names.Count; $index++) {
            $sourcePath = Join-Path $sourceRoot ($names[$index] + '.png')
            if (-not (Test-Path -LiteralPath $sourcePath)) { throw "Missing dynamic material source '$sourcePath'." }
            $source = [Drawing.Bitmap]::FromFile($sourcePath)
            try {
                $column = $index % 3; $row = [Math]::Floor($index / 3)
                [long]$sum = 0
                for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
                    $pixel = $source.GetPixel($x, $y)
                    $sum += [int](0.2126 * $pixel.R + 0.7152 * $pixel.G + 0.0722 * $pixel.B)
                } }
                $mean = [Math]::Max(1.0, $sum / 256.0)
                for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
                    $pixel = $source.GetPixel($x, $y)
                    $luma = 0.2126 * $pixel.R + 0.7152 * $pixel.G + 0.0722 * $pixel.B
                    $neutral = [int][Math]::Round([Math]::Clamp(($luma / $mean) * 232.0, 150.0, 255.0))
                    $atlas.SetPixel($column * 16 + $x, $row * 16 + $y,
                        [Drawing.Color]::FromArgb($pixel.A, $neutral, $neutral, $neutral))
                } }
            } finally { $source.Dispose() }
        }
        $atlas.Save($targetPath, [Drawing.Imaging.ImageFormat]::Png)
    } finally { $atlas.Dispose() }
}

function Get-DynamicMaterialIndex {
    param([string]$TextureName)
    $name = $TextureName.ToLowerInvariant()
    if ($name -match 'concrete|cement|stone') { return 0 }
    if ($name -match 'radar|screen|display|scan|cross|glow') { return 7 }
    if ($name -match 'brass|gold|copper') { return 5 }
    if ($name -match 'red|warning|stripe|handle') { return 6 }
    if ($name -match 'soot|burn|exhaust|char') { return 8 }
    if ($name -match 'black|shaft|shadow|rubber') { return 3 }
    if ($name -match 'steel|metal|silver|blade|jaw|rail|hinge') { return 4 }
    if ($name -match 'olive|green|body|shell|casing|warhead|fin') { return 2 }
    return 1
}

function Get-MaterialSourceName {
    param([string]$TextureName)
    $name = $TextureName.ToLowerInvariant()
    # Specific object roles take precedence over the old broad palette names.
    if ($name -match 'targeting_chip' -and $name -match 'body|green') { return 'circuit_board' }
    if ($name -match 'targeting_chip.*stock') { return 'gunmetal' }
    if ($name -match 'artillery_shell.*stock') { return 'brushed_steel' }
    if ($name -match '_tnt_stock') { return 'gunmetal' }
    if ($name -match 'concrete|cement|stone') { return 'concrete' }
    if ($name -match 'radar_cross|screen|display|scan|glow') { return 'radar_cross' }
    if ($name -match 'brass|gold|copper|driving_band|fuze_ring') { return 'brass' }
    if ($name -match 'extinguisher.*accent|extinguisher.*body|warning|hazard|red') { return 'warning_red' }
    if ($name -match 'hose.*dark|rubber|grip|pad|stock') { return 'rubber' }
    if ($name -match 'shaft|recess|black') { return 'shaft_black' }
    if ($name -match 'soot|burn|exhaust|char|nozzle|bore') { return 'soot_metal' }
    if ($name -match 'wrench.*body|wrench.*stock|steel|metal|silver|blade|jaw|rail|hinge|accent|blue') { return 'brushed_steel' }
    if ($name -match 'body_shadow|dark') { return 'gunmetal' }
    if ($name -match 'body|shell|casing|warhead|fin|green|olive|paint|yield|tip') { return 'olive_paint' }
    return 'painted_steel'
}

function Export-TintedMaterialTile {
    param([string]$SourceName, [string]$Tint, [string]$TargetPath)
    $sourcePath = Join-Path (Join-Path $PSScriptRoot 'material_sources') ($SourceName + '.png')
    if (-not (Test-Path -LiteralPath $sourcePath)) { throw "Missing material source '$sourcePath'." }
    $source = [Drawing.Bitmap]::FromFile($sourcePath)
    $target = [Drawing.Bitmap]::new(16, 16, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $colour = [Drawing.ColorTranslator]::FromHtml($Tint)
    try {
        [double]$sum = 0.0
        for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
            $pixel = $source.GetPixel($x, $y)
            $sum += 0.2126 * $pixel.R + 0.7152 * $pixel.G + 0.0722 * $pixel.B
        } }
        $mean = [Math]::Max(1.0, $sum / 256.0)
        for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
            $pixel = $source.GetPixel($x, $y)
            $luma = 0.2126 * $pixel.R + 0.7152 * $pixel.G + 0.0722 * $pixel.B
            # Normalize every ImageGen material around its own mean. This keeps
            # the authored seams, rivets and wear visible even with dark tints.
            $factor = [Math]::Clamp(0.20 + 0.82 * ($luma / $mean), 0.58, 1.42)
            $target.SetPixel($x, $y, [Drawing.Color]::FromArgb($pixel.A,
                [int][Math]::Min(255, [Math]::Round($colour.R * $factor)),
                [int][Math]::Min(255, [Math]::Round($colour.G * $factor)),
                [int][Math]::Min(255, [Math]::Round($colour.B * $factor))))
        } }
        [IO.Directory]::CreateDirectory((Split-Path -Parent $TargetPath)) | Out-Null
        $target.Save($TargetPath, [Drawing.Imaging.ImageFormat]::Png)
    } finally { $target.Dispose(); $source.Dispose() }
}

function Export-AdditionalMaterialTextures {
    $blockRoot = Join-Path $ResourceRoot 'textures\block'
    $itemRoot = Join-Path $ResourceRoot 'textures\item'

    # Placed utility machinery that predates the Blockbench catalogue.
    Export-TintedMaterialTile gunmetal '#4B575A' (Join-Path $blockRoot 'item_pipe.png')
    Export-TintedMaterialTile brushed_steel '#3DA7A8' (Join-Path $blockRoot 'item_pipe_input.png')
    Export-TintedMaterialTile warning_red '#B64B3D' (Join-Path $blockRoot 'item_pipe_output.png')
    Export-TintedMaterialTile brushed_steel '#3DA7A8' (Join-Path $blockRoot 'item_pipe_mode_input.png')
    Export-TintedMaterialTile warning_red '#B64B3D' (Join-Path $blockRoot 'item_pipe_mode_output.png')
    Export-TintedMaterialTile gunmetal '#3B4446' (Join-Path $blockRoot 'phalanx_turret_base.png')
    Export-TintedMaterialTile olive_paint '#59635A' (Join-Path $blockRoot 'phalanx_turret_side.png')
    Export-TintedMaterialTile brushed_steel '#687174' (Join-Path $blockRoot 'phalanx_turret_top.png')

    # Legacy component item IDs remain registered alongside the newer missile
    # warheads. Give every one a proper metallic casing surface.
    $yieldColours = [ordered]@{
        high_explosive = '#C85B26'; high_capacity_he = '#D58725'
        conventional = '#778B55'; heavy_conventional = '#667077'
        tactical_nuclear = '#B58135'; strategic_nuclear = '#9A7036'
        heavy_nuclear = '#86626A'
    }
    foreach ($entry in $yieldColours.GetEnumerator()) {
        foreach ($suffix in @('_warhead','_cluster_warhead')) {
            Export-TintedMaterialTile brushed_steel ([string]$entry.Value) `
                (Join-Path $itemRoot ($entry.Key + $suffix + '.png'))
        }
    }
}

function Read-BlockbenchModel {
    param([string]$Id)
    $entry = $script:Manifest | Where-Object id -eq $Id | Select-Object -First 1
    if ($null -eq $entry) { throw "No Blockbench model is registered for '$Id'." }
    $path = Join-Path $CatalogRoot ([string]$entry.model)
    return Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
}

function Read-MissileBlockbenchModel {
    param([string]$Id)
    $entry = $script:MissileManifest | Where-Object id -eq $Id | Select-Object -First 1
    if ($null -eq $entry) { throw "No missile Blockbench model is registered for '$Id'." }
    $path = Join-Path $MissileCatalogRoot ([string]$entry.model)
    return Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
}

function Get-TexturePalette {
    param([object]$Model, [switch]$PreserveEmbedded)
    $palette = @{}
    $directPaletteDirectory = Join-Path $ResourceRoot 'textures\blockbench_palette'
    $blockPaletteDirectory = Join-Path $ResourceRoot 'textures\block\blockbench_palette'
    $itemPaletteDirectory = Join-Path $ResourceRoot 'textures\item\blockbench_palette'
    foreach ($directory in @($directPaletteDirectory, $blockPaletteDirectory, $itemPaletteDirectory)) {
        [IO.Directory]::CreateDirectory($directory) | Out-Null
    }
    $whitePath = Join-Path $directPaletteDirectory 'ffffffff.png'
    if (-not (Test-Path -LiteralPath $whitePath)) {
        $white = [Drawing.Bitmap]::new(16, 16)
        try {
            $graphics = [Drawing.Graphics]::FromImage($white)
            try { $graphics.Clear([Drawing.Color]::White) } finally { $graphics.Dispose() }
            $white.Save($whitePath, [Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $white.Dispose()
        }
    }
    foreach ($texture in @($Model.textures)) {
        $separator = ([string]$texture.source).IndexOf(',')
        if ($separator -lt 0) { throw "Texture '$($texture.name)' is not embedded." }
        $bytes = [Convert]::FromBase64String(([string]$texture.source).Substring($separator + 1))
        $stream = [IO.MemoryStream]::new($bytes)
        try {
            $bitmap = [Drawing.Bitmap]::FromStream($stream)
            try {
                [long]$red = 0; [long]$green = 0; [long]$blue = 0; [long]$alpha = 0
                for ($y = 0; $y -lt $bitmap.Height; $y++) {
                    for ($x = 0; $x -lt $bitmap.Width; $x++) {
                        $pixel = $bitmap.GetPixel($x, $y)
                        $red += $pixel.R; $green += $pixel.G; $blue += $pixel.B; $alpha += $pixel.A
                    }
                }
                $count = [Math]::Max(1, $bitmap.Width * $bitmap.Height)
                $colour = [Drawing.Color]::FromArgb(
                    [int]($alpha / $count), [int]($red / $count),
                    [int]($green / $count), [int]($blue / $count))
            } finally {
                $bitmap.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
        $sourceName = Get-MaterialSourceName ([string]$texture.name)
        if ($PreserveEmbedded) {
            [byte[]]$materialBytes = [byte[]]::new(0)
        } else {
            [byte[]]$materialBytes = [IO.File]::ReadAllBytes((Join-Path (Join-Path $PSScriptRoot 'material_sources') ($sourceName + '.png')))
        }
        $hashInput = [byte[]]::new($bytes.Length + $materialBytes.Length)
        [Array]::Copy($bytes, 0, $hashInput, 0, $bytes.Length)
        [Array]::Copy($materialBytes, 0, $hashInput, $bytes.Length, $materialBytes.Length)
        $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($hashInput)).Substring(0, 10).ToLowerInvariant()
        $key = ('{0:x2}{1:x2}{2:x2}{3:x2}_{4}' -f $colour.R, $colour.G, $colour.B, $colour.A, $hash)
        foreach ($directory in @($blockPaletteDirectory, $itemPaletteDirectory)) {
            $palettePath = Join-Path $directory ($key + '.png')
            # The filename includes both embedded tint and material-source
            # content, so texture revisions never alias an older runtime tile.
            if (-not (Test-Path -LiteralPath $palettePath)) {
                if ($PreserveEmbedded) {
                    [IO.File]::WriteAllBytes($palettePath, $bytes)
                } else {
                    Export-TintedMaterialTile -SourceName $sourceName `
                        -Tint ('#{0:x2}{1:x2}{2:x2}' -f $colour.R, $colour.G, $colour.B) `
                        -TargetPath $palettePath
                }
            }
        }
        $palette[[string]$texture.id] = [pscustomobject]@{
            blockResource = "war_mod:block/blockbench_palette/$key"
            itemResource = "war_mod:item/blockbench_palette/$key"
            red = [int]$colour.R
            green = [int]$colour.G
            blue = [int]$colour.B
            materialIndex = Get-DynamicMaterialIndex ([string]$texture.name)
            emissive = ([string]$texture.name).EndsWith('_glow', [StringComparison]::OrdinalIgnoreCase)
        }
    }
    return $palette
}

function Get-AutoFitTransform {
    param([object[]]$Elements, [double]$TargetSpan = 14.0)
    $minimum = @([double]::PositiveInfinity, [double]::PositiveInfinity, [double]::PositiveInfinity)
    $maximum = @([double]::NegativeInfinity, [double]::NegativeInfinity, [double]::NegativeInfinity)
    foreach ($element in @($Elements)) {
        for ($axis = 0; $axis -lt 3; $axis++) {
            $minimum[$axis] = [Math]::Min($minimum[$axis], [double]$element.from[$axis])
            $maximum[$axis] = [Math]::Max($maximum[$axis], [double]$element.to[$axis])
        }
    }
    $largestSpan = 0.0
    for ($axis = 0; $axis -lt 3; $axis++) {
        $largestSpan = [Math]::Max($largestSpan, $maximum[$axis] - $minimum[$axis])
    }
    if ($largestSpan -le 0.0) { $largestSpan = 1.0 }
    $scale = $TargetSpan / $largestSpan
    $offset = @()
    for ($axis = 0; $axis -lt 3; $axis++) {
        $offset += 8.0 - (($minimum[$axis] + $maximum[$axis]) * 0.5 * $scale)
    }
    return [pscustomobject]@{ scale = $scale; offset = $offset }
}

function Snap-BlockModelAngle {
    param([double]$Angle)
    if ([Math]::Abs($Angle) -lt 0.001) { return 0.0 }
    if ([Math]::Abs($Angle) -ge 33.75) { return [Math]::Sign($Angle) * 45.0 }
    return [Math]::Sign($Angle) * 22.5
}

function Get-ElementRotation {
    param([object]$Element)
    if ($Element.PSObject.Properties.Name -notcontains 'rotation' -or $null -eq $Element.rotation) {
        return @(0.0, 0.0, 0.0)
    }
    $rotation = @($Element.rotation)
    if ($rotation.Count -ne 3) { return @(0.0, 0.0, 0.0) }
    return $rotation
}

function New-ModelObject {
    param(
        [object]$Model,
        [object[]]$Elements,
        [double]$Scale,
        [double[]]$Offset,
        [object]$Display = $null,
        [ValidateSet('block', 'item')][string]$TextureDomain = 'block',
        [switch]$PreserveEmbeddedTextures
    )
    $palette = Get-TexturePalette -Model $Model -PreserveEmbedded:$PreserveEmbeddedTextures
    $usedIds = [Collections.Generic.List[string]]::new()
    foreach ($element in @($Elements)) {
        foreach ($face in @($element.faces.PSObject.Properties.Value)) {
            $id = [string]$face.texture
            if (-not $usedIds.Contains($id)) { $usedIds.Add($id) }
        }
    }
    if ($usedIds.Count -eq 0 -and @($Model.textures).Count -gt 0) {
        $usedIds.Add([string]$Model.textures[0].id)
    }

    $textures = [ordered]@{}
    foreach ($id in $usedIds) {
        $textures["t$id"] = if ($TextureDomain -eq 'item') {
            $palette[$id].itemResource
        } else {
            $palette[$id].blockResource
        }
    }
    if ($usedIds.Count -gt 0) { $textures.particle = "#t$($usedIds[0])" }

    $jsonElements = [Collections.Generic.List[object]]::new()
    foreach ($element in @($Elements)) {
        $from = @()
        $to = @()
        $origin = @()
        for ($axis = 0; $axis -lt 3; $axis++) {
            $from += [Math]::Round(([double]$element.from[$axis] * $Scale) + $Offset[$axis], 5)
            $to += [Math]::Round(([double]$element.to[$axis] * $Scale) + $Offset[$axis], 5)
            $origin += [Math]::Round(([double]$element.origin[$axis] * $Scale) + $Offset[$axis], 5)
        }
        $faces = [ordered]@{}
        foreach ($faceProperty in $element.faces.PSObject.Properties) {
            $face = $faceProperty.Value
            $faces[$faceProperty.Name] = [ordered]@{
                uv = @($face.uv | ForEach-Object { [Math]::Round([double]$_, 5) })
                texture = "#t$([string]$face.texture)"
            }
        }
        $jsonElement = [ordered]@{
            name = [string]$element.name
            from = $from
            to = $to
            faces = $faces
        }
        $rotation = @(Get-ElementRotation -Element $element)
        $nonZeroAxes = @(0..2 | Where-Object { [Math]::Abs([double]$rotation[$_]) -gt 0.001 })
        if ($nonZeroAxes.Count -gt 0) {
            $axisIndex = $nonZeroAxes[0]
            $axisName = @('x', 'y', 'z')[$axisIndex]
            $jsonElement.rotation = [ordered]@{
                origin = $origin
                axis = $axisName
                angle = Snap-BlockModelAngle ([double]$rotation[$axisIndex])
            }
        }
        $jsonElements.Add([pscustomobject]$jsonElement)
    }

    $result = [ordered]@{
        ambientocclusion = $false
        textures = $textures
        elements = @($jsonElements)
    }
    if ($null -ne $Display) { $result.display = $Display }
    return [pscustomobject]$result
}

function Get-ExistingDisplay {
    param([string]$ItemModelName)
    $path = Join-Path $ResourceRoot "models\item\$ItemModelName.json"
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    $existing = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    if ($existing.PSObject.Properties.Name -notcontains 'display') { return $null }
    return Copy-JsonValue $existing.display
}

function Get-DefaultItemDisplay {
    return [ordered]@{
        gui = [ordered]@{ rotation = @(25, 45, 0); translation = @(0, 0, 0); scale = @(0.82, 0.82, 0.82) }
        ground = [ordered]@{ rotation = @(0, 0, 0); translation = @(0, 2, 0); scale = @(0.36, 0.36, 0.36) }
        fixed = [ordered]@{ rotation = @(0, 180, 0); translation = @(0, 0, 0); scale = @(0.62, 0.62, 0.62) }
        firstperson_righthand = [ordered]@{ rotation = @(0, -90, 20); translation = @(1, 2, 1); scale = @(0.58, 0.58, 0.58) }
        firstperson_lefthand = [ordered]@{ rotation = @(0, 90, -20); translation = @(-1, 2, 1); scale = @(0.58, 0.58, 0.58) }
        thirdperson_righthand = [ordered]@{ rotation = @(0, -90, 35); translation = @(0, 2, 1); scale = @(0.48, 0.48, 0.48) }
        thirdperson_lefthand = [ordered]@{ rotation = @(0, 90, -35); translation = @(0, 2, 1); scale = @(0.48, 0.48, 0.48) }
    }
}

function New-WeaponItemDisplay {
    param([double]$FirstPersonScale, [double]$ThirdPersonScale, [double]$GuiScale = 0.92)
    return [ordered]@{
        gui = [ordered]@{ rotation = @(18, 138, 0); translation = @(0, 0, 0); scale = @($GuiScale, $GuiScale, $GuiScale) }
        ground = [ordered]@{ rotation = @(0, -90, 0); translation = @(0, 2, 0); scale = @(0.55, 0.55, 0.55) }
        fixed = [ordered]@{ rotation = @(0, -90, 0); translation = @(0, 0, 0); scale = @(0.86, 0.86, 0.86) }
        firstperson_righthand = [ordered]@{ rotation = @(0, 90, -5); translation = @(1.2, 1.7, 1.0); scale = @($FirstPersonScale, $FirstPersonScale, $FirstPersonScale) }
        firstperson_lefthand = [ordered]@{ rotation = @(0, -90, 5); translation = @(-1.2, 1.7, 1.0); scale = @($FirstPersonScale, $FirstPersonScale, $FirstPersonScale) }
        thirdperson_righthand = [ordered]@{ rotation = @(0, 90, -12); translation = @(0, 2.8, 1.0); scale = @($ThirdPersonScale, $ThirdPersonScale, $ThirdPersonScale) }
        thirdperson_lefthand = [ordered]@{ rotation = @(0, -90, 12); translation = @(0, 2.8, 1.0); scale = @($ThirdPersonScale, $ThirdPersonScale, $ThirdPersonScale) }
    }
}

function Get-MissileItemDisplay {
    return [ordered]@{
        gui = [ordered]@{ rotation = @(18, 35, -15); translation = @(0, 0, 0); scale = @(1.02, 1.02, 1.02) }
        ground = [ordered]@{ rotation = @(0, 0, 90); translation = @(0, 2, 0); scale = @(0.52, 0.52, 0.52) }
        fixed = [ordered]@{ rotation = @(0, 0, 0); translation = @(0, 0, 0); scale = @(0.82, 0.82, 0.82) }
        firstperson_righthand = [ordered]@{ rotation = @(0, 0, -25); translation = @(1.0, 1.2, 0); scale = @(1.0, 1.0, 1.0) }
        firstperson_lefthand = [ordered]@{ rotation = @(0, 0, 25); translation = @(-1.0, 1.2, 0); scale = @(1.0, 1.0, 1.0) }
        thirdperson_righthand = [ordered]@{ rotation = @(0, 0, -18); translation = @(0, 2.0, 0); scale = @(1.10, 1.10, 1.10) }
        thirdperson_lefthand = [ordered]@{ rotation = @(0, 0, 18); translation = @(0, 2.0, 0); scale = @(1.10, 1.10, 1.10) }
    }
}

function Get-GameplayItemDisplay {
    param([string]$ItemModelName)
    if ($ItemModelName -eq 'pistol') {
        return (New-WeaponItemDisplay -FirstPersonScale 0.60 -ThirdPersonScale 0.48 -GuiScale 0.90)
    }
    if ($ItemModelName -eq 'assault_rifle') {
        return (New-WeaponItemDisplay -FirstPersonScale 1.34 -ThirdPersonScale 1.54)
    }
    if ($ItemModelName -eq 'sniper_rifle') {
        return (New-WeaponItemDisplay -FirstPersonScale 1.36 -ThirdPersonScale 1.60)
    }
    if ($ItemModelName -eq 'rocket_launcher') {
        return (New-WeaponItemDisplay -FirstPersonScale 1.34 -ThirdPersonScale 1.50)
    }
    if ($ItemModelName -like '*_missile' -or $ItemModelName -like '*_icbm' -or $ItemModelName -like 'anti_air_missile_*' -or $ItemModelName -eq 'he_rocket') {
        return (Get-MissileItemDisplay)
    }
    if ($ItemModelName -in @('pistol_ammo','rifle_ammo','sniper_ammo')) {
        $display=Get-DefaultItemDisplay
        $size=if($ItemModelName -eq 'pistol_ammo'){0.20}elseif($ItemModelName -eq 'rifle_ammo'){0.28}else{0.25}
        foreach($view in @('firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand')){
            $display[$view].scale=@($size,$size,$size)
            $display[$view].translation=@(0,1,0)
        }
        return $display
    }
    if ($ItemModelName -eq 'radar') {
        $display=Get-DefaultItemDisplay
        $display.gui.rotation=@(18,180,0)
        $display.gui.scale=@(0.98,0.98,0.98)
        $display.fixed.rotation=@(0,180,0)
        foreach($view in @('firstperson_righthand','firstperson_lefthand')){
            $display[$view].rotation=@(0,180,0)
            $display[$view].translation=@(0,1.5,1.5)
            $display[$view].scale=@(.68,.68,.68)
        }
        return $display
    }
    if ($ItemModelName -eq 'remote_launch_designator') {
        $display=Get-DefaultItemDisplay
        $display.gui.rotation=@(18,180,0)
        $display.fixed.rotation=@(0,180,0)
        foreach($view in @('firstperson_righthand','firstperson_lefthand')){
            $display[$view].rotation=@(0,180,0)
            $display[$view].translation=@(0,1.5,1.5)
            $display[$view].scale=@(.68,.68,.68)
        }
        return $display
    }
    if ($ItemModelName -like 'targeting_chip_tier_*') {
        $display=Get-DefaultItemDisplay
        $display.gui.rotation=@(22,180,0)
        $display.gui.scale=@(1.05,1.05,1.05)
        $display.fixed.rotation=@(0,180,0)
        $display.firstperson_righthand.rotation=@(0,180,-18)
        $display.firstperson_lefthand.rotation=@(0,180,18)
        $display.thirdperson_righthand.rotation=@(0,180,-28)
        $display.thirdperson_lefthand.rotation=@(0,180,28)
        foreach($view in @('firstperson_righthand','firstperson_lefthand')){
            $display[$view].translation=@(0,1.5,1.5)
            $display[$view].scale=@(.68,.68,.68)
        }
        return $display
    }
    if ($ItemModelName -eq 'pipe_wrench') {
        $display=Get-DefaultItemDisplay
        $display.gui.rotation=@(25,180,-28)
        $display.gui.scale=@(.92,.92,.92)
        $display.fixed.rotation=@(0,180,-35)
        $display.firstperson_righthand.rotation=@(0,180,-28)
        $display.firstperson_lefthand.rotation=@(0,180,28)
        $display.thirdperson_righthand.rotation=@(0,180,-40)
        $display.thirdperson_lefthand.rotation=@(0,180,40)
        return $display
    }
    if ($ItemModelName -eq 'target_designator') {
        return (New-WeaponItemDisplay -FirstPersonScale 0.78 -ThirdPersonScale 0.66 -GuiScale 0.90)
    }
    return $null
}

function Set-WeaponGripAnchor {
    param([object]$Model,[object]$Fit,[object]$Display)
    $grip=$Model.elements | Where-Object { $_.name -in @('grip_core','grip','pistol_grip') } | Select-Object -First 1
    if($null -eq $grip){return}
    # Locate the palm on the upper grip, using the same snapped element rotation
    # as the exported Java item model. Auto-fit centres the silhouette, not the hand.
    $x=([double]$grip.from[0]+[double]$grip.to[0])*0.5
    $y=[double]$grip.from[1]+([double]$grip.to[1]-[double]$grip.from[1])*0.62
    $z=([double]$grip.from[2]+[double]$grip.to[2])*0.5
    $rotation=@(Get-ElementRotation $grip)
    $angle=(Snap-BlockModelAngle $rotation[2])*[Math]::PI/180
    $px=$x-$grip.origin[0]; $py=$y-$grip.origin[1]
    $x=$grip.origin[0]+$px*[Math]::Cos($angle)-$py*[Math]::Sin($angle)
    $y=$grip.origin[1]+$px*[Math]::Sin($angle)+$py*[Math]::Cos($angle)
    $anchor=@(($x*$Fit.scale+$Fit.offset[0]-8),($y*$Fit.scale+$Fit.offset[1]-8),($z*$Fit.scale+$Fit.offset[2]-8))
    foreach($view in @('firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand')) {
        $transform=$Display[$view]
        $handSign=if($view.EndsWith('lefthand')){-1}else{1}
        $angleY=$transform.rotation[1]*$handSign*[Math]::PI/180
        $angleZ=$transform.rotation[2]*$handSign*[Math]::PI/180
        $scale=$transform.scale[0]
        # ItemTransform uses rotationXYZ, so Z acts before Y on the point.
        $x=($anchor[0]*[Math]::Cos($angleZ)-$anchor[1]*[Math]::Sin($angleZ))*$scale
        $y=($anchor[0]*[Math]::Sin($angleZ)+$anchor[1]*[Math]::Cos($angleZ))*$scale
        $z=$anchor[2]*$scale
        $transform.translation=@([Math]::Round(-($x*[Math]::Cos($angleY)+$z*[Math]::Sin($angleY))*$handSign,4),[Math]::Round(-$y,4),[Math]::Round(-(-$x*[Math]::Sin($angleY)+$z*[Math]::Cos($angleY)),4))
    }
}

function Export-ItemModel {
    param([string]$SourceId, [string]$TargetId = $SourceId)
    $model = Read-BlockbenchModel -Id $SourceId
    Export-ItemModelObject -Model $model -TargetId $TargetId
}

function Export-ItemModelObject {
    param([object]$Model, [string]$TargetId)
    $model = $Model
    $fit = Get-AutoFitTransform -Elements @($model.elements)
    $display = Get-GameplayItemDisplay $TargetId
    if ($null -eq $display) { $display = Get-ExistingDisplay $TargetId }
    if ($null -eq $display) { $display = Get-DefaultItemDisplay }
    if($TargetId -in @('pistol','assault_rifle','sniper_rifle','target_designator')){
        Set-WeaponGripAnchor -Model $model -Fit $fit -Display $display
    }
    $output = New-ModelObject -Model $model -Elements @($model.elements) -Scale $fit.scale -Offset $fit.offset -Display $display -TextureDomain item -PreserveEmbeddedTextures:($TargetId -eq 'phalanx_turret')
    Write-JsonFile -Path (Join-Path $ResourceRoot "models\item\$TargetId.json") -Value $output
}

function Find-OutlinerNode {
    param([object[]]$Nodes, [string]$Uuid)
    foreach ($node in @($Nodes)) {
        if ($node -is [string]) { continue }
        if ([string]$node.uuid -eq $Uuid) { return $node }
        $found = Find-OutlinerNode -Nodes @($node.children) -Uuid $Uuid
        if ($null -ne $found) { return $found }
    }
    return $null
}

function Get-ElementIds {
    param([object]$Node, [switch]$DirectOnly)
    $ids = [Collections.Generic.List[string]]::new()
    foreach ($child in @($Node.children)) {
        if ($child -is [string]) {
            $ids.Add([string]$child)
        } elseif (-not $DirectOnly) {
            foreach ($id in @(Get-ElementIds -Node $child)) { $ids.Add([string]$id) }
        }
    }
    return @($ids)
}

function Get-GroupElements {
    param([object]$Model, [string]$GroupName, [switch]$DirectOnly)
    $group = $Model.groups | Where-Object name -eq $GroupName | Select-Object -First 1
    if ($null -eq $group) { throw "Model '$($Model.name)' has no '$GroupName' group." }
    $node = Find-OutlinerNode -Nodes @($Model.outliner) -Uuid ([string]$group.uuid)
    if ($null -eq $node) { throw "Model '$($Model.name)' has no outliner node for '$GroupName'." }
    $ids = @(Get-ElementIds -Node $node -DirectOnly:$DirectOnly)
    return @($Model.elements | Where-Object { $ids -contains [string]$_.uuid })
}

function Export-BlockModel {
    param(
        [string]$SourceId,
        [string]$TargetId = $SourceId,
        [object[]]$Elements = $null,
        [double]$Scale = 1.0,
        [double[]]$Offset = @(8.0, 8.0, 8.0)
    )
    $model = Read-BlockbenchModel -Id $SourceId
    if ($null -eq $Elements) { $Elements = @($model.elements) }
    $output = New-ModelObject -Model $model -Elements @($Elements) -Scale $Scale -Offset $Offset
    Write-JsonFile -Path (Join-Path $ResourceRoot "models\block\$TargetId.json") -Value $output
}

function Get-WorkbenchHalfElements {
    param([object]$Model, [ValidateSet('left','right')][string]$Part)
    $minimumX = if ($Part -eq 'left') { 0.0 } else { 16.0 }
    $maximumX = if ($Part -eq 'left') { 16.0 } else { 32.0 }
    $offsetX = if ($Part -eq 'left') { 0.0 } else { -16.0 }
    $result = [Collections.Generic.List[object]]::new()
    foreach ($element in @($Model.elements)) {
        $rotation = @(Get-ElementRotation -Element $element)
        if (@($rotation | Where-Object { [Math]::Abs([double]$_) -gt 0.001 }).Count -gt 0) {
            throw "Rotated workbench element '$($element.name)' cannot be safely partitioned."
        }
        $fromX = [Math]::Max([double]$element.from[0], $minimumX)
        $toX = [Math]::Min([double]$element.to[0], $maximumX)
        if ($toX -le $fromX) { continue }
        $clone = Copy-JsonValue $element
        $clone.from = @(($fromX + $offsetX), [double]$element.from[1], [double]$element.from[2])
        $clone.to = @(($toX + $offsetX), [double]$element.to[1], [double]$element.to[2])
        # Remove the hidden X=16 interface faces from the two adjoining blocks.
        if ($Part -eq 'left' -and [Math]::Abs($toX - 16.0) -lt 0.001) {
            $clone.faces.PSObject.Properties.Remove('east')
        }
        if ($Part -eq 'right' -and [Math]::Abs($fromX - 16.0) -lt 0.001) {
            $clone.faces.PSObject.Properties.Remove('west')
        }
        $result.Add($clone)
    }
    return @($result)
}

function Export-WorkbenchBlocks {
    $model = Read-BlockbenchModel -Id 'missile_workbench'
    $left = New-ModelObject -Model $model -Elements @(Get-WorkbenchHalfElements -Model $model -Part left) -Scale 1.0 -Offset @(0.0, 0.0, 0.0)
    $right = New-ModelObject -Model $model -Elements @(Get-WorkbenchHalfElements -Model $model -Part right) -Scale 1.0 -Offset @(0.0, 0.0, 0.0)
    Write-JsonFile -Path (Join-Path $ResourceRoot 'models\block\missile_workbench_left.json') -Value $left
    Write-JsonFile -Path (Join-Path $ResourceRoot 'models\block\missile_workbench_right.json') -Value $right
    # Preserve the historic model ID as a left/controller fallback.
    Write-JsonFile -Path (Join-Path $ResourceRoot 'models\block\missile_workbench.json') -Value $left

    $variants = [ordered]@{}
    $facings = [ordered]@{ north = 0; east = 90; south = 180; west = 270 }
    foreach ($facing in $facings.GetEnumerator()) {
        foreach ($part in @('left','right')) {
            $apply = [ordered]@{ model = "war_mod:block/missile_workbench_$part" }
            if ([int]$facing.Value -ne 0) { $apply.y = [int]$facing.Value }
            $variants["facing=$($facing.Key),part=$part"] = $apply
        }
    }
    Write-JsonFile -Path (Join-Path $ResourceRoot 'blockstates\missile_workbench.json') -Value ([ordered]@{ variants = $variants })
}

function Export-EmptyBlockModel {
    param([string]$TargetId, [string]$Particle = 'war_mod:block/blockbench_palette/20292bff')
    $output = [ordered]@{
        ambientocclusion = $false
        textures = [ordered]@{ particle = $Particle }
        elements = @()
    }
    Write-JsonFile -Path (Join-Path $ResourceRoot "models\block\$TargetId.json") -Value $output
}

function Clip-SiloElements {
    param([object]$Model, [int]$CellX, [int]$CellZ)
    $minimumX = ($CellX * 16.0) - 8.0
    $maximumX = $minimumX + 16.0
    $minimumZ = ($CellZ * 16.0) - 8.0
    $maximumZ = $minimumZ + 16.0
    $result = [Collections.Generic.List[object]]::new()
    foreach ($element in @($Model.elements)) {
        $rotation = @(Get-ElementRotation -Element $element)
        if (@($rotation | Where-Object { [Math]::Abs([double]$_) -gt 0.001 }).Count -gt 0) {
            throw "Rotated silo element '$($element.name)' cannot be safely partitioned."
        }
        $fromX = [Math]::Max([double]$element.from[0], $minimumX)
        $toX = [Math]::Min([double]$element.to[0], $maximumX)
        $fromZ = [Math]::Max([double]$element.from[2], $minimumZ)
        $toZ = [Math]::Min([double]$element.to[2], $maximumZ)
        $fromY = [Math]::Max([double]$element.from[1], -16.0)
        $toY = [Math]::Min([double]$element.to[1], 32.0)
        if ($toX -le $fromX -or $toY -le $fromY -or $toZ -le $fromZ) { continue }
        $clone = Copy-JsonValue $element
        $clone.from = @($fromX, $fromY, $fromZ)
        $clone.to = @($toX, $toY, $toZ)
        $result.Add($clone)
    }
    return @($result)
}

function Export-SiloBlocks {
    $model = Read-BlockbenchModel -Id 'missile_silo'
    $staticModel = Copy-JsonValue $model
    $staticModel.elements = @(Get-GroupElements -Model $model -GroupName 'foundation')
    $parts = [ordered]@{
        north_west = @(-1, -1); north = @(0, -1); north_east = @(1, -1)
        west = @(-1, 0); center = @(0, 0); east = @(1, 0)
        south_west = @(-1, 1); south = @(0, 1); south_east = @(1, 1)
    }
    $outerParts = [ordered]@{
        outer_north_west=@(-2,-2);outer_north_inner_west=@(-1,-2);outer_north=@(0,-2);outer_north_inner_east=@(1,-2);outer_north_east=@(2,-2)
        outer_west_north=@(-2,-1);outer_east_north=@(2,-1);outer_west=@(-2,0);outer_east=@(2,0)
        outer_west_south=@(-2,1);outer_east_south=@(2,1)
        outer_south_west=@(-2,2);outer_south_inner_west=@(-1,2);outer_south=@(0,2);outer_south_inner_east=@(1,2);outer_south_east=@(2,2)
    }
    foreach ($entry in $parts.GetEnumerator()) {
        $cellX = [int]$entry.Value[0]
        $cellZ = [int]$entry.Value[1]
        $elements = @(Clip-SiloElements -Model $staticModel -CellX $cellX -CellZ $cellZ)
        $offset = @((8.0 - ($cellX * 16.0)), 0.0, (8.0 - ($cellZ * 16.0)))
        $output = New-ModelObject -Model $model -Elements $elements -Scale 1.0 -Offset $offset
        Write-JsonFile -Path (Join-Path $ResourceRoot "models\block\missile_silo_$($entry.Key).json") -Value $output
    }

    $variants = [ordered]@{}
    $facings = [ordered]@{ north = 0; east = 90; south = 180; west = 270 }
    $largeModel=Read-BlockbenchModel -Id 'missile_silo_large'
    $largeStatic=Copy-JsonValue $largeModel
    $largeStatic.elements=@(Get-GroupElements -Model $largeModel -GroupName 'foundation')
    $allParts=[ordered]@{}
    foreach($entry in $parts.GetEnumerator()){$allParts[$entry.Key]=$entry.Value}
    foreach($entry in $outerParts.GetEnumerator()){$allParts[$entry.Key]=$entry.Value}
    Export-EmptyBlockModel -TargetId 'missile_silo_unused'
    foreach($entry in $allParts.GetEnumerator()){
        $cellX=[int]$entry.Value[0];$cellZ=[int]$entry.Value[1]
        $elements=@(Clip-SiloElements -Model $largeStatic -CellX $cellX -CellZ $cellZ)
        $offset=@((8.0-$cellX*16.0),0.0,(8.0-$cellZ*16.0))
        $output=New-ModelObject -Model $largeModel -Elements $elements -Scale 1.0 -Offset $offset
        Write-JsonFile -Path (Join-Path $ResourceRoot "models\block\missile_silo_large_$($entry.Key).json") -Value $output
        foreach($facing in $facings.GetEnumerator()){
            $apply=[ordered]@{model="war_mod:block/missile_silo_large_$($entry.Key)"}
            if($facing.Value -ne 0){$apply.y=$facing.Value}
            $variants["part=$($entry.Key),facing=$($facing.Key),large=true"]=$apply
            if($outerParts.Contains($entry.Key)){
                $variants["part=$($entry.Key),facing=$($facing.Key),large=false"]=[ordered]@{model='war_mod:block/missile_silo_unused'}
            }
        }
    }
    foreach ($part in $parts.Keys) {
        foreach ($facing in $facings.GetEnumerator()) {
            $apply = [ordered]@{ model = "war_mod:block/missile_silo_$part" }
            if ($facing.Value -ne 0) { $apply.y = $facing.Value }
            $variants["part=$part,facing=$($facing.Key),large=false"] = $apply
        }
    }
    Write-JsonFile -Path (Join-Path $ResourceRoot 'blockstates\missile_silo.json') -Value ([ordered]@{ variants = $variants })
}

function Format-JavaFloat {
    param([double]$Value)
    if ([Math]::Abs($Value) -lt 0.0000001) { $Value = 0.0 }
    return [string]::Format($Invariant, '{0:0.#####}F', $Value)
}

function Get-JavaCubeLine {
    param([object]$Element, [hashtable]$Palette)
    $textureId = [string]$Element.faces.north.texture
    $colour = $Palette[$textureId]
    $values = @()
    $values += @($Element.from | ForEach-Object { Format-JavaFloat ([double]$_) })
    $values += @($Element.to | ForEach-Object { Format-JavaFloat ([double]$_) })
    $values += @($Element.origin | ForEach-Object { Format-JavaFloat ([double]$_) })
    $rotation = @(Get-ElementRotation -Element $Element)
    $values += @($rotation | ForEach-Object { Format-JavaFloat ([double]$_) })
    $values += @([string]$colour.red, [string]$colour.green, [string]$colour.blue,
        [string]$colour.materialIndex, ([string]([bool]$colour.emissive)).ToLowerInvariant())
    return '        new Cube(' + ($values -join ', ') + ')'
}

function Export-JavaMeshes {
    $definitions = [ordered]@{}
    foreach ($id in @('pistol_bullet', 'rifle_bullet', 'sniper_bullet', 'falling_warhead', 'artillery_shell', 'he_rocket', 'anti_air_missile_mk1', 'anti_air_missile_mk2')) {
        $definitions[$id.ToUpperInvariant()] = [pscustomobject]@{ model = (Read-BlockbenchModel -Id $id); elements = $null }
        $definitions[$id.ToUpperInvariant()].elements = @($definitions[$id.ToUpperInvariant()].model.elements)
    }

    foreach ($entry in @($script:MissileManifest)) {
        $id = [string]$entry.id
        $model = Read-MissileBlockbenchModel -Id $id
        $definitions[$id.ToUpperInvariant()] = [pscustomobject]@{ model = $model; elements = @($model.elements) }
    }

    $artillery = Read-BlockbenchModel -Id 'artillery_cannon'
    $definitions.ARTILLERY_FIXED = [pscustomobject]@{ model = $artillery; elements = @(Get-GroupElements -Model $artillery -GroupName 'fixed_base') }
    $definitions.ARTILLERY_YAW = [pscustomobject]@{ model = $artillery; elements = @(Get-GroupElements -Model $artillery -GroupName 'yaw_turret' -DirectOnly) }
    $definitions.ARTILLERY_PITCH = [pscustomobject]@{ model = $artillery; elements = @(Get-GroupElements -Model $artillery -GroupName 'pitch_barrel') }

    $radar = Read-BlockbenchModel -Id 'radar_station'
    $definitions.RADAR_YAW = [pscustomobject]@{ model = $radar; elements = @(Get-GroupElements -Model $radar -GroupName 'yaw_head' -DirectOnly) }
    $definitions.RADAR_PITCH = [pscustomobject]@{ model = $radar; elements = @(Get-GroupElements -Model $radar -GroupName 'pitch_dish') }

    $silo = Read-BlockbenchModel -Id 'missile_silo'
    $definitions.SILO_DOOR_LEFT = [pscustomobject]@{ model = $silo; elements = @(Get-GroupElements -Model $silo -GroupName 'left_door') }
    $definitions.SILO_DOOR_RIGHT = [pscustomobject]@{ model = $silo; elements = @(Get-GroupElements -Model $silo -GroupName 'right_door') }
    $largeSilo = Read-BlockbenchModel -Id 'missile_silo_large'
    $definitions.SILO_LARGE_DOOR_LEFT = [pscustomobject]@{ model = $largeSilo; elements = @(Get-GroupElements -Model $largeSilo -GroupName 'left_door') }
    $definitions.SILO_LARGE_DOOR_RIGHT = [pscustomobject]@{ model = $largeSilo; elements = @(Get-GroupElements -Model $largeSilo -GroupName 'right_door') }

    $builder = [Text.StringBuilder]::new()
    [void]$builder.AppendLine('package com.andye.warmod.client.model;')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('import com.mojang.blaze3d.vertex.PoseStack;')
    [void]$builder.AppendLine('import com.mojang.blaze3d.vertex.VertexConsumer;')
    [void]$builder.AppendLine('import net.minecraft.client.renderer.texture.OverlayTexture;')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('/** Generated from tools/visuals/blockbench/gameplay_catalog. Do not hand-edit. */')
    [void]$builder.AppendLine('public final class BlockbenchGameplayMeshes {')
    [void]$builder.AppendLine('    public enum Model { ' + (($definitions.Keys | ForEach-Object { [string]$_ }) -join ', ') + ' }')
    [void]$builder.AppendLine('    private BlockbenchGameplayMeshes() { }')
    [void]$builder.AppendLine()

    foreach ($entry in $definitions.GetEnumerator()) {
        $palette = Get-TexturePalette -Model $entry.Value.model
        [void]$builder.AppendLine("    private static final Cube[] $($entry.Key) = {")
        $lines = @($entry.Value.elements | ForEach-Object { Get-JavaCubeLine -Element $_ -Palette $palette })
        if ($lines.Count -gt 0) { [void]$builder.AppendLine(($lines -join ",`n")) }
        [void]$builder.AppendLine('    };')
        [void]$builder.AppendLine()
    }

    [void]$builder.AppendLine('    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,')
    [void]$builder.AppendLine('        final Model model, final float scale, final float originX, final float originY,')
    [void]$builder.AppendLine('        final float originZ, final int light) {')
    [void]$builder.AppendLine('        render(pose, buffer, model, scale, originX, originY, originZ, light, 255);')
    [void]$builder.AppendLine('    }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('    public static void renderQuarter(final PoseStack.Pose pose, final VertexConsumer buffer,')
    [void]$builder.AppendLine('        final Model model, final float scale, final float originX, final float originY,')
    [void]$builder.AppendLine('        final float originZ, final int light, final int alpha,')
    [void]$builder.AppendLine('        final float minimumCenterY, final float maximumCenterY, final int quarter) {')
    [void]$builder.AppendLine('        if (quarter < 0 || quarter > 3) throw new IllegalArgumentException("quarter must be 0..3");')
    [void]$builder.AppendLine('        for (Cube cube : cubes(model)) {')
    [void]$builder.AppendLine('            float centerY = (cube.y0 + cube.y1) * 0.5F;')
    [void]$builder.AppendLine('            if (centerY >= minimumCenterY && centerY <= maximumCenterY)')
    [void]$builder.AppendLine('                cube.renderQuarter(pose, buffer, scale, originX, originY, originZ, light, alpha, quarter);')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine('    }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,')
    [void]$builder.AppendLine('        final Model model, final float scale, final float originX, final float originY,')
    [void]$builder.AppendLine('        final float originZ, final int light, final int alpha) {')
    [void]$builder.AppendLine('        render(pose, buffer, model, scale, originX, originY, originZ, light, alpha,')
    [void]$builder.AppendLine('            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);')
    [void]$builder.AppendLine('    }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,')
    [void]$builder.AppendLine('        final Model model, final float scale, final float originX, final float originY,')
    [void]$builder.AppendLine('        final float originZ, final int light, final int alpha,')
    [void]$builder.AppendLine('        final float minimumCenterY, final float maximumCenterY) {')
    [void]$builder.AppendLine('        for (Cube cube : cubes(model)) {')
    [void]$builder.AppendLine('            float centerY = (cube.y0 + cube.y1) * 0.5F;')
    [void]$builder.AppendLine('            if (centerY >= minimumCenterY && centerY <= maximumCenterY)')
    [void]$builder.AppendLine('                cube.render(pose, buffer, scale, originX, originY, originZ, light, alpha);')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine('    }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('    private static Cube[] cubes(final Model model) {')
    [void]$builder.AppendLine('        return switch (model) {')
    foreach ($key in $definitions.Keys) {
        [void]$builder.AppendLine("            case $key -> $key;")
    }
    [void]$builder.AppendLine('        };')
    [void]$builder.AppendLine('    }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('    private static final class Cube {')
    [void]$builder.AppendLine('        private final float x0, y0, z0, x1, y1, z1, ox, oy, oz;')
    [void]$builder.AppendLine('        private final float sinX, cosX, sinY, cosY, sinZ, cosZ;')
    [void]$builder.AppendLine('        private final int red, green, blue, materialIndex;')
    [void]$builder.AppendLine('        private final boolean emissive;')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('        private Cube(float x0, float y0, float z0, float x1, float y1, float z1,')
    [void]$builder.AppendLine('            float ox, float oy, float oz, float rotationX, float rotationY, float rotationZ,')
    [void]$builder.AppendLine('            int red, int green, int blue, int materialIndex, boolean emissive) {')
    [void]$builder.AppendLine('            this.x0=x0; this.y0=y0; this.z0=z0; this.x1=x1; this.y1=y1; this.z1=z1;')
    [void]$builder.AppendLine('            this.ox=ox; this.oy=oy; this.oz=oz;')
    [void]$builder.AppendLine('            float rx=(float)Math.toRadians(rotationX), ry=(float)Math.toRadians(rotationY), rz=(float)Math.toRadians(rotationZ);')
    [void]$builder.AppendLine('            sinX=(float)Math.sin(rx); cosX=(float)Math.cos(rx);')
    [void]$builder.AppendLine('            sinY=(float)Math.sin(ry); cosY=(float)Math.cos(ry);')
    [void]$builder.AppendLine('            sinZ=(float)Math.sin(rz); cosZ=(float)Math.cos(rz);')
    [void]$builder.AppendLine('            this.red=red; this.green=green; this.blue=blue; this.materialIndex=materialIndex; this.emissive=emissive;')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('        private void render(PoseStack.Pose pose, VertexConsumer buffer, float scale,')
    [void]$builder.AppendLine('            float originX, float originY, float originZ, int light, int alpha) {')
    [void]$builder.AppendLine('            renderBounds(pose,buffer,x0,y0,z0,x1,y1,z1,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('        private void renderQuarter(PoseStack.Pose pose, VertexConsumer buffer, float scale,')
    [void]$builder.AppendLine('            float originX, float originY, float originZ, int light, int alpha, int quarter) {')
    [void]$builder.AppendLine('            boolean positiveX = quarter == 0 || quarter == 3;')
    [void]$builder.AppendLine('            boolean positiveZ = quarter == 0 || quarter == 1;')
    [void]$builder.AppendLine('            float qx0=positiveX?Math.max(x0,0):x0, qx1=positiveX?x1:Math.min(x1,0);')
    [void]$builder.AppendLine('            float qz0=positiveZ?Math.max(z0,0):z0, qz1=positiveZ?z1:Math.min(z1,0);')
    [void]$builder.AppendLine('            if (qx1 <= qx0 || qz1 <= qz0) return;')
    [void]$builder.AppendLine('            renderBounds(pose,buffer,qx0,y0,qz0,qx1,y1,qz1,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('        private void renderBounds(PoseStack.Pose pose, VertexConsumer buffer,')
    [void]$builder.AppendLine('            float bx0,float by0,float bz0,float bx1,float by1,float bz1,float scale,')
    [void]$builder.AppendLine('            float originX,float originY,float originZ,int light,int alpha) {')
    [void]$builder.AppendLine('            quad(pose,buffer,bx0,by0,bz0,bx1,by0,bz0,bx1,by1,bz0,bx0,by1,bz0,0,0,-1,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('            quad(pose,buffer,bx1,by0,bz1,bx0,by0,bz1,bx0,by1,bz1,bx1,by1,bz1,0,0,1,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('            quad(pose,buffer,bx0,by0,bz1,bx0,by0,bz0,bx0,by1,bz0,bx0,by1,bz1,-1,0,0,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('            quad(pose,buffer,bx1,by0,bz0,bx1,by0,bz1,bx1,by1,bz1,bx1,by1,bz0,1,0,0,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('            quad(pose,buffer,bx0,by1,bz0,bx1,by1,bz0,bx1,by1,bz1,bx0,by1,bz1,0,1,0,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('            quad(pose,buffer,bx0,by0,bz1,bx1,by0,bz1,bx1,by0,bz0,bx0,by0,bz0,0,-1,0,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('        private void quad(PoseStack.Pose pose, VertexConsumer buffer,')
    [void]$builder.AppendLine('            float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,')
    [void]$builder.AppendLine('            float nx,float ny,float nz,float scale,float originX,float originY,float originZ,int light,int alpha) {')
    [void]$builder.AppendLine('            float inset=0.004F, tile=1F/3F;')
    [void]$builder.AppendLine('            float u0=(materialIndex%3)*tile+inset, v0=(materialIndex/3)*tile+inset;')
    [void]$builder.AppendLine('            float u1=(materialIndex%3+1)*tile-inset, v1=(materialIndex/3+1)*tile-inset;')
    [void]$builder.AppendLine('            vertex(pose,buffer,ax,ay,az,nx,ny,nz,u0,v1,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('            vertex(pose,buffer,bx,by,bz,nx,ny,nz,u1,v1,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('            vertex(pose,buffer,cx,cy,cz,nx,ny,nz,u1,v0,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('            vertex(pose,buffer,dx,dy,dz,nx,ny,nz,u0,v0,scale,originX,originY,originZ,light,alpha);')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('        private void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x,float y,float z,')
    [void]$builder.AppendLine('            float nx,float ny,float nz,float u,float v,float scale,float originX,float originY,float originZ,int light,int alpha) {')
    [void]$builder.AppendLine('            float px=x-ox, py=y-oy, pz=z-oz;')
    [void]$builder.AppendLine('            float py1=py*cosX-pz*sinX, pz1=py*sinX+pz*cosX;')
    [void]$builder.AppendLine('            float px2=px*cosY+pz1*sinY, pz2=-px*sinY+pz1*cosY;')
    [void]$builder.AppendLine('            float px3=px2*cosZ-py1*sinZ, py3=px2*sinZ+py1*cosZ;')
    [void]$builder.AppendLine('            float nny=ny*cosX-nz*sinX, nnz=ny*sinX+nz*cosX;')
    [void]$builder.AppendLine('            float nnx2=nx*cosY+nnz*sinY, nnz2=-nx*sinY+nnz*cosY;')
    [void]$builder.AppendLine('            float nnx=nnx2*cosZ-nny*sinZ, nny2=nnx2*sinZ+nny*cosZ;')
    [void]$builder.AppendLine('            buffer.addVertex(pose,(px3+ox-originX)*scale,(py3+oy-originY)*scale,(pz2+oz-originZ)*scale)')
    [void]$builder.AppendLine('                .setColor(red,green,blue,alpha).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)')
    [void]$builder.AppendLine('                .setLight(emissive?0xF000F0:light).setNormal(pose,nnx,nny2,nnz2);')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine('    }')
    [void]$builder.AppendLine('}')

    $target = Join-Path $ClientSourceRoot 'com\andye\warmod\client\model\BlockbenchGameplayMeshes.java'
    Write-TextFile -Path $target -Content $builder.ToString().TrimEnd()
}

function Export-LegacySupportBlocks {
    foreach ($tier in 1..3) {
        $id = "missile_silo_guidance_support_tier_$tier"
        $model = Read-BlockbenchModel -Id $id
        $lowerElements = [Collections.Generic.List[object]]::new()
        $upperElements = [Collections.Generic.List[object]]::new()
        foreach ($element in @($model.elements)) {
            if ([double]$element.to[1] -le 32.0) {
                $lowerElements.Add($element)
                continue
            }
            # Preserve the old tower silhouette: vanilla rejects coordinates
            # above 32, so overflow is drawn from the existing upper block.
            $rotation = @(Get-ElementRotation -Element $element)
            if (@($rotation | Where-Object { [Math]::Abs([double]$_) -gt 0.001 }).Count -gt 0) {
                throw "Rotated legacy support element '$($element.name)' needs a geometric split."
            }
            if ([double]$element.from[1] -lt 32.0) {
                $lower = Copy-JsonValue $element
                $lower.to[1] = 32.0
                $lowerElements.Add($lower)
            }
            $upper = Copy-JsonValue $element
            $upper.from[1] = [Math]::Max([double]$upper.from[1], 32.0)
            if ([double]$upper.to[1] -gt 48.0) {
                throw "Legacy support '$id' extends beyond its two retained blocks."
            }
            $upperElements.Add($upper)
        }
        $lower = New-ModelObject -Model $model -Elements @($lowerElements) -Scale 1.0 -Offset @(16.0, 0.0, 16.0)
        # The upper support block sits exactly one block above the lower one.
        $upper = New-ModelObject -Model $model -Elements @($upperElements) -Scale 1.0 -Offset @(16.0, -16.0, 16.0)
        foreach ($part in @('front_lower', 'rear_lower')) {
            Write-JsonFile -Path (Join-Path $ResourceRoot "models\block\guidance_tier_${tier}_$part.json") -Value $lower
        }
        foreach ($part in @('front_upper', 'rear_upper')) {
            if ($upperElements.Count -eq 0) {
                Export-EmptyBlockModel -TargetId "guidance_tier_${tier}_$part"
            } else {
                Write-JsonFile -Path (Join-Path $ResourceRoot "models\block\guidance_tier_${tier}_$part.json") -Value $upper
            }
        }
    }
}

$Manifest = Get-Content -Raw -LiteralPath $ManifestPath | ConvertFrom-Json
$MissileManifest = Get-Content -Raw -LiteralPath $MissileManifestPath | ConvertFrom-Json
if ($LegacySupportsOnly) {
    Export-LegacySupportBlocks
    Write-Output 'Exported legacy guidance support blocks with bounded upper overflow.'
    return
}

$dynamicOnly = @('pistol_bullet', 'rifle_bullet', 'sniper_bullet', 'falling_warhead', 'artillery_shell')
foreach ($entry in @($Manifest)) {
    $id = [string]$entry.id
    if ($dynamicOnly -contains $id) { continue }
    if ($id -like '*_tnt') { continue }
    if ($id -eq 'missile_silo_large') { continue }
    if ($id -eq 'missile_silo') { Export-ItemModel -SourceId 'missile_silo_large' -TargetId 'missile_silo'; continue }
    if ($id -eq 'artillery_cannon') { Export-ItemModel -SourceId $id -TargetId 'artillery_cannon_inventory' }
    else { Export-ItemModel -SourceId $id }
    if(([string]$entry.category).StartsWith('components') -or $id -eq 'missile_workbench' -or $id -eq 'launch_controller'){
        Write-JsonFile -Path (Join-Path $ResourceRoot "items\$id.json") -Value ([ordered]@{
            model=[ordered]@{type='minecraft:model';model="war_mod:item/$id"}
        })
    }
}

foreach ($entry in @($MissileManifest)) {
    $id = [string]$entry.id
    Export-ItemModelObject -Model (Read-MissileBlockbenchModel -Id $id) -TargetId $id
}

$legacyMissileAliases = [ordered]@{
    conventional_icbm = 'conventional_missile'
    conventional_cluster_icbm = 'conventional_cluster_missile'
    nuclear_icbm = 'strategic_nuclear_missile'
    nuclear_cluster_icbm = 'strategic_nuclear_cluster_missile'
}
foreach ($alias in $legacyMissileAliases.GetEnumerator()) {
    Export-ItemModelObject -Model (Read-MissileBlockbenchModel -Id ([string]$alias.Value)) -TargetId ([string]$alias.Key)
}

foreach ($entry in @($Manifest | Where-Object { ([string]$_.id) -like '*_tnt' })) {
    $id = [string]$entry.id
    Export-BlockModel -SourceId $id -Offset @(8.0, 8.0, 8.0)
    # Fit the complete detonator/cluster profile inside an inventory cell,
    # independently of the full-sized placed block.
    Export-ItemModel -SourceId $id
}

Export-LegacySupportBlocks

$supportRotations = [ordered]@{
    front = [ordered]@{
        north = [ordered]@{ left = 0; right = 90 }
        east = [ordered]@{ left = 90; right = 180 }
        south = [ordered]@{ left = 180; right = 270 }
        west = [ordered]@{ left = 270; right = 0 }
    }
    rear = [ordered]@{
        north = [ordered]@{ left = 270; right = 180 }
        east = [ordered]@{ left = 0; right = 270 }
        south = [ordered]@{ left = 90; right = 0 }
        west = [ordered]@{ left = 180; right = 90 }
    }
}
$supportMultipart = [Collections.Generic.List[object]]::new()
foreach ($tier in 1..3) {
    foreach ($longitudinal in @('front', 'rear')) {
        foreach ($height in @('lower', 'upper')) {
            $part = "${longitudinal}_${height}"
            foreach ($facing in @('north', 'east', 'south', 'west')) {
                foreach ($side in @('left', 'right')) {
                    $apply = [ordered]@{ model = "war_mod:block/guidance_tier_${tier}_$part" }
                    $rotation = [int]$supportRotations[$longitudinal][$facing][$side]
                    if ($rotation -ne 0) { $apply.y = $rotation }
                    $supportMultipart.Add([pscustomobject][ordered]@{
                        when = [ordered]@{ tier = [string]$tier; part = $part; facing = $facing; side = $side }
                        apply = $apply
                    })
                }
            }
        }
    }
}
Write-JsonFile -Path (Join-Path $ResourceRoot 'blockstates\missile_silo_guidance_support.json') -Value ([ordered]@{
    multipart = @($supportMultipart)
})

$radarModel = Read-BlockbenchModel -Id 'radar_station'
$radarFixed = @(Get-GroupElements -Model $radarModel -GroupName 'fixed_foundation')
$radarScaleInModelUnits = 44.0 / 28.0
$radarBlock = New-ModelObject -Model $radarModel -Elements $radarFixed -Scale $radarScaleInModelUnits -Offset @(8.0, 0.0, 8.0)
Write-JsonFile -Path (Join-Path $ResourceRoot 'models\block\radar_station_base.json') -Value $radarBlock
foreach ($name in @('radar_station_mast', 'radar_station_support', 'radar_station_clearance')) { Export-EmptyBlockModel -TargetId $name }
Write-JsonFile -Path (Join-Path $ResourceRoot 'blockstates\radar_station.json') -Value ([ordered]@{
    multipart = @([ordered]@{
        when = [ordered]@{ part = 'bottom_center' }
        apply = [ordered]@{ model = 'war_mod:block/radar_station_base' }
    })
})

Export-BlockModel -SourceId 'radar_display_panel' -Offset @(8.0, 8.0, 8.0)
if(@($Manifest | Where-Object id -eq 'launch_controller').Count -gt 0){
    Export-BlockModel -SourceId 'launch_controller' -Offset @(8.0, 8.0, 8.0)
    $controllerVariants = [ordered]@{
        'facing=north' = [ordered]@{ model = 'war_mod:block/launch_controller' }
        'facing=east' = [ordered]@{ model = 'war_mod:block/launch_controller'; y = 90 }
        'facing=south' = [ordered]@{ model = 'war_mod:block/launch_controller'; y = 180 }
        'facing=west' = [ordered]@{ model = 'war_mod:block/launch_controller'; y = 270 }
    }
    Write-JsonFile -Path (Join-Path $ResourceRoot 'blockstates\launch_controller.json') -Value ([ordered]@{ variants = $controllerVariants })
}
if(@($Manifest | Where-Object id -eq 'missile_workbench').Count -gt 0){
    Export-WorkbenchBlocks
}
Export-EmptyBlockModel -TargetId 'artillery_cannon'
Export-SiloBlocks
Export-AdditionalMaterialTextures
Export-DynamicMaterialAtlas
Export-JavaMeshes

Write-Output "Exported $(@($Manifest).Count) gameplay catalogue entries and $(@($MissileManifest).Count) strategic missile entries into runtime models, textures, and generated client meshes."
