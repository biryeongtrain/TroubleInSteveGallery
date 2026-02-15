package kim.biryeong.ttt.game.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import kim.biryeong.ttt.game.data.Date;
import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.game.data.PlayerRoundDataInstance;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
final class GameDataManager {
    private static final String PLAYER_DATA_DIRECTORY = "playerData";
    private static final String ROUND_DATA_DIRECTORY = "roundData";

    private final Logger logger = LoggerFactory.getLogger("TTS_DataManager");
    private final Gson gson = new Gson();
    private final Map<UUID, PlayerDataInstance> playerDataByUuid = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRoundDataInstance> roundDataByUuid = new ConcurrentHashMap<>();
    private final Path ttsPath;
    private final GameManager gameManager;

    GameDataManager(GameManager gameManager) {
        this.gameManager = gameManager;
        this.ttsPath = FabricLoader.getInstance().getGameDir().resolve("tts");
    }

    boolean tryToLoadPlayerData(UUID uuid) {
        if (!ensureDirectory(this.ttsPath, "base tts data")) {
            return false;
        }

        Path path = getPlayerDataPath(uuid);
        if (!Files.exists(path)) {
            return false;
        }

        try {
            String string = Files.readString(path);
            JsonElement json = this.gson.fromJson(string, JsonElement.class);
            PlayerDataInstance data = PlayerDataInstance.CODEC.decode(JsonOps.INSTANCE, json).getOrThrow().getFirst();
            this.playerDataByUuid.put(uuid, data);
            return true;
        } catch (Exception exception) {
            this.logger.error("Cannot read player data for {} from {}", uuid, path, exception);
            backupCorruptedPlayerData(uuid, path);
            return false;
        }
    }

    void createNewData(UUID uuid) {
        this.playerDataByUuid.put(uuid, PlayerDataInstance.createNew(uuid));
    }

    void saveAll() {
        for (UUID uuid : Set.copyOf(this.playerDataByUuid.keySet())) {
            saveData(uuid, false);
        }
    }

    void saveData(UUID uuid, boolean removeAfterSave) {
        if (!ensureDirectory(this.ttsPath.resolve(PLAYER_DATA_DIRECTORY), "player data")) {
            return;
        }

        PlayerDataInstance data = removeAfterSave
                ? this.playerDataByUuid.remove(uuid)
                : this.playerDataByUuid.get(uuid);

        if (data == null) {
            this.logger.warn("Cannot save player data: no loaded data exists for {}", uuid);
            return;
        }

        String encoded;
        try {
            encoded = PlayerDataInstance.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().toString();
        } catch (Exception exception) {
            this.logger.error("Cannot encode player data for {}", uuid, exception);
            return;
        }

        writeText(getPlayerDataPath(uuid), encoded, "player data", uuid.toString());
    }

    void saveRoundData() {
        if (this.roundDataByUuid.isEmpty()) {
            return;
        }

        Date date = Date.fromNow();
        Path roundDirectoryPath = this.ttsPath.resolve(ROUND_DATA_DIRECTORY).resolve(date.toString());
        if (!ensureDirectory(roundDirectoryPath, "round data")) {
            return;
        }

        this.roundDataByUuid.forEach((uuid, roundData) -> {
            String encoded;
            try {
                encoded = PlayerRoundDataInstance.CODEC.encodeStart(JsonOps.INSTANCE, roundData).getOrThrow().toString();
            } catch (Exception exception) {
                this.logger.error("Cannot encode round data for {}", uuid, exception);
                return;
            }
            writeText(getRoundDataPath(date, uuid), encoded, "round data", uuid.toString());
        });

        this.roundDataByUuid.clear();
    }

    void startToRecordKillData() {
        var playerManager = GameManager.server.getPlayerManager();
        Set<UUID> participants = Set.copyOf(GameManager.getInstance().getPlayers());

        for (UUID participantUuid : participants) {
            ServerPlayerEntity player = playerManager.getPlayer(participantUuid);
            if (player == null) {
                this.logger.warn(
                        "Cannot initialize round kill tracking for {}: player is not online",
                        participantUuid
                );
                continue;
            }
            this.roundDataByUuid.put(participantUuid, PlayerRoundDataInstance.create(player));
        }
    }

    void recordKillData(@Nullable ServerPlayerEntity killer, ServerPlayerEntity victim, DamageSource damageSource) {
        if (!GameManager.getInstance().isGameStarted()) {
            throw new IllegalStateException("Game is not started.");
        }

        int elapsedSeconds = this.gameManager.gameInstanceManager.getElapsedTicks() / 20;
        if (killer != null) {
            recordRoundKillData(killer.getUuid(), elapsedSeconds, victim, damageSource);
        }
        recordRoundKillData(victim.getUuid(), elapsedSeconds, killer, damageSource);
    }

    private void recordRoundKillData(
            UUID recorderUuid,
            int elapsedSeconds,
            @Nullable ServerPlayerEntity counterpartPlayer,
            DamageSource damageSource
    ) {
        PlayerRoundDataInstance roundData = this.roundDataByUuid.get(recorderUuid);
        if (roundData == null) {
            this.logger.warn(
                    "Cannot record kill data for {}: round data is not initialized",
                    recorderUuid
            );
            return;
        }

        roundData.recordKillData(elapsedSeconds, counterpartPlayer, damageSource);
    }

    private void backupCorruptedPlayerData(UUID uuid, Path sourcePath) {
        Path backupPath = getPlayerDataBackupPath(uuid);
        try {
            Files.copy(sourcePath, backupPath);
        } catch (IOException exception) {
            this.logger.error("Cannot backup corrupted player data {} to {}", sourcePath, backupPath, exception);
        }
    }

    private boolean ensureDirectory(Path path, String operationTarget) {
        try {
            Files.createDirectories(path);
            return true;
        } catch (IOException exception) {
            this.gameManager.LOGGER.error("Cannot create directory for {}", operationTarget, exception);
            return false;
        }
    }

    private void writeText(Path path, String encodedData, String dataType, String identifier) {
        try {
            Files.writeString(path, encodedData);
        } catch (IOException exception) {
            this.logger.error("Cannot save {} for {}", dataType, identifier, exception);
            this.logger.info("Unpersisted {} payload: {}", dataType, encodedData);
        }
    }

    private Path getPlayerDataPath(UUID uuid) {
        return this.ttsPath.resolve(PLAYER_DATA_DIRECTORY).resolve(uuid + ".json");
    }

    private Path getPlayerDataBackupPath(UUID uuid) {
        return this.ttsPath.resolve(PLAYER_DATA_DIRECTORY).resolve(uuid + "-bak.json");
    }

    private Path getRoundDataPath(Date date, UUID uuid) {
        return this.ttsPath.resolve(ROUND_DATA_DIRECTORY).resolve(date.toString()).resolve(uuid + ".json");
    }

    public @NotNull PlayerDataInstance getData(ServerPlayerEntity player) {
        return Objects.requireNonNull(
                getData(player.getUuid()),
                "Missing player data for " + player.getUuid()
        );
    }

    public @Nullable PlayerDataInstance getData(UUID uuid) {
        return this.playerDataByUuid.get(uuid);
    }
}
