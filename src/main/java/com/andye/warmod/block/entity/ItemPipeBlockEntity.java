package com.andye.warmod.block.entity;

import com.andye.warmod.block.ItemPipeBlock;
import com.andye.warmod.block.MissileSiloBlock;
import com.andye.warmod.block.MissileWorkbenchBlock;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.block.PhalanxTurretBlock;
import com.andye.warmod.logistics.PipeConnectionMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class ItemPipeBlockEntity extends BlockEntity {
    private static final int TRANSFER_INTERVAL_TICKS = 8;
    private static final int ITEMS_PER_TRANSFER = 5;
    private static final int MAX_NETWORK_PIPES = 1024;

    public ItemPipeBlockEntity(
        final BlockPos position,
        final BlockState state
    ) {
        super(ModBlockEntities.ITEM_PIPE, position, state);
    }

    public static void serverTick(
        final Level level,
        final BlockPos position,
        final BlockState state,
        final ItemPipeBlockEntity pipe
    ) {
        if (!(level instanceof ServerLevel server)
            || !state.is(ModBlocks.ITEM_PIPE)
            || Math.floorMod(
                server.getGameTime() + position.asLong(),
                TRANSFER_INTERVAL_TICKS
            ) != 0L) {
            return;
        }

        pipe.transfer(server);
    }

    private void transfer(final ServerLevel level) {
        BlockState state = level.getBlockState(worldPosition);

        if (!state.is(ModBlocks.ITEM_PIPE)) {
            return;
        }

        List<Endpoint> outputs = findOutputs(level);

        if (outputs.isEmpty()) {
            return;
        }

        for (Direction inputSide : Direction.values()) {
            if (ItemPipeBlock.mode(state, inputSide)
                != PipeConnectionMode.INPUT) {
                continue;
            }

            BlockPos sourcePosition = worldPosition.relative(inputSide);

            if (!level.hasChunkAt(sourcePosition)) {
                continue;
            }

            Container source = containerAt(level, sourcePosition);

            if (source == null) {
                continue;
            }

            if (transferFrom(
                source,
                sourcePosition,
                inputSide.getOpposite(),
                outputs,
                level
            )) {
                return;
            }
        }
    }

    private boolean transferFrom(
        final Container source,
        final BlockPos sourcePosition,
        final Direction sourceFace,
        final List<Endpoint> outputs,
        final ServerLevel level
    ) {
        int[] slots = slotsFor(source, sourceFace);

        for (int slot : slots) {
            if (!validSlot(source, slot)) {
                continue;
            }

            ItemStack sourceStack = source.getItem(slot);

            if (sourceStack.isEmpty()
                || !canExtract(source, slot, sourceStack, sourceFace)) {
                continue;
            }

            int remainingBudget = Math.min(
                ITEMS_PER_TRANSFER,
                sourceStack.getCount()
            );
            int moved = 0;

            for (Endpoint endpoint : outputs) {
                if (remainingBudget <= 0) {
                    break;
                }

                if (endpoint.inventoryPosition().equals(sourcePosition)
                    || !level.hasChunkAt(endpoint.inventoryPosition())) {
                    continue;
                }

                Container destination = containerAt(
                    level,
                    endpoint.inventoryPosition()
                );

                if (destination == null || destination == source) {
                    continue;
                }

                ItemStack liveSource = source.getItem(slot);

                if (liveSource.isEmpty()
                    || !canExtract(source, slot, liveSource, sourceFace)) {
                    break;
                }

                int request = Math.min(remainingBudget, liveSource.getCount());
                ItemStack extracted = source.removeItem(slot, request);

                if (extracted.isEmpty()) {
                    break;
                }

                int extractedCount = extracted.getCount();
                ItemStack remainder = insert(
                    destination,
                    endpoint.inventoryFace(),
                    extracted
                );
                int inserted = extractedCount - remainder.getCount();

                if (!remainder.isEmpty()) {
                    restore(source, slot, remainder);
                }

                /* removeItem/rollback is still an inventory mutation even if
                 * the destination accepted nothing. Persist the restored slot.
                 */
                source.setChanged();

                if (inserted > 0) {
                    moved += inserted;
                    remainingBudget -= inserted;
                }
            }

            if (moved > 0) {
                return true;
            }
        }

        return false;
    }

    private List<Endpoint> findOutputs(final ServerLevel level) {
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<Endpoint> outputs = new ArrayList<>();

        queue.addLast(new Node(worldPosition, 0));
        visited.add(worldPosition);

        while (!queue.isEmpty() && visited.size() <= MAX_NETWORK_PIPES) {
            Node node = queue.removeFirst();
            BlockState state = level.getBlockState(node.position());

            if (!state.is(ModBlocks.ITEM_PIPE)) {
                continue;
            }

            for (Direction direction : Direction.values()) {
                PipeConnectionMode connection =
                    ItemPipeBlock.mode(state, direction);

                if (connection == PipeConnectionMode.OUTPUT) {
                    outputs.add(new Endpoint(
                        node.position().relative(direction),
                        direction.getOpposite(),
                        node.distance()
                    ));
                    continue;
                }

                if (connection != PipeConnectionMode.PIPE) {
                    continue;
                }

                BlockPos neighbourPosition = node.position().relative(direction);

                if (visited.contains(neighbourPosition)
                    || !level.hasChunkAt(neighbourPosition)) {
                    continue;
                }

                BlockState neighbour = level.getBlockState(neighbourPosition);

                if (!neighbour.is(ModBlocks.ITEM_PIPE)
                    || ItemPipeBlock.mode(neighbour, direction.getOpposite())
                        != PipeConnectionMode.PIPE) {
                    continue;
                }

                visited.add(neighbourPosition);
                queue.addLast(new Node(
                    neighbourPosition,
                    node.distance() + 1
                ));
            }
        }

        outputs.sort(
            Comparator.comparingInt(Endpoint::distance)
                .thenComparingLong(endpoint ->
                    endpoint.inventoryPosition().asLong()
                )
                .thenComparingInt(endpoint ->
                    endpoint.inventoryFace().ordinal()
                )
        );

        return List.copyOf(outputs);
    }

    /**
     * Inserts the supplied stack and returns the unaccepted remainder.
     */
    private static ItemStack insert(
        final Container destination,
        final Direction face,
        final ItemStack supplied
    ) {
        if (supplied.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = supplied.copy();
        int[] slots = slotsFor(destination, face);

        for (int slot : slots) {
            if (remainder.isEmpty()) {
                break;
            }

            if (!validSlot(destination, slot)) {
                continue;
            }

            ItemStack existing = destination.getItem(slot);

            if (existing.isEmpty()
                || !ItemStack.isSameItemSameComponents(existing, remainder)
                || !canInsert(destination, slot, remainder, face)) {
                continue;
            }

            int maximum = Math.min(
                destination.getMaxStackSize(),
                existing.getMaxStackSize()
            );
            int amount = Math.min(
                remainder.getCount(),
                maximum - existing.getCount()
            );

            if (amount <= 0) {
                continue;
            }

            ItemStack combined = existing.copy();
            combined.grow(amount);
            destination.setItem(slot, combined);
            remainder.shrink(amount);
        }

        for (int slot : slots) {
            if (remainder.isEmpty()) {
                break;
            }

            if (!validSlot(destination, slot)) {
                continue;
            }

            if (!destination.getItem(slot).isEmpty()
                || !canInsert(destination, slot, remainder, face)) {
                continue;
            }

            int maximum = Math.min(
                destination.getMaxStackSize(),
                remainder.getMaxStackSize()
            );
            int amount = Math.min(remainder.getCount(), maximum);

            destination.setItem(slot, remainder.copyWithCount(amount));
            remainder.shrink(amount);
        }

        if (remainder.getCount() < supplied.getCount()) {
            destination.setChanged();
        }

        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    private static void restore(
        final Container source,
        final int slot,
        final ItemStack remainder
    ) {
        ItemStack current = source.getItem(slot);

        if (current.isEmpty()) {
            source.setItem(slot, remainder.copy());
            return;
        }

        if (ItemStack.isSameItemSameComponents(current, remainder)) {
            ItemStack restored = current.copy();
            restored.grow(remainder.getCount());
            source.setItem(slot, restored);
            return;
        }

        /*
         * This should be unreachable on the single server thread. Preserve
         * items rather than deleting them if a non-standard inventory mutates
         * its slot during extraction.
         */
        for (int candidate = 0; candidate < source.getContainerSize(); candidate++) {
            if (source.getItem(candidate).isEmpty()
                && source.canPlaceItem(candidate, remainder)) {
                source.setItem(candidate, remainder.copy());
                return;
            }
        }

        throw new IllegalStateException(
            "Unable to return rejected item-pipe transfer to source inventory"
        );
    }

    private static boolean validSlot(
        final Container container,
        final int slot
    ) {
        return slot >= 0 && slot < container.getContainerSize();
    }

    private static int[] slotsFor(
        final Container container,
        final Direction face
    ) {
        if (container instanceof WorldlyContainer worldly) {
            return worldly.getSlotsForFace(face);
        }

        int[] slots = new int[container.getContainerSize()];

        for (int slot = 0; slot < slots.length; slot++) {
            slots[slot] = slot;
        }

        return slots;
    }

    private static boolean canExtract(
        final Container container,
        final int slot,
        final ItemStack stack,
        final Direction face
    ) {
        return !(container instanceof WorldlyContainer worldly)
            || worldly.canTakeItemThroughFace(slot, stack, face);
    }

    private static boolean canInsert(
        final Container container,
        final int slot,
        final ItemStack stack,
        final Direction face
    ) {
        return container.canPlaceItem(slot, stack)
            && (!(container instanceof WorldlyContainer worldly)
                || worldly.canPlaceItemThroughFace(slot, stack, face));
    }

    private static @Nullable Container containerAt(
        final ServerLevel level,
        final BlockPos position
    ) {
        BlockState state = level.getBlockState(position);

        if (state.is(ModBlocks.MISSILE_SILO)) {
            return MissileSiloBlock.resolve(level, position, state);
        }

        if (state.is(ModBlocks.MISSILE_WORKBENCH)) {
            return MissileWorkbenchBlock.resolve(level, position, state);
        }

        if (state.is(ModBlocks.PHALANX_TURRET)) {
            return PhalanxTurretBlock.resolve(level, position, state);
        }

        return level.getBlockEntity(position) instanceof Container container
            ? container
            : null;
    }

    private record Node(BlockPos position, int distance) {
    }

    private record Endpoint(
        BlockPos inventoryPosition,
        Direction inventoryFace,
        int distance
    ) {
    }
}
