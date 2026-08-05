package com.andye.warmod.testtool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-thread explosion scope used both to suppress item drops and to capture
 * the blocks actually changed by the vanilla explosion implementation.
 */
public final class WarheadExplosionDropContext {
	private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

	private WarheadExplosionDropContext() {
	}

	public static void enter() {
		Scope scope = ACTIVE.get();
		if (scope == null) ACTIVE.set(new Scope());
		else scope.depth++;
	}

	public static boolean isActive() {
		return ACTIVE.get() != null;
	}

	public static void recordDestroyed(final BlockPos position, final BlockState originalState) {
		Scope scope = ACTIVE.get();
		if (scope == null || position == null || originalState == null || originalState.isAir()) return;
		scope.destroyed.putIfAbsent(position.immutable(), originalState);
	}

	public static List<DestroyedBlock> exitAndCollect() {
		Scope scope = ACTIVE.get();
		if (scope == null) return List.of();
		if (scope.depth > 1) {
			scope.depth--;
			return List.of();
		}
		ACTIVE.remove();
		return scope.destroyed.entrySet().stream()
			.map(entry -> new DestroyedBlock(entry.getKey(), entry.getValue()))
			.toList();
	}

	public static void abort() {
		ACTIVE.remove();
	}

	private static final class Scope {
		private int depth = 1;
		private final Map<BlockPos, BlockState> destroyed = new LinkedHashMap<>();
	}

	public record DestroyedBlock(BlockPos position, BlockState originalState) {
	}
}
