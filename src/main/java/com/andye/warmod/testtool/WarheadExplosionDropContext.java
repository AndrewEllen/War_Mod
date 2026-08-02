package com.andye.warmod.testtool;

/** Server-thread scope that suppresses only War Mod test-warhead explosion drops. */
public final class WarheadExplosionDropContext {
	private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

	private WarheadExplosionDropContext() { }

	public static void enter() { DEPTH.set(DEPTH.get() + 1); }
	public static boolean isActive() { return DEPTH.get() > 0; }
	public static void exit() {
		int depth = DEPTH.get() - 1;
		if (depth <= 0) DEPTH.remove();
		else DEPTH.set(depth);
	}
}