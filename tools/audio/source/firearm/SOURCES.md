# Firearm source masters

The six firearm masters are trimmed, level-safe derivatives of real firearm recordings from
**The Free Firearm Sound Library**, created by Ben Jaszczak, Brian Nelson, Kevin Heras, and
Matthew Nanney. The project and the OpenGameArt mirror release the library under CC0.

- Source page: https://opengameart.org/content/the-free-firearm-sound-library
- Direct archive: https://opengameart.org/sites/default/files/Prepared%20SFX%20Library.7z
- Retrieved: 2026-08-23
- Licence shown: CC0
- Pistol: Walther PPQ `X_39P.wav` (near), `X_31P.wav` (mid distance)
- Automatic rifle: AR-15 `D_32P.wav` (near), `D_24P.wav` (mid distance)
- Sniper rifle: Tikka Model T3 `W_29P.wav` (near), `W_24P.wav` (mid distance)

`bullet_crackle_cc0.wav` is the CC0 `snd_bulletcrackle.wav` by Julie Damsgaard / Spring Spring
from **Various Sound Effects**.

- Source page: https://opengameart.org/content/various-sound-effects-0
- Direct file: https://opengameart.org/sites/default/files/snd_bulletcrackle.wav
- Retrieved: 2026-08-23
- Licence shown: CC0

Run `tools/audio/build_firearm_audio.ps1` with ffmpeg available to regenerate the shipped mono
OGG distance profiles. Near and medium use their matching microphone perspectives; far and
extreme are filtered from the real mid-distance recordings.
