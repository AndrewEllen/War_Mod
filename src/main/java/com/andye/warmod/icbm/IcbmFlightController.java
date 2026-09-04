package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import com.andye.warmod.icbm.guidance.IcbmGuidanceProfile;
import com.andye.warmod.icbm.guidance.IcbmGuidanceResolver;
import com.andye.warmod.icbm.network.ClientboundIcbmGuidanceUpdatePayload;
import com.andye.warmod.icbm.network.ClientboundIcbmSeparationPayload;
import com.andye.warmod.icbm.network.IcbmVisualNetworking;
import com.andye.warmod.radar.RadarRemovalReason;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.silo.MissileSiloCollisionContext;
import com.andye.warmod.silo.MissileSiloCollisionDetector;
import com.andye.warmod.silo.MissileSiloDetonationService;
import com.andye.warmod.warhead.WarheadLaunchService;
import com.andye.warmod.warhead.StrategicMissilePayloadRegistry;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.CancellationReason;
import com.andye.warmod.warhead.WarheadPreparationCoordinator;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadYieldRegistry;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class IcbmFlightController {
    private IcbmFlightPlan activeFlightPlan;
    private final IcbmChunkTicketController chunkTickets;
    private final @Nullable MissileSiloCollisionContext collisionContext;
    private final @Nullable IcbmGuidanceProfile guidanceProfile;
    private Vec3 previousPosition;
    private boolean guidanceResolved,separated,completed;
    private long cleanupElapsed=Long.MAX_VALUE;
    public IcbmFlightController(IcbmFlightPlan plan){this(plan,null,null);}public IcbmFlightController(IcbmFlightPlan plan,@Nullable MissileSiloCollisionContext collision){this(plan,collision,null);}public IcbmFlightController(IcbmFlightPlan plan,@Nullable MissileSiloCollisionContext collision,@Nullable IcbmGuidanceProfile guidance){activeFlightPlan=plan;chunkTickets=new IcbmChunkTicketController(plan);collisionContext=collision;guidanceProfile=guidance;previousPosition=plan.launchPosition();}
    public IcbmFlightPlan flightPlan(){return activeFlightPlan;}public boolean completed(){return completed;}public boolean separated(){return separated;}
    public com.andye.warmod.antiair.StrategicMissileTargetState targetState(long gameTime){double e=Math.max(0,gameTime-activeFlightPlan.launchGameTime());return new com.andye.warmod.antiair.StrategicMissileTargetState(activeFlightPlan.missileId(),activeFlightPlan.payloadType(),IcbmTrajectory.position(activeFlightPlan,e),IcbmTrajectory.velocity(activeFlightPlan,e),false,!completed&&!separated);}
    public boolean cancelForInterception(ServerLevel level,java.util.UUID interceptorId,Vec3 position){if(completed||separated)return false;separated=true;IcbmVisualNetworking.sendRemove(level,activeFlightPlan.missileId(),activeFlightPlan.ownerPlayerId(),activeFlightPlan.launchPosition(),position);cancelPreparation(level,CancellationReason.INTERCEPTED);complete(level);return true;}
    public void tick(ServerLevel level){if(completed)return;long elapsed=Math.max(0,level.getGameTime()-activeFlightPlan.launchGameTime());if(!guidanceResolved&&guidanceProfile!=null&&elapsed>=activeFlightPlan.ignitionTicks()+activeFlightPlan.boostTicks())resolveGuidance(level);Vec3 current=IcbmTrajectory.position(activeFlightPlan,elapsed);if(collisionContext!=null&&elapsed<activeFlightPlan.ignitionTicks()+activeFlightPlan.boostTicks()){var hit=MissileSiloCollisionDetector.findFirst(level,previousPosition,current,collisionContext);if(hit!=null){collide(level,hit);return;}}previousPosition=current;chunkTickets.update(level,elapsed);if(!separated&&elapsed>=activeFlightPlan.separationTick()){if(!chunkTickets.separationReady(level))return;separate(level,elapsed);}if(separated&&elapsed>=cleanupElapsed)complete(level);}
    private void resolveGuidance(ServerLevel level){guidanceResolved=true;var resolution=IcbmGuidanceResolver.resolve(guidanceProfile,activeFlightPlan.missileId(),candidate->IcbmLaunchService.retargetFromBurnout(level,activeFlightPlan,candidate).isPresent());IcbmFlightPlan revised=IcbmLaunchService.retargetFromBurnout(level,activeFlightPlan,resolution.resolvedTarget()).orElse(activeFlightPlan);if(!IcbmLaunchService.validBoostGeometry(revised)){if(SharedConstants.IS_RUNNING_IN_IDE)WarMod.LOGGER.warn("ICBM {} invalid boost geometry: launch={}, burnout={}",revised.missileId(),revised.launchPosition(),revised.burnoutPosition());revised=activeFlightPlan;}activeFlightPlan=revised;chunkTickets.updatePlan(revised);RadarTrackingService.updateIcbmPlan(level,revised);IcbmVisualNetworking.sendGuidanceUpdate(level,new ClientboundIcbmGuidanceUpdatePayload(revised.missileId(),revised.separationPosition(),revised.intendedTarget(),revised.coastTicks(),resolution.guidanceTier(),resolution.errorX(),resolution.errorZ(),level.getGameTime()),revised.ownerPlayerId(),revised.launchPosition());if(SharedConstants.IS_RUNNING_IN_IDE)WarMod.LOGGER.info("ICBM {} guidance resolved: tier={}, requested={}, offset=<{},{}>, resolved={}",revised.missileId(),resolution.guidanceTier(),resolution.requestedTarget(),resolution.errorX(),resolution.errorZ(),resolution.resolvedTarget());}
    private void collide(ServerLevel level,MissileSiloCollisionDetector.Collision hit){finishCollision(level,hit);}
    private void finishCollision(ServerLevel level,MissileSiloCollisionDetector.Collision hit){IcbmVisualNetworking.sendRemove(level,activeFlightPlan.missileId(),activeFlightPlan.ownerPlayerId(),activeFlightPlan.launchPosition(),hit.impactPosition());chunkTickets.releaseAll(level);MissileSiloDetonationService.detonateAt(level,activeFlightPlan.ownerPlayerId(),activeFlightPlan.missileId(),activeFlightPlan.missileId(),hit.impactPosition(),activeFlightPlan.visualSeed(),activeFlightPlan.payloadType());if(SharedConstants.IS_RUNNING_IN_IDE)WarMod.LOGGER.info("Silo missile {} struck block {} at {}",activeFlightPlan.missileId(),hit.blockPosition(),hit.impactPosition());completed=true;}
    public void cancel(ServerLevel level){if(completed)return;IcbmVisualNetworking.sendRemove(level,activeFlightPlan.missileId(),activeFlightPlan.ownerPlayerId(),activeFlightPlan.launchPosition(),activeFlightPlan.intendedTarget());cancelPreparation(level,CancellationReason.EXPLICIT);complete(level);}
    private void separate(final ServerLevel level, final long elapsed) {
        separated = true;
        Vec3 velocity = IcbmTrajectory.velocity(activeFlightPlan,
            activeFlightPlan.separationTick());
        var strategic = StrategicMissilePayloadRegistry.get(activeFlightPlan.missileId(),
            activeFlightPlan.payloadType());
        java.util.List<WarheadLaunchService.LaunchResult> terminals =
            strategic.deliveryMode() == WarheadDeliveryMode.CLUSTER_FOUR
                ? WarheadLaunchService.launchClusterFromCarrier(level,
                    activeFlightPlan.ownerPlayerId(),
                    activeFlightPlan.separationPosition(), activeFlightPlan.intendedTarget(),
                    activeFlightPlan.visualSeed(), activeFlightPlan.payloadType(),
                    activeFlightPlan.missileId())
                : WarheadLaunchService.launchFromCarrier(level,
                    activeFlightPlan.ownerPlayerId(),
                    activeFlightPlan.separationPosition(), activeFlightPlan.intendedTarget(),
                    activeFlightPlan.visualSeed(), activeFlightPlan.payloadType(),
                    activeFlightPlan.missileId()).map(java.util.List::of)
                    .orElseGet(java.util.List::of);
        if (terminals.isEmpty()) {
            IcbmVisualNetworking.sendRemove(level, activeFlightPlan.missileId(),
                activeFlightPlan.ownerPlayerId(), activeFlightPlan.launchPosition(),
                activeFlightPlan.intendedTarget());
            RadarTrackingService.removeTrack(level, activeFlightPlan.missileId(),
                RadarRemovalReason.TERMINAL_LAUNCH_FAILED);
            cancelPreparation(level, CancellationReason.MALFORMED_DATA);
            complete(level);
            return;
        }
        int longest = 0;
        for (var terminal : terminals) {
            RadarTrackingService.registerTerminalSeparation(level,
                activeFlightPlan.missileId(), terminal);
            longest = Math.max(longest, terminal.flightTicks());
        }
        var primary = terminals.getFirst();
        WarheadYield exactYield = WarheadYieldRegistry.resolve(level,
            activeFlightPlan.missileId(), activeFlightPlan.missileId(),
            activeFlightPlan.payloadType());
        chunkTickets.markSeparated(elapsed);
        cleanupElapsed = (long) activeFlightPlan.separationTick() + longest
            + IcbmConstants.IMPACT_CHUNK_TAIL_TICKS;
        IcbmVisualNetworking.sendSeparation(level, new ClientboundIcbmSeparationPayload(
            activeFlightPlan.missileId(), primary.warheadId(),
            activeFlightPlan.separationPosition(), velocity, level.getGameTime(),
            activeFlightPlan.visualSeed(), activeFlightPlan.payloadType(), exactYield,
            strategic.deliveryMode()), activeFlightPlan.ownerPlayerId(),
            activeFlightPlan.launchPosition(), activeFlightPlan.intendedTarget());
        if (SharedConstants.IS_RUNNING_IN_IDE && terminals.size() == 4)
            WarMod.LOGGER.info("ICBM {} separated cluster: payload={}, children={}",
                activeFlightPlan.missileId(), activeFlightPlan.payloadType(), terminals.size());
    }
    private void cancelPreparation(ServerLevel level,CancellationReason reason){WarheadPreparationCoordinator.cancelPreparation(level,activeFlightPlan.missileId(),reason);}
    private void complete(ServerLevel level){chunkTickets.releaseAll(level);com.andye.warmod.warhead.StrategicMissilePayloadRegistry.remove(activeFlightPlan.missileId());completed=true;}
}
