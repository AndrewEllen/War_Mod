package com.andye.warmod.warhead.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Mth;

/** Combines nearby encoded debris entries into fewer, larger connected fragments. */
final class WarheadDebrisVisualTuner {
    private static final int MAX_PARTS = 48;

    private WarheadDebrisVisualTuner() { }

    static ClientboundWarheadDebrisPayload tune(final ClientboundWarheadDebrisPayload payload,
        final float visualScale, final boolean nuclear) {
        if (payload == null || payload.entries().size() < 2) return payload;
        int groupSize = nuclear
            ? Mth.clamp(Math.round(2.0F + visualScale * 0.85F), 3, 6)
            : visualScale < 0.55F ? 1 : Mth.clamp(Math.round(1.0F + visualScale), 2, 4);
        if (groupSize <= 1) return payload;

        ArrayList<ClientboundWarheadDebrisPayload.Entry> remaining = new ArrayList<>(payload.entries());
        ArrayList<ClientboundWarheadDebrisPayload.Entry> combined = new ArrayList<>();
        while (!remaining.isEmpty() && combined.size() < 320) {
            ClientboundWarheadDebrisPayload.Entry root = remaining.remove(0);
            remaining.sort(Comparator.comparingDouble(entry -> distanceSquared(root, entry)));
            ArrayList<ClientboundWarheadDebrisPayload.Entry> group = new ArrayList<>();
            group.add(root);
            int partCount = root.parts().size();
            while (!remaining.isEmpty() && group.size() < groupSize) {
                ClientboundWarheadDebrisPayload.Entry candidate = remaining.get(0);
                if (partCount + candidate.parts().size() > MAX_PARTS) break;
                if (distanceSquared(root, candidate) > 72.0) break;
                remaining.remove(0);
                group.add(candidate);
                partCount += candidate.parts().size();
            }
            combined.add(combine(group, visualScale, nuclear));
        }
        return new ClientboundWarheadDebrisPayload(payload.impactId(), payload.originX(), payload.originY(),
            payload.originZ(), payload.spawnGameTime(), List.copyOf(combined));
    }

    private static ClientboundWarheadDebrisPayload.Entry combine(
        final List<ClientboundWarheadDebrisPayload.Entry> group, final float visualScale,
        final boolean nuclear) {
        double weightTotal = 0.0;
        double offsetX = 0.0, offsetY = 0.0, offsetZ = 0.0;
        double velocityX = 0.0, velocityY = 0.0, velocityZ = 0.0;
        double spinX = 0.0, spinY = 0.0, spinZ = 0.0;
        for (ClientboundWarheadDebrisPayload.Entry entry : group) {
            double weight = Math.max(1, entry.parts().size());
            weightTotal += weight;
            offsetX += entry.offsetX() * weight;
            offsetY += entry.offsetY() * weight;
            offsetZ += entry.offsetZ() * weight;
            velocityX += entry.velocityX() * weight;
            velocityY += entry.velocityY() * weight;
            velocityZ += entry.velocityZ() * weight;
            spinX += entry.spinX() * weight;
            spinY += entry.spinY() * weight;
            spinZ += entry.spinZ() * weight;
        }
        offsetX /= weightTotal; offsetY /= weightTotal; offsetZ /= weightTotal;
        velocityX /= weightTotal; velocityY /= weightTotal; velocityZ /= weightTotal;
        spinX /= weightTotal; spinY /= weightTotal; spinZ /= weightTotal;

        ArrayList<ClientboundWarheadDebrisPayload.Part> parts = new ArrayList<>(MAX_PARTS);
        for (ClientboundWarheadDebrisPayload.Entry entry : group) {
            for (ClientboundWarheadDebrisPayload.Part part : entry.parts()) {
                if (parts.size() >= MAX_PARTS) break;
                int x = Mth.clamp((int) Math.round(entry.offsetX() + part.offsetX() - offsetX), -12, 12);
                int y = Mth.clamp((int) Math.round(entry.offsetY() + part.offsetY() - offsetY), -12, 12);
                int z = Mth.clamp((int) Math.round(entry.offsetZ() + part.offsetZ() - offsetZ), -12, 12);
                parts.add(new ClientboundWarheadDebrisPayload.Part(part.blockStateId(), (byte) x, (byte) y, (byte) z));
            }
        }

        double horizontal = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        double maximumHorizontal = (nuclear ? 0.58 : 0.72) + 0.34 / Math.sqrt(Math.max(1, parts.size()));
        if (horizontal > maximumHorizontal) {
            double factor = maximumHorizontal / horizontal;
            velocityX *= factor;
            velocityZ *= factor;
        }
        double maximumVertical = (nuclear ? 0.42 : 0.56) + 0.34 / Math.sqrt(Math.max(1, parts.size()));
        velocityY = Mth.clamp(velocityY, 0.10, maximumVertical);
        double spinScale = 1.0 / Math.sqrt(Math.max(1.0, parts.size() / 4.0));
        float scale = Mth.clamp(0.92F + (float) Math.sqrt(parts.size()) * 0.028F
            + Math.min(0.08F, visualScale * 0.018F), 0.92F, 1.15F);
        int lifetime = Mth.clamp(90 + parts.size() * 2, 100, 240);
        return new ClientboundWarheadDebrisPayload.Entry((float) offsetX, (float) offsetY, (float) offsetZ,
            (float) velocityX, (float) velocityY, (float) velocityZ,
            (float) (spinX * spinScale), (float) (spinY * spinScale), (float) (spinZ * spinScale),
            scale, lifetime, List.copyOf(parts));
    }

    private static double distanceSquared(final ClientboundWarheadDebrisPayload.Entry a,
        final ClientboundWarheadDebrisPayload.Entry b) {
        double x = a.offsetX() - b.offsetX();
        double y = a.offsetY() - b.offsetY();
        double z = a.offsetZ() - b.offsetZ();
        return x * x + y * y + z * z;
    }
}
