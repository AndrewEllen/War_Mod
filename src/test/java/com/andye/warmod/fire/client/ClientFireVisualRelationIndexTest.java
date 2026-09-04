package com.andye.warmod.fire.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.network.FireVisualBand;
import com.andye.warmod.fire.network.FireVisualCell;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class ClientFireVisualRelationIndexTest {
    @Test
    void matchesTheLivePredicateForActiveAndRetiringRepresentations() {
        List<FireVisualCell> live = List.of(
            cell(11L, 0L),       // incoming parent -> existing id
            cell(12L, 21L),      // incoming id -> existing parent
            cell(13L, 31L),      // shared non-zero parent
            cell(14L, 0L));      // zero parent must not make siblings related
        ClientFireVisualManager.RepresentationRelationIndex index =
            new ClientFireVisualManager.RepresentationRelationIndex();
        live.forEach(index::add);

        for (FireVisualCell incoming : List.of(
            cell(101L, 11L), cell(21L, 0L), cell(102L, 31L),
            cell(103L, 0L), cell(104L, 44L))) {
            boolean expected = live.stream().anyMatch(existing ->
                ClientFireVisualManager.related(incoming, existing));
            assertEquals(expected, index.relatedTo(incoming),
                () -> "relation mismatch for incoming cell " + incoming.id());
        }
    }

    @Test
    void keepsARepresentationRelatedUntilItsLastActiveOrRetiringCopyLeaves() {
        FireVisualCell outgoing = cell(50L, 0L);
        FireVisualCell incomingChild = cell(51L, 50L);
        ClientFireVisualManager.RepresentationRelationIndex index =
            new ClientFireVisualManager.RepresentationRelationIndex();

        index.add(outgoing); // active complete-band member
        index.add(outgoing); // retiring smoke copy during replacement
        index.remove(outgoing);
        assertTrue(index.relatedTo(incomingChild));
        index.remove(outgoing);
        assertFalse(index.relatedTo(incomingChild));
    }

    private static FireVisualCell cell(final long id, final long parentId) {
        return new FireVisualCell(id, parentId, FireVisualBand.PATCH, 1,
            (int) id, 64, 0, new Vec3(id, 64.5, 0.5), new Vec3(0.4, 0.8, 0.4),
            1L, 0.8F, 1.2F, 0.4F, 0.9F, 0.8F, 0.7F, 0.6F,
            new Vec3(0.0, 0.1, 0.0), 1, id * 31L,
            Direction.UP, FirePhase.FLAMING, 4L);
    }
}
