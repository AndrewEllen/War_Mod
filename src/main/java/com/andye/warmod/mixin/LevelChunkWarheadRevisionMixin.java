package com.andye.warmod.mixin;

import com.andye.warmod.warhead.WarheadChunkRevisionAccess;
import com.andye.warmod.warhead.WarheadSectionRevisionCounter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkWarheadRevisionMixin
    implements WarheadChunkRevisionAccess {
    @Unique
    private final WarheadSectionRevisionCounter war_mod$warheadRevisions =
        new WarheadSectionRevisionCounter();

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void war_mod$recordStateMutation(final BlockPos position,
        final BlockState state, final int flags,
        final CallbackInfoReturnable<BlockState> callback) {
        BlockState previous = callback.getReturnValue();
        if (previous != null && !previous.equals(state)) {
            war_mod$warheadRevisions.markChanged(
                SectionPos.blockToSectionCoord(position.getY()));
        }
    }

    @Override
    public long war_mod$getChunkRevision() {
        return war_mod$warheadRevisions.chunkRevision();
    }

    @Override
    public long war_mod$getSectionRevision(final int sectionY) {
        return war_mod$warheadRevisions.sectionRevision(sectionY);
    }

    @Override
    public void war_mod$markBulkSectionChanged(final int sectionY) {
        war_mod$warheadRevisions.markChanged(sectionY);
    }
}
