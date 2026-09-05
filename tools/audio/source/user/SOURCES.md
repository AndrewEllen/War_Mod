# User-supplied firearm and hearing source recordings

These eleven M4A files were supplied directly by the project owner on 2026-08-24 for use in
War Mod. They are preserved here as the reproducible masters for the generated in-game OGGs.

- `pistol_shooting.m4a`: close pistol report
- `automatic_rifle_shot.m4a`: multiple automatic-rifle report variations
- `sniper_shot_close.m4a`: close sniper report
- `sniper_shot_medium_distance.m4a`: medium-distance sniper report
- `far_away_gunshot.m4a`: shared distant firearm report
- `bullet_pass_by.m4a`: multiple projectile flyby variations
- `bullet_pass_by_hit_metal.m4a`: combined flyby and hard-impact variations
- `bullet_ricochet.m4a`: multiple hard-surface ricochet variations
- `bullet_impact_dirt.m4a`: soft/default terrain impact
- `bullet_impact_body.m4a`: unarmoured body impact
- `ear_ringing_sfx.m4a`: shockwave hearing-trauma ringing

Run `tools/audio/build_firearm_audio.ps1` and `tools/audio/build_hearing_audio.ps1` with ffmpeg
available to regenerate the shipped mono positional firearm assets and stereo listener-relative
hearing assets. No third-party licence is asserted here; provenance is the owner's direct supply.
