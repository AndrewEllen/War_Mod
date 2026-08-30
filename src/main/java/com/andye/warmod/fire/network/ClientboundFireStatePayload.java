package com.andye.warmod.fire.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.fire.FirePhase;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Periodic complete snapshots plus explicit loss-tolerant cell deltas. */
public record ClientboundFireStatePayload(long serverGameTime, long generation,
    int completeBandMask, List<CellEntry> cells, List<Long> removedCellIds,
    boolean emberComplete, List<EmberEntry> embers)
    implements CustomPacketPayload {
    public static final int MAX_CELLS = 1_024;
    public static final int MAX_REMOVED_CELLS = 1_024;
    /** Compatibility alias for diagnostics written before the cell protocol. */
    public static final int MAX_ENTRIES = MAX_CELLS;
    public static final int MAX_EMBERS = 96;
    public static final Type<ClientboundFireStatePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "fire_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFireStatePayload>
        STREAM_CODEC = StreamCodec.of(ClientboundFireStatePayload::write,
            ClientboundFireStatePayload::read);

    private static void write(final RegistryFriendlyByteBuf buffer,
        final ClientboundFireStatePayload payload) {
        buffer.writeLong(payload.serverGameTime);
        buffer.writeLong(payload.generation);
        buffer.writeByte(payload.completeBandMask);
        int count = Math.min(MAX_CELLS, payload.cells.size());
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            CellEntry cell = payload.cells.get(index);
            buffer.writeLong(cell.id);
            buffer.writeLong(cell.parentId);
            buffer.writeByte(cell.band);
            buffer.writeVarInt(cell.cellSize);
            buffer.writeInt(cell.cellX); buffer.writeInt(cell.cellY); buffer.writeInt(cell.cellZ);
            buffer.writeDouble(cell.centroidX); buffer.writeDouble(cell.centroidY);
            buffer.writeDouble(cell.centroidZ);
            buffer.writeFloat(cell.extentX); buffer.writeFloat(cell.extentY);
            buffer.writeFloat(cell.extentZ);
            buffer.writeLong(cell.occupancyMask);
            buffer.writeFloat(cell.flameEnergy); buffer.writeFloat(cell.smokeMass);
            buffer.writeFloat(cell.maximumHeat); buffer.writeFloat(cell.averageIntensity);
            buffer.writeFloat(cell.coveredArea); buffer.writeFloat(cell.clumpStrength);
            buffer.writeFloat(cell.windX); buffer.writeFloat(cell.windY);
            buffer.writeFloat(cell.windZ);
            buffer.writeVarInt(cell.hostCount);
            buffer.writeLong(cell.seed);
            buffer.writeByte(cell.dominantFace);
            buffer.writeByte(cell.phase);
            buffer.writeLong(cell.ignitionGameTime);
        }
        int removedCount = Math.min(MAX_REMOVED_CELLS, payload.removedCellIds.size());
        buffer.writeVarInt(removedCount);
        for (int index = 0; index < removedCount; index++) {
            buffer.writeLong(payload.removedCellIds.get(index));
        }
        buffer.writeBoolean(payload.emberComplete);
        int emberCount = Math.min(MAX_EMBERS, payload.embers.size());
        buffer.writeVarInt(emberCount);
        for (int index = 0; index < emberCount; index++) {
            EmberEntry ember = payload.embers.get(index);
            buffer.writeLong(ember.id); buffer.writeDouble(ember.x);
            buffer.writeDouble(ember.y); buffer.writeDouble(ember.z);
            buffer.writeFloat(ember.velocityX); buffer.writeFloat(ember.velocityY);
            buffer.writeFloat(ember.velocityZ); buffer.writeFloat(ember.windX);
            buffer.writeFloat(ember.windY); buffer.writeFloat(ember.windZ);
            buffer.writeFloat(ember.intensity);
            buffer.writeLong(ember.seed); buffer.writeLong(ember.startGameTime);
            buffer.writeVarInt(ember.lifetime);
        }
    }

    private static ClientboundFireStatePayload read(final RegistryFriendlyByteBuf buffer) {
        long gameTime = buffer.readLong();
        long generation = buffer.readLong();
        int completeBandMask = buffer.readUnsignedByte();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_CELLS)
            throw new IllegalArgumentException("Invalid fire visual cell count");
        List<CellEntry> cells = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cells.add(new CellEntry(buffer.readLong(), buffer.readLong(), buffer.readByte(),
                buffer.readVarInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readLong(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readVarInt(), buffer.readLong(), buffer.readByte(),
                buffer.readByte(), buffer.readLong()));
        }
        int removedCount = buffer.readVarInt();
        if (removedCount < 0 || removedCount > MAX_REMOVED_CELLS)
            throw new IllegalArgumentException("Invalid removed fire cell count");
        List<Long> removed = new ArrayList<>(removedCount);
        for (int index = 0; index < removedCount; index++) removed.add(buffer.readLong());
        boolean emberComplete = buffer.readBoolean();
        int emberCount = buffer.readVarInt();
        if (emberCount < 0 || emberCount > MAX_EMBERS)
            throw new IllegalArgumentException("Invalid firebrand entry count");
        List<EmberEntry> embers = new ArrayList<>(emberCount);
        for (int index = 0; index < emberCount; index++) embers.add(new EmberEntry(
            buffer.readLong(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            buffer.readLong(), buffer.readLong(), buffer.readVarInt()));
        return new ClientboundFireStatePayload(gameTime, generation, completeBandMask,
            List.copyOf(cells), List.copyOf(removed), emberComplete,
            List.copyOf(embers));
    }

    public ClientboundFireStatePayload(final long serverGameTime,
        final long generation, final int completeBandMask,
        final List<CellEntry> cells, final boolean emberComplete,
        final List<EmberEntry> embers) {
        this(serverGameTime, generation, completeBandMask, cells, List.of(),
            emberComplete, embers);
    }

    public boolean isWellFormed() {
        if (generation < 0L || (completeBandMask & ~FireVisualBand.COMPLETE_MASK) != 0
            || cells == null || cells.size() > MAX_CELLS
            || removedCellIds == null || removedCellIds.size() > MAX_REMOVED_CELLS
            || embers == null
            || embers.size() > MAX_EMBERS) return false;
        for (CellEntry cell : cells) if (cell == null || !cell.isWellFormed()) return false;
        for (Long removed : removedCellIds) if (removed == null || removed <= 0L) return false;
        for (EmberEntry ember : embers) if (ember == null || !ember.isWellFormed()) return false;
        return true;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record CellEntry(long id, long parentId, byte band, int cellSize,
        int cellX, int cellY, int cellZ,
        double centroidX, double centroidY, double centroidZ,
        float extentX, float extentY, float extentZ, long occupancyMask,
        float flameEnergy, float smokeMass, float maximumHeat,
        float averageIntensity, float coveredArea, float clumpStrength,
        float windX, float windY, float windZ, int hostCount, long seed,
        byte dominantFace, byte phase, long ignitionGameTime) {

        public static CellEntry from(final FireVisualCell cell) {
            return new CellEntry(cell.id(), cell.parentId(),
                (byte) cell.band().wireId(), cell.cellSize(),
                cell.cellX(), cell.cellY(), cell.cellZ(), cell.centroid().x,
                cell.centroid().y, cell.centroid().z, (float) cell.extents().x,
                (float) cell.extents().y, (float) cell.extents().z,
                cell.occupancyMask(), cell.flameEnergy(), cell.smokeMass(),
                cell.maximumHeat(), cell.averageIntensity(), cell.coveredArea(),
                cell.clumpStrength(),
                (float) cell.wind().x, (float) cell.wind().y, (float) cell.wind().z,
                cell.hostCount(), cell.seed(), (byte) cell.dominantFace().ordinal(),
                (byte) cell.phase().ordinal(), cell.ignitionGameTime());
        }

        public FireVisualCell toCell() {
            return new FireVisualCell(id, parentId,
                FireVisualBand.fromWireId(Byte.toUnsignedInt(band)),
                cellSize, cellX, cellY, cellZ,
                new Vec3(centroidX, centroidY, centroidZ),
                new Vec3(extentX, extentY, extentZ), occupancyMask,
                flameEnergy, smokeMass, maximumHeat, averageIntensity, coveredArea,
                clumpStrength, new Vec3(windX, windY, windZ), hostCount, seed,
                Direction.values()[Byte.toUnsignedInt(dominantFace)],
                FirePhase.values()[Byte.toUnsignedInt(phase)], ignitionGameTime);
        }

        public boolean isWellFormed() {
            int bandIndex = Byte.toUnsignedInt(band);
            int faceIndex = Byte.toUnsignedInt(dominantFace);
            int phaseIndex = Byte.toUnsignedInt(phase);
            return id > 0L && parentId >= 0L && parentId != id
                && bandIndex < FireVisualBand.values().length
                && cellSize > 0 && cellSize <= 4_096
                && Double.isFinite(centroidX) && Double.isFinite(centroidY)
                && Double.isFinite(centroidZ) && finiteNonNegative(extentX)
                && finiteNonNegative(extentY) && finiteNonNegative(extentZ)
                && occupancyMask != 0L && finiteNonNegative(flameEnergy)
                && finiteNonNegative(smokeMass) && finiteNonNegative(maximumHeat)
                && finiteNonNegative(averageIntensity) && finiteNonNegative(coveredArea)
                && finiteNonNegative(clumpStrength) && clumpStrength <= 2.0F
                && finite(windX, 2.5F) && finite(windY, 2.5F)
                && finite(windZ, 2.5F) && hostCount > 0 && hostCount <= 65_536
                && faceIndex < Direction.values().length
                && phaseIndex < FirePhase.values().length;
        }

        private static boolean finiteNonNegative(final float value) {
            return Float.isFinite(value) && value >= 0.0F;
        }
    }

    public record EmberEntry(long id, double x, double y, double z,
        float velocityX, float velocityY, float velocityZ,
        float windX, float windY, float windZ, float intensity,
        long seed, long startGameTime, int lifetime) {
        public boolean isWellFormed() {
            return id > 0L && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && finite(velocityX, 4.0F) && finite(velocityY, 4.0F)
                && finite(velocityZ, 4.0F) && finite(windX, 2.5F)
                && finite(windY, 2.5F) && finite(windZ, 2.5F)
                && finiteRange(intensity, 0.0F, 1.2F)
                && lifetime > 0 && lifetime <= 320;
        }
        private static boolean finiteRange(final float value, final float minimum,
            final float maximum) {
            return Float.isFinite(value) && value >= minimum && value <= maximum;
        }
    }

    private static boolean finite(final float value, final float limit) {
        return Float.isFinite(value) && Math.abs(value) <= limit;
    }
}
