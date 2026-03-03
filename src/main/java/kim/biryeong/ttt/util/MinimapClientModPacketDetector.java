package kim.biryeong.ttt.util;

import kim.biryeong.ttt.TroubleInTerroristTownMod;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MinimapClientModPacketDetector {
    private static final Logger LOGGER = TroubleInTerroristTownMod.LOGGER;
    private static final String JOURNEYMAP_NAMESPACE = "journeymap";
    private static final String XAEROMINIMAP_NAMESPACE = "xaerominimap";
    private static final String XAEROWORLDMAP_NAMESPACE = "xaeroworldmap";

    private static final Map<UUID, Set<DetectedMod>> DETECTED_MODS_BY_PLAYER = new ConcurrentHashMap<>();

    private MinimapClientModPacketDetector() {
        throw new IllegalStateException("Utility class");
    }

    public static void onPlayerPacket(ServerPlayerEntity player, Packet<?> packet) {
        if (!(packet instanceof CustomPayloadC2SPacket payloadPacket)) {
            return;
        }

        Identifier packetId = payloadPacket.payload().getId().id();
        if (packetId == null) {
            return;
        }

        onPlayerPacket(player, packetId);
    }

    public static void onPlayerPacket(ServerPlayerEntity player, @Nullable Identifier packetId) {
        if (packetId == null) {
            return;
        }
        EnumSet<DetectedMod> detectedMods = detectMinimapModByPacketId(packetId);
        if (detectedMods.isEmpty()) {
            return;
        }

        Set<DetectedMod> playerState = DETECTED_MODS_BY_PLAYER.computeIfAbsent(
                player.getUuid(),
                uuid -> ConcurrentHashMap.newKeySet()
        );

        for (DetectedMod mod : detectedMods) {
            if (playerState.add(mod)) {
                logDetectedMod(player, packetId, mod);
            }
        }
    }

    public static void onPlayerDisconnected(ServerPlayerEntity player) {
        DETECTED_MODS_BY_PLAYER.remove(player.getUuid());
    }

    private static EnumSet<DetectedMod> detectMinimapModByPacketId(Identifier packetId) {
        String namespace = packetId.getNamespace();
        EnumSet<DetectedMod> detectedMods = EnumSet.noneOf(DetectedMod.class);

        if (JOURNEYMAP_NAMESPACE.equals(namespace)) {
            detectedMods.add(DetectedMod.JOURNEYMAP);
        }

        if (isXaeroPacket(namespace)) {
            detectedMods.add(DetectedMod.XAEROMINIMAP);
        }

        return detectedMods;
    }

    private static boolean isXaeroPacket(String namespace) {
        return XAEROMINIMAP_NAMESPACE.equals(namespace)
                || XAEROWORLDMAP_NAMESPACE.equals(namespace)
                || namespace.startsWith("xaero");
    }

    private static void logDetectedMod(ServerPlayerEntity player, Identifier packetId, DetectedMod mod) {
        LOGGER.info(
                "Detected {} installation from client mod packets: player={}, uuid={}, packet={}",
                mod.displayName,
                player.getNameForScoreboard(),
                player.getUuid(),
                packetId
        );
    }

    private enum DetectedMod {
        JOURNEYMAP("JourneyMap"),
        XAEROMINIMAP("Xaero Minimap");

        private final String displayName;

        DetectedMod(String displayName) {
            this.displayName = displayName;
        }
    }
}
