package com.andye.warmod.warhead;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.AcousticEngine;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.entity.WarheadDebrisEntity;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.testtool.TestExplosionService;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class WarheadImpactService {
	private WarheadImpactService() { }
	public static void impact(final ServerLevel level, final @Nullable ServerPlayer owner, final UUID id, final Vec3 pos, final long seed) {
		impact(level, owner, id, id, pos, seed, WarheadPayloadType.CONVENTIONAL);
	}
	public static void impact(final ServerLevel level, final @Nullable ServerPlayer owner, final UUID id, final Vec3 pos,
		final long seed, final WarheadPayloadType payloadType) {
		impact(level, owner, id, id, pos, seed, payloadType);
	}
	public static void impact(final ServerLevel level, final @Nullable ServerPlayer owner, final UUID id,
		final UUID radarRootTrackId, final Vec3 pos, final long seed, final WarheadPayloadType payloadType) {
		detonateAt(level, owner, id, radarRootTrackId, pos, seed, payloadType);
	}
	public static void detonateAt(final ServerLevel level, final @Nullable ServerPlayer owner, final UUID id,
		final UUID radarRootTrackId, final Vec3 pos, final long seed, final WarheadPayloadType payloadType) {
		Objects.requireNonNull(level); Objects.requireNonNull(id); Objects.requireNonNull(pos); Objects.requireNonNull(payloadType);
		if (!pos.isFinite()) throw new IllegalArgumentException("impactPosition must be finite");
		WarheadImpactProfile profile = WarheadImpactProfiles.get(payloadType);
		RadarTrackingService.registerImpact(level, id, radarRootTrackId, pos, payloadType, profile.impactVisualScale());
		List<DebrisCandidate> candidates = sample(level, pos, seed, profile);
		WarheadVisualNetworking.sendImpact(level, new ClientboundWarheadImpactPayload(id, pos.x, pos.y, pos.z,
			level.getGameTime(), seed, payloadType, profile.impactVisualScale()), pos);
		TestExplosionService.createExplosion(level, owner, pos, profile.explosionStrength());
		spawnDebris(level, pos, seed, candidates, profile);
		AcousticEngine.playSound(level, pos, AcousticSounds.WARHEAD_IMPACT_THUD_ID, SoundSource.BLOCKS, .72F, 1.0F);
		AcousticEngine.playSound(level, pos, AcousticSounds.LARGE_EXPLOSION_ID, SoundSource.BLOCKS, profile.acousticVolume(), profile.acousticPitch());
		if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("Warhead {} emitted impact thud and explosion at {}", id, pos);
		if (payloadType == WarheadPayloadType.NUCLEAR && SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("Nuclear warhead {} impacted at {}", id, pos);
	}
	private static List<DebrisCandidate> sample(final ServerLevel level, final Vec3 center, final long seed, final WarheadImpactProfile profile) {
		int radius = profile.debrisSampleRadius();
		int maximum = Math.max(2048, profile.maximumDebrisEntities() * 8);
		BlockPos origin = BlockPos.containing(center); List<DebrisCandidate> eligible = new ArrayList<>();
		for (int dx=-radius; dx<=radius; dx++) for (int dy=-radius; dy<=radius; dy++) for (int dz=-radius; dz<=radius; dz++) {
			if (dx*dx+dy*dy+dz*dz > radius*radius) continue;
			BlockPos p=origin.offset(dx,dy,dz);
			if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(p.getX()),SectionPos.blockToSectionCoord(p.getZ()))) continue;
			BlockState s=level.getBlockState(p);
			if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.getDestroySpeed(level,p)<0.0F || s.getRenderShape()==RenderShape.INVISIBLE) continue;
			eligible.add(new DebrisCandidate(p.immutable(),s));
		}
		eligible.sort(Comparator.comparingLong(c -> mix(c.position().asLong() ^ seed)));
		return List.copyOf(eligible.subList(0,Math.min(maximum,eligible.size())));
	}
	private static void spawnDebris(final ServerLevel level, final Vec3 center, final long seed, final List<DebrisCandidate> candidates,
		final WarheadImpactProfile profile) {
		List<DebrisCandidate> destroyed=new ArrayList<>();
		for (DebrisCandidate c:candidates) if (!level.getBlockState(c.position()).equals(c.state())) destroyed.add(c);
		destroyed.sort(Comparator.comparingLong(c -> mix(c.position().asLong() ^ seed ^ 0x444542524953L)));
		int count=Math.min(profile.maximumDebrisEntities(),destroyed.size()), largeCount=Math.min(profile.maximumLargeDebrisEntities(),count);
		for(int i=0;i<count;i++) { DebrisCandidate c=destroyed.get(i); SplittableRandom random=new SplittableRandom(mix(seed^c.position().asLong())); boolean large=i<largeCount;
			Vec3 spawn=Vec3.atCenterOf(c.position()), radial=new Vec3(spawn.x-center.x,0,spawn.z-center.z);
			if(radial.lengthSqr()<1.0E-5) radial=new Vec3(random.nextDouble(-1,1),0,random.nextDouble(-1,1));
			Vec3 outward=radial.normalize(), sideways=new Vec3(-outward.z,0,outward.x); double normalized=Math.min(1,Math.sqrt(radial.lengthSqr())/profile.debrisSampleRadius());
			double h=(large?random.nextDouble(.15,.48):random.nextDouble(.22,.68))*profile.debrisVelocityScale();
			double v=(large?random.nextDouble(.24,.66):random.nextDouble(.30,.82))+(1-normalized)*.12;
			Vec3 velocity=outward.scale(h).add(sideways.scale(random.nextDouble(-.08,.08))).add(0,Math.min(1.12,v*profile.debrisVelocityScale()),0);
			double spinLimit=large?.14:.30; Vec3 spin=new Vec3(random.nextDouble(-spinLimit,spinLimit),random.nextDouble(-spinLimit,spinLimit),random.nextDouble(-spinLimit,spinLimit));
			float scale=(float)(large?random.nextDouble(.65,1.05):random.nextDouble(.25,.60)); int lifetime=large?random.nextInt(65,126):random.nextInt(50,106);
			level.addFreshEntity(new WarheadDebrisEntity(level,c.state(),spawn,velocity,spin,lifetime,scale)); }
	}
	private static long mix(long v){v^=v>>>30;v*=0xBF58476D1CE4E5B9L;v^=v>>>27;v*=0x94D049BB133111EBL;return v^(v>>>31);}
	private record DebrisCandidate(BlockPos position, BlockState state) { }
}