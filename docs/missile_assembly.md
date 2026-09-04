# Missile assembly

Craft a Missile Workbench, which occupies two blocks side by side, then place one body, targeting chip and warhead/controller into its three input slots. Both halves open the same shared inventory and it automatically assembles one missile every half second while matching components and output space are available.

- **ICBM body:** accepts any of the 14 missile warheads (seven yields, single or cluster).
- **Anti-air body:** accepts a ballistic controller or a self-destruct controller. The controllers retain the existing interceptor fallback behavior; their recipes include TNT.
- **Targeting chips:** Tier 1, 2 and 3 give strategic inaccuracy of at most 50, 25 and 0 blocks respectively; interceptor miss allowances are 15, 10 and 0 blocks.
- **Automation:** either workbench half accepts compatible bodies, chips and warheads/controllers from any side, including unsided inventory access, into their keyed input slots. Either half exposes only completed missiles for extraction; components cannot be extracted through automation. Hoppers and item pipes use the same rules.

All 48 assembled combinations are in the creative inventory. Regular ICBMs are ordered from the smallest yield to the largest, with Tiers 1, 2 and 3 together for each yield; cluster ICBMs follow in the same order. Regular warhead parts then follow from smallest yield to largest, followed by cluster warhead parts in that order. ICBM names identify the payload, while the guidance tier appears in the tooltip. Tier and payload are preserved in the resulting stack, saved data and silo launch. Different tiers cannot merge into one silo stack. Existing missile stacks without chip data use Tier 1. Guidance support registry IDs remain readable for old saves, but their recipes and creative entries are removed, and they no longer affect launches.

Loaded missiles remain concealed until the silo doors open and the launch sequence begins. Reloading happens behind closed doors. Interceptor target solutions are calculated when flight starts so the launch does not reuse an old target position.

Newly placed silos occupy 5 by 5 blocks with wider doors. Existing 3-by-3 silos retain their original footprint and remain functional without modifying the surrounding world. A newly placed silo is owned by its placer. Its ownership panel can claim or unclaim the silo and maintain the player whitelist; the owner and listed allies are excluded from its automatic defensive targeting. New registrations require a game restart; resource reloading alone cannot add the workbench or components.
