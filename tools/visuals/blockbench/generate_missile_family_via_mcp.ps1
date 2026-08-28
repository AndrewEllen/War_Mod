param(
    [string]$Endpoint = 'http://127.0.0.1:3000/bb-mcp',
    [string]$OutputRoot = (Join-Path $PSScriptRoot 'missiles')
)

$ErrorActionPreference = 'Stop'
$script:requestId = 1

function New-McpSession {
    $initialize = @{
        jsonrpc = '2.0'
        id = $script:requestId++
        method = 'initialize'
        params = @{
            protocolVersion = '2025-06-18'
            capabilities = @{}
            clientInfo = @{ name = 'war-mod-missile-family-generator'; version = '1.0' }
        }
    } | ConvertTo-Json -Depth 20 -Compress
    $response = Invoke-WebRequest -Uri $Endpoint -Method Post -ContentType 'application/json' `
        -Headers @{ Accept = 'application/json, text/event-stream' } -Body $initialize -TimeoutSec 20
    $script:sessionId = [string]$response.Headers['Mcp-Session-Id']
    if (-not $script:sessionId) { throw 'Blockbench MCP did not return a session ID.' }
    $script:headers = @{ Accept = 'application/json, text/event-stream'; 'Mcp-Session-Id' = $script:sessionId }
    $initialized = '{"jsonrpc":"2.0","method":"notifications/initialized"}'
    Invoke-WebRequest -Uri $Endpoint -Method Post -ContentType 'application/json' `
        -Headers $script:headers -Body $initialized -TimeoutSec 20 | Out-Null
}

