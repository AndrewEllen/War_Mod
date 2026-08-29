param(
    [string]$Endpoint = 'http://127.0.0.1:3000/bb-mcp',
    [string]$OutputRoot = (Join-Path $PSScriptRoot 'gameplay_catalog')
)

$ErrorActionPreference = 'Stop'
$script:requestId = 1

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

function Texture([string]$Name,[string]$Color) {
    Invoke-McpTool create_texture @{
        name       = $Name
        width      = 32
        height     = 32
        fill_color = $Color
        layer_name = 'base'
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
        (Box track_l -16 0 -13 -10 4 13), (Box track_r 10 0 -13 16 4 13),
        (Box track_l_top -15 4 -10 -11 6 10), (Box track_r_top 11 4 -10 15 6 10),
        (Box glacis -10 4 -12 10 7 -7), (Box engine_deck -10 4 5 10 7 12),
        (Box hull_center -11 3 -8 11 8 8), (Box turret_race -7 7 -7 7 9 7),
        (Box tow_eye_l -14 1 -14 -11 3 -12), (Box tow_eye_r 11 1 -14 14 3 -12)
    ) $T.dark fixed_base

    Cubes @(
        (Box wheel_l_1 -16.3 .7 -10.5 -9.7 3.6 -6.5), (Box wheel_l_2 -16.3 .7 -4.3 -9.7 3.6 -.3),
        (Box wheel_l_3 -16.3 .7 2.0 -9.7 3.6 6.0), (Box wheel_l_4 -16.3 .7 8.0 -9.7 3.6 12.0),
        (Box wheel_r_1 9.7 .7 -10.5 16.3 3.6 -6.5), (Box wheel_r_2 9.7 .7 -4.3 16.3 3.6 -.3),
        (Box wheel_r_3 9.7 .7 2.0 16.3 3.6 6.0), (Box wheel_r_4 9.7 .7 8.0 16.3 3.6 12.0)
    ) $T.stock fixed_base

    Cubes @(
        (Box spade_l -19 -1 7 -14 1.2 14), (Box spade_r 14 -1 7 19 1.2 14),
        (Box stabiliser_l -14 1 9 -10 3.2 18), (Box stabiliser_r 10 1 9 14 3.2 18)
    ) $T.accent fixed_base

    Cubes @(
        (Box turret_skirt -9 8 -8 9 11 8), (Box turret_body -8 10 -7 8 16 7),
        (Box turret_front -7 11 -10 7 15 -6), (Box bustle -7 11 6 7 15 11),
        (Box trunnion_block_l -10 12 -5 -6 17 3), (Box trunnion_block_r 6 12 -5 10 17 3),
        (Box commander_hatch -3 16 1 3 17 6), (Box sight_housing 4 15 -6 7 20 -2)
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

function Build-Silo([hashtable]$T) {
    Add-BbGroup silo_root
    Add-BbGroup foundation silo_root
    Add-BbGroup hatch silo_root
    Add-BbGroup controls silo_root

    Cubes @(
        (Box slab_outer -24 0 -24 24 3 24), (Box slab_inner -22 3 -22 22 5 22),
        (Box shaft_n -12 -26 -12 12 3 -8), (Box shaft_s -12 -26 8 12 3 12),
        (Box shaft_w -12 -26 -8 -8 3 8), (Box shaft_e 8 -26 -8 12 3 8),
        (Box blast_wall_n -22 5 -22 22 9 -18), (Box blast_wall_s -22 5 18 22 9 22),
        (Box blast_wall_w -22 5 -18 -18 9 18), (Box blast_wall_e 18 5 -18 22 9 18)
    ) $T.dark foundation

    Cubes @(
        (Box ring_n -14 -2 -14 14 2 -10), (Box ring_s -14 -2 10 14 2 14),
        (Box ring_w -14 -2 -10 -10 2 10), (Box ring_e 10 -2 -10 14 2 10),
        (Box ring_diag_nw -11 -2 -13 -8 2 -10), (Box ring_diag_ne 8 -2 -13 11 2 -10),
        (Box ring_diag_sw -11 -2 10 -8 2 13), (Box ring_diag_se 8 -2 10 11 2 13)
    ) $T.body foundation

    Cubes @(
        (Box door_l -11 5 -12 -.6 8 12), (Box door_r .6 5 -12 11 8 12),
        (Box door_rib_l -10.5 8 -11.5 -.8 9.2 11.5), (Box door_rib_r .8 8 -11.5 10.5 9.2 11.5),
        (Box rail_l -15 4 -13 -12 7 13), (Box rail_r 12 4 -13 15 7 13),
        (Box hinge_l -13 5 -11 -10 10 11), (Box hinge_r 10 5 -11 13 10 11)
    ) $T.body hatch

    Cubes @(
        (Box door_stripe_l -10.2 9.1 -1.1 -1.0 9.5 1.1),
        (Box door_stripe_r 1.0 9.1 -1.1 10.2 9.5 1.1),
        (Box bay_light_n -8 5 -17.8 8 7 -17.4), (Box bay_light_s -8 5 17.4 8 7 17.8)
    ) $T.warning hatch

    Cubes @(
        (Box console 14 5 8 21 14 18), (Box console_top 13.5 13 -1 21.5 16 18),
        (Box panel 13.3 9 10 13.6 14 17), (Box keypad 13.2 6 11 13.6 8.5 16),
        (Box vent -21.5 6 8 -21.2 12 17), (Box emergency_box -21.8 7 -15 -18.5 13 -9)
    ) $T.accent controls
    Add-Block screen 13.0 10 11 13.25 13.5 16 $T.glow controls
    Add-Block emergency_lamp -22.0 13 -14 -18.3 15 -10 $T.warning controls
}

function Build-Support([int]$Tier,[hashtable]$T) {
    Add-BbGroup support_root
    Add-BbGroup frame support_root
    Add-BbGroup electronics support_root
    Add-BbGroup antenna support_root

    $h = 8 + ($Tier * 3.2)
    Cubes @(
        (Box plinth -6 0 -6 6 2.2 6), (Box plinth_top -5.2 2.2 -5.2 5.2 3.2 5.2),
        (Box mast -2.4 3.2 -2.4 2.4 $h 2.4),
        (RotBox brace_nw -5.0 2.6 -.6 -3.8 ($h-.8) .6 @(-4.4,2.6,0) @(0,0,-10)),
        (RotBox brace_ne 3.8 2.6 -.6 5.0 ($h-.8) .6 @(4.4,2.6,0) @(0,0,10)),
        (RotBox brace_sw -.6 2.6 -5.0 .6 ($h-.8) -3.8 @(0,2.6,-4.4) @(10,0,0)),
        (RotBox brace_se -.6 2.6 3.8 .6 ($h-.8) 5.0 @(0,2.6,4.4) @(-10,0,0))
    ) $T.dark frame

    for($i = 0; $i -lt $Tier; $i++){
        $baseY = 4.0 + ($i * 3.1)
        Add-Block "processor_$i" 2.35 $baseY -1.8 5.8 ($baseY + 2.0) 1.8 $T.body electronics
        Add-Block "processor_$i`_screen" 5.7 ($baseY+.35) -1.2 6.0 ($baseY + 1.65) 1.2 $T.glow electronics
        Add-Block "power_$i" -5.8 $baseY -1.6 -2.35 ($baseY + 1.8) 1.6 $T.accent electronics
    }

    if($Tier -ge 1){
        Add-Block ring_low -3.0 ($h - 2.5) -3.0 3.0 ($h - 1.5) 3.0 $T.stock frame
    }
    if($Tier -ge 2){
        Cubes @(
            (Box antenna_l -4.8 $h  -.5 -3.4 ($h + 4.2) .5),
            (Box antenna_r 3.4 $h -.5 4.8 ($h + 4.2) .5),
            (Box crossbar -5.2 ($h+3.6) -.7 5.2 ($h+4.4) .7),
            (RotBox sensor_panel -5.8 ($h+1.8) -4.3 -2.4 ($h+4.8) 4.3 @(-3.4,($h+3.3),0) @(0,0,-12))
        ) $T.body antenna
    }
    if($Tier -ge 3){
        Cubes @(
            (Box crown_left -6.0 ($h + 4.8) -2.6 -1.0 ($h + 6.7) 2.6),
            (Box crown_right 1.0 ($h + 4.8) -2.6 6.0 ($h + 6.7) 2.6),
            (Box radar_focus -1.3 ($h + 6.4) -1.1 1.3 ($h + 8.2) 1.1),
            (Box beacon -.55 ($h+8.2) -.55 .55 ($h+10.0) .55)
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
            Add-Block receiver -5.0 -2.2 -2.0 4.0 3.2 2.0 $T.body item_root
            Add-Block sensor_bell 3.2 -3.4 -3.3 8.2 4.4 3.3 $T.dark item_root
            Add-Block sensor_face 7.9 -2.6 -2.5 8.6 3.6 2.5 $T.glow item_root
            Add-Block hood 2.7 4.0 -3.5 8.7 5.2 3.5 $T.stock item_root
            Add-Block grip -1.2 -9.0 -1.2 1.4 -2.2 1.2 $T.stock item_root
            Add-Block trigger 1.0 -5.0 -1.45 2.2 -3.1 -1.1 $T.warning item_root
            Add-Block top_display -4.2 3.1 -1.4 .8 5.0 1.4 $T.dark item_root
            Add-Block display_glass -3.7 4.8 -.95 .3 5.15 .95 $T.glow item_root
        }
        'linking_tool' {
            Add-Block body -3.6 -6 -1.8 3.6 5.0 1.8 $T.dark item_root
            Add-Block grip -1.4 -9.4 -1.1 1.4 -5.6 1.1 $T.stock item_root
            Add-Block screen -2.6 -.5 -2.05 2.6 3.8 -1.8 $T.glow item_root
            Add-Block port_l -4.3 -4.8 -1.0 -3.4 -2.0 1.0 $T.blue item_root
            Add-Block port_r 3.4 -4.8 -1.0 4.3 -2.0 1.0 $T.green item_root
            Add-Block coil -2.5 5.0 -2.5 2.5 7.4 2.5 $T.accent item_root
            Add-Block aerial -.45 7.4 -.45 .45 11.2 .45 $T.glow item_root
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
            Add-Block handle -0.85 -8.1 -0.85 0.85 4.9 0.85 $T.body item_root
            Add-Block jaw -4.4 2.5 -1.1 -1.2 6.4 1.1 $T.dark item_root
            Add-Block jaw2 1.2 2.5 -1.1 4.4 6.4 1.1 $T.dark item_root
            Add-Block ring -2 3.0 -0.9 2 5.8 0.9 $T.accent item_root
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
            Add-Block casing -7.2 -6.1 -1.6 7.2 6.2 1.6 $T.dark item_root
            Add-Block screen -5.4 -4.4 -1.95 5.4 4.4 -1.75 $T.body item_root
            Add-Block screen_light -5.4 -3.8 -1.98 5.4 3.8 -1.84 $T.glow item_root
            Add-Block button_row -5.2 -5.2 -1.6 5.2 -4.8 -1.6 $T.warning item_root
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
    @{id='missile_silo_guidance_support_tier_1';category='machines/supports';build='support';arg=1},
    @{id='missile_silo_guidance_support_tier_2';category='machines/supports';build='support';arg=2},
    @{id='missile_silo_guidance_support_tier_3';category='machines/supports';build='support';arg=3},
    @{id='rocket_launcher';category='firearms';build='launcher'},
    @{id='he_rocket';category='projectiles';build='missile';arg='he_rocket'},
    @{id='radar';category='equipment';build='utility';arg='radar_gun'},
    @{id='target_designator';category='equipment';build='utility';arg='target_designator'},
    @{id='remote_launch_designator';category='equipment';build='utility';arg='remote_designator'},
    @{id='radar_linking_tool';category='equipment';build='utility';arg='linking_tool'},
    @{id='pistol_ammo';category='ammunition';build='utility';arg='pistol_mag'},
    @{id='rifle_ammo';category='ammunition';build='utility';arg='rifle_mag'},
    @{id='sniper_ammo';category='ammunition';build='utility';arg='sniper_mag'},
    @{id='anti_air_gun_ammo';category='ammunition';build='utility';arg='ammo_box'},
    @{id='phalanx_turret';category='machines';build='utility';arg='turret'},
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
    }
}

New-McpSession
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
        support { Build-Support $spec.arg $local }
        launcher { Build-Launcher $local }
        utility { Build-Utility $spec.arg $local }
        tnt { Build-Tnt $spec.id $spec.accent $spec.cluster $local }
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
    $shot = Invoke-McpTool capture_screenshot @{}
    $image = $shot | Where-Object type -eq image | Select-Object -First 1
    if(-not $image){
        throw "No preview for $($spec.id)"
    }
    [IO.File]::WriteAllBytes($previewPath,[Convert]::FromBase64String($image.data))

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

$manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $OutputRoot 'gameplay_model_manifest.json') -Encoding utf8
Write-Host "Wrote $($manifest.Count) model entries."

$runtimeExporter = Join-Path $PSScriptRoot 'export_gameplay_runtime_assets.ps1'
if(-not (Test-Path -LiteralPath $runtimeExporter)){
    throw "Runtime Blockbench exporter is missing: $runtimeExporter"
}
& $runtimeExporter -CatalogRoot $OutputRoot
