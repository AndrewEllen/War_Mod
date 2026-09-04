param(
    [string]$Endpoint = 'http://127.0.0.1:3000/bb-mcp',
    [string]$OutputRoot = (Join-Path $PSScriptRoot 'gameplay_catalog'),
    [string[]]$OnlyIds = @()
)

$ErrorActionPreference = 'Stop'
$script:requestId = 1
$script:materialSourceRoot = Join-Path $PSScriptRoot 'material_sources'

try {
    Add-Type -AssemblyName System.Drawing.Common -ErrorAction Stop
} catch {
    Add-Type -AssemblyName System.Drawing -ErrorAction Stop
}

function New-McpSession {
    $body = @{
        jsonrpc  = '2.0'
        id       = $script:requestId++
        method   = 'initialize'
        params   = @{
            protocolVersion = '2025-06-18'
            capabilities    = @{}
            clientInfo      = @{name = 'war-mod-gameplay-model-catalog'; version = '2.0'}
        }
    } | ConvertTo-Json -Depth 20 -Compress

    $response = Invoke-WebRequest -Uri $Endpoint -Method Post -ContentType 'application/json' `
        -Headers @{Accept = 'application/json, text/event-stream'} -Body $body -TimeoutSec 20
    $script:sessionId = [string]$response.Headers['Mcp-Session-Id']
    if (-not $script:sessionId) { throw 'Blockbench MCP did not return a session ID.' }
    $script:headers = @{
        Accept        = 'application/json, text/event-stream'
        'Mcp-Session-Id' = $script:sessionId
    }
    Invoke-WebRequest -Uri $Endpoint -Method Post -ContentType 'application/json' -Headers $script:headers `
        -Body '{"jsonrpc":"2.0","method":"notifications/initialized"}' -TimeoutSec 20 | Out-Null
}

