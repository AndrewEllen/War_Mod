package com.andye.warmod.acoustics;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.model.AcousticDistanceProfile;
import com.andye.warmod.acoustics.model.AcousticDistanceSound;
import com.andye.warmod.acoustics.model.AcousticResponseProfile;
import com.andye.warmod.acoustics.model.AcousticSoundDefinition;
import java.util.List;
import net.minecraft.resources.Identifier;

public final class AcousticSounds {
	public static final Identifier LARGE_EXPLOSION_ID = id("large_explosion");
	public static final Identifier ICBM_ENGINE_RUMBLE_ID = id("icbm_engine_rumble");
	public static final Identifier TERMINAL_DESCENT_RUSH_ID = id("terminal_descent_rush");
	public static final Identifier TERMINAL_SONIC_BOOM_ID = id("terminal_sonic_boom");
	public static final Identifier WARHEAD_IMPACT_THUD_ID = id("warhead_impact_thud");
	public static final Identifier TACTICAL_HE_EXPLOSION_ID = id("tactical_he_explosion");
	public static final Identifier ARTILLERY_FIRE_ID = id("artillery_fire");
	public static final Identifier PISTOL_FIRE_ID = id("pistol_fire");
	public static final Identifier RIFLE_FIRE_ID = id("rifle_fire");
	public static final Identifier SNIPER_FIRE_ID = id("sniper_fire");
	public static final Identifier BULLET_CRACK_ID = id("bullet_crack");
	public static final Identifier BULLET_IMPACT_ID = id("bullet_impact");
	private static boolean registered;

	private AcousticSounds() {
	}

