package com.andye.warmod.phalanx;

import com.andye.warmod.antiair.AntiAirFlightControllerManager;
import com.andye.warmod.icbm.IcbmFlightControllerManager;
import com.andye.warmod.warhead.IncomingWarheadRegistry;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class PointDefenceInterceptionService {
    private PointDefenceInterceptionService() { }
    public static boolean intercept(final ServerLevel level, final PhalanxTargetSnapshot target, final UUID bulletId, final Vec3 hitPosition) {
        boolean intercepted;
        if (target.kind() == PhalanxTargetKind.ICBM_CARRIER) intercepted = IcbmFlightControllerManager.cancelForInterception(level, target.rootId(), bulletId, hitPosition);
        else if (target.kind() == PhalanxTargetKind.MK_I_FALLBACK) intercepted = AntiAirFlightControllerManager.cancelForPointDefence(level, target.targetId(), bulletId, hitPosition);
        else intercepted = IncomingWarheadRegistry.getByWarheadId(level, target.targetId()).map(warhead -> warhead.cancelForPointDefence(level, bulletId, hitPosition)).orElse(false);
        if (intercepted) { level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH,0xFFFFE8B0), hitPosition.x, hitPosition.y, hitPosition.z, 1, 0.0, 0.0, 0.0, 0.0); level.sendParticles(ParticleTypes.SMOKE, hitPosition.x, hitPosition.y, hitPosition.z, 10, 0.35, 0.35, 0.35, 0.025); }
        return intercepted;
    }
}