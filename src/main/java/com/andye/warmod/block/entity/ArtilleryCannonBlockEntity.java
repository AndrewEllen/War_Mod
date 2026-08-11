package com.andye.warmod.block.entity;

import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.artillery.ArtilleryLaunchService;
import com.andye.warmod.artillery.ArtilleryPayload;
import com.andye.warmod.artillery.ArtilleryPayloadItems;
import com.andye.warmod.artillery.ArtilleryTrajectory;
import com.andye.warmod.block.ArtilleryCannonBlock;
import com.andye.warmod.item.component.TargetCoordinates;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ArtilleryCannonBlockEntity extends BlockEntity implements Container {
    private ItemStack ammunition = ItemStack.EMPTY;
    private @Nullable TargetCoordinates target;
    private int previousSignal;
    private int cooldown;
    private boolean redstoneCheck;
    private String lastError = "";
    public ArtilleryCannonBlockEntity(final BlockPos pos, final BlockState state) { super(ModBlockEntities.ARTILLERY_CANNON, pos, state); }
    public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final ArtilleryCannonBlockEntity cannon) {
        if (!(level instanceof ServerLevel server)) return;
        int signal = level.getBestNeighborSignal(pos);
        if (cannon.cooldown > 0) cannon.cooldown--;
        if ((cannon.previousSignal == 0 && signal > 0) || cannon.redstoneCheck && signal > 0 && cannon.previousSignal == 0) cannon.fire(server, null);
        cannon.previousSignal = signal; cannon.redstoneCheck = false;
    }
    public void markRedstoneCheck() { redstoneCheck = true; }
    public void setTarget(final @Nullable TargetCoordinates value) { target = value != null && value.isValid() ? value : null; lastError = ""; sync(); }
    public @Nullable TargetCoordinates target() { return target; }
    public ItemStack ammunition() { return ammunition; }
    public String lastError() { return lastError; }
    public int cooldown() { return cooldown; }
    public int insert(final ItemStack stack, final int requested) { if (!ArtilleryPayloadItems.compatible(ammunition, stack)) return 0; int amount = Math.max(0, Math.min(requested, ArtilleryConstants.MAX_AMMUNITION - ammunition.getCount())); if (amount == 0) return 0; if (ammunition.isEmpty()) ammunition = stack.copyWithCount(amount); else ammunition.grow(amount); sync(); return amount; }
    public boolean fire(final ServerLevel level, final @Nullable Player player) {
        if (cooldown > 0) return fail("Artillery is cycling");
        ArtilleryPayload payload = ArtilleryPayloadItems.payload(ammunition);
        if (payload == null) return fail("Load an artillery shell");
        if (target == null || !target.dimension().equals(level.dimension())) return fail("Program a same-dimension target");
        Vec3 destination = target.position();
        Vec3 pivot = barrelPivot();
        if (!level.getWorldBorder().isWithinBounds(destination) || level.isOutsideBuildHeight(BlockPos.containing(destination)) || pivot.distanceTo(destination) > ArtilleryConstants.MAX_RANGE_BLOCKS) return fail("Target exceeds 1,000-block artillery range");
        ArtilleryTrajectory.LaunchSolution launch = ArtilleryTrajectory.solveFromCannon(pivot, destination).orElse(null);
        if (launch == null) return fail("Target is outside the ballistic envelope");
        ItemStack reserved = ammunition.split(1);
        if (ArtilleryLaunchService.launch(level, player == null ? null : player.getUUID(), launch.muzzle(), destination, payload).isEmpty()) { if (ammunition.isEmpty()) ammunition = reserved; else ammunition.grow(reserved.getCount()); return fail("Unable to launch artillery warhead"); }
        cooldown = ArtilleryConstants.FIRE_COOLDOWN_TICKS; lastError = ""; sync(); return true;
    }
    private boolean fail(final String error) { lastError = error; sync(); return false; }
    private Vec3 barrelPivot() { return Vec3.atCenterOf(worldPosition).add(0.0, ArtilleryConstants.BARREL_PIVOT_HEIGHT, 0.0); }
    private void sync() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    @Override protected void saveAdditional(final ValueOutput out) { super.saveAdditional(out); out.store("ammunition", ItemStack.OPTIONAL_CODEC, ammunition); out.storeNullable("target", TargetCoordinates.CODEC, target); out.putInt("signal", previousSignal); out.putInt("cooldown", cooldown); out.putString("error", lastError); }
    @Override protected void loadAdditional(final ValueInput in) { super.loadAdditional(in); ItemStack loaded = in.read("ammunition", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY); ammunition = ArtilleryPayloadItems.isWarhead(loaded) ? loaded.copyWithCount(Math.min(ArtilleryConstants.MAX_AMMUNITION, loaded.getCount())) : ItemStack.EMPTY; target = in.read("target", TargetCoordinates.CODEC).filter(TargetCoordinates::isValid).orElse(null); previousSignal = Math.max(0, Math.min(15, in.getIntOr("signal", 0))); cooldown = Math.max(0, in.getIntOr("cooldown", 0)); lastError = in.getStringOr("error", ""); }
    @Override public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(final net.minecraft.core.HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public int getContainerSize() { return 1; } @Override public boolean isEmpty() { return ammunition.isEmpty(); } @Override public ItemStack getItem(final int slot) { return slot == 0 ? ammunition : ItemStack.EMPTY; } @Override public ItemStack removeItem(final int slot, final int amount) { if (slot != 0) return ItemStack.EMPTY; ItemStack result = ammunition.split(amount); sync(); return result; } @Override public ItemStack removeItemNoUpdate(final int slot) { if (slot != 0) return ItemStack.EMPTY; ItemStack result = ammunition; ammunition = ItemStack.EMPTY; return result; } @Override public void setItem(final int slot, final ItemStack stack) { if (slot == 0) { ammunition = ArtilleryPayloadItems.isWarhead(stack) ? stack.copyWithCount(Math.min(ArtilleryConstants.MAX_AMMUNITION, stack.getCount())) : ItemStack.EMPTY; sync(); } } @Override public int getMaxStackSize() { return ArtilleryConstants.MAX_AMMUNITION; } @Override public boolean stillValid(final Player player) { return Container.stillValidBlockEntity(this, player); } @Override public boolean canPlaceItem(final int slot, final ItemStack stack) { return slot == 0 && ArtilleryPayloadItems.compatible(ammunition, stack); } @Override public void clearContent() { ammunition = ItemStack.EMPTY; sync(); }
}
