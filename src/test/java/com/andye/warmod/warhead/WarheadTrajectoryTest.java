package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class WarheadTrajectoryTest {
	@Test
	void positionAtTickZeroEqualsStart() {
		Vec3 start = new Vec3(4.0, 220.0, -3.0);
		Vec3 target = new Vec3(40.0, 70.0, 12.0);
		assertEquals(start, WarheadTrajectory.position(start, target, 0.0, 50));
	}

	@Test
	void positionAtFinalTickEqualsTarget() {
		Vec3 start = new Vec3(4.0, 220.0, -3.0);
		Vec3 target = new Vec3(40.0, 70.0, 12.0);
		assertEquals(target, WarheadTrajectory.position(start, target, 50.0, 50));
	}

	@Test
	void downwardTrajectoryDescendsMonotonically() {
		Vec3 start = new Vec3(4.0, 220.0, -3.0);
		Vec3 target = new Vec3(40.0, 70.0, 12.0);
		double previousY = start.y;
		for (int tick = 1; tick <= 50; tick++) {
			double y = WarheadTrajectory.position(start, target, tick, 50).y;
			assertTrue(y <= previousY);
			previousY = y;
		}
	}

	@Test
	void progressIsClamped() {
		assertEquals(0.0, WarheadTrajectory.progress(-10.0, 50));
		assertEquals(1.0, WarheadTrajectory.progress(100.0, 50));
	}

	@Test
	void clusterQuarterUsesGravityAndStillHitsItsAuthoritativeTarget() {
		Vec3 start = new Vec3(0.0, 480.0, 0.0);
		Vec3 target = new Vec3(48.0, 64.0, -36.0);
		int ticks = 100;
		assertEquals(start, WarheadTrajectory.position(start, target, 0.0, ticks, 2, 4));
		assertEquals(target, WarheadTrajectory.position(start, target, ticks, ticks, 2, 4));
		double startVertical = WarheadTrajectory.velocity(start, target, 0.0, ticks, 2, 4).y;
		double endVertical = WarheadTrajectory.velocity(start, target, ticks, ticks, 2, 4).y;
		assertTrue(endVertical < startVertical);
		assertTrue(WarheadTrajectory.position(start, target, 50.0, ticks, 2, 4).y
			> start.lerp(target, 0.5).y);
	}
}
