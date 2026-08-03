package com.andye.warmod.radar.display.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.radar.display.RadarDisplayConstants;
import com.andye.warmod.radar.display.RadarDisplayOfflineReason;
import com.andye.warmod.radar.display.RadarDisplaySnapshot;
import com.andye.warmod.radar.network.RadarNetworkCodecs;
import com.andye.warmod.radar.station.RadarStationObservation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundRadarDisplayStatePayload(
    RadarDisplaySnapshot snapshot
) implements CustomPacketPayload {
    public static final Type<ClientboundRadarDisplayStatePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(
            WarMod.MOD_ID,
            "radar_display_state"
        ));

    public static final StreamCodec<
        RegistryFriendlyByteBuf,
        ClientboundRadarDisplayStatePayload
    > STREAM_CODEC = StreamCodec.of(
        ClientboundRadarDisplayStatePayload::write,
        ClientboundRadarDisplayStatePayload::read
    );

    private static void write(
        final RegistryFriendlyByteBuf buffer,
        final ClientboundRadarDisplayStatePayload payload
    ) {
        RadarDisplaySnapshot snapshot = payload.snapshot();

        buffer.writeUUID(snapshot.displayId());
        buffer.writeIdentifier(snapshot.dimension());
        buffer.writeBlockPos(snapshot.controller());
        buffer.writeVarInt(snapshot.facing().get3DDataValue());
        buffer.writeVarInt(snapshot.size());
        buffer.writeVarInt(snapshot.displayRadius());
        buffer.writeBoolean(snapshot.structureValid());
        buffer.writeBoolean(snapshot.online());
        buffer.writeVarInt(snapshot.offlineReason().ordinal());

        buffer.writeBoolean(snapshot.radarId() != null);
        if (snapshot.radarId() != null) {
            buffer.writeUUID(snapshot.radarId());
        }

        buffer.writeBoolean(snapshot.radarCentre() != null);
        if (snapshot.radarCentre() != null) {
            buffer.writeBlockPos(snapshot.radarCentre());
        }

        buffer.writeLong(snapshot.serverGameTime());
        buffer.writeLong(snapshot.phaseOffset());
        buffer.writeVarInt(snapshot.sweepPeriodTicks());
        buffer.writeDouble(snapshot.warningRadius());
        buffer.writeDouble(snapshot.fireRadius());
        buffer.writeVarInt(snapshot.redstoneSignal());

        buffer.writeVarInt(snapshot.observations().size());

        for (RadarStationObservation observation
            : snapshot.observations()) {
            buffer.writeUUID(observation.trackId());

            RadarNetworkCodecs.writeTrack(
                buffer,
                observation.trackSnapshot()
            );

            RadarNetworkCodecs.writeVec(
                buffer,
                observation.observedPosition()
            );

            RadarNetworkCodecs.writeVec(
                buffer,
                observation.observedVelocity()
            );

            RadarNetworkCodecs.writeVec(
                buffer,
                observation.predictedImpactPosition()
            );

            buffer.writeLong(observation.observationGameTime());
            buffer.writeDouble(observation.observedRouteTime());
            buffer.writeBoolean(observation.threatensWarningZone());
        }
    }

    private static ClientboundRadarDisplayStatePayload read(
        final RegistryFriendlyByteBuf buffer
    ) {
        UUID displayId = buffer.readUUID();
        Identifier dimension = buffer.readIdentifier();
        BlockPos controller = buffer.readBlockPos();

        Direction facing = Direction.from3DDataValue(
            buffer.readVarInt()
        );

        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException(
                "Radar Display facing must be horizontal"
            );
        }

        int size = buffer.readVarInt();
        int displayRadius = buffer.readVarInt();
        boolean structureValid = buffer.readBoolean();
        boolean online = buffer.readBoolean();

        RadarDisplayOfflineReason reason =
            RadarDisplayOfflineReason.fromNetworkId(
                buffer.readVarInt()
            );

        UUID radarId = buffer.readBoolean()
            ? buffer.readUUID()
            : null;

        BlockPos radarCentre = buffer.readBoolean()
            ? buffer.readBlockPos()
            : null;

        long serverGameTime = buffer.readLong();
        long phaseOffset = buffer.readLong();
        int sweepPeriodTicks = buffer.readVarInt();
        double warningRadius = buffer.readDouble();
        double fireRadius = buffer.readDouble();
        int redstoneSignal = buffer.readVarInt();

        int count = buffer.readVarInt();

        if (count < 0
            || count > RadarDisplayConstants.MAX_OBSERVED_TRACKS) {
            throw new IllegalArgumentException(
                "Invalid Radar Display observation count: " + count
            );
        }

        List<RadarStationObservation> observations =
            new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            observations.add(new RadarStationObservation(
                buffer.readUUID(),
                RadarNetworkCodecs.readTrack(buffer),
                RadarNetworkCodecs.readVec(buffer),
                RadarNetworkCodecs.readVec(buffer),
                RadarNetworkCodecs.readVec(buffer),
                buffer.readLong(),
                buffer.readDouble(),
                buffer.readBoolean()
            ));
        }

        return new ClientboundRadarDisplayStatePayload(
            new RadarDisplaySnapshot(
                displayId,
                dimension,
                controller,
                facing,
                size,
                displayRadius,
                structureValid,
                online,
                reason,
                radarId,
                radarCentre,
                serverGameTime,
                phaseOffset,
                sweepPeriodTicks,
                warningRadius,
                fireRadius,
                redstoneSignal,
                observations
            )
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
