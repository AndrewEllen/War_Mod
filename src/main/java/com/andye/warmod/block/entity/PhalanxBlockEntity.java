package com.andye.warmod.block.entity;

import com.andye.warmod.block.PhalanxStructure;
import com.andye.warmod.block.PhalanxTurretBlock;
import com.andye.warmod.defence.DefenceAlly;
import com.andye.warmod.defence.DefenceOwnershipSnapshot;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.phalanx.PhalanxBulletManager;
import com.andye.warmod.phalanx.PhalanxConstants;
import com.andye.warmod.phalanx.PhalanxGunStatus;
import com.andye.warmod.phalanx.PhalanxLeadSolver;
import com.andye.warmod.phalanx.PhalanxTargetClaimRegistry;
import com.andye.warmod.phalanx.PhalanxTargetSelector;
import com.andye.warmod.phalanx.PhalanxTargetService;
import com.andye.warmod.phalanx.PhalanxTargetSnapshot;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PhalanxBlockEntity
    extends BlockEntity
    implements WorldlyContainer {

    private static final int[] SLOTS = createSlots();

    private final ItemStack[] ammo =
        new ItemStack[PhalanxConstants.AMMO_SLOT_COUNT];

    private UUID turretId =
        UUID.randomUUID();

    private @Nullable UUID ownerId;
    private final LinkedHashMap<UUID, String> allies = new LinkedHashMap<>();
    private @Nullable UUID currentTarget;

    private String ownerName =
        "SERVER";

    private Direction facing =
        Direction.NORTH;

    private boolean enabled = true;
    private boolean teardown;

    private float yaw;
    private float pitch;
    private float desiredYaw;
    private float desiredPitch;
    private float bloom;
    private float barrelSpin;

    private long shotSequence;
    private long lastStateBroadcast =
        Long.MIN_VALUE;

    private float lastBroadcastYaw;
    private float lastBroadcastPitch;
    private float lastBroadcastSpin;

    private PhalanxGunStatus lastBroadcastStatus =
        PhalanxGunStatus.IDLE;

    private int lastBroadcastRounds = -1;

    private int shotCooldown;
    private int burstShots;
    private int recovery;

    private PhalanxGunStatus status =
        PhalanxGunStatus.IDLE;

    public PhalanxBlockEntity(
        final BlockPos position,
        final BlockState state
    ) {
        super(
            ModBlockEntities.PHALANX_TURRET,
            position,
            state
        );

        Arrays.fill(ammo, ItemStack.EMPTY);

        if (
            state.hasProperty(
                PhalanxTurretBlock.FACING
            )
        ) {
            facing =
                state.getValue(
                    PhalanxTurretBlock.FACING
                );
        }
    }

    public void initialize(
        final @Nullable Player owner,
        final Direction facing
    ) {
        turretId =
            UUID.randomUUID();

        ownerId =
            owner == null
                ? null
                : owner.getUUID();

        ownerName =
            owner == null
                ? "SERVER"
                : owner.getGameProfile()
                    .name();

        allies.clear();

        this.facing =
            facing;

        yaw =
            yawForFacing(facing);

        desiredYaw =
            yaw;

        pitch =
            0.0F;

        desiredPitch =
            0.0F;

        sync();
    }

    public UUID turretId() {
        return turretId;
    }

    public @Nullable UUID ownerPlayerId() {
        return ownerId;
    }

    public String ownerDisplayName() {
        return ownerName;
    }

    public List<DefenceAlly> allies() {
        return allies.entrySet().stream()
            .map(entry -> new DefenceAlly(entry.getKey(), entry.getValue()))
            .toList();
    }

    public DefenceOwnershipSnapshot ownership() {
        return new DefenceOwnershipSnapshot(ownerId, ownerName, allies());
    }

    public boolean claimOwnership(final ServerPlayer actor) {
        if (ownerId != null) return false;
        ownerId = actor.getUUID();
        ownerName = actor.getGameProfile().name();
        allies.clear();
        sync();
        return true;
    }

    public boolean unclaimOwnership(final ServerPlayer actor) {
        if (!actor.getUUID().equals(ownerId)) return false;
        ownerId = null;
        ownerName = "SERVER";
        allies.clear();
        sync();
        return true;
    }

    public boolean addAlly(final ServerPlayer actor, final UUID playerId, final String playerName) {
        if (!actor.getUUID().equals(ownerId) || playerId.equals(ownerId) || allies.containsKey(playerId))
            return false;
        allies.put(playerId, playerName);
        sync();
        return true;
    }

    public boolean removeAlly(final ServerPlayer actor, final UUID playerId) {
        if (!actor.getUUID().equals(ownerId) || allies.remove(playerId) == null) return false;
        sync();
        return true;
    }

    public @Nullable DefenceAlly allyByName(final String playerName) {
        return allies().stream()
            .filter(ally -> ally.playerName().equalsIgnoreCase(playerName))
            .findFirst()
            .orElse(null);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(
        final boolean value
    ) {
        enabled =
            value;

        sync();
    }

    public int rounds() {
        int total = 0;

        for (ItemStack stack : ammo) {
            total += stack.getCount();
        }

        return total;
    }

    public PhalanxGunStatus status() {
        return status;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float bloom() {
        return bloom;
    }

    public float barrelSpin() {
        return barrelSpin;
    }

    public @Nullable UUID currentTarget() {
        return currentTarget;
    }

    public boolean teardown() {
        return teardown;
    }

    public void setTeardown(
        final boolean value
    ) {
        teardown =
            value;
    }

    public static void serverTick(
        final Level level,
        final BlockPos position,
        final BlockState state,
        final PhalanxBlockEntity blockEntity
    ) {
        if (
            level
                instanceof ServerLevel server
        ) {
            blockEntity.tick(server);
        }
    }

    private void tick(
        final ServerLevel level
    ) {
        if (
            !PhalanxStructure.complete(
                level,
                worldPosition
            )
            || !enabled
        ) {
            status =
                PhalanxGunStatus.IDLE;

            currentTarget =
                null;

            PhalanxTargetClaimRegistry.release(
                level,
                turretId
            );

            cool();

            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        if (rounds() == 0) {
            status =
                PhalanxGunStatus.OUT_OF_AMMO;

            currentTarget =
                null;

            PhalanxTargetClaimRegistry.release(
                level,
                turretId
            );

            cool();

            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        Vec3 centre =
            Vec3.atCenterOf(
                worldPosition
            );

        Vec3 pivot =
            centre.add(
                0.0,
                1.42,
                0.0
            );

        List<PhalanxTargetSnapshot> candidates =
            PhalanxTargetService.snapshot(
                level
            ).stream()
                .filter(candidate -> ownership().isHostile(
                    candidate.affiliation(), candidate.forcedHostile()))
                .toList();

        PhalanxTargetSnapshot target =
            PhalanxTargetSelector.select(
                level,
                turretId,
                currentTarget,
                centre,
                pivot,
                candidates
            ).orElse(null);

        if (target == null) {
            status =
                PhalanxGunStatus.IDLE;

            currentTarget =
                null;

            PhalanxTargetClaimRegistry.release(
                level,
                turretId
            );

            cool();

            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        currentTarget =
            target.targetId();

        PhalanxTargetClaimRegistry.claim(
            level,
            turretId,
            currentTarget,
            level.getGameTime()
        );

        boolean insideFiringCylinder =
            PhalanxTargetSelector
                .withinFiringCylinder(
                    pivot,
                    target
                );

        PhalanxLeadSolver.Solution solution =
            insideFiringCylinder
                ? PhalanxLeadSolver.solve(
                    pivot,
                    target.position(),
                    target.velocity()
                ).orElse(null)
                : null;

        Vec3 aimDirection =
            solution == null
                ? directTrackingDirection(
                    pivot,
                    target.position()
                )
                : solution.direction()
                    .normalize();

        if (
            aimDirection == null
            || !aimDirection.isFinite()
            || aimDirection.lengthSqr()
                < 1.0E-8
        ) {
            status =
                PhalanxGunStatus.TARGET_OUT_OF_ARC;

            cool();

            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        desiredYaw =
            (float)Math.toDegrees(
                Math.atan2(
                    -aimDirection.x,
                    aimDirection.z
                )
            );

        double desiredElevation =
            Math.toDegrees(
                Math.atan2(
                    aimDirection.y,
                    aimDirection.horizontalDistance()
                )
            );

        desiredElevation =
            Mth.clamp(
                desiredElevation,
                PhalanxConstants
                    .MIN_ELEVATION_DEGREES,
                PhalanxConstants
                    .MAX_ELEVATION_DEGREES
            );

        desiredPitch =
            (float)-desiredElevation;

        yaw =
            approachAngle(
                yaw,
                desiredYaw,
                12.0F
            );

        pitch =
            approachLinear(
                pitch,
                desiredPitch,
                8.0F
            );

        status =
            PhalanxGunStatus.TRACKING;

        barrelSpin =
            Math.min(
                1.0F,
                barrelSpin + 0.12F
            );

        /*
         * The turret may track and spin up at any distance. It only fires after
         * the target enters the 400-block horizontal cylinder.
         */
        if (!insideFiringCylinder) {
            recoverCooldowns();

            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        if (solution == null) {
            status =
                PhalanxGunStatus.TARGET_OUT_OF_ARC;

            recoverCooldowns();

            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        double elevation =
            solution.elevationDegrees();

        if (
            elevation
                > PhalanxConstants.MAX_ELEVATION_DEGREES
            || elevation
                < PhalanxConstants.MIN_ELEVATION_DEGREES
        ) {
            status =
                PhalanxGunStatus.TARGET_OUT_OF_ARC;

            recoverCooldowns();

            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        if (recovery > 0) {
            recovery--;

            status =
                PhalanxGunStatus.RELOADING;

            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        if (shotCooldown > 0) {
            shotCooldown--;

            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        if (
            angleError(
                solution.direction()
            ) > 2.5
            || barrelSpin < 0.72F
        ) {
            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        double spread =
            Math.min(
                PhalanxConstants.MAX_SPREAD_DEGREES,
                PhalanxConstants.BASE_SPREAD_DEGREES
                    + bloom
            );

        Vec3 direction =
            PhalanxLeadSolver.spread(
                solution.direction(),
                turretId,
                shotSequence,
                level.getGameTime(),
                spread
            );

        Vec3 muzzle =
            pivot.add(
                solution.direction()
                    .normalize()
                    .scale(0.90)
            );

        double requiredLifetime =
            Math.ceil(
                solution.flightTicks()
            )
                + PhalanxConstants
                    .BULLET_LIFETIME_SAFETY_TICKS;

        if (
            !Double.isFinite(
                requiredLifetime
            )
            || requiredLifetime <= 0.0
            || requiredLifetime
                > Integer.MAX_VALUE
        ) {
            status =
                PhalanxGunStatus.TARGET_OUT_OF_ARC;

            recoverCooldowns();

            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        int bulletLifetime =
            Math.max(
                1,
                (int)requiredLifetime
            );

        if (
            !PhalanxBulletManager.fire(
                level,
                this,
                target.targetId(),
                muzzle,
                direction.scale(
                    PhalanxConstants
                        .BULLET_SPEED_BLOCKS_PER_TICK
                ),
                bulletLifetime,
                turretId.getMostSignificantBits()
                    ^ shotSequence
            )
        ) {
            broadcastStateIfNeeded(
                level,
                false
            );

            return;
        }

        consumeRound();

        shotSequence++;
        burstShots++;

        shotCooldown =
            PhalanxConstants.SHOT_INTERVAL_TICKS;

        status =
            PhalanxGunStatus.FIRING;

        bloom =
            (float)Math.min(
                PhalanxConstants.MAX_SPREAD_DEGREES
                    - PhalanxConstants.BASE_SPREAD_DEGREES,
                bloom
                    + PhalanxConstants
                        .BLOOM_PER_SHOT_DEGREES
            );

        if (
            burstShots
                >= PhalanxConstants.BURST_SIZE
        ) {
            burstShots = 0;

            recovery =
                PhalanxConstants
                    .BURST_RECOVERY_TICKS;
        }

        broadcastStateIfNeeded(
            level,
            true
        );
    }

    private static @Nullable Vec3 directTrackingDirection(
        final Vec3 origin,
        final Vec3 target
    ) {
        Vec3 direction =
            target.subtract(origin);

        if (
            !direction.isFinite()
            || direction.lengthSqr()
                < 1.0E-8
        ) {
            return null;
        }

        return direction.normalize();
    }

    private void recoverCooldowns() {
        if (shotCooldown > 0) {
            shotCooldown--;
        }

        if (recovery > 0) {
            recovery--;
        }

        bloom =
            Math.max(
                0.0F,
                bloom
                    - (float)PhalanxConstants
                        .BLOOM_RECOVERY_DEGREES_PER_TICK
            );
    }

    private void broadcastStateIfNeeded(
        final ServerLevel level,
        final boolean force
    ) {
        long now =
            level.getGameTime();

        boolean active =
            status == PhalanxGunStatus.TRACKING
                || status == PhalanxGunStatus.FIRING
                || status == PhalanxGunStatus.RELOADING;

        long minimumInterval =
            active
                ? 2L
                : 20L;

        boolean intervalElapsed =
            lastStateBroadcast == Long.MIN_VALUE
                || now - lastStateBroadcast
                    >= minimumInterval;

        boolean heartbeat =
            lastStateBroadcast == Long.MIN_VALUE
                || now - lastStateBroadcast
                    >= 80L;

        boolean changed =
            Math.abs(
                Mth.wrapDegrees(
                    yaw - lastBroadcastYaw
                )
            ) >= 0.5F
                || Math.abs(
                    pitch - lastBroadcastPitch
                ) >= 0.5F
                || Math.abs(
                    barrelSpin
                        - lastBroadcastSpin
                ) >= 0.04F
                || status
                    != lastBroadcastStatus
                || rounds()
                    != lastBroadcastRounds;

        if (
            !force
            && !heartbeat
            && (
                !intervalElapsed
                || !changed
            )
        ) {
            return;
        }

        lastStateBroadcast =
            now;

        lastBroadcastYaw =
            yaw;

        lastBroadcastPitch =
            pitch;

        lastBroadcastSpin =
            barrelSpin;

        lastBroadcastStatus =
            status;

        lastBroadcastRounds =
            rounds();

        com.andye.warmod.phalanx.network
            .PhalanxNetworking.sendState(
                level,
                this
            );
    }

    private double angleError(
        final Vec3 direction
    ) {
        Vec3 aim =
            new Vec3(
                -Mth.sin(
                    yaw * Mth.DEG_TO_RAD
                ) * Mth.cos(
                    pitch * Mth.DEG_TO_RAD
                ),
                -Mth.sin(
                    pitch * Mth.DEG_TO_RAD
                ),
                Mth.cos(
                    yaw * Mth.DEG_TO_RAD
                ) * Mth.cos(
                    pitch * Mth.DEG_TO_RAD
                )
            );

        return Math.toDegrees(
            Math.acos(
                Mth.clamp(
                    aim.normalize()
                        .dot(
                            direction.normalize()
                        ),
                    -1.0,
                    1.0
                )
            )
        );
    }

    private static float approachAngle(
        final float current,
        final float target,
        final float maximumChange
    ) {
        float difference =
            Mth.wrapDegrees(
                target - current
            );

        return Mth.wrapDegrees(
            current
                + Mth.clamp(
                    difference,
                    -maximumChange,
                    maximumChange
                )
        );
    }

    private static float approachLinear(
        final float current,
        final float target,
        final float maximumChange
    ) {
        return current
            + Mth.clamp(
                target - current,
                -maximumChange,
                maximumChange
            );
    }

    private static float yawForFacing(
        final Direction direction
    ) {
        return switch (direction) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }

    private void cool() {
        bloom =
            Math.max(
                0.0F,
                bloom
                    - (float)PhalanxConstants
                        .BLOOM_RECOVERY_DEGREES_PER_TICK
            );

        barrelSpin =
            Math.max(
                0.0F,
                barrelSpin - 0.08F
            );
    }

    private void consumeRound() {
        for (
            int index = 0;
            index < ammo.length;
            index++
        ) {
            if (ammo[index].isEmpty()) {
                continue;
            }

            ammo[index].shrink(1);

            if (ammo[index].isEmpty()) {
                ammo[index] =
                    ItemStack.EMPTY;
            }

            setChanged();

            return;
        }
    }

    private void sync() {
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(
                worldPosition,
                getBlockState(),
                getBlockState(),
                3
            );
        }
    }

    @Override
    public int getContainerSize() {
        return ammo.length;
    }

    @Override
    public boolean isEmpty() {
        return rounds() == 0;
    }

    @Override
    public ItemStack getItem(
        final int slot
    ) {
        return slot >= 0
                && slot < ammo.length
            ? ammo[slot]
            : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(
        final int slot,
        final int count
    ) {
        if (
            slot < 0
            || slot >= ammo.length
            || count <= 0
        ) {
            return ItemStack.EMPTY;
        }

        ItemStack result =
            ammo[slot].split(count);

        if (ammo[slot].isEmpty()) {
            ammo[slot] =
                ItemStack.EMPTY;
        }

        sync();

        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(
        final int slot
    ) {
        if (
            slot < 0
            || slot >= ammo.length
        ) {
            return ItemStack.EMPTY;
        }

        ItemStack result =
            ammo[slot];

        ammo[slot] =
            ItemStack.EMPTY;

        return result;
    }

    @Override
    public void setItem(
        final int slot,
        final ItemStack stack
    ) {
        if (
            slot < 0
            || slot >= ammo.length
        ) {
            return;
        }

        ammo[slot] =
            stack.is(
                ModItems.ANTI_AIR_GUN_AMMO
            )
                ? stack.copyWithCount(
                    Math.min(
                        64,
                        stack.getCount()
                    )
                )
                : ItemStack.EMPTY;

        sync();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(
        final Player player
    ) {
        return level
                == player.level()
            && player.distanceToSqr(
                Vec3.atCenterOf(
                    worldPosition
                )
            ) <= 64.0;
    }

    @Override
    public boolean canPlaceItem(
        final int slot,
        final ItemStack stack
    ) {
        return slot >= 0
            && slot < ammo.length
            && stack.is(
                ModItems.ANTI_AIR_GUN_AMMO
            );
    }

    @Override
    public void clearContent() {
        Arrays.fill(ammo, ItemStack.EMPTY);
        sync();
    }

    @Override
    public int[] getSlotsForFace(
        final Direction direction
    ) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(
        final int slot,
        final ItemStack stack,
        final @Nullable Direction direction
    ) {
        return canPlaceItem(
            slot,
            stack
        );
    }

    @Override
    public boolean canTakeItemThroughFace(
        final int slot,
        final ItemStack stack,
        final Direction direction
    ) {
        return slot >= 0
            && slot < ammo.length;
    }

    @Override
    protected void saveAdditional(
        final ValueOutput output
    ) {
        super.saveAdditional(output);

        output.store(
            "turret_id",
            UUIDUtil.CODEC,
            turretId
        );

        output.storeNullable(
            "owner_id",
            UUIDUtil.CODEC,
            ownerId
        );

        output.putString(
            "owner_name",
            ownerName
        );

        output.store("allies", DefenceAlly.CODEC.listOf(), allies());

        output.putString(
            "facing",
            facing.getSerializedName()
        );

        output.putBoolean(
            "enabled",
            enabled
        );

        output.putFloat(
            "yaw",
            yaw
        );

        output.putFloat(
            "pitch",
            pitch
        );

        output.putFloat(
            "bloom",
            bloom
        );

        output.putLong(
            "shot_sequence",
            shotSequence
        );

        for (int index = 0; index < ammo.length; index++) {
            output.store(
                "ammo_" + index,
                ItemStack.OPTIONAL_CODEC,
                ammo[index]
            );
        }
    }

    @Override
    protected void loadAdditional(
        final ValueInput input
    ) {
        super.loadAdditional(input);

        turretId =
            input.read(
                "turret_id",
                UUIDUtil.CODEC
            ).orElseGet(
                UUID::randomUUID
            );

        ownerId =
            input.read(
                "owner_id",
                UUIDUtil.CODEC
            ).orElse(null);

        ownerName =
            input.getStringOr(
                "owner_name",
                "SERVER"
            );

        allies.clear();
        for (DefenceAlly ally : input.read("allies", DefenceAlly.CODEC.listOf()).orElse(List.of())) {
            if (!ally.playerId().equals(ownerId))
                allies.putIfAbsent(ally.playerId(), ally.playerName());
        }

        facing =
            Direction.byName(
                input.getStringOr(
                    "facing",
                    "north"
                )
            );

        if (facing == null) {
            facing =
                Direction.NORTH;
        }

        enabled =
            input.getBooleanOr(
                "enabled",
                true
            );

        yaw =
            input.getFloatOr(
                "yaw",
                yawForFacing(facing)
            );

        pitch =
            input.getFloatOr(
                "pitch",
                0.0F
            );

        desiredYaw =
            yaw;

        desiredPitch =
            pitch;

        bloom =
            input.getFloatOr(
                "bloom",
                0.0F
            );

        shotSequence =
            input.getLongOr(
                "shot_sequence",
                0L
            );

        for (int index = 0; index < ammo.length; index++) {
            ammo[index] =
                input.read(
                    "ammo_" + index,
                    ItemStack.OPTIONAL_CODEC
                ).orElse(
                    ItemStack.EMPTY
                );
        }
    }

    private static int[] createSlots() {
        int[] slots =
            new int[PhalanxConstants.AMMO_SLOT_COUNT];

        for (int index = 0; index < slots.length; index++) {
            slots[index] = index;
        }

        return slots;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server) {
            PhalanxTargetClaimRegistry.release(
                server,
                turretId
            );
        }

        super.setRemoved();
    }

    @Override
    public @Nullable Packet<
        ClientGamePacketListener
    > getUpdatePacket() {
        return ClientboundBlockEntityDataPacket
            .create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(
        final net.minecraft.core.HolderLookup.Provider registries
    ) {
        return saveCustomOnly(
            registries
        );
    }
}
