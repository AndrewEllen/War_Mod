# War Mod

War Mod brings large-scale modern warfare to Minecraft with strategic missiles, artillery, powerful explosives, radar networks, and automated air defence.

The mod is built around battles that feel dramatic and dangerous. Missiles travel across the world, radar systems track incoming threats, defensive weapons attempt to intercept them, and explosions leave lasting damage across the landscape.

## Features

### Strategic missiles

- Build missile silos and load them with up to 16 ICBMs.
- Launch conventional, nuclear, and cluster payloads.
- Select distant targets with coordinate designators.
- Link a remote launch designator to a silo and fire it from elsewhere.
- Improve silo guidance with three support tiers.
- Watch missiles boost, travel, separate, and descend toward their targets.

### Nuclear weapons

- Tactical, strategic, and heavy nuclear yields.
- Large animated mushroom clouds with fire, smoke, and glowing hot cores.
- Expanding and returning shockwaves with distant explosion audio.
- Blinding nuclear flashes and powerful screen shake.
- Deep craters with scorched rock, fused sand, magma cracks, and burning ground.
- Wide wasteland damage that strips forests, chars wood, breaks glass, and changes the atmosphere around the blast site.

### Conventional explosives

- Multiple conventional explosive sizes.
- Single and cluster payloads.
- Placeable timed explosive blocks.
- Dense fireballs, dust fronts, debris, smoke, and terrain-following shockwaves.

### Artillery and handheld weapons

- Target coordinates and fire long-range artillery shells in a high arc.
- Use the rocket launcher with high-explosive, conventional, or nuclear ammunition.
- Fire visible warheads that use the same impact systems as larger strategic weapons.

### Radar and defence

- Build radar stations to detect and track incoming missiles.
- Link physical radar display panels to a station and monitor contacts in the world.
- Deploy Anti-Air Missile Mk I and Mk II interceptors.
- Build Phalanx Anti-Air Turrets with ammunition for close-range automatic defence.

### Logistics

- Move ammunition and supplies through item pipes.
- Configure pipe connections with the Pipe Wrench.
- Use dedicated interfaces for silos, artillery cannons, radar equipment, and defensive weapons.

### Dynamic fire

- Start custom, particle-rendered fires with the Custom Fire Debug Stick.
- Crouch-use the stick to cycle Small, Structure, and Inferno ignition strengths.
- Fire climbs exposed vegetation and structures, follows local wind, and can carry embers across gaps.
- Strategic explosions create an outward pressure wind followed by a weaker return flow for fire only.
- Flowing or placed Minecraft water suppresses fire; the Fire Hose and Fire Extinguisher provide directed suppression.
- Fire fuel overrides are data-packable through the `war_mod:fire_fuel_high`, `fire_fuel_medium`, `fire_fuel_low`, and `fire_immune` block tags.

### Visual compatibility

War Mod includes rendering paths for standard Minecraft, Sodium, Iris shaders, and Distant Horizons. Explosion effects are designed to remain visible at long range while respecting nearby terrain and distant terrain detail.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API
- Java 25

Sodium, Iris, and Distant Horizons are optional.

## Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Install Fabric API.
3. Place the War Mod JAR file in your Minecraft `mods` folder.
4. Launch the game with the Fabric profile.

## Warning

The larger weapons can permanently alter a very wide area. Back up important worlds before using strategic or heavy nuclear explosives.

## License

All rights reserved.
