package com.andye.warmod.antiair.client;

import com.andye.warmod.antiair.AntiAirFlightPhase;
import com.andye.warmod.antiair.client.render.AntiAirMissileMesh;
import com.andye.warmod.icbm.client.render.IcbmLongRangeRenderContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class AntiAirTrailSampler {
    public record Sample(Vec3 position, float age, float width, float alpha) { }

    private AntiAirTrailSampler() { }

    public static List<Sample> sample(final AntiAirVisualState state,
        final long gameTime, final double partial,
        final IcbmLongRangeRenderContext.Lod lod) {
        int count = switch (lod) {
            case NEAR -> 26;
            case MEDIUM -> 14;
            case FAR -> 6;
            case EXTREME -> 3;
        };
        double history = state.phase() == AntiAirFlightPhase.INTERCEPT ? 14.0 : 24.0;
        float density = state.phase() == AntiAirFlightPhase.INTERCEPT ? .52F : 1.0F;
        float nozzleOffset = AntiAirMissileMesh.nozzleY(state.variant());
        ArrayList<Sample> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            float age = index / (float) Math.max(1, count - 1);
            double samplePartial = partial - age * history;
            Vec3 center = state.position(gameTime, samplePartial);
            Vec3 velocity = state.velocity(gameTime, samplePartial);
            if (!center.isFinite() || !velocity.isFinite()) continue;
            Vec3 axis = velocity.lengthSqr() < 1.0E-8
                ? new Vec3(0.0, 1.0, 0.0) : velocity.normalize();
            Vec3 nozzle = center.add(axis.scale(nozzleOffset));
            result.add(new Sample(nozzle, age,
                (.045F + age * .24F) * density, (1.0F - age) * density));
        }
        return List.copyOf(result);
    }
}
