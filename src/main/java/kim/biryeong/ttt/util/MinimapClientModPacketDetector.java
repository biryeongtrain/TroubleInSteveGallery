package kim.biryeong.ttt.util;

import kim.biryeong.ttt.TroubleInTerroristTownMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class MinimapClientModPacketDetector {
    private static final Logger LOGGER = TroubleInTerroristTownMod.LOGGER;
    private static final String JOURNEYMAP_NAMESPACE = "journeymap";
    private static final String XAEROMINIMAP_NAMESPACE = "xaerominimap";
    private static final Identifier JOURNEYMAP_HANDSHAKE_CHANNEL = Identifier.of(JOURNEYMAP_NAMESPACE, "version");
    private static final Identifier JOURNEYMAP_PERMISSION_REQUEST_CHANNEL = Identifier.of(JOURNEYMAP_NAMESPACE, "perm_req");
    private static final Identifier XAERO_MINIMAP_HANDSHAKE_CHANNEL = Identifier.of(XAEROMINIMAP_NAMESPACE, "main");
    private static final String JOURNEYMAP_HANDSHAKE_VERSION_JSON =
            "{\"journeymap_version\":{\"full\":\"6.0.0-beta.52\",\"major\":6,\"minor\":0,\"micro\":0,\"patch\":\"-beta.52\"},"
                    + "\"loader_version\":\"0.132.0+1.21.8\",\"loader\":\"fabric\",\"minecraft_version\":\"1.21.8\"}";
    private static final int XAERO_MINIMAP_HANDSHAKE_PACKET_ID = 1;
    private static final int XAERO_MINIMAP_HANDSHAKE_PROTOCOL_VERSION = 3;

    private static final CustomPayload.Id<JourneyMapHandshakePayload> JOURNEYMAP_HANDSHAKE_PAYLOAD_ID =
            new CustomPayload.Id<>(JOURNEYMAP_HANDSHAKE_CHANNEL);
    private static final PacketCodec<RegistryByteBuf, JourneyMapHandshakePayload> JOURNEYMAP_HANDSHAKE_CODEC =
            PacketCodec.ofStatic(
                    (buf, payload) -> buf.writeString(payload.version()),
                    buf -> new JourneyMapHandshakePayload(buf.readString())
            );

    private static final CustomPayload.Id<XaeroMinimapHandshakePayload> XAERO_MINIMAP_HANDSHAKE_PAYLOAD_ID =
            new CustomPayload.Id<>(XAERO_MINIMAP_HANDSHAKE_CHANNEL);
    private static final PacketCodec<RegistryByteBuf, XaeroMinimapHandshakePayload> XAERO_MINIMAP_HANDSHAKE_CODEC =
            PacketCodec.ofStatic(
                    (buf, payload) -> {
                        buf.writeByte(payload.packetId());
                        buf.writeInt(payload.networkVersion());
                    },
                    buf -> new XaeroMinimapHandshakePayload(buf.readByte(), buf.readInt())
            );
    private static final CustomPayload.Id<RawPayload> JOURNEYMAP_PERMISSION_REQUEST_PAYLOAD_ID =
            new CustomPayload.Id<>(JOURNEYMAP_PERMISSION_REQUEST_CHANNEL);
    private static final PacketCodec<RegistryByteBuf, RawPayload> JOURNEYMAP_PERMISSION_REQUEST_CODEC =
            PacketCodec.ofStatic(
                    (buf, payload) -> buf.writeBytes(payload.data()),
                    buf -> {
                        byte[] data = new byte[buf.readableBytes()];
                        buf.readBytes(data);
                        return new RawPayload(JOURNEYMAP_PERMISSION_REQUEST_PAYLOAD_ID, data);
                    }
            );

    private static boolean payloadTypesRegistered;
    private static boolean globalReceiversRegistered;

    private MinimapClientModPacketDetector() {
        throw new IllegalStateException("Utility class");
    }

    public static void initialize() {
        registerPayloadTypes();
        registerGlobalReceivers();
    }

    private static void registerPayloadTypes() {
        if (payloadTypesRegistered) {
            return;
        }

        try {
            PayloadTypeRegistry.playC2S().register(JOURNEYMAP_HANDSHAKE_PAYLOAD_ID, JOURNEYMAP_HANDSHAKE_CODEC);
        } catch (IllegalArgumentException ignored) {
            // Another mod may register the same payload ID; receiving still works with that registration.
        }

        try {
            PayloadTypeRegistry.playS2C().register(JOURNEYMAP_HANDSHAKE_PAYLOAD_ID, JOURNEYMAP_HANDSHAKE_CODEC);
        } catch (IllegalArgumentException ignored) {
            // Another mod may register the same payload ID; sending still works with that registration.
        }

        try {
            PayloadTypeRegistry.playC2S().register(XAERO_MINIMAP_HANDSHAKE_PAYLOAD_ID, XAERO_MINIMAP_HANDSHAKE_CODEC);
        } catch (IllegalArgumentException ignored) {
            // Another mod may register the same payload ID; receiving still works with that registration.
        }

        try {
            PayloadTypeRegistry.playS2C().register(XAERO_MINIMAP_HANDSHAKE_PAYLOAD_ID, XAERO_MINIMAP_HANDSHAKE_CODEC);
        } catch (IllegalArgumentException ignored) {
            // Another mod may register the same payload ID; sending still works with that registration.
        }

        try {
            PayloadTypeRegistry.playC2S().register(JOURNEYMAP_PERMISSION_REQUEST_PAYLOAD_ID, JOURNEYMAP_PERMISSION_REQUEST_CODEC);
        } catch (IllegalArgumentException ignored) {
            // Another mod may register the same payload ID; receiving still works with that registration.
        }

        payloadTypesRegistered = true;
    }

    public static void onPlayerJoined(ServerPlayerEntity player) {
        registerPayloadTypes();
        registerGlobalReceivers();
        sendJourneyMapHandshake(player);
        sendXaeroMinimapHandshake(player);
    }

    private static void registerGlobalReceivers() {
        if (globalReceiversRegistered) {
            return;
        }

        try {
            ServerPlayNetworking.registerGlobalReceiver(
                    JOURNEYMAP_HANDSHAKE_PAYLOAD_ID,
                    (payload, context) -> onPlayerPacket(context.player(), JOURNEYMAP_HANDSHAKE_CHANNEL)
            );
        } catch (IllegalArgumentException ignored) {
            // Another mod may already own this receiver; packet detection can still run through packet events.
        }

        try {
            ServerPlayNetworking.registerGlobalReceiver(
                    XAERO_MINIMAP_HANDSHAKE_PAYLOAD_ID,
                    (payload, context) -> onPlayerPacket(context.player(), XAERO_MINIMAP_HANDSHAKE_CHANNEL)
            );
        } catch (IllegalArgumentException ignored) {
            // Another mod may already own this receiver; packet detection can still run through packet events.
        }

        try {
            ServerPlayNetworking.registerGlobalReceiver(
                    JOURNEYMAP_PERMISSION_REQUEST_PAYLOAD_ID,
                    (payload, context) -> onPlayerPacket(context.player(), JOURNEYMAP_PERMISSION_REQUEST_CHANNEL)
            );
        } catch (IllegalArgumentException ignored) {
            // Another mod may already own this receiver; packet detection can still run through packet events.
        }

        globalReceiversRegistered = true;
    }

    public static void onPlayerPacket(ServerPlayerEntity player, Packet<?> packet) {
        if (!(packet instanceof CustomPayloadC2SPacket payloadPacket)) {
            return;
        }

        Identifier packetId = payloadPacket.payload().getId().id();
        if (packetId == null) {
            return;
        }
        if (globalReceiversRegistered && isReceiverHandledChannel(packetId)) {
            return;
        }

        onPlayerPacket(player, packetId);
    }

    public static void onPlayerPacket(ServerPlayerEntity player, @Nullable Identifier packetId) {
        if (packetId == null) {
            return;
        }
        if (JOURNEYMAP_HANDSHAKE_CHANNEL.equals(packetId)) {
            logDetectedMod(player, packetId, "JourneyMap");
            return;
        }
        if (JOURNEYMAP_PERMISSION_REQUEST_CHANNEL.equals(packetId)) {
            logDetectedMod(player, packetId, "JourneyMap");
            return;
        }
        if (XAERO_MINIMAP_HANDSHAKE_CHANNEL.equals(packetId)) {
            logDetectedMod(player, packetId, "Xaero Minimap");
        }
    }

    public static void onPlayerDisconnected(ServerPlayerEntity player) {
    }

    private static boolean isReceiverHandledChannel(Identifier packetId) {
        return JOURNEYMAP_HANDSHAKE_CHANNEL.equals(packetId)
                || JOURNEYMAP_PERMISSION_REQUEST_CHANNEL.equals(packetId)
                || XAERO_MINIMAP_HANDSHAKE_CHANNEL.equals(packetId);
    }

    private static void sendJourneyMapHandshake(ServerPlayerEntity player) {
        if (!payloadTypesRegistered) {
            return;
        }

        try {
            ServerPlayNetworking.send(player, new JourneyMapHandshakePayload(JOURNEYMAP_HANDSHAKE_VERSION_JSON));
        } catch (Exception ex) {
            LOGGER.warn(
                    "Failed to send synthetic JourneyMap handshake to player {} (uuid={})",
                    player.getNameForScoreboard(),
                    player.getUuid(),
                    ex
            );
        }
    }

    private static void sendXaeroMinimapHandshake(ServerPlayerEntity player) {
        if (!payloadTypesRegistered) {
            return;
        }

        try {
            ServerPlayNetworking.send(
                    player,
                    new XaeroMinimapHandshakePayload(
                            XAERO_MINIMAP_HANDSHAKE_PACKET_ID,
                            XAERO_MINIMAP_HANDSHAKE_PROTOCOL_VERSION
                    )
            );
        } catch (Exception ex) {
            LOGGER.warn(
                    "Failed to send synthetic Xaero Minimap handshake to player {} (uuid={})",
                    player.getNameForScoreboard(),
                    player.getUuid(),
                    ex
            );
        }
    }

    private static final class JourneyMapHandshakePayload implements CustomPayload {
        private final String version;

        private JourneyMapHandshakePayload(String version) {
            this.version = version;
        }

        String version() {
            return version;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return JOURNEYMAP_HANDSHAKE_PAYLOAD_ID;
        }
    }

    private static final class XaeroMinimapHandshakePayload implements CustomPayload {
        private final int packetId;
        private final int networkVersion;

        private XaeroMinimapHandshakePayload(int packetId, int networkVersion) {
            this.packetId = packetId;
            this.networkVersion = networkVersion;
        }

        int packetId() {
            return packetId;
        }

        int networkVersion() {
            return networkVersion;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return XAERO_MINIMAP_HANDSHAKE_PAYLOAD_ID;
        }
    }

    private static final class RawPayload implements CustomPayload {
        private final Id<RawPayload> id;
        private final byte[] data;

        private RawPayload(Id<RawPayload> id, byte[] data) {
            this.id = id;
            this.data = data;
        }

        byte[] data() {
            return data;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return id;
        }
    }

    private static void logDetectedMod(ServerPlayerEntity player, Identifier packetId, String modDisplayName) {
        LOGGER.info(
                "Detected {} installation from client mod packets: player={}, uuid={}, packet={}",
                modDisplayName,
                player.getNameForScoreboard(),
                player.getUuid(),
                packetId
        );
    }
}
