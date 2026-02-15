package kim.biryeong.ttt.game.manager;

import kim.biryeong.ttt.util.DebugFakePlayerRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class RoundReplayRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoundReplayRecorder.class);
    private static final String SERVER_REPLAY_MOD_ID = "server-replay";

    private final Set<UUID> recordingPlayers = new HashSet<>();
    private boolean missingDependencyLogged = false;

    void startRoundRecordings(MinecraftServer server, Collection<ServerPlayerEntity> players) {
        if (!isAvailable()) {
            return;
        }

        for (ServerPlayerEntity player : players) {
            UUID uuid = player.getUuid();
            if (this.recordingPlayers.contains(uuid)) {
                continue;
            }
            if (DebugFakePlayerRegistry.contains(uuid)) {
                continue;
            }

            String playerName = player.getGameProfile().getName();
            boolean started = execute(server, "replay start players " + playerName);
            if (!started) {
                LOGGER.warn("Failed to start replay recording for player {} ({})", playerName, uuid);
                continue;
            }

            this.recordingPlayers.add(uuid);
        }
    }

    void stopRoundRecordings(MinecraftServer server, boolean save) {
        if (!isAvailable()) {
            this.recordingPlayers.clear();
            return;
        }

        for (UUID uuid : Set.copyOf(this.recordingPlayers)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
                continue;
            }

            String playerName = player.getGameProfile().getName();
            boolean stopped = execute(server, "replay stop players " + playerName + " " + save);
            if (!stopped) {
                LOGGER.warn("Failed to stop replay recording for player {} ({})", playerName, uuid);
                continue;
            }

            this.recordingPlayers.remove(uuid);
        }

        this.recordingPlayers.clear();
    }

    void stopPlayerRecording(MinecraftServer server, ServerPlayerEntity player, boolean save) {
        UUID uuid = player.getUuid();
        if (!this.recordingPlayers.contains(uuid)) {
            return;
        }
        if (!isAvailable()) {
            this.recordingPlayers.remove(uuid);
            return;
        }

        ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(uuid);
        if (onlinePlayer == null) {
            this.recordingPlayers.remove(uuid);
            return;
        }

        String playerName = onlinePlayer.getGameProfile().getName();
        boolean stopped = execute(server, "replay stop players " + playerName + " " + save);
        if (!stopped) {
            LOGGER.warn("Failed to stop replay recording for player {} ({}) on leave", playerName, uuid);
        }

        this.recordingPlayers.remove(uuid);
    }

    void clear() {
        this.recordingPlayers.clear();
    }

    private boolean isAvailable() {
        boolean isLoaded = FabricLoader.getInstance().isModLoaded(SERVER_REPLAY_MOD_ID);
        if (!isLoaded && !this.missingDependencyLogged) {
            LOGGER.warn("server-replay is not loaded; round replay recording is disabled.");
            this.missingDependencyLogged = true;
        }
        return isLoaded;
    }

    private boolean execute(MinecraftServer server, String command) {
        try {
            return server.getCommandManager().getDispatcher().execute(command, server.getCommandSource()) > 0;
        } catch (Exception exception) {
            LOGGER.error("Error while executing replay command: {}", command, exception);
            return false;
        }
    }
}
