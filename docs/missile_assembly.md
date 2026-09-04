# Missile assembly

Craft a Missile Workbench, then place one body, targeting chip and warhead/controller into its three input slots. It automatically assembles one missile every half second while matching components and output space are available.

- **ICBM body:** accepts any of the 14 missile warheads (seven yields, single or cluster).
- **Anti-air body:** accepts a ballistic controller or a self-destruct controller. The controllers retain the existing interceptor fallback behavior; their recipes include TNT.
- **Targeting chips:** Tier 1, 2 and 3 give strategic target error of at most 50, 25 and 0 blocks respectively; interceptor miss allowances are 15, 10 and 0 blocks.
- **Automation:** top inserts bodies; north/south insert chips; east/west insert warheads/controllers; bottom extracts completed missiles. Hoppers and item pipes use the same sided inventory rules.

All 48 assembled combinations are in the creative inventory. Tier and payload are preserved in the resulting stack, saved data and silo launch. Different tiers cannot merge into one silo stack. Existing missile stacks without chip data use Tier 1. Guidance support registry IDs remain readable for old saves, but their recipes and creative entries are removed, and they no longer affect launches.

Loaded missiles remain concealed. A launch reserves one missile, opens the doors over 12 ticks, raises the missile over the next 12 ticks, then starts flight. Doors remain open for 10 more ticks before closing over 20 ticks. Reloading happens behind closed doors. Interceptor target solutions are calculated when flight starts so the opening delay does not reuse an old target position.

Newly placed silos occupy 5 by 5 blocks with wider doors. Existing 3-by-3 silos retain their original footprint and remain functional without modifying the surrounding world. New registrations require a game restart; resource reloading alone cannot add the workbench or components.

## Runtime acceptance

The assembly tests cover all payload/tier combinations, incompatible inputs and prevention of mixed-tier stacks. Live validation also exercised top/side hopper insertion, bottom extraction, and two consecutive silo launches through the last missile. See [validation results](2026-09-04-fire-and-gameplay-validation.md). Full/incompatible output recovery, every pipe orientation and interruption during launch were not individually exercised in the live world.
