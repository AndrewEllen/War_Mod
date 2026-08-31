package com.andye.warmod.particle.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.andye.warmod.particle.gpu.GpuParticleEngine.EmitterCommand;
import com.andye.warmod.particle.gpu.GpuParticleEngine.ParticleType;
import com.andye.warmod.particle.gpu.GpuParticleEngine.VisualLayer;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class GpuParticleRoutingTest {
    @Test
    void probeDoesNotPromoteRealLayerWorkOrAddVisibleOpticalEnergy() {
        EmitterCommand real = emitter(0);
        EmitterCommand probe = emitter(GpuParticleEngine.SYNTHETIC_PROBE_FLAG);

        List<EmitterCommand> routed = GpuParticleEngine.routedEmitters(
            List.of(real, probe), VisualLayer.FLAMES);

        assertEquals(1, routed.size());
        assertEquals(GpuParticleEngine.SYNTHETIC_PROBE_FLAG,
            routed.getFirst().flags());
        assertEquals(0.001F, routed.getFirst().opacity());
    }

    private static EmitterCommand emitter(final int flags) {
        return new EmitterCommand(Vec3.ZERO, Vec3.ZERO, 1.0F, 0.2F,
            1.0F, 0.5F, 0.2F, 1.0F, 0.05F, 0.0F, 0.0F,
            48, 17, ParticleType.FIRE, flags, 1.0F).withSemanticLayer(
                VisualLayer.FLAMES);
    }
}
