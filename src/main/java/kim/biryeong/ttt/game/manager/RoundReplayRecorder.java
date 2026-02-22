package kim.biryeong.ttt.game.manager;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.HashSet;
import java.util.Set;

final class RoundReplayRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoundReplayRecorder.class);
    private static final String SERVER_REPLAY_MOD_ID = "server-replay";

    private final Set<String> chunkRecorderNames = new HashSet<>();
    private boolean missingDependencyLogged = false;

    void startRoundChunkRecording(
            MinecraftServer server,
            ServerWorld world,
            BlockBounds mapBounds,
            Identifier mapId
    ) {
        if (!isAvailable()) {
            return;
        }
        if (world == null || mapBounds == null) {
            LOGGER.warn("Cannot start chunk replay recording: world or map bounds are null.");
            return;
        }

        BlockPos min = mapBounds.min();
        BlockPos max = mapBounds.max();
        int fromX = Math.floorDiv(min.getX(), 16);
        int fromZ = Math.floorDiv(min.getZ(), 16);
        int toX = Math.floorDiv(max.getX(), 16);
        int toZ = Math.floorDiv(max.getZ(), 16);

        String recorderName = buildChunkRecorderName(mapId);
        String command = "replay start chunks from %d %d to %d %d in %s named %s".formatted(
                fromX,
                fromZ,
                toX,
                toZ,
                world.getRegistryKey().getValue(),
                recorderName
        );
        boolean started = execute(server, command);
        if (!started) {
            LOGGER.warn(
                    "Failed to start chunk replay recording for map {} in dimension {} (chunks: {} {} -> {} {}).",
                    mapId,
                    world.getRegistryKey().getValue(),
                    fromX,
                    fromZ,
                    toX,
                    toZ
            );
            return;
        }

        this.chunkRecorderNames.add(recorderName);
        LOGGER.info(
                "Started chunk replay recording '{}' for map {} in dimension {} (chunks: {} {} -> {} {}).",
                recorderName,
                mapId,
                world.getRegistryKey().getValue(),
                fromX,
                fromZ,
                toX,
                toZ
        );
    }

    void stopRoundRecordings(MinecraftServer server, boolean save) {
        if (!isAvailable()) {
            this.chunkRecorderNames.clear();
            return;
        }

        for (String recorderName : Set.copyOf(this.chunkRecorderNames)) {
            boolean stopped = execute(server, "replay stop chunks named " + recorderName + " " + save);
            if (!stopped) {
                LOGGER.warn("Failed to stop chunk replay recording '{}'.", recorderName);
                continue;
            }

            this.chunkRecorderNames.remove(recorderName);
        }

        this.chunkRecorderNames.clear();
    }

    void clear() {
        this.chunkRecorderNames.clear();
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

    private static String buildChunkRecorderName(Identifier mapId) {
        return "ttt_round_%s_%d".formatted(
                mapId.toString().replace(':', '_').replace('/', '_'),
                System.currentTimeMillis()
        );
    }
}