function Invoke-McpTool {
    param([string]$Name, [hashtable]$Arguments, [switch]$AllowError)
    $payload = @{
        jsonrpc = '2.0'
        id = $script:requestId++
        method = 'tools/call'
        params = @{ name = $Name; arguments = $Arguments }
    } | ConvertTo-Json -Depth 60 -Compress
    $result = ((Invoke-WebRequest -Uri $Endpoint -Method Post -ContentType 'application/json' `
        -Headers $script:headers -Body $payload -TimeoutSec 30).Content | ConvertFrom-Json)
    if ($result.result.isError -and -not $AllowError) {
        $message = ($result.result.content | Where-Object type -eq 'text' | ForEach-Object text) -join "`n"
        throw "Blockbench tool '$Name' failed: $message"
    }
    return $result.result.content
}

function Add-Cubes {
    param([array]$Elements, [string]$Texture, [string]$Group)
    Invoke-McpTool 'place_cube' @{
        elements = $Elements
        texture = $Texture
        group = $Group
        faces = $true
    } | Out-Null
}

function Cube {
    param([string]$Name, [double[]]$From, [double[]]$To, [double[]]$Origin = @(0,0,0), [double[]]$Rotation = @(0,0,0))
    return @{ name = $Name; from = $From; to = $To; origin = $Origin; rotation = $Rotation }
}

function Add-Group {
    param([string]$Name, [string]$Parent, [double[]]$Origin = @(0,0,0))
    Invoke-McpTool 'add_group' @{
        name = $Name; parent = $Parent; origin = $Origin; rotation = @(0,0,0); shade = $true
    } | Out-Null
}

function Add-OctagonalSection {
    param(
        [string]$Prefix, [double]$Half, [double]$Bottom, [double]$Top,
        [string]$Texture, [string]$Group, [double]$Corner = 0.58
    )
    $mid = $Half - 0.48
    Add-Cubes @(
        (Cube "${Prefix}_core" @(-$mid,$Bottom,-$mid) @($mid,$Top,$mid)),
        (Cube "${Prefix}_x_shell" @(-$Half,$Bottom,-$mid) @($Half,$Top,$mid)),
        (Cube "${Prefix}_z_shell" @(-$mid,$Bottom,-$Half) @($mid,$Top,$Half))
    ) $Texture $Group
    $cornerPos = $Half - 0.38
    $cornerHalf = $Corner / 2.0
    Add-Cubes @(
        (Cube "${Prefix}_corner_nw" @(-$cornerHalf,$Bottom,-$cornerHalf) @($cornerHalf,$Top,$cornerHalf) @(-$cornerPos,0,-$cornerPos) @(0,45,0)),
        (Cube "${Prefix}_corner_ne" @(-$cornerHalf,$Bottom,-$cornerHalf) @($cornerHalf,$Top,$cornerHalf) @($cornerPos,0,-$cornerPos) @(0,45,0)),
        (Cube "${Prefix}_corner_sw" @(-$cornerHalf,$Bottom,-$cornerHalf) @($cornerHalf,$Top,$cornerHalf) @(-$cornerPos,0,$cornerPos) @(0,45,0)),
        (Cube "${Prefix}_corner_se" @(-$cornerHalf,$Bottom,-$cornerHalf) @($cornerHalf,$Top,$cornerHalf) @($cornerPos,0,$cornerPos) @(0,45,0))
    ) $Texture $Group
}

$specs = @(
    @{ id='high_explosive'; display='High Explosive'; accent='#C85B26'; tier=0; bodyHalf=2.35; bottom=-13.0; top=8.0; nose=@(2.45,1.85,1.10); finSpan=2.0; finHeight=5.2; detail='compact' },
    @{ id='high_capacity_he'; display='High-Capacity HE'; accent='#D58725'; tier=1; bodyHalf=2.45; bottom=-14.0; top=8.5; nose=@(2.60,1.95,1.20); finSpan=2.2; finHeight=5.8; detail='shoulder_ribs' },
    @{ id='conventional'; display='Conventional'; accent='#778B55'; tier=2; bodyHalf=2.50; bottom=-15.0; top=9.0; nose=@(2.65,2.00,1.20); finSpan=2.25; finHeight=6.2; detail='balanced' },
    @{ id='heavy_conventional'; display='Heavy Conventional'; accent='#667077'; tier=3; bodyHalf=2.75; bottom=-16.0; top=9.5; nose=@(2.95,2.25,1.35); finSpan=2.55; finHeight=6.8; detail='armoured' },
    @{ id='tactical_nuclear'; display='Tactical Nuclear'; accent='#B58135'; tier=4; bodyHalf=2.50; bottom=-15.5; top=10.0; nose=@(2.70,2.05,1.15); finSpan=2.25; finHeight=6.0; detail='nuclear_canards' },
    @{ id='strategic_nuclear'; display='Strategic Nuclear'; accent='#9A7036'; tier=5; bodyHalf=2.65; bottom=-17.0; top=10.5; nose=@(2.85,2.10,1.20); finSpan=2.35; finHeight=6.5; detail='long_range' },
    @{ id='heavy_nuclear'; display='Heavy Nuclear'; accent='#86626A'; tier=6; bodyHalf=2.95; bottom=-18.0; top=11.0; nose=@(3.15,2.35,1.45); finSpan=2.75; finHeight=7.2; detail='reinforced' }
)

New-Item -ItemType Directory -Force -Path (Join-Path $OutputRoot 'single') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutputRoot 'cluster') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutputRoot 'previews\single') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutputRoot 'previews\cluster') | Out-Null

New-McpSession
$manifest = @()