	public static void register() {
		if (registered) return;
		register(LARGE_EXPLOSION_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_NEAR_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_MEDIUM_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_FAR_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_EXTREME_ID,
			new double[]{0, 90, 210, 300, 1536},
			new float[]{1.15F, 1.20F, 1.35F, 1.55F}, .008, true,
			AcousticResponseProfile.EXPLOSION);
		register(ICBM_ENGINE_RUMBLE_ID,
			ModSoundEvents.MISSILE_ENGINE_NEAR_ID,
			ModSoundEvents.MISSILE_ENGINE_MEDIUM_ID,
			ModSoundEvents.MISSILE_ENGINE_FAR_ID,
			ModSoundEvents.MISSILE_ENGINE_EXTREME_ID,
			new double[]{0, 140, 400, 850, 1536},
			new float[]{1, 1.10F, 1.25F, 1.40F}, .006, false,
			AcousticResponseProfile.STANDARD);
		register(TERMINAL_DESCENT_RUSH_ID,
			ModSoundEvents.TERMINAL_RUSH_NEAR_ID,
			ModSoundEvents.TERMINAL_RUSH_MEDIUM_ID,
			ModSoundEvents.TERMINAL_RUSH_FAR_ID,
			ModSoundEvents.TERMINAL_RUSH_EXTREME_ID,
			new double[]{0, 120, 320, 750, 1536},
			new float[]{1, 1.10F, 1.20F, 1.30F}, .006, false,
			AcousticResponseProfile.STANDARD);
		register(TERMINAL_SONIC_BOOM_ID,
			ModSoundEvents.SONIC_BOOM_NEAR_ID,
			ModSoundEvents.SONIC_BOOM_MEDIUM_ID,
			ModSoundEvents.SONIC_BOOM_FAR_ID,
			ModSoundEvents.SONIC_BOOM_EXTREME_ID,
			new double[]{0, 160, 400, 850, 1536},
			new float[]{1.10F, 1.20F, 1.35F, 1.50F}, .006, true,
			AcousticResponseProfile.STANDARD);
		register(WARHEAD_IMPACT_THUD_ID,
			ModSoundEvents.IMPACT_THUD_NEAR_ID,
			ModSoundEvents.IMPACT_THUD_MEDIUM_ID,
			ModSoundEvents.IMPACT_THUD_FAR_ID,
			ModSoundEvents.IMPACT_THUD_EXTREME_ID,
			new double[]{0, 120, 300, 700, 1536},
			new float[]{.85F, .95F, 1.05F, 1.15F}, .008, false,
			AcousticResponseProfile.STANDARD);
		register(TACTICAL_HE_EXPLOSION_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_NEAR_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_MEDIUM_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_FAR_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_EXTREME_ID,
			new double[]{0, 65, 145, 220, 480},
			new float[]{.72F, .58F, .38F, .20F}, .018, true,
			AcousticResponseProfile.EXPLOSION);
		register(ARTILLERY_FIRE_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_NEAR_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_MEDIUM_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_FAR_ID,
			ModSoundEvents.PROTOTYPE_EXPLOSION_EXTREME_ID,
			new double[]{0, 110, 280, 620, 1_200},
			new float[]{.92F, .72F, .48F, .26F}, .009, true,
			AcousticResponseProfile.EXPLOSION);
		register(PISTOL_FIRE_ID,
			ModSoundEvents.PISTOL_NEAR_ID, ModSoundEvents.PISTOL_MEDIUM_ID,
			ModSoundEvents.PISTOL_FAR_ID, ModSoundEvents.PISTOL_EXTREME_ID,
			new double[]{0, 75, 190, 430, 900},
			new float[]{1.0F, .82F, .56F, .30F}, .008, true,
			AcousticResponseProfile.FIREARM);
		register(RIFLE_FIRE_ID,
			ModSoundEvents.RIFLE_NEAR_ID, ModSoundEvents.RIFLE_MEDIUM_ID,
			ModSoundEvents.RIFLE_FAR_ID, ModSoundEvents.RIFLE_EXTREME_ID,
			new double[]{0, 100, 280, 650, 1_400},
			new float[]{1.12F, .94F, .68F, .38F}, .006, true,
			AcousticResponseProfile.FIREARM);
		register(SNIPER_FIRE_ID,
			ModSoundEvents.SNIPER_NEAR_ID, ModSoundEvents.SNIPER_MEDIUM_ID,
			ModSoundEvents.SNIPER_FAR_ID, ModSoundEvents.SNIPER_EXTREME_ID,
			new double[]{0, 130, 360, 850, 1_900},
			new float[]{1.24F, 1.08F, .78F, .44F}, .005, true,
			AcousticResponseProfile.FIREARM);
		register(BULLET_CRACK_ID,
			ModSoundEvents.BULLET_CRACK_NEAR_ID, ModSoundEvents.BULLET_CRACK_MEDIUM_ID,
			ModSoundEvents.BULLET_CRACK_FAR_ID, ModSoundEvents.BULLET_CRACK_EXTREME_ID,
			new double[]{0, 45, 130, 320, 700},
			new float[]{1.0F, .76F, .46F, .22F}, .010, false,
			AcousticResponseProfile.FIREARM);
		register(BULLET_IMPACT_ID,
			ModSoundEvents.BULLET_IMPACT_NEAR_ID, ModSoundEvents.BULLET_IMPACT_MEDIUM_ID,
			ModSoundEvents.BULLET_IMPACT_FAR_ID, ModSoundEvents.BULLET_IMPACT_EXTREME_ID,
			new double[]{0, 24, 65, 130, 240},
			new float[]{.90F, .68F, .40F, .18F}, .012, false,
			AcousticResponseProfile.FIREARM);
		registered = true;
		WarMod.LOGGER.info("Registered propagated acoustic definitions.");
	}

	private static void register(final Identifier definition, final Identifier near,
		final Identifier medium, final Identifier far, final Identifier extreme,
		final double[] ranges, final float[] volumes, final double minimumGain,
		final boolean echoes, final AcousticResponseProfile responseProfile) {
		AcousticSoundRegistry.register(new AcousticSoundDefinition(definition, List.of(
			new AcousticDistanceSound(AcousticDistanceProfile.NEAR, near,
				ranges[0], ranges[1], volumes[0], 1),
			new AcousticDistanceSound(AcousticDistanceProfile.MEDIUM, medium,
				ranges[1], ranges[2], volumes[1], 1),
			new AcousticDistanceSound(AcousticDistanceProfile.FAR, far,
				ranges[2], ranges[3], volumes[2], 1),
			new AcousticDistanceSound(AcousticDistanceProfile.EXTREME, extreme,
				ranges[3], ranges[4], volumes[3], 1)),
			343.0, ranges[ranges.length - 1], minimumGain, echoes, responseProfile));
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, path);
	}
}
