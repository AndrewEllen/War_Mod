[CmdletBinding()]
param(
    [string]$CatalogRoot = (Join-Path $PSScriptRoot 'gameplay_catalog'),
    [string]$ResourceRoot = (Join-Path $PSScriptRoot '..\..\..\src\main\resources\assets\war_mod'),
    [string]$ClientSourceRoot = (Join-Path $PSScriptRoot '..\..\..\src\client\java')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

try {
    Add-Type -AssemblyName System.Drawing.Common -ErrorAction Stop
} catch {
    Add-Type -AssemblyName System.Drawing -ErrorAction Stop
}

$CatalogRoot = [IO.Path]::GetFullPath($CatalogRoot)
$ResourceRoot = [IO.Path]::GetFullPath($ResourceRoot)
$ClientSourceRoot = [IO.Path]::GetFullPath($ClientSourceRoot)
$ManifestPath = Join-Path $CatalogRoot 'gameplay_model_manifest.json'
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

function Read-BlockbenchModel {
    param([string]$Id)
    $entry = $script:Manifest | Where-Object id -eq $Id | Select-Object -First 1
    if ($null -eq $entry) { throw "No Blockbench model is registered for '$Id'." }
    $path = Join-Path $CatalogRoot ([string]$entry.model)
    return Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
}

function Get-TexturePalette {
    param([object]$Model)
    $palette = @{}
    $paletteDirectory = Join-Path $ResourceRoot 'textures\blockbench_palette'
    [IO.Directory]::CreateDirectory($paletteDirectory) | Out-Null
    $whitePath = Join-Path $paletteDirectory 'ffffffff.png'
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
                $colour = $bitmap.GetPixel(0, 0)
            } finally {
                $bitmap.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
        $key = '{0:x2}{1:x2}{2:x2}{3:x2}' -f $colour.R, $colour.G, $colour.B, $colour.A
        $pngPath = Join-Path $paletteDirectory ($key + '.png')
        [IO.File]::WriteAllBytes($pngPath, $bytes)
        $palette[[string]$texture.id] = [pscustomobject]@{
            resource = "war_mod:blockbench_palette/$key"
            red = [int]$colour.R
            green = [int]$colour.G
            blue = [int]$colour.B
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
        [object]$Display = $null
    )
    $palette = Get-TexturePalette -Model $Model
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
    foreach ($id in $usedIds) { $textures["t$id"] = $palette[$id].resource }
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

function Export-ItemModel {
    param([string]$SourceId, [string]$TargetId = $SourceId)
    $model = Read-BlockbenchModel -Id $SourceId
    $fit = Get-AutoFitTransform -Elements @($model.elements)
    $display = Get-ExistingDisplay $TargetId
    if ($null -eq $display) { $display = Get-DefaultItemDisplay }
    $output = New-ModelObject -Model $model -Elements @($model.elements) -Scale $fit.scale -Offset $fit.offset -Display $display
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

function Export-EmptyBlockModel {
    param([string]$TargetId, [string]$Particle = 'war_mod:blockbench_palette/20292bff')
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
    $parts = [ordered]@{
        north_west = @(-1, -1); north = @(0, -1); north_east = @(1, -1)
        west = @(-1, 0); center = @(0, 0); east = @(1, 0)
        south_west = @(-1, 1); south = @(0, 1); south_east = @(1, 1)
    }
    foreach ($entry in $parts.GetEnumerator()) {
        $cellX = [int]$entry.Value[0]
        $cellZ = [int]$entry.Value[1]
        $elements = @(Clip-SiloElements -Model $model -CellX $cellX -CellZ $cellZ)
        $offset = @((8.0 - ($cellX * 16.0)), 0.0, (8.0 - ($cellZ * 16.0)))
        $output = New-ModelObject -Model $model -Elements $elements -Scale 1.0 -Offset $offset
        Write-JsonFile -Path (Join-Path $ResourceRoot "models\block\missile_silo_$($entry.Key).json") -Value $output
    }

    $variants = [ordered]@{}
    $facings = [ordered]@{ north = 0; east = 90; south = 180; west = 270 }
    foreach ($part in $parts.Keys) {
        foreach ($facing in $facings.GetEnumerator()) {
            $apply = [ordered]@{ model = "war_mod:block/missile_silo_$part" }
            if ($facing.Value -ne 0) { $apply.y = $facing.Value }
            $variants["part=$part,facing=$($facing.Key)"] = $apply
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
    $values += @([string]$colour.red, [string]$colour.green, [string]$colour.blue, ([string]([bool]$colour.emissive)).ToLowerInvariant())
    return '        new Cube(' + ($values -join ', ') + ')'
}

function Export-JavaMeshes {
    $definitions = [ordered]@{}
    foreach ($id in @('pistol_bullet', 'rifle_bullet', 'sniper_bullet', 'falling_warhead', 'artillery_shell', 'he_rocket', 'anti_air_missile_mk1', 'anti_air_missile_mk2')) {
        $definitions[$id.ToUpperInvariant()] = [pscustomobject]@{ model = (Read-BlockbenchModel -Id $id); elements = $null }
        $definitions[$id.ToUpperInvariant()].elements = @($definitions[$id.ToUpperInvariant()].model.elements)
    }

    $artillery = Read-BlockbenchModel -Id 'artillery_cannon'
    $definitions.ARTILLERY_FIXED = [pscustomobject]@{ model = $artillery; elements = @(Get-GroupElements -Model $artillery -GroupName 'fixed_base') }
    $definitions.ARTILLERY_YAW = [pscustomobject]@{ model = $artillery; elements = @(Get-GroupElements -Model $artillery -GroupName 'yaw_turret' -DirectOnly) }
    $definitions.ARTILLERY_PITCH = [pscustomobject]@{ model = $artillery; elements = @(Get-GroupElements -Model $artillery -GroupName 'pitch_barrel') }

    $radar = Read-BlockbenchModel -Id 'radar_station'
    $definitions.RADAR_YAW = [pscustomobject]@{ model = $radar; elements = @(Get-GroupElements -Model $radar -GroupName 'yaw_head' -DirectOnly) }
    $definitions.RADAR_PITCH = [pscustomobject]@{ model = $radar; elements = @(Get-GroupElements -Model $radar -GroupName 'pitch_dish') }

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
    [void]$builder.AppendLine('        for (Cube cube : cubes(model)) cube.render(pose, buffer, scale, originX, originY, originZ, light);')
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
    [void]$builder.AppendLine('        private final int red, green, blue;')
    [void]$builder.AppendLine('        private final boolean emissive;')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('        private Cube(float x0, float y0, float z0, float x1, float y1, float z1,')
    [void]$builder.AppendLine('            float ox, float oy, float oz, float rotationX, float rotationY, float rotationZ,')
    [void]$builder.AppendLine('            int red, int green, int blue, boolean emissive) {')
    [void]$builder.AppendLine('            this.x0=x0; this.y0=y0; this.z0=z0; this.x1=x1; this.y1=y1; this.z1=z1;')
    [void]$builder.AppendLine('            this.ox=ox; this.oy=oy; this.oz=oz;')
    [void]$builder.AppendLine('            float rx=(float)Math.toRadians(rotationX), ry=(float)Math.toRadians(rotationY), rz=(float)Math.toRadians(rotationZ);')
    [void]$builder.AppendLine('            sinX=(float)Math.sin(rx); cosX=(float)Math.cos(rx);')
    [void]$builder.AppendLine('            sinY=(float)Math.sin(ry); cosY=(float)Math.cos(ry);')
    [void]$builder.AppendLine('            sinZ=(float)Math.sin(rz); cosZ=(float)Math.cos(rz);')
    [void]$builder.AppendLine('            this.red=red; this.green=green; this.blue=blue; this.emissive=emissive;')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('        private void render(PoseStack.Pose pose, VertexConsumer buffer, float scale,')
    [void]$builder.AppendLine('            float originX, float originY, float originZ, int light) {')
    [void]$builder.AppendLine('            quad(pose,buffer,x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0,0,0,-1,scale,originX,originY,originZ,light);')
    [void]$builder.AppendLine('            quad(pose,buffer,x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1,0,0,1,scale,originX,originY,originZ,light);')
    [void]$builder.AppendLine('            quad(pose,buffer,x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1,-1,0,0,scale,originX,originY,originZ,light);')
    [void]$builder.AppendLine('            quad(pose,buffer,x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0,1,0,0,scale,originX,originY,originZ,light);')
    [void]$builder.AppendLine('            quad(pose,buffer,x0,y1,z0,x1,y1,z0,x1,y1,z1,x0,y1,z1,0,1,0,scale,originX,originY,originZ,light);')
    [void]$builder.AppendLine('            quad(pose,buffer,x0,y0,z1,x1,y0,z1,x1,y0,z0,x0,y0,z0,0,-1,0,scale,originX,originY,originZ,light);')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('        private void quad(PoseStack.Pose pose, VertexConsumer buffer,')
    [void]$builder.AppendLine('            float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,')
    [void]$builder.AppendLine('            float nx,float ny,float nz,float scale,float originX,float originY,float originZ,int light) {')
    [void]$builder.AppendLine('            vertex(pose,buffer,ax,ay,az,nx,ny,nz,scale,originX,originY,originZ,light);')
    [void]$builder.AppendLine('            vertex(pose,buffer,bx,by,bz,nx,ny,nz,scale,originX,originY,originZ,light);')
    [void]$builder.AppendLine('            vertex(pose,buffer,cx,cy,cz,nx,ny,nz,scale,originX,originY,originZ,light);')
    [void]$builder.AppendLine('            vertex(pose,buffer,dx,dy,dz,nx,ny,nz,scale,originX,originY,originZ,light);')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('        private void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x,float y,float z,')
    [void]$builder.AppendLine('            float nx,float ny,float nz,float scale,float originX,float originY,float originZ,int light) {')
    [void]$builder.AppendLine('            float px=x-ox, py=y-oy, pz=z-oz;')
    [void]$builder.AppendLine('            float py1=py*cosX-pz*sinX, pz1=py*sinX+pz*cosX;')
    [void]$builder.AppendLine('            float px2=px*cosY+pz1*sinY, pz2=-px*sinY+pz1*cosY;')
    [void]$builder.AppendLine('            float px3=px2*cosZ-py1*sinZ, py3=px2*sinZ+py1*cosZ;')
    [void]$builder.AppendLine('            float nny=ny*cosX-nz*sinX, nnz=ny*sinX+nz*cosX;')
    [void]$builder.AppendLine('            float nnx2=nx*cosY+nnz*sinY, nnz2=-nx*sinY+nnz*cosY;')
    [void]$builder.AppendLine('            float nnx=nnx2*cosZ-nny*sinZ, nny2=nnx2*sinZ+nny*cosZ;')
    [void]$builder.AppendLine('            buffer.addVertex(pose,(px3+ox-originX)*scale,(py3+oy-originY)*scale,(pz2+oz-originZ)*scale)')
    [void]$builder.AppendLine('                .setColor(red,green,blue,255).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY)')
    [void]$builder.AppendLine('                .setLight(emissive?0xF000F0:light).setNormal(pose,nnx,nny2,nnz2);')
    [void]$builder.AppendLine('        }')
    [void]$builder.AppendLine('    }')
    [void]$builder.AppendLine('}')

    $target = Join-Path $ClientSourceRoot 'com\andye\warmod\client\model\BlockbenchGameplayMeshes.java'
    Write-TextFile -Path $target -Content $builder.ToString().TrimEnd()
}

$Manifest = Get-Content -Raw -LiteralPath $ManifestPath | ConvertFrom-Json

$dynamicOnly = @('pistol_bullet', 'rifle_bullet', 'sniper_bullet', 'falling_warhead', 'artillery_shell')
foreach ($entry in @($Manifest)) {
    $id = [string]$entry.id
    if ($dynamicOnly -contains $id) { continue }
    if ($id -like '*_tnt') { continue }
    if ($id -eq 'artillery_cannon') { Export-ItemModel -SourceId $id -TargetId 'artillery_cannon_inventory' }
    else { Export-ItemModel -SourceId $id }
}

foreach ($entry in @($Manifest | Where-Object { ([string]$_.id) -like '*_tnt' })) {
    $id = [string]$entry.id
    Export-BlockModel -SourceId $id -Offset @(8.0, 8.0, 8.0)
    Write-JsonFile -Path (Join-Path $ResourceRoot "models\item\$id.json") -Value ([ordered]@{
        parent = "war_mod:block/$id"
        display = (Get-DefaultItemDisplay)
    })
}

foreach ($tier in 1..3) {
    $id = "missile_silo_guidance_support_tier_$tier"
    $model = Read-BlockbenchModel -Id $id
    $lower = New-ModelObject -Model $model -Elements @($model.elements) -Scale 1.0 -Offset @(8.0, 0.0, 8.0)
    foreach ($part in @('front_lower', 'rear_lower')) {
        Write-JsonFile -Path (Join-Path $ResourceRoot "models\block\guidance_tier_${tier}_$part.json") -Value $lower
    }
    foreach ($part in @('front_upper', 'rear_upper')) {
        Export-EmptyBlockModel -TargetId "guidance_tier_${tier}_$part"
    }
}

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

Export-BlockModel -SourceId 'radar_display_panel' -Offset @(8.0, 0.0, 8.0)
Export-EmptyBlockModel -TargetId 'artillery_cannon'
Export-SiloBlocks
Export-JavaMeshes

Write-Output "Exported $(@($Manifest).Count) saved Blockbench catalogue entries into runtime models, textures, and generated client meshes."