foreach ($spec in $specs) {
    foreach ($cluster in @($false, $true)) {
        $suffix = if ($cluster) { '_cluster_missile' } else { '_missile' }
        $modelId = $spec.id + $suffix
        $category = if ($cluster) { 'cluster' } else { 'single' }
        $modelPath = Join-Path (Join-Path $OutputRoot $category) ($modelId + '.bbmodel')
        $previewPath = Join-Path (Join-Path $OutputRoot "previews\$category") ($modelId + '.png')

        # Blockbench 5.0 creates the free-format project before its current MCP handler
        # reports a harmless edit_mode error, so allow that one response and verify state.
        Invoke-McpTool 'create_project' @{ name=$modelId; format='generic' } -AllowError | Out-Null
        Invoke-McpTool 'risky_eval' @{ code="Project.name = '$modelId'; Project.saved = false; Project.name" } | Out-Null
        $projectInfo = Invoke-McpTool 'get_project_info' @{}
        $projectText = ($projectInfo | Where-Object type -eq 'text' | Select-Object -First 1).text | ConvertFrom-Json
        if ($projectText.format.id -ne 'free' -or $projectText.counts.outliner_elements -ne 0) {
            throw "Unexpected project state before generating $modelId"
        }
        Invoke-McpTool 'save_checkpoint' @{ name="Blank $modelId project" } | Out-Null

        $bodyTexture = "${modelId}_body"
        $bodyShadowTexture = "${modelId}_body_shadow"
        $darkTexture = "${modelId}_dark_metal"
        $accentTexture = "${modelId}_accent"
        $warningTexture = "${modelId}_cluster_warning"
        $nuclearTexture = "${modelId}_nuclear_marker"
        $tipTexture = "${modelId}_tip"
        foreach ($texture in @(
            @{name=$bodyTexture;color='#C2BFA9'},
            @{name=$bodyShadowTexture;color='#77786C'},
            @{name=$darkTexture;color='#2C3332'},
            @{name=$accentTexture;color=$spec.accent},
            @{name=$warningTexture;color='#E2AD2A'},
            @{name=$nuclearTexture;color='#EBD236'},
            @{name=$tipTexture;color=($(if ($spec.tier -ge 4) {'#A52523'} else {'#3B4140'}))}
        )) {
            Invoke-McpTool 'create_texture' @{
                name=$texture.name; width=32; height=32; fill_color=$texture.color; layer_name='base'
            } | Out-Null
        }

        Add-Group 'missile_root' 'root'
        Add-Group 'motor_body' 'missile_root'
        Add-Group 'payload_cone' 'missile_root'
        Add-Group 'flight_controls' 'missile_root'
        Add-Group 'engine' 'missile_root'
        Add-Group 'variant_details' 'missile_root'

        Add-OctagonalSection 'motor' $spec.bodyHalf $spec.bottom $spec.top $bodyTexture 'motor_body'

        $ringHalf = $spec.bodyHalf + 0.22
        $aftRing = $spec.bottom + 3.0
        $forwardRing = $spec.top - 2.0
        Add-Cubes @(
            (Cube 'aft_identification_ring' @(-$ringHalf,$aftRing,-$ringHalf) @($ringHalf,($aftRing+0.8),$ringHalf)),
            (Cube 'forward_identification_ring' @(-$ringHalf,$forwardRing,-$ringHalf) @($ringHalf,($forwardRing+0.8),$ringHalf))
        ) $accentTexture 'motor_body'

        $spine = $spec.bodyHalf + 0.10
        Add-Cubes @(
            (Cube 'service_spine_front' @(-0.28,($spec.bottom+4),(-$spine-0.18)) @(0.28,($spec.top-3),(-$spine+0.10))),
            (Cube 'service_spine_rear' @(-0.28,($spec.bottom+4),($spine-0.10)) @(0.28,($spec.top-3),($spine+0.18))),
            (Cube 'service_spine_left' @((-$spine-0.18),($spec.bottom+4),-0.28) @((-$spine+0.10),($spec.top-3),0.28)),
            (Cube 'service_spine_right' @(($spine-0.10),($spec.bottom+4),-0.28) @(($spine+0.18),($spec.top-3),0.28))
        ) $darkTexture 'motor_body'

        $nose0 = $spec.nose[0]; $nose1 = $spec.nose[1]; $nose2 = $spec.nose[2]
        $y0 = $spec.top; $y1 = $y0 + 3.0; $y2 = $y1 + 2.8; $y3 = $y2 + 2.4; $y4 = $y3 + 1.6
        Add-OctagonalSection 'payload_shoulder' $nose0 $y0 $y1 $darkTexture 'payload_cone' 0.54
        Add-OctagonalSection 'payload_mid' $nose1 $y1 $y2 $accentTexture 'payload_cone' 0.48
        Add-OctagonalSection 'payload_upper' $nose2 $y2 $y3 $bodyShadowTexture 'payload_cone' 0.42
        Add-Cubes @((Cube 'impact_fuze' @(-0.52,$y3,-0.52) @(0.52,$y4,0.52))) $tipTexture 'payload_cone'

        if ($spec.tier -ge 4) {
            Add-Cubes @(
                (Cube 'nuclear_marker_front' @(-0.62,($y0+0.8),(-$nose0-0.18)) @(0.62,($y0+2.0),(-$nose0+0.10))),
                (Cube 'nuclear_marker_rear' @(-0.62,($y0+0.8),($nose0-0.10)) @(0.62,($y0+2.0),($nose0+0.18)))
            ) $nuclearTexture 'payload_cone'
        }

        if ($cluster) {
            $canisterRadius = $nose0 + 0.48
            $canisterBottom = $y0 + 0.35
            $canisterTop = $y2 - 0.25
            Add-Cubes @(
                (Cube 'cluster_canister_front' @(-0.62,$canisterBottom,(-$canisterRadius-0.42)) @(0.62,$canisterTop,(-$canisterRadius+0.42)) @(0,$y1,0) @(-6,0,0)),
                (Cube 'cluster_canister_rear' @(-0.62,$canisterBottom,($canisterRadius-0.42)) @(0.62,$canisterTop,($canisterRadius+0.42)) @(0,$y1,0) @(6,0,0)),
                (Cube 'cluster_canister_left' @((-$canisterRadius-0.42),$canisterBottom,-0.62) @((-$canisterRadius+0.42),$canisterTop,0.62) @(0,$y1,0) @(0,0,6)),
                (Cube 'cluster_canister_right' @(($canisterRadius-0.42),$canisterBottom,-0.62) @(($canisterRadius+0.42),$canisterTop,0.62) @(0,$y1,0) @(0,0,-6))
            ) $warningTexture 'payload_cone'
            Add-Cubes @(
                (Cube 'cluster_cone_collar' @((-$nose0-0.34),($y0+0.15),(-$nose0-0.34)) @(($nose0+0.34),($y0+0.85),($nose0+0.34)))
            ) $warningTexture 'payload_cone'
        }

        $finRoot = $spec.bodyHalf - 0.10
        $finOuter = $spec.bodyHalf + $spec.finSpan
        $finBottom = $spec.bottom + 0.8
        $finTop = $finBottom + $spec.finHeight
        Add-Cubes @(
            (Cube 'fin_front' @(-0.42,$finBottom,-$finOuter) @(0.42,$finTop,-$finRoot) @(0,($finBottom+2.0),0) @(5,0,0)),
            (Cube 'fin_rear' @(-0.42,$finBottom,$finRoot) @(0.42,$finTop,$finOuter) @(0,($finBottom+2.0),0) @(-5,0,0)),
            (Cube 'fin_left' @(-$finOuter,$finBottom,-0.42) @(-$finRoot,$finTop,0.42) @(0,($finBottom+2.0),0) @(0,0,-5)),
            (Cube 'fin_right' @($finRoot,$finBottom,-0.42) @($finOuter,$finTop,0.42) @(0,($finBottom+2.0),0) @(0,0,5))
        ) $darkTexture 'flight_controls'

        $engineHalf = $spec.bodyHalf - 0.15
        Add-Cubes @(
            (Cube 'engine_mount' @(-$engineHalf,($spec.bottom-1.2),-$engineHalf) @($engineHalf,$spec.bottom,$engineHalf)),
            (Cube 'square_nozzle_outer' @(-1.65,($spec.bottom-3.0),-1.65) @(1.65,($spec.bottom-1.2),1.65)),
            (Cube 'square_nozzle_recess' @(-1.0,($spec.bottom-3.35),-1.0) @(1.0,($spec.bottom-2.9),1.0))
        ) $darkTexture 'engine'

        # Yield-specific greebles keep the family related without making silhouettes interchangeable.
        switch ($spec.detail) {
            'compact' {
                Add-Cubes @((Cube 'he_fuze_access_panel' @(-0.85,($spec.top-0.2),(-$spec.bodyHalf-0.20)) @(0.85,($spec.top+1.2),(-$spec.bodyHalf+0.08)))) $accentTexture 'variant_details'
            }
            'shoulder_ribs' {
                Add-Cubes @(
                    (Cube 'capacity_rib_left' @((-$spec.bodyHalf-0.22),($spec.top-4),-0.45) @((-$spec.bodyHalf+0.05),($spec.top-0.5),0.45)),
                    (Cube 'capacity_rib_right' @(($spec.bodyHalf-0.05),($spec.top-4),-0.45) @(($spec.bodyHalf+0.22),($spec.top-0.5),0.45))
                ) $accentTexture 'variant_details'
            }
            'balanced' {
                Add-Cubes @(
                    (Cube 'conventional_data_panel' @(($spec.bodyHalf-0.08),-1,-0.9) @(($spec.bodyHalf+0.28),3.2,0.9)),
                    (Cube 'conventional_panel_light' @(($spec.bodyHalf+0.27),0.1,-0.35) @(($spec.bodyHalf+0.40),2.1,0.35))
                ) $accentTexture 'variant_details'
            }
            'armoured' {
                Add-Cubes @(
                    (Cube 'heavy_armour_front' @(-1.3,($spec.bottom+6),(-$spec.bodyHalf-0.35)) @(1.3,($spec.top-4),(-$spec.bodyHalf+0.05))),
                    (Cube 'heavy_armour_rear' @(-1.3,($spec.bottom+6),($spec.bodyHalf-0.05)) @(1.3,($spec.top-4),($spec.bodyHalf+0.35)))
                ) $bodyShadowTexture 'variant_details'
            }
            'nuclear_canards' {
                $canardY = $spec.top - 1.0
                Add-Cubes @(
                    (Cube 'tactical_canard_left' @((-$spec.bodyHalf-1.0),$canardY,-0.28) @(-$spec.bodyHalf,($canardY+2.0),0.28)),
                    (Cube 'tactical_canard_right' @($spec.bodyHalf,$canardY,-0.28) @(($spec.bodyHalf+1.0),($canardY+2.0),0.28))
                ) $accentTexture 'variant_details'
            }
            'long_range' {
                Add-Cubes @(
                    (Cube 'guidance_box_front' @(-0.85,1.0,(-$spec.bodyHalf-0.32)) @(0.85,5.0,(-$spec.bodyHalf+0.05))),
                    (Cube 'guidance_box_rear' @(-0.85,1.0,($spec.bodyHalf-0.05)) @(0.85,5.0,($spec.bodyHalf+0.32)))
                ) $darkTexture 'variant_details'
            }
            'reinforced' {
                $middle = ($spec.bottom + $spec.top) / 2
                Add-Cubes @(
                    (Cube 'heavy_nuclear_mid_brace' @(-$ringHalf,($middle-0.55),-$ringHalf) @($ringHalf,($middle+0.55),$ringHalf)),
                    (Cube 'heavy_nuclear_payload_brace' @((-$nose0-0.25),($y1-0.45),(-$nose0-0.25)) @(($nose0+0.25),($y1+0.45),($nose0+0.25)))
                ) $accentTexture 'variant_details'
            }
        }

        Invoke-McpTool 'save_checkpoint' @{ name="Completed $modelId" } | Out-Null
        $outline = Invoke-McpTool 'list_outline' @{ include_cubes=$true; include_meshes=$true; max_depth=8 }
        $outlineData = (($outline | Where-Object type -eq 'text' | Select-Object -First 1).text | ConvertFrom-Json)
        if ($outlineData.counts.meshes -ne 0 -or $outlineData.counts.cubes -lt 30) {
            throw "Unexpected geometry counts for $modelId"
        }
        Invoke-McpTool 'export_model' @{ codec_id='project'; path=$modelPath; max_content_length=0 } | Out-Null
        Invoke-McpTool 'select_all_of_type' @{ type='cube' } | Out-Null
        Invoke-McpTool 'trigger_action' @{ action='focus_on_selection'; confirmDialog=$false } | Out-Null
        Invoke-McpTool 'risky_eval' @{ code='Outliner.selected.length = 0; true' } | Out-Null
        $shot = Invoke-McpTool 'capture_screenshot' @{}
        $image = $shot | Where-Object type -eq 'image' | Select-Object -First 1
        if (-not $image) { throw "No preview returned for $modelId" }
        [System.IO.File]::WriteAllBytes($previewPath, [Convert]::FromBase64String($image.data))

        $manifest += [pscustomobject]@{
            id = $modelId
            display_name = $spec.display + $(if ($cluster) {' Cluster Missile'} else {' Missile'})
            yield = $spec.id
            delivery = $(if ($cluster) {'cluster_four'} else {'single'})
            category = $category
            accent = $spec.accent
            nuclear = ($spec.tier -ge 4)
            cubes = [int]$outlineData.counts.cubes
            meshes = [int]$outlineData.counts.meshes
            model = ($modelPath.Substring($OutputRoot.Length + 1) -replace '\\','/')
            preview = ($previewPath.Substring($OutputRoot.Length + 1) -replace '\\','/')
        }
        Write-Host "Exported $modelId ($($outlineData.counts.cubes) cubes)"
    }
}

$manifestPath = Join-Path $OutputRoot 'missile_model_manifest.json'
$manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM
Write-Host "Wrote manifest: $manifestPath"
