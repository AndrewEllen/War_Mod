package com.andye.warmod.block.entity;

import com.andye.warmod.block.ItemPipeBlock;
import com.andye.warmod.block.MissileSiloBlock;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.block.PhalanxTurretBlock;
import com.andye.warmod.logistics.PipeConnectionMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
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

        pipe.transfer(server, state);
    }

    private void transfer(
        final ServerLevel level,
        final BlockState state
    ) {
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
            Container source = containerAt(level, sourcePosition);

            if (source == null) {
                continue;
            }

            transferFrom(
                source,
                sourcePosition,
                inputSide.getOpposite(),
                outputs,
                level
            );
        }
    }

    private void transferFrom(
        final Container source,
        final BlockPos sourcePosition,
        final Direction sourceFace,
        final List<Endpoint> outputs,
        final ServerLevel level
    ) {
        int[] slots = slotsFor(source, sourceFace);

        for (int slot : slots) {
            ItemStack sourceStack = source.getItem(slot);

            if (sourceStack.isEmpty()
                || !canExtract(source, slot, sourceStack, sourceFace)) {
                continue;
            }

            int requested = Math.min(
                ITEMS_PER_TRANSFER,
                sourceStack.getCount()
            );
            ItemStack offered = sourceStack.copyWithCount(requested);
            int inserted = 0;

            for (Endpoint endpoint : outputs) {
                if (endpoint.inventoryPosition().equals(sourcePosition)) {
                    continue;
                }

                Container destination = containerAt(
                    level,
                    endpoint.inventoryPosition()
                );

                if (destination == null) {
                    continue;
                }

                int accepted = insert(
                    destination,
                    endpoint.inventoryFace(),
                    offered.copyWithCount(requested - inserted)
                );

                inserted += accepted;

                if (inserted >= requested) {
                    break;
                }
            }

            if (inserted <= 0) {
                continue;
            }

            sourceStack.shrink(inserted);
            source.setItem(
                slot,
                sourceStack.isEmpty()
                    ? ItemStack.EMPTY
                    : sourceStack
            );
            source.setChanged();
            return;
        }
    }

    private List<Endpoint> findOutputs(
        final ServerLevel level
    ) {
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<Endpoint> outputs = new ArrayList<>();

        queue.addLast(new Node(worldPosition, 0));
        visited.add(worldPosition);

        while (!queue.isEmpty()
            && visited.size() <= MAX_NETWORK_PIPES) {
            Node node = queue.removeFirst();
            BlockState state = level.getBlockState(node.position());

            if (!state.is(ModBlocks.ITEM_PIPE)) {
                continue;
            }

            for (Direction direction : Direction.values()) {
                PipeConnectionMode mode = ItemPipeBlock.mode(state, direction);

                if (mode == PipeConnectionMode.OUTPUT) {
                    outputs.add(new Endpoint(
                        node.position().relative(direction),
                        direction.getOpposite(),
                        node.distance()
                    ));
                    continue;
                }

                if (mode != PipeConnectionMode.PIPE) {
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

        return List.copyOf(outputs);
    }

    private static int insert(
        final Container destination,
        final Direction face,
        final ItemStack offered
    ) {
        if (offered.isEmpty()) {
            return 0;
        }

        int originalCount = offered.getCount();
        int[] slots = slotsFor(destination, face);

        for (int slot : slots) {
            if (offered.isEmpty()) {
                break;
            }

            ItemStack existing = destination.getItem(slot);

            if (existing.isEmpty()
                || !ItemStack.isSameItemSameComponents(existing, offered)
                || !canInsert(destination, slot, offered, face)) {
                continue;
            }

            int maximum = Math.min(
                destination.getMaxStackSize(),
                existing.getMaxStackSize()
            );
            int amount = Math.min(
                offered.getCount(),
                maximum - existing.getCount()
            );

            if (amount <= 0) {
                continue;
            }

            existing.grow(amount);
            destination.setItem(slot, existing);
            offered.shrink(amount);
        }

        for (int slot : slots) {
            if (offered.isEmpty()) {
                break;
            }

            if (!destination.getItem(slot).isEmpty()
                || !canInsert(destination, slot, offered, face)) {
                continue;
            }

            int maximum = Math.min(
                destination.getMaxStackSize(),
                offered.getMaxStackSize()
            );
            int amount = Math.min(offered.getCount(), maximum);

            destination.setItem(
                slot,
                offered.copyWithCount(amount)
            );
            offered.shrink(amount);
        }

        int inserted = originalCount - offered.getCount();

        if (inserted > 0) {
            destination.setChanged();
        }

        return inserted;
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

        if (state.is(ModBlocks.PHALANX_TURRET)) {
            return PhalanxTurretBlock.resolve(level, position, state);
        }

        return level.getBlockEntity(position) instanceof Container container
            ? container
            : null;
    }

    private record Node(
        BlockPos position,
        int distance
    ) {
    }

    private record Endpoint(
        BlockPos inventoryPosition,
        Direction inventoryFace,
        int distance
    ) {
    }
}
