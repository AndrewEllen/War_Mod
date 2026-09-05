# Launch Controller

The Launch Controller links up to 64 Missile Silos and launches the linked group from one screen, one remote, or one redstone pulse. Each silo keeps its own ammunition and saved target.

## Crafting

Craft one controller with five iron ingots, two redstone dust, one Tier 1 Targeting Chip, and one comparator:

```text
III
RTR
ICI
```

`I` is an iron ingot, `R` is redstone dust, `T` is a Tier 1 Targeting Chip, and `C` is a comparator. Mine the placed controller with a pickaxe to recover it.

## Link silos with the Controller Linking Tool

1. Hold the Controller Linking Tool and use the Launch Controller once to select it.
2. Keep holding the tool and use each Missile Silo that should join the group. The controller selection stays on the tool, so you can walk through the installation and add silos without returning to the controller each time.
3. Using an already linked silo does not remove it. Open the controller and use that silo's **Remove** button when you want to unlink it.
4. Sneak-use the tool in the air when you want to clear its selected controller.

The controller screen lists coordinates, dimension, live status when the silo is loaded, and a **Remove** button for every link.

The original Remote Launch Designator linking method remains available: sneak-use the remote on a silo to select it, then sneak-use the Launch Controller to add or remove that silo.

## Launch the group

- Open the controller and choose **Launch all (saved targets)** to launch every ready silo at its own saved target.
- Send a redstone signal into an unpowered controller to do the same. It reacts only to the rising edge, so turn the signal off before the next pulse.
- To aim the whole group with the remote, crouch-use the remote in the air to clear its current link, then sneak-use the Launch Controller. A normal remote launch supplies one shared aimed target to every linked ready silo.

The controller records a short batch result in its screen. Missing, unloaded, empty, busy, or otherwise invalid silos do not stop the remaining valid silos from receiving the launch request.