function Invoke-McpTool {
    param([string]$Name, [hashtable]$Arguments, [switch]$AllowError)
    $body = @{
        jsonrpc = '2.0'
        id      = $script:requestId++
        method  = 'tools/call'
        params  = @{
            name      = $Name
            arguments = $Arguments
        }
    } | ConvertTo-Json -Depth 80 -Compress
    $response = ((Invoke-WebRequest -Uri $Endpoint -Method Post -ContentType 'application/json' `
        -Headers $script:headers -Body $body -TimeoutSec 40).Content | ConvertFrom-Json)
    if ($response.result.isError -and -not $AllowError) {
        $message = ($response.result.content | Where-Object type -eq 'text' | ForEach-Object text) -join "`n"
        throw "Blockbench tool '$Name' failed: $message"
    }
    $response.result.content
}

function Add-Cube([string]$Name,[double[]]$From,[double[]]$To,[double[]]$Origin=@(0,0,0),[double[]]$Rotation=@(0,0,0)) {
    @{name=$Name;from=$From;to=$To;origin=$Origin;rotation=$Rotation}
}

function Add-BbGroup([string]$Name,[string]$Parent='root',[double[]]$Origin=@(0,0,0),[double[]]$Rotation=@(0,0,0)) {
    Invoke-McpTool add_group @{
        name=$Name;parent=$Parent;origin=$Origin;rotation=$Rotation;shade=$true
    } | Out-Null
}

function Cubes([array]$Elements,[string]$Texture,[string]$Group) {
    Invoke-McpTool place_cube @{
        elements = $Elements
        texture  = $Texture
        group    = $Group
        faces    = $true
    } | Out-Null
}

function Get-MaterialTemplate([string]$Name) {
    $file = switch -Regex ($Name) {
        'concrete' { 'concrete.png'; break }
        'shaft|recess|black' { 'shaft_black.png'; break }
        'warning' { 'warning_red.png'; break }
        'brass' { 'brass.png'; break }
        'radar_cross' { 'radar_cross.png'; break }
        'body|green|paint' { 'olive_paint.png'; break }
        'accent|blue|steel' { 'brushed_steel.png'; break }
        'stock|rubber|soot' { 'soot_metal.png'; break }
        default { 'gunmetal.png' }
    }
    $path = Join-Path $script:materialSourceRoot $file
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing material source: $path" }
    return $path
}

function New-TintedTextureData([string]$Name,[string]$Color) {
    $source = [Drawing.Bitmap]::FromFile((Get-MaterialTemplate $Name))
    $target = [Drawing.Bitmap]::new(16,16,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $tint = [Drawing.ColorTranslator]::FromHtml($Color)
    try {
        for($y=0;$y -lt 16;$y++){
            for($x=0;$x -lt 16;$x++){
                $pixel=$source.GetPixel($x,$y)
                $detail=(0.2126*$pixel.R+0.7152*$pixel.G+0.0722*$pixel.B)/255.0
                $factor=0.58+0.78*$detail
                $red=[Math]::Min(255,[Math]::Round($tint.R*$factor))
                $green=[Math]::Min(255,[Math]::Round($tint.G*$factor))
                $blue=[Math]::Min(255,[Math]::Round($tint.B*$factor))
                $target.SetPixel($x,$y,[Drawing.Color]::FromArgb(255,$red,$green,$blue))
            }
        }
        $stream=[IO.MemoryStream]::new()
        try {
            $target.Save($stream,[Drawing.Imaging.ImageFormat]::Png)
            return 'data:image/png;base64,'+[Convert]::ToBase64String($stream.ToArray())
        } finally { $stream.Dispose() }
    } finally {
        $source.Dispose()
        $target.Dispose()
    }
}

function Texture([string]$Name,[string]$Color) {
    Invoke-McpTool create_texture @{
        name       = $Name
        width      = 16
        height     = 16
        data       = New-TintedTextureData $Name $Color
    } | Out-Null
}

function Box([string]$Name,[double]$x0,[double]$y0,[double]$z0,[double]$x1,[double]$y1,[double]$z1) {
    Add-Cube $Name @($x0,$y0,$z0) @($x1,$y1,$z1)
}

function RotBox([string]$Name,[double]$x0,[double]$y0,[double]$z0,[double]$x1,[double]$y1,[double]$z1,[double[]]$Origin,[double[]]$Rotation) {
    Add-Cube $Name @($x0,$y0,$z0) @($x1,$y1,$z1) $Origin $Rotation
}

function RoundedBar([string]$Prefix,[double]$Half,[double]$y0,[double]$y1,[string]$Texture,[string]$Group,[double]$Inset=.47,[double]$CornerPad=.34) {
    $inner = $Half - $Inset
    $cornerOffset = $Half - $CornerPad
    $cornerHalf = 0.36
    Cubes @(
        (Box "${Prefix}_core" (-$inner) $y0 (-$inner) $inner $y1 $inner),
        (Box "${Prefix}_x" (-$Half) $y0 (-$inner) $Half $y1 $inner),
        (Box "${Prefix}_z" (-$inner) $y0 (-$Half) $inner $y1 $Half),
        (Add-Cube "${Prefix}_c1" @(-$cornerHalf,$y0,-$cornerHalf) @($cornerHalf,$y1,$cornerHalf) @(-$cornerOffset,0,-$cornerOffset) @(0,45,0)),
        (Add-Cube "${Prefix}_c2" @(-$cornerHalf,$y0,-$cornerHalf) @($cornerHalf,$y1,$cornerHalf) @($cornerOffset,0,-$cornerOffset) @(0,45,0)),
        (Add-Cube "${Prefix}_c3" @(-$cornerHalf,$y0,-$cornerHalf) @($cornerHalf,$y1,$cornerHalf) @(-$cornerOffset,0,$cornerOffset) @(0,45,0)),
        (Add-Cube "${Prefix}_c4" @(-$cornerHalf,$y0,-$cornerHalf) @($cornerHalf,$y1,$cornerHalf) @($cornerOffset,0,$cornerOffset) @(0,45,0))
    ) $Texture $Group
}

function Add-Block([string]$Name,[double]$x0,[double]$y0,[double]$z0,[double]$x1,[double]$y1,[double]$z1,[string]$Texture,[string]$Group) {
    Cubes @((Box $Name $x0 $y0 $z0 $x1 $y1 $z1)) $Texture $Group
}

function Build-Gun([string]$Kind,[hashtable]$T) {
    Add-BbGroup firearm_root
    Add-BbGroup receiver firearm_root
    Add-BbGroup barrel firearm_root
    Add-BbGroup furniture firearm_root
    Add-BbGroup magazine firearm_root
    Add-BbGroup optics firearm_root

    if($Kind -eq 'pistol'){
        # Compact service pistol: stepped slide, framed lower, angled grip and visible controls.
        Cubes @(
            (Box slide -5.8 0.2 -1.45 6.8 3.0 1.45),
            (Box slide_top -4.8 2.9 -1.05 5.8 3.7 1.05),
            (Box frame -4.6 -1.8 -1.25 4.8 0.5 1.25),
            (Box dust_cover 0.5 -2.4 -1.15 5.5 -1.2 1.15),
            (Box muzzle_block 5.5 -0.2 -1.28 7.2 2.3 1.28)
        ) $T.dark receiver
        Add-Block ejection_port -0.8 2.45 -1.51 2.2 3.12 -1.43 $T.brass receiver
        Add-Block takedown_pin 1.8 -0.8 -1.5 2.6 0.0 -1.3 $T.accent receiver
        Add-Block barrel_opening 6.75 0.45 -0.7 7.35 1.75 0.7 $T.stock barrel
        Cubes @(
            (RotBox grip_core -3.8 -8.6 -1.2 -0.2 -1.1 1.2 @(-2,-1.2,0) @(0,0,-10)),
            (RotBox grip_panel_l -3.55 -7.8 -1.38 -0.45 -2.2 -1.17 @(-2,-1.2,0) @(0,0,-10)),
            (RotBox grip_panel_r -3.55 -7.8 1.17 -0.45 -2.2 1.38 @(-2,-1.2,0) @(0,0,-10)),
            (RotBox magazine_floor -4.0 -9.0 -1.5 -0.1 -8.1 1.5 @(-2,-1.2,0) @(0,0,-10))
        ) $T.stock furniture
        Add-Block trigger_guard -1.4 -3.5 -0.95 1.2 -1.2 0.95 $T.body receiver
        Add-Block trigger -0.2 -3.1 -0.28 0.45 -1.4 0.28 $T.warning receiver
        Add-Block front_sight 4.9 3.65 -0.22 5.5 4.5 0.22 $T.glow optics
        Add-Block rear_sight -4.6 3.58 -0.85 -3.8 4.3 0.85 $T.body optics
        return
    }

    if($Kind -eq 'rifle'){
        # Assault rifle: compact receiver, separate handguard, curved magazine and adjustable stock.
        Cubes @(
            (Box upper -5.2 0.0 -1.35 6.0 3.0 1.35),
            (Box lower -4.2 -2.1 -1.25 4.8 0.4 1.25),
            (Box handguard 5.2 -0.6 -1.55 13.0 2.3 1.55),
            (Box handguard_top 4.8 2.25 -1.1 13.4 3.0 1.1),
            (Box rail -5.5 3.0 -0.38 13.6 3.45 0.38)
        ) $T.body receiver
        Cubes @(
            (Box gas_block 12.0 -0.2 -1.8 13.2 2.6 1.8),
            (Box barrel_main 12.8 0.55 -0.55 19.2 1.45 0.55),
            (Box flash_hider 18.8 0.2 -0.9 21.2 1.8 0.9),
            (Box muzzle_bore 21.0 0.62 -0.42 21.5 1.38 0.42)
        ) $T.dark barrel
        Cubes @(
            (Box buffer_tube -10.5 0.6 -0.65 -4.8 1.7 0.65),
            (Box stock_beam -15.8 -0.3 -1.2 -9.0 2.4 1.2),
            (Box butt_pad -17.0 -1.6 -1.7 -15.2 3.1 1.7),
            (Box cheek_riser -14.8 2.3 -1.15 -7.8 3.2 1.15)
        ) $T.stock furniture
        Cubes @(
            (RotBox grip -3.9 -8.0 -1.05 -1.0 -1.1 1.05 @(-2.4,-1.0,0) @(0,0,-14)),
            (RotBox mag_upper 0.0 -5.6 -1.35 3.8 -1.2 1.35 @(1.3,-1.3,0) @(0,0,9)),
            (RotBox mag_lower 0.6 -9.0 -1.28 4.2 -4.6 1.28 @(1.3,-1.3,0) @(0,0,9))
        ) $T.dark magazine
        Add-Block trigger_guard -1.0 -4.1 -0.85 1.2 -1.4 0.85 $T.dark receiver
        Add-Block charging_handle -5.8 2.2 -1.9 -3.9 2.8 -1.2 $T.accent receiver
        Add-Block rear_sight -4.0 3.35 -0.65 -3.1 4.5 0.65 $T.dark optics
        Add-Block front_sight 12.0 2.9 -0.35 12.8 5.0 0.35 $T.dark optics
        return
    }

    # Precision rifle: long free-float barrel, shaped stock, large optic and folding bipod.
    Cubes @(
        (Box receiver -6.5 -0.4 -1.45 5.4 2.8 1.45),
        (Box receiver_rail -6.8 2.75 -0.45 6.2 3.35 0.45),
        (Box fore_end 4.6 -0.8 -1.35 13.5 2.0 1.35),
        (Box bolt -5.3 1.3 1.3 -1.8 2.1 2.7),
        (Box bolt_knob -2.2 0.2 2.4 -1.2 1.5 3.4)
    ) $T.body receiver
    Cubes @(
        (Box barrel_heavy 12.8 0.25 -0.58 25.0 1.15 0.58),
        (Box muzzle_brake 24.3 -0.15 -1.05 28.0 1.55 1.05),
        (Box muzzle_slot_a 25.0 0.15 -1.2 25.7 1.25 -0.9),
        (Box muzzle_slot_b 26.3 0.15 -1.2 27.0 1.25 -0.9)
    ) $T.dark barrel
    Cubes @(
        (Box stock_spine -16.8 -0.1 -1.2 -5.8 2.2 1.2),
        (Box cheek_piece -14.7 2.0 -1.45 -7.0 3.2 1.45),
        (Box butt_plate -18.2 -2.2 -1.65 -16.3 3.0 1.65),
        (Box stock_hook -15.2 -4.7 -1.0 -11.8 -0.1 1.0),
        (RotBox pistol_grip -5.2 -8.0 -1.05 -2.1 -1.1 1.05 @(-3.7,-1.0,0) @(0,0,-12))
    ) $T.stock furniture
    Add-Block trigger_guard -3.0 -4.1 -0.9 -0.6 -1.2 0.9 $T.dark receiver
    Cubes @(
        (Box scope_tube -5.2 4.2 -0.78 8.8 5.7 0.78),
        (Box scope_front 7.4 3.55 -1.35 11.2 6.35 1.35),
        (Box scope_rear -7.3 3.75 -1.15 -4.4 6.0 1.15),
        (Box turret_top 0.0 5.6 -0.65 2.2 7.1 0.65),
        (Box mount_front 4.7 3.1 -0.9 6.0 4.4 0.9),
        (Box mount_rear -3.2 3.1 -0.9 -1.9 4.4 0.9)
    ) $T.dark optics
    Cubes @(
        (RotBox bipod_l 11.0 -7.5 -2.5 12.0 -0.2 -1.5 @(11.5,-0.2,-1.9) @(0,0,12)),
        (RotBox bipod_r 11.0 -7.5 1.5 12.0 -0.2 2.5 @(11.5,-0.2,1.9) @(0,0,-12))
    ) $T.accent furniture
}

function Build-Bullet([string]$Kind,[hashtable]$T) {
    Add-BbGroup bullet_root
    Add-BbGroup projectile bullet_root
    Add-BbGroup markings bullet_root

    $length = if($Kind -eq 'sniper'){5.8}elseif($Kind -eq 'rifle'){4.6}else{3.3}
    $radius = if($Kind -eq 'sniper'){0.58}elseif($Kind -eq 'rifle'){0.48}else{0.42}
    $y0 = -1 * ($length / 2)
    $bodyTop = $y0 + ($length * 0.58)
    RoundedBar 'jacket' $radius $y0 $bodyTop $T.brass projectile 0.18 0.12
    RoundedBar 'ogive_low' ($radius * .86) $bodyTop ($bodyTop + $length*.18) $T.brass projectile 0.15 0.10
    RoundedBar 'ogive_mid' ($radius * .60) ($bodyTop + $length*.18) ($bodyTop + $length*.31) $T.accent projectile 0.12 0.08
    Add-Block point (-$radius*.22) ($bodyTop+$length*.31) (-$radius*.22) ($radius*.22) ($bodyTop+$length*.42) ($radius*.22) $T.accent projectile
    Add-Block cannelure (-$radius*1.05) ($y0+$length*.19) (-$radius*1.05) ($radius*1.05) ($y0+$length*.26) ($radius*1.05) $T.dark markings
    if($Kind -eq 'rifle'){
        Add-Block tracer_mark (-$radius*1.06) ($y0+$length*.05) (-$radius*1.06) ($radius*1.06) ($y0+$length*.12) ($radius*1.06) $T.green markings
    }
    if($Kind -eq 'sniper'){
        Add-Block penetrator_tip (-$radius*.24) ($bodyTop+$length*.34) (-$radius*.24) ($radius*.24) ($bodyTop+$length*.50) ($radius*.24) $T.dark markings
        Add-Block api_mark (-$radius*1.08) ($y0+$length*.29) (-$radius*1.08) ($radius*1.08) ($y0+$length*.36) ($radius*1.08) $T.warning markings
    }
}

function Build-Missile([string]$Kind,[hashtable]$T) {
    Add-BbGroup missile_root
    Add-BbGroup motor_body missile_root
    Add-BbGroup payload missile_root
    Add-BbGroup controls missile_root
    Add-BbGroup engine missile_root
    Add-BbGroup details missile_root

    $isMk2 = $Kind -eq 'aa_mk2'
    $isRocket = $Kind -eq 'he_rocket'
    $half = if($isRocket){2.05}elseif($isMk2){1.45}else{1.24}
    $bottom = if($isRocket){-8.0}elseif($isMk2){-13.0}else{-10.5}
    $top = if($isRocket){5.2}elseif($isMk2){8.6}else{7.0}
    $finSpan = if($isRocket){2.0}elseif($isMk2){2.7}else{2.25}
    $finHeight = if($isRocket){3.6}elseif($isMk2){5.8}else{4.6}
    $marker = if($isRocket){$T.warning}elseif($isMk2){$T.blue}else{$T.green}

    RoundedBar 'airframe' $half $bottom $top $T.body motor_body 0.32 0.24
    RoundedBar 'guidance_section' ($half*.92) ($top-1.1) ($top+2.4) $T.dark payload 0.28 0.20
    RoundedBar 'ogive_low' ($half*.76) ($top+2.4) ($top+3.9) $T.stock payload 0.24 0.17
    RoundedBar 'ogive_high' ($half*.48) ($top+3.9) ($top+5.2) $T.stock payload 0.16 0.12
    Add-Block seeker_tip (-$half*.22) ($top+5.2) (-$half*.22) ($half*.22) ($top+6.1) ($half*.22) $T.glow payload

    $ring = $half + .18
    Add-Block forward_ident (-$ring) ($top+.25) (-$ring) $ring ($top+1.0) $ring $marker details
    Add-Block motor_joint (-$ring) ($bottom+3.6) (-$ring) $ring ($bottom+4.35) $ring $T.accent details
    if($isMk2){
        Add-Block stage_joint (-$ring) ($bottom+8.2) (-$ring) $ring ($bottom+9.0) $ring $T.blue details
        Add-Block data_spine (-$half-.16) ($bottom+5.0) (-.22) (-$half+.08) ($top-.8) .22 $T.blue details
    }
    if($isRocket){
        Add-Block hazard_band (-$ring) ($top-2.0) (-$ring) $ring ($top-1.1) $ring $T.warning details
    }

    $finRoot = $half - .08
    $finOuter = $half + $finSpan
    $finStart = $bottom + .8
    $finTop = $finStart + $finHeight
    Cubes @(
        (Box fin_n -.30 $finStart (-$finOuter) .30 $finTop (-$finRoot)),
        (Box fin_s -.30 $finStart $finRoot .30 $finTop $finOuter),
        (Box fin_w (-$finOuter) $finStart -.30 (-$finRoot) $finTop .30),
        (Box fin_e $finRoot $finStart -.30 $finOuter $finTop .30)
    ) $T.dark controls
    if($isMk2){
        $canardRoot=$half-.04; $canardOut=$half+1.35; $cy=$top-1.0
        Cubes @(
            (Box canard_n -.18 $cy (-$canardOut) .18 ($cy+2.2) (-$canardRoot)),
            (Box canard_s -.18 $cy $canardRoot .18 ($cy+2.2) $canardOut),
            (Box canard_w (-$canardOut) $cy -.18 (-$canardRoot) ($cy+2.2) .18),
            (Box canard_e $canardRoot $cy -.18 $canardOut ($cy+2.2) .18)
        ) $T.accent controls
    }

    RoundedBar 'nozzle_outer' ($half*.82) ($bottom-2.0) ($bottom+.2) $T.dark engine .26 .18
    RoundedBar 'nozzle_bell' ($half*.60) ($bottom-3.0) ($bottom-1.2) $T.stock engine .20 .14
    Add-Block exhaust_core (-$half*.24) ($bottom-3.2) (-$half*.24) ($half*.24) ($bottom-1.1) ($half*.24) $T.glow engine
}

function Build-Shell([string]$Kind,[hashtable]$T) {
    Add-BbGroup shell_root
    Add-BbGroup casing shell_root
    Add-BbGroup fuze shell_root
    Add-BbGroup stabilisers shell_root
    if($Kind -eq 'falling_warhead'){
        $half = 2.1
        $height = 14
    } else {
        $half = 1.55
        $height = 8.8
    }

    $y0 = (-1 * ($height/2))
    $y1 = $height/2
    RoundedBar 'shell_body' $half $y0 ($y1-2.6) $T.body casing .34 .24
    RoundedBar 'ogive_low' ($half*.86) ($y1-2.6) ($y1-1.2) $T.body fuze .28 .20
    RoundedBar 'ogive_high' ($half*.58) ($y1-1.2) $y1 $T.stock fuze .20 .14
    Add-Block shell_tip (-$half*.22) $y1 (-$half*.22) ($half*.22) ($y1+1.05) ($half*.22) $T.dark fuze
    $band=$half+.16
    Add-Block driving_band (-$band) ($y0+1.0) (-$band) $band ($y0+1.75) $band $T.brass casing
    Add-Block stencil_band (-$half-.06) ($y0+$height*.48) (-$half-.06) ($half+.06) ($y0+$height*.58) ($half+.06) $T.warning casing

    if($Kind -eq 'falling_warhead'){
        RoundedBar 'tail_motor' 0.92 ($y0-2.2) ($y0+2.7) $T.dark stabilisers .24 .16
        $finOut = ($half + 1.45)
        Cubes @(
            (Box fin_n -.24 ($y0-1.0) (-$finOut) .24 ($y0+3.0) (-$half)),
            (Box fin_s -.24 ($y0-1.0) $half .24 ($y0+3.0) $finOut),
            (Box fin_w (-$finOut) ($y0-1.0) -.24 (-$half) ($y0+3.0) .24),
            (Box fin_e $half ($y0-1.0) -.24 $finOut ($y0+3.0) .24)
        ) $T.dark stabilisers
        Add-Block arming_window (-.7) ($y1-4.6) (-$half-.12) .7 ($y1-3.0) (-$half+.02) $T.glow fuze
    }
}

function Build-Artillery([hashtable]$T) {
    Add-BbGroup artillery_root
    Add-BbGroup fixed_base artillery_root @(0,0,0)
    Add-BbGroup yaw_turret artillery_root @(0,8,0)
    Add-BbGroup pitch_barrel yaw_turret @(0,14,-3)

    Cubes @(
        (Box ground_plate -8 0 -8 8 2 8), (Box pedestal -5 2 -5 5 7 5),
        (Box turret_race -7 7 -7 7 9 7),
        (Box outrigger_left -18 0 -2 -7 2 2), (Box outrigger_right 7 0 -2 18 2 2),
        (Box trail_left -6 0 6 -3 2 18), (Box trail_right 3 0 6 6 2 18)
    ) $T.dark fixed_base

    Cubes @(
        (Box ground_pad_l -20 -.5 -4 -15 1 4), (Box ground_pad_r 15 -.5 -4 20 1 4),
        (Box trail_spade_l -8 -.5 16 -2 1 20), (Box trail_spade_r 2 -.5 16 8 1 20)
    ) $T.stock fixed_base

    Cubes @(
        (Box elevation_wheel -10.5 9 0 -9.5 14 5), (Box operator_seat -10 6 6 -5 7 11)
    ) $T.accent fixed_base

    Cubes @(
        (Box carriage -6 8 -5 6 10 5),
        (Box shield_left -11 9 -7 -4 18 -6), (Box shield_right 4 9 -7 11 18 -6),
        (Box trunnion_block_l -10 12 -5 -6 17 3), (Box trunnion_block_r 6 12 -5 10 17 3),
        (Box sight_housing 4 15 -6 7 20 -2)
    ) $T.body yaw_turret
    Add-Block optic_glass 4.4 16.2 -6.3 6.6 18.7 -6.0 $T.glow yaw_turret
    Add-Block warning_panel -8.2 11.7 3.0 -8.0 14.2 6.5 $T.warning yaw_turret
    Cubes @(
        (Box breech -4.8 11 -2 4.8 17 8),
        (Box mantlet -6 11 -6 6 17 -1),
        (Box recoil_l -3.3 15 -18 -1.2 17 -4),
        (Box recoil_r 1.2 15 -18 3.3 17 -4),
        (Box barrel_sleeve -2.8 12.1 -20 2.8 16.2 -5)
    ) $T.accent pitch_barrel

    Cubes @(
        (Box gun_tube -1.35 13.0 -38 1.35 15.4 -17),
        (Box muzzle_brake -2.8 12.2 -43 2.8 16.0 -37.5),
        (Box brake_slot_l -3.0 13.0 -41.8 -2.4 15.2 -38.8),
        (Box brake_slot_r 2.4 13.0 -41.8 3.0 15.2 -38.8),
        (Box muzzle_bore -.75 13.45 -43.5 .75 14.95 -42.7)
    ) $T.dark pitch_barrel
}

function Build-Radar([hashtable]$T) {
    Add-BbGroup radar_root
    Add-BbGroup fixed_foundation radar_root
    Add-BbGroup yaw_head radar_root @(0,18,0)
    Add-BbGroup pitch_dish yaw_head @(0,25,0) @(18,0,0)

    Cubes @(
        (Box foundation -14 0 -14 14 3 14), (Box mast_core -3 5 -3 3 18 3),
        (RotBox brace_nw -10 4 -1 -8 17 1 @(-8,4,0) @(0,0,-18)),
        (RotBox brace_ne 8 4 -1 10 17 1 @(8,4,0) @(0,0,18)),
        (RotBox brace_sw -1 4 -10 1 17 -8 @(0,4,-8) @(18,0,0)),
        (RotBox brace_se -1 4 8 1 17 10 @(0,4,8) @(-18,0,0)),
        (Box cabinet_vent_l -9.3 6.0 -9.35 -7.6 10.3 -9.0),
        (Box cabinet_vent_r 6.3 6.0 -9.35 8.0 10.3 -9.0)
    ) $T.dark fixed_foundation
    Cubes @(
        (Box footing -11 3 -11 11 5 11),
        (Box cabinet_l -10 4 -9 -4 12 -3), (Box cabinet_r 4 4 -9 10 12 -3),
        (Box cabinet_rear -7 4 4 7 10 10),
        (Box cabinet_top_l -10.4 12 -9.4 -3.6 12.8 -2.6),
        (Box cabinet_top_r 3.6 12 -9.4 10.4 12.8 -2.6)
    ) $T.body fixed_foundation
    Add-Block status_lamp -7 8 -9.38 -5 10 -9.02 $T.glow fixed_foundation

    Cubes @(
        (Box yaw_ring -7 16 -7 7 19 7), (Box yaw_housing -5 18 -5 5 22 5),
        (Box elevation_cradle_l -8 20 .6 -5.5 28 4.6),
        (Box elevation_cradle_r 5.5 20 .6 8 28 4.6)
    ) $T.body yaw_head
    Cubes @(
        (Box pitch_bearing_l -8.7 22.3 .4 -6.3 27.7 5.0),
        (Box pitch_bearing_r 6.3 22.3 .4 8.7 27.7 5.0),
        (Box dish_axle -9 24.25 .35 9 25.75 2.15),
        (Box elevation_motor 7.2 19 2.0 10.2 23.5 6.0),
        (Box cable_trunk -.9 18 3.8 .9 23 5.6)
    ) $T.dark yaw_head

    # Seven broad, shallow-curved panels read as one military reflector from every angle.
    $segments = @(
        @{n='center';x0=-3.0;x1=3.0;y0=19.0;y1=31.0;o=@(0,25,0);r=@(0,0,0)},
        @{n='inner_l';x0=-7.0;x1=-3.0;y0=19.0;y1=31.0;o=@(-3,25,0);r=@(0,-6,0)},
        @{n='inner_r';x0=3.0;x1=7.0;y0=19.0;y1=31.0;o=@(3,25,0);r=@(0,6,0)},
        @{n='mid_l';x0=-11.0;x1=-7.0;y0=19.5;y1=30.5;o=@(-7,25,0);r=@(0,-12,0)},
        @{n='mid_r';x0=7.0;x1=11.0;y0=19.5;y1=30.5;o=@(7,25,0);r=@(0,12,0)},
        @{n='outer_l';x0=-15.0;x1=-11.0;y0=20.5;y1=29.5;o=@(-11,25,0);r=@(0,-18,0)},
        @{n='outer_r';x0=11.0;x1=15.0;y0=20.5;y1=29.5;o=@(11,25,0);r=@(0,18,0)}
    )
    foreach($segment in $segments){
        $midX = ($segment.x0 + $segment.x1) / 2
        Cubes @(
            (Add-Cube "reflector_$($segment.n)" @($segment.x0,$segment.y0,-.45) @($segment.x1,$segment.y1,.35) $segment.o $segment.r)
        ) $T.body pitch_dish
        Cubes @(
            (Add-Cube "rim_$($segment.n)_top" @(($segment.x0-.12),($segment.y1-.15),-.70) @(($segment.x1+.12),($segment.y1+.55),.65) $segment.o $segment.r),
            (Add-Cube "rim_$($segment.n)_bottom" @(($segment.x0-.12),($segment.y0-.55),-.70) @(($segment.x1+.12),($segment.y0+.15),.65) $segment.o $segment.r),
            (Add-Cube "rib_$($segment.n)_vertical" @(($midX-.18),$segment.y0,-.68) @(($midX+.18),$segment.y1,-.43) $segment.o $segment.r),
            (Add-Cube "grid_$($segment.n)_lower" @($segment.x0,21.85,-.68) @($segment.x1,22.15,-.43) $segment.o $segment.r),
            (Add-Cube "grid_$($segment.n)_middle" @($segment.x0,24.82,-.68) @($segment.x1,25.18,-.43) $segment.o $segment.r),
            (Add-Cube "grid_$($segment.n)_upper" @($segment.x0,27.85,-.68) @($segment.x1,28.15,-.43) $segment.o $segment.r)
        ) $T.dark pitch_dish
    }
    Cubes @(
        (Add-Cube outer_rim_l @(-15.35,20.35,-.75) @(-14.75,29.65,.75) @(-11,25,0) @(0,-18,0)),
        (Add-Cube outer_rim_r @(14.75,20.35,-.75) @(15.35,29.65,.75) @(11,25,0) @(0,18,0))
    ) $T.dark pitch_dish

    Cubes @(
        (Box feed_horn_body -1.0 24.0 -8.8 1.0 26.0 -7.1),
        (Box feed_horn_flare -1.4 23.6 -9.9 1.4 26.4 -8.6)
    ) $T.body pitch_dish
    Cubes @(
        (Box rear_hub -2.2 22.8 .35 2.2 27.2 2.0),
        (Box feed_spine -.45 24.5 -7.6 .45 25.5 -.1),
        (Box feed_face -1.5 23.7 -10.05 1.5 26.3 -9.82)
    ) $T.dark pitch_dish
    Add-Block feed_glass -.45 24.55 -10.25 .45 25.45 -10.06 $T.glow pitch_dish
}

function Build-Silo([hashtable]$T,[bool]$Large=$false) {
    Add-BbGroup silo_root
    Add-BbGroup foundation silo_root
    $hinge = if($Large){20}else{12}
    Add-BbGroup left_door silo_root @(-$hinge,5,0)
    Add-BbGroup right_door silo_root @($hinge,5,0)

    $concrete="$($T.body)_concrete"
    $doorSteel="$($T.dark)_door_steel"
    $shaft="$($T.dark)_shaft_black"
    Texture $concrete '#92928A'
    Texture $doorSteel '#343A3C'
    Texture $shaft '#101315'

    if($Large){
        # Purpose-built five-block emplacement. The outer pad is poured
        # concrete, while the two independent door meshes meet exactly at x=0.
        Cubes @(
            (Box pad_n -40 0 -40 40 4 -21), (Box pad_s -40 0 21 40 4 40),
            (Box pad_w -40 0 -21 -21 4 21), (Box pad_e 21 0 -21 40 4 21),
            (Box curb_n -40 4 -40 40 6 -37), (Box curb_s -40 4 37 40 6 40),
            (Box curb_w -40 4 -37 -37 6 37), (Box curb_e 37 4 -37 40 6 37),
            (Box footing_n -35 -3 -35 35 0 -24), (Box footing_s -35 -3 24 35 0 35),
            (Box footing_w -35 -3 -24 -24 0 24), (Box footing_e 24 -3 -24 35 0 24)
        ) $concrete foundation
        Cubes @(
            (Box throat_n -22 -16 -22 22 6 -19), (Box throat_s -22 -16 19 22 6 22),
            (Box throat_w -22 -16 -19 -19 6 19), (Box throat_e 19 -16 -19 22 6 19),
            (Box hinge_bed_l -23 4 -20 -19 7 20), (Box hinge_bed_r 19 4 -20 23 7 20),
            (Box drain_n -16 4.05 -27 16 4.5 -25), (Box drain_s -16 4.05 25 16 4.5 27)
        ) $doorSteel foundation
        # Keep the throat visually dark without exposing the terrain block below.
        # The upper face sits just above the placement surface and remains well
        # below the closed doors at y=6.
        Add-Block recessed_floor -19 0.1 -19 19 0.3 19 $shaft foundation
        Cubes @(
            (Box left_leaf -20 6 -20 0 8.6 20),
            (Box left_rib_outer -19.2 8.55 -19.2 -17.7 9.35 19.2),
            (Box left_rib_n -18.0 8.55 -19.2 -1.0 9.35 -17.7),
            (Box left_rib_s -18.0 8.55 17.7 -1.0 9.35 19.2),
            (Box left_hinge -22.1 5.5 -17.5 -19.0 9.7 17.5)
        ) $doorSteel left_door
        Cubes @(
            (Box right_leaf 0 6 -20 20 8.6 20),
            (Box right_rib_outer 17.7 8.55 -19.2 19.2 9.35 19.2),
            (Box right_rib_n 1.0 8.55 -19.2 18.0 9.35 -17.7),
            (Box right_rib_s 1.0 8.55 17.7 18.0 9.35 19.2),
            (Box right_hinge 19.0 5.5 -17.5 22.1 9.7 17.5)
        ) $doorSteel right_door
        Add-Block left_warning -13.5 9.3 -1.0 -3.5 9.65 1.0 $T.warning left_door
        Add-Block right_warning 3.5 9.3 -1.0 13.5 9.65 1.0 $T.warning right_door
        return
    }

    # A clean three-block-square military emplacement with a recessed launch
    # throat. The doors are separate groups so the block entity renderer can
    # hinge them open while a missile rises from below ground.
    Cubes @(
        (Box lower_slab -24 0 -24 24 2 24),
        (Box upper_slab -23 2 -23 23 4 23),
        (Box shaft_n -12 -26 -12 12 4 -9), (Box shaft_s -12 -26 9 12 4 12),
        (Box shaft_w -12 -26 -9 -9 4 9), (Box shaft_e 9 -26 -9 12 4 9),
        (Box curb_n -23 4 -23 23 7 -20), (Box curb_s -23 4 20 23 7 23),
        (Box curb_w -23 4 -20 -20 7 20), (Box curb_e 20 4 -20 23 7 20)
    ) $concrete foundation

    Cubes @(
        (Box throat_n -14 3 -14 14 6 -11), (Box throat_s -14 3 11 14 6 14),
        (Box throat_w -14 3 -11 -11 6 11), (Box throat_e 11 3 -11 14 6 11),
        (Box service_strip_n -19 4 -18 19 4.8 -16),
        (Box service_strip_s -19 4 16 19 4.8 18)
    ) $doorSteel foundation
    Add-Block recessed_floor -10.8 0.1 -8.8 10.8 0.3 8.8 $shaft foundation

    Cubes @(
        (Box left_leaf -11.5 5 -11 0 7.4 11),
        (Box left_rib_a -10.8 7.35 -10.2 -1.2 8.15 -8.8),
        (Box left_rib_b -10.8 7.35 8.8 -1.2 8.15 10.2),
        (Box left_hinge -12.7 4.8 -9.5 -10.9 8.5 9.5)
    ) $doorSteel left_door

    Cubes @(
        (Box right_leaf 0 5 -11 11.5 7.4 11),
        (Box right_rib_a 1.2 7.35 -10.2 10.8 8.15 -8.8),
        (Box right_rib_b 1.2 7.35 8.8 10.8 8.15 10.2),
        (Box right_hinge 10.9 4.8 -9.5 12.7 8.5 9.5)
    ) $doorSteel right_door
    Add-Block left_warning -9.2 8.1 -1.0 -2.0 8.5 1.0 $T.warning left_door
    Add-Block right_warning 2.0 8.1 -1.0 9.2 8.5 1.0 $T.warning right_door
}

function Build-Support([int]$Tier,[hashtable]$T) {
    Add-BbGroup support_root
    Add-BbGroup frame support_root
    Add-BbGroup electronics support_root
    Add-BbGroup antenna support_root

    # Lower support blocks sit one block above the silo foundation. Extending
    # down by exactly one block removes the previous floating gap. The slim
    # mast stays at the inward corner of each diagonal support cell rather than
    # occupying the missile's centre line.
    $h = 15 + ($Tier * 5.0)
    Cubes @(
        (Box anchor_foot -3.4 -16 -3.4 3.4 -13.2 3.4),
        (Box anchor_neck -2.8 -13.2 -2.8 2.8 -10.5 2.8),
        (Box mast -1.65 -10.5 -1.65 1.65 $h 1.65),
        (RotBox brace_x -3.0 -11.0 -.45 -1.8 ($h-1.0) .45 @(-2.4,-10.5,0) @(0,0,-5)),
        (RotBox brace_z -.45 -11.0 -3.0 .45 ($h-1.0) -1.8 @(0,-10.5,-2.4) @(5,0,0)),
        (Box clamp_low -2.4 -1.0 -2.4 2.4 .6 2.4),
        (Box clamp_high -2.4 ($h-2.2) -2.4 2.4 ($h-.6) 2.4)
    ) $T.dark frame

    for($i = 0; $i -lt $Tier; $i++){
        $baseY = 1.5 + ($i * 4.4)
        Add-Block "processor_$i" 1.6 $baseY -1.45 3.4 ($baseY + 2.5) 1.45 $T.body electronics
        Add-Block "processor_$i`_screen" 3.35 ($baseY+.45) -.9 3.6 ($baseY + 2.05) .9 $T.glow electronics
    }

    if($Tier -ge 1){
        Add-Block ring_low -2.3 ($h - 4.8) -2.3 2.3 ($h - 3.5) 2.3 $T.stock frame
    }
    if($Tier -ge 2){
        Cubes @(
            (Box antenna_l -2.8 $h -.35 -2.0 ($h + 4.2) .35),
            (Box antenna_r 2.0 $h -.35 2.8 ($h + 4.2) .35),
            (Box crossbar -3.2 ($h+3.5) -.5 3.2 ($h+4.3) .5),
            (Box inward_clamp -4.0 ($h-1.0) -1.0 -1.4 ($h+1.6) 1.0)
        ) $T.body antenna
    }
    if($Tier -ge 3){
        Cubes @(
            (Box crown_left -3.7 ($h + 4.2) -1.5 -.5 ($h + 5.8) 1.5),
            (Box crown_right .5 ($h + 4.2) -1.5 3.7 ($h + 5.8) 1.5),
            (Box radar_focus -.9 ($h + 5.6) -.8 .9 ($h + 7.2) .8),
            (Box beacon -.4 ($h+7.2) -.4 .4 ($h+8.8) .4)
        ) $T.glow electronics
    }
}

function Build-Launcher([hashtable]$T) {
    Add-BbGroup launcher_root
    Add-BbGroup tube launcher_root
    Add-BbGroup stock launcher_root
    Add-BbGroup controls launcher_root
    Add-BbGroup sights launcher_root

    Cubes @(
        (Box launch_tube -13 -2.5 -2.5 13 2.5 2.5),
        (Box tube_top -11 2.5 -1.8 11 3.2 1.8), (Box tube_bottom -11 -3.2 -1.8 11 -2.5 1.8),
        (Box rear_vent -15 -3.3 -3.3 -11 3.3 3.3), (Box vent_core -15.4 -2.1 -2.1 -14.5 2.1 2.1),
        (Box muzzle_ring 10.5 -3.35 -3.35 14.5 3.35 3.35), (Box muzzle_bore 14.2 -2.25 -2.25 15.0 2.25 2.25),
        (Box heat_shield -7 -2.9 -3.0 6 2.2 -2.5), (Box heat_shield_r -7 -2.9 2.5 6 2.2 3.0)
    ) $T.body tube

    Cubes @(
        (Box shoulder_pad -12.8 -6.0 -2.2 -9.6 -2.7 2.2),
        (RotBox shoulder_strut -11.5 -5.1 -.6 -7.0 -3.9 .6 @(-10.5,-3.2,0) @(0,0,-18)),
        (Box trigger_box -1.3 -4.2 -1.6 2.5 -2.1 1.6),
        (Box front_handle 5.5 -6.7 -1.15 8.0 -2.2 1.15)
    ) $T.dark stock
    Add-Block pistol_grip -1.9 -8.4 -1.2 .4 -4.0 1.2 $T.stock controls
    Add-Block safety_paddle 1.4 -3.4 1.55 3.2 -2.5 1.9 $T.warning controls
    Cubes @(
        (Box optic_mount -1.5 3.1 -.8 4.8 4.0 .8),
        (Box optic_body -.8 4.0 -1.2 5.8 6.2 1.2),
        (Box optic_lens 5.7 4.25 -.95 6.2 5.95 .95),
        (Box rangefinder 1.0 6.1 -.7 4.0 7.3 .7)
    ) $T.dark sights
    Add-Block optic_glass 5.95 4.55 -.68 6.25 5.65 .68 $T.glow sights
}

function Build-Utility([string]$Kind,[hashtable]$T) {
    Add-BbGroup item_root

    switch($Kind){
        'target_designator' {
            Add-Block body -4.6 -3.0 -2.2 4.6 4.2 2.2 $T.dark item_root
            Add-Block armor_shell -3.8 -2.4 -2.5 3.8 3.5 2.5 $T.body item_root
            Add-Block optic_l -4.2 -.2 -3.2 -1.0 3.0 -2.4 $T.stock item_root
            Add-Block optic_r 1.0 -.2 -3.2 4.2 3.0 -2.4 $T.stock item_root
            Add-Block lens_l -3.6 .3 -3.4 -1.5 2.5 -3.1 $T.glow item_root
            Add-Block lens_r 1.5 .3 -3.4 3.6 2.5 -3.1 $T.glow item_root
            Add-Block grip -1.5 -9.0 -1.2 1.5 -2.6 1.2 $T.stock item_root
            Add-Block trigger -.45 -4.7 -1.55 .65 -3.2 -1.1 $T.warning item_root
            Add-Block range_antenna -.4 4.2 -.4 .4 8.4 .4 $T.accent item_root
        }
        'remote_designator' {
            Add-Block rugged_case -5.2 -6.3 -1.6 5.2 6.3 1.6 $T.dark item_root
            Add-Block case_inset -4.4 -5.5 -1.9 4.4 5.3 -1.6 $T.body item_root
            Add-Block display -3.7 -.5 -2.05 3.7 4.4 -1.85 $T.glow item_root
            Add-Block keypad -3.6 -4.6 -2.05 3.6 -1.4 -1.85 $T.stock item_root
            Add-Block safety_cover 2.0 -4.4 -2.25 4.1 -1.5 -1.95 $T.warning item_root
            Add-Block antenna -4.2 6.0 -.45 -3.3 11.0 .45 $T.accent item_root
            Add-Block antenna_tip -4.4 10.8 -.65 -3.1 12.2 .65 $T.glow item_root
            Add-Block side_grip 5.0 -3.8 -.9 6.4 3.8 .9 $T.stock item_root
        }
        'radar_gun' {
            Add-Block receiver -6.2 -2.3 -2.0 3.2 3.0 2.0 $T.body item_root
            Add-Block rear_battery -8.0 -1.6 -1.65 -6.0 2.4 1.65 $T.dark item_root
            Add-Block sensor_neck 2.8 -2.0 -2.2 5.0 3.2 2.2 $T.accent item_root
            Add-Block sensor_bell_mid 4.6 -3.0 -3.0 7.0 4.2 3.0 $T.dark item_root
            Add-Block sensor_bell_front 6.7 -3.8 -3.8 9.2 5.0 3.8 $T.dark item_root
            Add-Block sensor_face 8.9 -3.0 -3.0 9.6 4.2 3.0 $T.glow item_root
            Add-Block sensor_hood 5.8 4.7 -4.0 9.7 5.8 4.0 $T.stock item_root
            Add-Block pistol_grip -2.4 -9.2 -1.25 .6 -2.0 1.25 $T.stock item_root
            Add-Block trigger .2 -5.1 -1.48 1.6 -3.0 -1.08 $T.warning item_root
            Add-Block top_display -5.6 2.8 -1.45 -.3 5.3 1.45 $T.dark item_root
            Add-Block display_glass -5.1 5.05 -.98 -.8 5.42 .98 $T.glow item_root
            Add-Block range_antenna -6.7 3.0 -.38 -6.0 8.4 .38 $T.accent item_root
        }
        'linking_tool' {
            Add-Block rugged_handset -3 -6 -1.15 3 5 1.15 $T.dark item_root
            Add-Block faceplate -2.65 -5.6 -1.3 2.65 4.6 -1.14 $T.body item_root
            Add-Block readout -2.1 .1 -1.42 2.1 3.8 -1.29 $T.dark item_root
            Add-Block signal_trace -1.7 1.7 -1.47 1.5 2.05 -1.41 $T.glow item_root
            Add-Block source_button -1.9 -2.2 -1.55 -.3 -.7 -1.3 $T.blue item_root
            Add-Block destination_button .3 -2.2 -1.55 1.9 -.7 -1.3 $T.green item_root
            Add-Block link_button -1.7 -4.5 -1.55 1.7 -3.2 -1.3 $T.accent item_root
            Add-Block antenna -2.3 5 -.35 -1.65 9 .35 $T.dark item_root
            Add-Block antenna_ring -2.45 5 -.5 -1.5 5.6 .5 $T.brass item_root
        }
        'tablet' {
            Add-Block rugged_case -7.5 -5 -1 7.5 5 1 $T.dark item_root
            Add-Block front_bezel -7.1 -4.6 -1.15 7.1 4.6 -.99 $T.body item_root
            Add-Block screen -6 -3.7 -1.25 5.6 3.7 -1.14 $T.dark item_root
            Add-Block scan_horizontal -5.2 -.12 -1.29 4.9 .12 -1.24 $T.green item_root
            Add-Block scan_vertical -.15 -3.05 -1.29 .15 3.05 -1.24 $T.green item_root
            Add-Block power_key 6 -2.8 -1.3 6.5 -1.9 -1.14 $T.accent item_root
            foreach($x in @(-7.6,6.4)){ foreach($y in @(-5.1,3.9)){
                Add-Block corner_guard $x $y -1.35 ($x+1.2) ($y+1.2) 1.2 $T.dark item_root
            }}
        }
        {$_ -in @('pistol_mag','rifle_mag','sniper_mag')} {
            $w=if($Kind -eq 'pistol_mag'){1.7}elseif($Kind -eq 'sniper_mag'){2.7}else{2.3}
            $h=if($Kind -eq 'pistol_mag'){7.0}elseif($Kind -eq 'sniper_mag'){9.0}else{11.0}
            Add-Block mag_body (-$w) (-$h/2) -1.2 $w ($h/2) 1.2 $T.dark item_root
            Add-Block feed_throat (-$w-.15) ($h/2-.5) -1.45 ($w+.15) ($h/2+.9) 1.45 $T.body item_root
            Add-Block floor_plate (-$w-.3) (-$h/2-.7) -1.5 ($w+.3) (-$h/2+.1) 1.5 $T.stock item_root
            Add-Block witness_strip (-$w-.08) (-$h*.18) -1.26 (-$w+.18) ($h*.22) -1.20 $T.brass item_root
            if($Kind -eq 'rifle_mag'){ Add-Block curve_block -.2 (-$h/2-1.0) -1.1 ($w+.6) (-$h/2+2.0) 1.1 $T.dark item_root }
            if($Kind -eq 'sniper_mag'){ Add-Block caliber_mark (-$w-.25) -.7 -1.3 ($w+.25) .7 -1.18 $T.warning item_root }
        }
        'ammo_box' {
            Add-Block crate -5 -3.1 -4.5 5 3.1 4.5 $T.dark item_root
            Add-Block lid -5.3 3.2 -4.7 5.3 4.6 4.7 $T.body item_root
            Add-Block lock_l -0.8 1.4 -4.65 0.8 2.8 -4.1 $T.warning item_root
            Add-Block reinforcement -5.4 -2 4.2 5.4 -0.3 4.7 $T.accent item_root
        }
        'wrench' {
            Add-Block forged_handle -1.25 -9.4 -.8 1.25 3.4 .8 $T.warning item_root
            Add-Block handle_heel -1.75 -10.0 -1.05 1.75 -8.5 1.05 $T.dark item_root
            Add-Block slide_rail -1.65 .5 -.95 1.65 6.3 .95 $T.body item_root
            Add-Block fixed_jaw_base -1.65 3.4 -1.3 4.2 5.15 1.3 $T.dark item_root
            Add-Block fixed_tooth 3.3 4.7 -1.45 5.2 6.65 1.45 $T.stock item_root
            Add-Block movable_spine -4.9 2.0 -1.25 -2.25 9.25 1.25 $T.dark item_root
            Add-Block movable_crown -4.9 8.0 -1.3 3.4 9.8 1.3 $T.dark item_root
            Add-Block movable_tooth 2.1 6.95 -1.45 4.1 9.2 1.45 $T.stock item_root
            Add-Block adjuster_axle -2.45 1.1 -1.35 2.45 3.25 1.35 $T.brass item_root
            Add-Block adjuster_wheel -2.75 1.45 -1.6 2.75 2.95 1.6 $T.brass item_root
            foreach($x in @(-2.2,-1.1,0,1.1,2.2)) {
                Add-Block adjustment_knurl $x 1.35 -1.72 ($x+.28) 3.05 -1.58 $T.dark item_root
            }
            foreach($y in @(-7.5,-5.5,-3.5)) {
                Add-Block handle_rib -1.22 $y -.82 1.22 ($y+.5) .82 $T.dark item_root
            }
        }
        'hose' {
            Add-Block nozzle -8.4 -0.8 -0.85 5.4 0.8 0.85 $T.body item_root
            Add-Block hose_body -2.7 -5.8 -0.5 0.8 -1.2 0.5 $T.dark item_root
            Add-Block coupling 4.0 -2.2 -1.6 7.2 2.2 1.6 $T.body item_root
            Add-Block valve -0.5 1.0 -0.7 0.5 3.0 0.7 $T.warning item_root
        }
        'extinguisher' {
            Add-Block tank -3.6 -7.4 -2.6 3.6 5.8 2.6 $T.accent item_root
            Add-Block neck -1.0 5.2 -0.85 1.0 7.4 0.85 $T.dark item_root
            Add-Block handle -4.4 7.0 -0.6 2.4 9.0 0.6 $T.body item_root
            Add-Block hose 2.4 0.4 -3.1 3.9 7.0 -2.2 $T.dark item_root
            Add-Block decal -2.0 0.5 -2.9 2.0 3.8 -2.5 $T.glow item_root
        }
        'panel' {
            Texture panel_black '#060809'
            Add-Block cabinet -8 -8 -7.75 8 8 8 $T.dark item_root
            Add-Block blank_screen -7.35 -7.35 -7.94 7.35 7.35 -7.75 panel_black item_root
            Add-Block bezel_top -8 7.35 -8 8 8 -7.7 $T.dark item_root
            Add-Block bezel_bottom -8 -8 -8 8 -7.35 -7.7 $T.dark item_root
            Add-Block bezel_left -8 -7.35 -8 -7.35 7.35 -7.7 $T.dark item_root
            Add-Block bezel_right 7.35 -7.35 -8 8 7.35 -7.7 $T.dark item_root
            Add-Block invalid_status 6.35 6.35 -7.98 6.85 6.85 -7.93 $T.warning item_root
        }
        'pipe' {
            Add-Block pipe -2.2 -8.2 -2.2 2.2 8.2 2.2 $T.body item_root
            Add-Block band_top -2.8 -7.3 -2.7 2.8 -6.2 2.7 $T.accent item_root
            Add-Block band_mid -2.8 6.2 -2.7 2.8 7.3 2.7 $T.accent item_root
            Add-Block collar -3.0 -0.5 -3.0 3.0 0.5 3.0 $T.dark item_root
        }
        'turret' {
            Add-Block base -8 -4.2 -8 8 0.2 8 $T.dark item_root
            Add-Block rotation_ring -6.4 0 -6.4 6.4 2.0 6.4 $T.stock item_root
            Add-Block body -5.4 1.8 -5.4 5.4 9.2 5.4 $T.body item_root
            Add-Block drum -3.2 5.2 -8.2 3.2 10.0 -4.2 $T.dark item_root
            Add-Block gun_left -1.8 6.2 -17.8 -.35 8.0 -6.0 $T.accent item_root
            Add-Block gun_right .35 6.2 -17.8 1.8 8.0 -6.0 $T.accent item_root
            Add-Block muzzle_cluster -2.2 5.8 -19.0 2.2 8.5 -17.2 $T.dark item_root
            Add-Block optics -4.3 8.4 -.8 4.3 11.5 2.8 $T.dark item_root
            Add-Block optic_glass -3.1 9.1 2.7 3.1 10.8 3.0 $T.glow item_root
            Add-Block warning_left -5.5 3.4 -5.5 5.5 4.3 -5.2 $T.warning item_root
        }
        default {
            Add-Block body -2.2 -7.2 -2.2 2.2 7.2 2.2 $T.dark item_root
            Add-Block cap -2.6 5 -2.8 2.6 7.4 2.8 $T.body item_root
            Add-Block stripe -2.3 -0.4 -2.3 2.3 1.0 2.3 $T.accent item_root
        }
    }
}

function Build-Tnt([string]$Id,[string]$Accent,[bool]$Cluster,[hashtable]$T) {
    Add-BbGroup tnt_root
    Add-BbGroup cluster_pack tnt_root
    Add-BbGroup strap tnt_root

    Add-Block casing -8 -8 -8 8 8 8 $T.body tnt_root
    Add-Block top_plate -7.5 7.7 -7.5 7.5 8.7 7.5 $T.dark tnt_root
    Add-Block bottom_plate -7.5 -8.7 -7.5 7.5 -7.7 7.5 $T.dark tnt_root
    Cubes @(
        (Box corner_nw -8.6 -8.4 -8.6 -6.8 8.4 -6.8), (Box corner_ne 6.8 -8.4 -8.6 8.6 8.4 -6.8),
        (Box corner_sw -8.6 -8.4 6.8 -6.8 8.4 8.6), (Box corner_se 6.8 -8.4 6.8 8.6 8.4 8.6)
    ) $T.dark tnt_root

    Texture "${Id}_yield" $Accent
    if($Cluster){
        Add-Block cluster_rack -6.6 8.5 -6.6 6.6 10.2 6.6 $T.accent cluster_pack
        Cubes @(
            (Box submunition_n -1.6 10.0 -5.8 1.6 14.0 -2.6), (Box submunition_s -1.6 10.0 2.6 1.6 14.0 5.8),
            (Box submunition_w -5.8 10.0 -1.6 -2.6 14.0 1.6), (Box submunition_e 2.6 10.0 -1.6 5.8 14.0 1.6)
        ) "${Id}_yield" cluster_pack
        Cubes @(
            (Box sub_cap_n -1.0 14.0 -5.2 1.0 15.4 -3.2), (Box sub_cap_s -1.0 14.0 3.2 1.0 15.4 5.2),
            (Box sub_cap_w -5.2 14.0 -1.0 -3.2 15.4 1.0), (Box sub_cap_e 3.2 14.0 -1.0 5.2 15.4 1.0)
        ) $T.dark cluster_pack
    } else {
        Add-Block detonator_base -2.4 8.4 -2.4 2.4 10.0 2.4 $T.accent tnt_root
        Add-Block fuse -1.0 9.8 -1.0 1.0 13.0 1.0 $T.warning tnt_root
        Add-Block fuse_tip -.5 12.8 -.5 .5 14.2 .5 $T.glow tnt_root
    }

    Cubes @(
        (Box strap_x -8.45 -1.1 -7.4 8.45 1.1 7.4),
        (Box strap_z -7.4 -1.15 -8.45 7.4 1.15 8.45)
    ) $T.accent strap
    Cubes @(
        (Box yield_band_n -7.4 3.0 -8.15 7.4 5.0 -7.85), (Box yield_band_s -7.4 3.0 7.85 7.4 5.0 8.15),
        (Box yield_band_w -8.15 3.0 -7.4 -7.85 5.0 7.4), (Box yield_band_e 7.85 3.0 -7.4 8.15 5.0 7.4),
        (Box label_a -5.8 -4.8 -8.2 -.8 -2.3 -7.82), (Box label_b .8 -4.8 -8.2 5.8 -2.3 -7.82)
    ) "${Id}_yield" tnt_root
    Add-Block handle -3.8 8.5 -1.0 3.8 10.6 1.0 $T.stock strap
}

function Build-Phalanx([hashtable]$T) {
    # Import the actual deployed turret's boxes at zero yaw/elevation. The
    # inventory silhouette stays coupled to the existing animated renderer.
    Add-BbGroup turret_root
    $source = Get-Content -Raw (Join-Path $PSScriptRoot '../../../src/client/java/com/andye/warmod/phalanx/client/PhalanxTurretMesh.java')
    $methods = @(
        @{name='renderStaticBase';offset=@(-.5,0,-.5)},
        @{name='renderYawHousing';offset=@(0,1.02,0)},
        @{name='renderCradle';offset=@(0,1.26,0)},
        @{name='renderBarrels';offset=@(0,1.26,0)}
    )
    $index=0
    foreach($method in $methods){
        $body = [regex]::Match($source, '(?s)public static void '+$method.name+'\(.*?(?=public static void|private static void)').Value
        foreach($match in [regex]::Matches($body,'(?s)box\(pose, buffer,\s*((?:-?[.\d]+F,\s*){6})(\d+),\s*(\d+),\s*(\d+),')){
            $coordinates=@($match.Groups[1].Value -split ',' | Where-Object { $_.Trim() } | ForEach-Object { [double]($_.Trim() -replace 'F','') })
            $color='#{0:x2}{1:x2}{2:x2}' -f [int]$match.Groups[2].Value,[int]$match.Groups[3].Value,[int]$match.Groups[4].Value
            $texture='turret_'+$color.Substring(1)
            if(-not $script:turretTextures.ContainsKey($texture)){ Texture $texture $color; $script:turretTextures[$texture]=$true }
            $from=@();$to=@()
            for($axis=0;$axis -lt 3;$axis++){
                $from+=16*($coordinates[$axis]+$method.offset[$axis]);$to+=16*($coordinates[$axis+3]+$method.offset[$axis])
            }
            Cubes @((Add-Cube ('turret_part_'+$index++) $from $to)) $texture turret_root
        }
    }
    foreach($column in 0..3){
        $x=(-.245+$column*.16)*16
        Add-Block sensor_cell $x 21.52 -11.136 ($x+1.28) 22.8 -10.8 $T.body turret_root
    }
    foreach($barrel in 0..5){
        $angle=$barrel*[Math]::PI/3; $x=[Math]::Cos($angle)*1.84; $y=20.16+[Math]::Sin($angle)*1.84
        Add-Block ('barrel_'+$barrel) ($x-.4) ($y-.4) 9.6 ($x+.4) ($y+.4) 27.52 $T.dark turret_root
    }
    Add-Block standby_lamp 3.2 8.8 6.448 4.16 9.76 6.8 $T.green turret_root
}

function Build-Component([string]$Kind,[hashtable]$T) {
    Add-BbGroup component_root
    if($Kind -like 'chip_*'){
        $tier=[int]$Kind.Substring(5)
        Add-Block circuit_board -5 -3 -.35 5 3 .35 $T.green component_root
        Add-Block processor -1.8 -1.6 -.65 1.8 1.6 -.34 $T.dark component_root
        foreach($i in 0..6){ $x=-4.1+$i*1.2; Add-Block edge_contact $x -3.7 -.4 ($x+.6) -2.4 .4 $T.brass component_root }
        foreach($i in 1..$tier){ $x=-4+$i*1.6; Add-Block tier_mark $x 1.9 -.48 ($x+.65) 2.5 -.34 $T.glow component_root }
        Add-Block signal_bus -4.3 -.9 -.45 -2.5 1.3 -.34 $T.accent component_root
    } elseif($Kind -eq 'workbench'){
        # Two-block industrial assembly bench. The editable source is deliberately
        # authored in block coordinates X=0..32, Y=0..16, Z=0..16 so runtime
        # export can produce exact left/right halves without scaling either one.
        $benchPaint="$($T.body)_bench_paint"; Texture $benchPaint '#4E594B'
        $benchSteel="$($T.accent)_bench_steel"; Texture $benchSteel '#747B7A'
        $benchTop="$($T.accent)_worktop_steel"; Texture $benchTop '#858B88'
        $benchRubber="$($T.dark)_cradle_rubber"; Texture $benchRubber '#202424'
        $benchWarning="$($T.warning)_warning_stripe"; Texture $benchWarning '#A94632'

        # One continuous slab and cabinet shell make both placed halves opaque
        # from below. Detail plates sit proud of the shell rather than sharing a
        # face, avoiding the coplanar flicker visible in the review screenshot.
        Add-Block sealed_floor 0 0 0 32 2 16 $benchSteel component_root
        Add-Block cabinet_shell .6 2 .8 31.4 9.65 15.2 $benchPaint component_root
        Add-Block toe_kick .6 2 .18 31.4 2.6 .8 $T.dark component_root
        Add-Block worktop 0 9.65 0 32 11.15 16 $benchTop component_root
        Add-Block front_lip 0 9.25 .25 32 10.15 1.05 $benchSteel component_root

        # Front storage and service access. Faces are offset from the cabinet by
        # 0.25 model units and handles project farther, so none are coplanar.
        foreach($x in @(1.35,22.65)){
            foreach($y in @(2.45,5.55)){
                Add-Block drawer_face $x $y .52 ($x+8) ($y+2.55) .82 $benchSteel component_root
                Add-Block drawer_handle ($x+2.45) ($y+1.02) .18 ($x+5.55) ($y+1.48) .52 $T.brass component_root
            }
        }
        Add-Block service_door_left 10.05 2.35 .5 15.75 8.75 .82 $T.dark component_root
        Add-Block service_door_right 16.25 2.35 .5 21.95 8.75 .82 $T.dark component_root
        Add-Block service_handle_left 14.55 4.65 .16 15.15 6.5 .5 $T.brass component_root
        Add-Block service_handle_right 16.85 4.65 .16 17.45 6.5 .5 $T.brass component_root
        Add-Block warning_bar 10.8 7.85 .14 21.2 8.35 .5 $benchWarning component_root

        # Recessed, rubber-lined longitudinal bed for a missile body. Three
        # spaced U cradles support it across the full two-block assembly length.
        Add-Block assembly_bed 1.4 11.15 3.15 30.6 11.55 12.85 $benchRubber component_root
        Add-Block guide_rail_front 1.8 11.55 3.25 30.2 12.05 4.1 $benchSteel component_root
        Add-Block guide_rail_rear 1.8 11.55 11.9 30.2 12.05 12.75 $benchSteel component_root
        foreach($centre in @(5.3,16,26.7)){
            $x0=$centre-1.15; $x1=$centre+1.15
            Add-Block cradle_bridge $x0 12.05 4.05 $x1 12.55 11.95 $benchSteel component_root
            Add-Block cradle_front $x0 12.55 4.05 $x1 14.65 5.25 $benchSteel component_root
            Add-Block cradle_rear $x0 12.55 10.75 $x1 14.65 11.95 $benchSteel component_root
            Add-Block cradle_pad_front $x0 14.65 4.15 $x1 15.05 5.45 $benchRubber component_root
            Add-Block cradle_pad_rear $x0 14.65 10.55 $x1 15.05 11.85 $benchRubber component_root
        }

        # Low rear service rail and a keyed component tray keep the work surface
        # readable without turning the bench into a tall machine cabinet.
        Add-Block rear_service_rail .8 11.15 14.35 31.2 12.55 15.45 $benchPaint component_root
        Add-Block keyed_parts_tray 2.1 11.55 1.25 9.25 12.15 2.75 $T.stock component_root
        foreach($x in @(3.0,5.1,7.2)){ Add-Block keyed_slot $x 12.15 1.45 ($x+1.1) 12.45 2.55 $T.dark component_root }
        Add-Block control_panel 23 11.55 1.2 29.8 12.35 2.8 $benchSteel component_root
        Add-Block control_screen 24.05 12.35 1.48 27.25 12.62 2.52 $T.green component_root
        Add-Block emergency_stop 28.2 12.35 1.62 29.15 12.85 2.38 $benchWarning component_root
    }
}

function Build-DerivedComponent([string]$Kind,[string]$Id) {
    $sourceId=$Kind
    $sourcePath=if($Kind -eq 'icbm_body'){Join-Path $PSScriptRoot 'missiles/single/conventional_missile.bbmodel'}
        elseif($Kind -in @('anti_air_body','anti_air_controller_ballistic','anti_air_controller_self_destruct')){
            $variant=if($Kind -eq 'anti_air_controller_self_destruct'){'mk2'}else{'mk1'}
            Join-Path $OutputRoot "missiles/anti_air/anti_air_missile_$variant.bbmodel"
        }else{
            $variant=if($Kind -like '*cluster*'){'cluster'}else{'single'}
            Join-Path $PSScriptRoot "missiles/$variant/$Kind.bbmodel"
        }
    $model=Get-Content -Raw $sourcePath | ConvertFrom-Json
    $groupName=if($Kind -like 'anti_air*'){'payload'}else{'payload_cone'}
    $group=$model.groups | Where-Object name -eq $groupName | Select-Object -First 1
    function Find-ComponentNode($nodes,$id){ foreach($n in $nodes){if($n -is [string]){continue};if($n.uuid -eq $id){return $n};$found=Find-ComponentNode $n.children $id;if($found){return $found}} }
    function Component-Ids($node){foreach($c in $node.children){if($c -is [string]){$c}else{Component-Ids $c}}}
    $payloadIds=@(Component-Ids (Find-ComponentNode $model.outliner $group.uuid))
    if($Kind -like 'anti_air*'){
        $payloadIds+=@($model.elements | Where-Object name -eq 'forward_ident' | ForEach-Object uuid)
    }
    $body=$Kind -in @('icbm_body','anti_air_body')
    $model.elements=@($model.elements | Where-Object {if($body){$payloadIds -notcontains $_.uuid}else{$payloadIds -contains $_.uuid -or ($_.name -match 'cluster|warhead|payload' -and $_.from[1] -ge 6)}})
    if($model.elements.Count -eq 0){throw "Empty derived component $Id"}
    $model.name=$Id; $model.groups=@();$model.outliner=@($model.elements | ForEach-Object uuid)
    $json=($model | ConvertTo-Json -Depth 80 -Compress).Replace('/', '\u002f')
    Invoke-McpTool risky_eval @{code="Codecs.project.parse($json);Project.name='$Id';Canvas.updateAll();true"} | Out-Null
}

$palette=@{
    body='catalog_body'; dark='catalog_dark'; accent='catalog_accent'; stock='catalog_stock'; brass='catalog_brass';
    glow='catalog_glow'; green='catalog_green'; blue='catalog_blue'; warning='catalog_warning'
}
$colors=@{
    catalog_body='#59634F'; catalog_dark='#20292B'; catalog_accent='#A68A5B'; catalog_stock='#604735';
    catalog_brass='#C3A45A'; catalog_glow='#45C7C4'; catalog_green='#6E8B4B'; catalog_blue='#477FA5';
    catalog_warning='#D6523C'
}

$specs=@(
    @{id='missile_workbench';category='machines';build='component';arg='workbench'},
    @{id='icbm_body';category='components';build='derived';arg='icbm_body'},
    @{id='anti_air_body';category='components';build='derived';arg='anti_air_body'},
    @{id='targeting_chip_tier_1';category='components';build='component';arg='chip_1'},
    @{id='targeting_chip_tier_2';category='components';build='component';arg='chip_2'},
    @{id='targeting_chip_tier_3';category='components';build='component';arg='chip_3'},
    @{id='anti_air_controller_ballistic';category='components';build='derived';arg='anti_air_controller_ballistic'},
    @{id='anti_air_controller_self_destruct';category='components';build='derived';arg='anti_air_controller_self_destruct'},
    @{id='pistol';category='firearms';build='gun';arg='pistol'},
    @{id='assault_rifle';category='firearms';build='gun';arg='rifle'},
    @{id='sniper_rifle';category='firearms';build='gun';arg='sniper'},
    @{id='pistol_bullet';category='projectiles';build='bullet';arg='pistol'},
    @{id='rifle_bullet';category='projectiles';build='bullet';arg='rifle'},
    @{id='sniper_bullet';category='projectiles';build='bullet';arg='sniper'},
    @{id='falling_warhead';category='projectiles';build='shell';arg='falling_warhead'},
    @{id='artillery_shell';category='projectiles';build='shell';arg='artillery_shell'},
    @{id='anti_air_missile_mk1';category='missiles/anti_air';build='missile';arg='aa_mk1'},
    @{id='anti_air_missile_mk2';category='missiles/anti_air';build='missile';arg='aa_mk2'},
    @{id='artillery_cannon';category='machines';build='artillery'},
    @{id='radar_station';category='machines';build='radar'},
    @{id='missile_silo';category='machines';build='silo'},
    @{id='missile_silo_large';category='machines';build='silo_large'},
    @{id='missile_silo_guidance_support_tier_1';category='machines/supports';build='support';arg=1},
    @{id='missile_silo_guidance_support_tier_2';category='machines/supports';build='support';arg=2},
    @{id='missile_silo_guidance_support_tier_3';category='machines/supports';build='support';arg=3},
    @{id='rocket_launcher';category='firearms';build='launcher'},
    @{id='he_rocket';category='projectiles';build='missile';arg='he_rocket'},
    @{id='radar';category='equipment';build='utility';arg='tablet'},
    @{id='target_designator';category='equipment';build='utility';arg='radar_gun'},
    @{id='remote_launch_designator';category='equipment';build='utility';arg='remote_designator'},
    @{id='radar_linking_tool';category='equipment';build='utility';arg='linking_tool'},
    @{id='pistol_ammo';category='ammunition';build='utility';arg='pistol_mag'},
    @{id='rifle_ammo';category='ammunition';build='utility';arg='rifle_mag'},
    @{id='sniper_ammo';category='ammunition';build='utility';arg='sniper_mag'},
    @{id='anti_air_gun_ammo';category='ammunition';build='utility';arg='ammo_box'},
    @{id='phalanx_turret';category='machines';build='phalanx'},
    @{id='radar_display_panel';category='machines';build='utility';arg='panel'},
    @{id='item_pipe';category='equipment';build='utility';arg='pipe'},
    @{id='pipe_wrench';category='equipment';build='utility';arg='wrench'},
    @{id='fire_hose';category='equipment';build='utility';arg='hose'},
    @{id='fire_extinguisher';category='equipment';build='utility';arg='extinguisher'},
    @{id='master_explosive_test_stick';category='debug';build='utility';arg='stick'},
    @{id='anti_air_test_stick';category='debug';build='utility';arg='stick'},
    @{id='fire_debug_stick';category='debug';build='utility';arg='stick'}
)

$yieldSpecs=@(
    @{id='high_explosive';accent='#C85B26'},
    @{id='high_capacity_he';accent='#D58725'},
    @{id='conventional';accent='#778B55'},
    @{id='heavy_conventional';accent='#667077'},
    @{id='tactical_nuclear';accent='#B58135'},
    @{id='strategic_nuclear';accent='#9A7036'},
    @{id='heavy_nuclear';accent='#86626A'}
)

foreach($yield in $yieldSpecs){
    foreach($cluster in @($false,$true)){
        $suffix = if($cluster){'_cluster_tnt'} else {'_tnt'}
        $specs += @{id=$yield.id + $suffix; category='explosives/tnt'; build='tnt'; accent=$yield.accent; cluster=$cluster}
        $missileSuffix=if($cluster){'_cluster_missile'}else{'_missile'}
        $specs += @{id=$yield.id + $missileSuffix + '_warhead'; category='components/warheads'; build='derived'; arg=$yield.id + $missileSuffix}
    }
}

$allSpecs = @($specs)
if($OnlyIds.Count -gt 0){
    $knownIds = @($allSpecs | ForEach-Object { [string]$_.id })
    $unknown = @($OnlyIds | Where-Object { $_ -notin $knownIds })
    if($unknown.Count -gt 0){ throw "Unknown model id(s): $($unknown -join ', ')" }
    $specs = @($allSpecs | Where-Object { [string]$_.id -in $OnlyIds })
}

New-McpSession
Invoke-McpTool list_export_formats @{} | Out-Null
$manifest = @()

foreach($spec in $specs){
    $dir = Join-Path $OutputRoot $spec.category
    $previewDir = Join-Path $OutputRoot ('previews/' + $spec.category)
    New-Item -ItemType Directory -Force -Path $dir, $previewDir | Out-Null

    $modelPath = Join-Path $dir ($spec.id + '.bbmodel')
    $previewPath = Join-Path $previewDir ($spec.id + '.png')

    Invoke-McpTool create_project @{name = $spec.id; format = 'generic'} -AllowError | Out-Null
    Invoke-McpTool risky_eval @{code = "Project.name='$($spec.id)';Project.saved=false;Project.name"} | Out-Null

    $info = Invoke-McpTool get_project_info @{}
    $project = (($info | Where-Object type -eq 'text' | Select-Object -First 1).text | ConvertFrom-Json)
    if($project.format.id -ne 'free' -or $project.counts.outliner_elements -ne 0){
        throw "Bad preflight state for $($spec.id)"
    }

    Invoke-McpTool list_outline @{include_cubes=$true; include_meshes=$true; max_depth=2} | Out-Null
    Invoke-McpTool list_textures @{} | Out-Null
    Invoke-McpTool save_checkpoint @{name="Blank $($spec.id)"} | Out-Null

    $local = @{}
    foreach($key in $palette.Keys){
        if($spec.build -eq 'derived'){ continue }
        $name = "$($spec.id)_$key"
        $local[$key] = $name
        Texture $name $colors[$palette[$key]]
    }

    switch($spec.build){
        gun { Build-Gun $spec.arg $local }
        bullet { Build-Bullet $spec.arg $local }
        missile { Build-Missile $spec.arg $local }
        shell { Build-Shell $spec.arg $local }
        artillery { Build-Artillery $local }
        radar { Build-Radar $local }
        silo { Build-Silo $local }
        silo_large { Build-Silo $local $true }
        support { Build-Support $spec.arg $local }
        launcher { Build-Launcher $local }
        utility { Build-Utility $spec.arg $local }
        tnt { Build-Tnt $spec.id $spec.accent $spec.cluster $local }
        phalanx { $script:turretTextures=@{}; Build-Phalanx $local }
        component { Build-Component $spec.arg $local }
        derived { Build-DerivedComponent $spec.arg $spec.id }
    }

    Invoke-McpTool save_checkpoint @{name="Completed $($spec.id)"} | Out-Null
    $outline = Invoke-McpTool list_outline @{include_cubes=$true; include_meshes=$true; max_depth=8}
    $data = (($outline | Where-Object type -eq 'text' | Select-Object -First 1).text | ConvertFrom-Json)
    if($data.counts.cubes -lt 2 -or $data.counts.meshes -ne 0){
        throw "Invalid geometry for $($spec.id)"
    }

    Invoke-McpTool export_model @{codec_id='project'; path=$modelPath; max_content_length=0} | Out-Null

    Invoke-McpTool select_all_of_type @{type='cube'} | Out-Null
    Invoke-McpTool trigger_action @{action='focus_on_selection'; confirmDialog = $false} | Out-Null
    Invoke-McpTool risky_eval @{code='Outliner.selected.length=0;true'} | Out-Null
    $previewModel=Get-Content -Raw $modelPath | ConvertFrom-Json
    $minimum=@();$maximum=@();$centre=@()
    foreach($axis in 0..2){
        $minimum+=($previewModel.elements | ForEach-Object {$_.from[$axis]} | Measure-Object -Minimum).Minimum
        $maximum+=($previewModel.elements | ForEach-Object {$_.to[$axis]} | Measure-Object -Maximum).Maximum
        $centre+=($minimum[$axis]+$maximum[$axis])*.5
    }
    $span=([double[]]@(0..2 | ForEach-Object {$maximum[$_]-$minimum[$_]}) | Measure-Object -Maximum).Maximum
    Invoke-McpTool set_camera_angle @{projection='perspective';position=@(($centre[0]+$span*1.15),($centre[1]+$span*.75),($centre[2]-$span*1.15));target=$centre} | Out-Null
    $shot = Invoke-McpTool capture_screenshot @{}
    $image = $shot | Where-Object type -eq image | Select-Object -First 1
    if(-not $image){
        throw "No preview for $($spec.id)"
    }
    $previewTemporary=$previewPath+'.pending'
    [IO.File]::WriteAllBytes($previewTemporary,[Convert]::FromBase64String($image.data))
    [IO.File]::Move($previewTemporary,$previewPath,$true)
    if($spec.id -eq 'missile_silo_large'){
        Invoke-McpTool risky_eval @{code="Group.all.find(g=>g.name==='left_door').rotation[2]=82;Group.all.find(g=>g.name==='right_door').rotation[2]=-82;Canvas.updateAll();true"} | Out-Null
        $openShot=Invoke-McpTool capture_screenshot @{}
        $openImage=$openShot | Where-Object type -eq image | Select-Object -First 1
        if(-not $openImage){throw 'No open-door preview for missile_silo_large'}
        $openPath=Join-Path $previewDir 'missile_silo_large_open.png'
        $openTemporary=$openPath+'.pending'
        [IO.File]::WriteAllBytes($openTemporary,[Convert]::FromBase64String($openImage.data))
        [IO.File]::Move($openTemporary,$openPath,$true)
    }

    $manifest += [pscustomobject]@{
        id       = $spec.id
        category = $spec.category
        kind     = $spec.build
        cubes    = [int]$data.counts.cubes
        meshes   = [int]$data.counts.meshes
        model    = ($modelPath.Substring($OutputRoot.Length + 1) -replace '\\','/')
        preview  = ($previewPath.Substring($OutputRoot.Length + 1) -replace '\\','/')
    }

    Write-Host "Exported $($spec.id) ($($data.counts.cubes) cubes)"
}

$manifestPath = Join-Path $OutputRoot 'gameplay_model_manifest.json'
if($OnlyIds.Count -gt 0 -and (Test-Path -LiteralPath $manifestPath)){
    $existing = @(Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json)
    $existingById = @{}; foreach($entry in $existing){ $existingById[[string]$entry.id] = $entry }
    $updatesById = @{}; foreach($entry in $manifest){ $updatesById[[string]$entry.id] = $entry }
    $merged = [Collections.Generic.List[object]]::new()
    foreach($spec in $allSpecs){
        $id = [string]$spec.id
        if($updatesById.ContainsKey($id)){ $merged.Add($updatesById[$id]) }
        elseif($existingById.ContainsKey($id)){ $merged.Add($existingById[$id]) }
    }
    $manifest = @($merged)
}
$manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding utf8
Write-Host "Wrote $($manifest.Count) model entries."

$runtimeExporter = Join-Path $PSScriptRoot 'export_gameplay_runtime_assets.ps1'
if(-not (Test-Path -LiteralPath $runtimeExporter)){
    throw "Runtime Blockbench exporter is missing: $runtimeExporter"
}
& $runtimeExporter -CatalogRoot $OutputRoot
