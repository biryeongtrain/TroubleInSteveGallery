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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
final class GameDataManager {
    private final Logger LOGGER = LoggerFactory.getLogger("TTS_DataManager");
    private final Gson GSON = new Gson();
    private final Map<UUID, PlayerDataInstance> DATA_MAP = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRoundDataInstance> ROUND_DATA_MAP = new ConcurrentHashMap<>();
    private final Path TTS_PATH;
    private final GameManager gameManager;

    GameDataManager(GameManager gameManager) {
        this.gameManager = gameManager;
        TTS_PATH = FabricLoader.getInstance().getGameDir().resolve("tts");
    }

    boolean tryToLoadPlayerData(UUID uuid) {
        if (!Files.exists(TTS_PATH)) {
            try {
                Files.createDirectories(TTS_PATH);
            } catch (IOException e) {
                gameManager.LOGGER.error("cannot create directory for player data", e);
                return false;
            }
        }

        Path path = getPlayerDataPath(uuid.toString());
        if (!Files.exists(path)) {
            return false;
        }
        try {
            var string = Files.readString(path);
            JsonElement json = GSON.fromJson(string, JsonElement.class);
            var data = PlayerDataInstance.CODEC.decode(JsonOps.INSTANCE, json).getOrThrow();
            DATA_MAP.put(uuid, data.getFirst());
            return true;
        } catch (Exception e) {
            LOGGER.error("cannot read player data", e);
            try {
                LOGGER.warn("copy backup data to");
                Files.copy(path, getPlayerDataPath(uuid + "-bak.json"));
            } catch (IOException ex) {
                LOGGER.error("cannot backup player data", ex);
            }
            return false;
        }
    }

    void createNewData(UUID uuid) {
        DATA_MAP.put(uuid, PlayerDataInstance.createNew(uuid));
    }

    void saveAll() {
        this.DATA_MAP.values().forEach(v -> this.saveData(v.getUuid(), false));
    }

    void saveData(UUID uuid, boolean left) {
        try {
            Files.createDirectories(TTS_PATH.resolve("playerData"));
        } catch (IOException e) {
            gameManager.LOGGER.error("cannot create directory for player data", e);
            return;
        }
        Path path = getPlayerDataPath(uuid.toString());
        var data = left ? DATA_MAP.remove(uuid) : DATA_MAP.get(uuid);
        var string = PlayerDataInstance.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().toString();
        try {
            Files.writeString(path, string);
        } catch (Exception e) {
            LOGGER.error("cannot save player data", e);
            LOGGER.info("unsaved data : \n {}", string);
        }
    }

    void saveRoundData() {
        try {
            Files.createDirectories(TTS_PATH.resolve("roundData"));
        } catch (IOException e) {
            gameManager.LOGGER.error("cannot create directory for round data", e);
            return;
        }
        Date date = Date.fromNow();
        try {
            Files.createDirectories(TTS_PATH.resolve("roundData").resolve(date.toString()));
        } catch (IOException e) {
            gameManager.LOGGER.error("cannot create directory for round data", e);
            return;
        }
        ROUND_DATA_MAP.forEach((uuid, roundData) -> {
            Path path = getRoundDataPath(date, uuid.toString());
            var string = PlayerRoundDataInstance.CODEC.encodeStart(JsonOps.INSTANCE, roundData).getOrThrow().toString();
            try {
                Files.writeString(path, string);
            } catch (Exception e) {
                LOGGER.error("cannot save round data for player {}", uuid, e);
                LOGGER.info("unsaved round data : \n {}", string);
            }
        });

        ROUND_DATA_MAP.clear();
    }


    void startToRecordKillData() {
        var manager = GameManager.server.getPlayerManager();
        GameManager.getInstance().getPlayers().forEach(pUUID -> {
            ServerPlayerEntity player = manager.getPlayer(pUUID);
            if (player == null) {
                LOGGER.error("Player {} is not found! it can't be happened! removing this player in participant list...", pUUID);
                GameManager.getInstance().getPlayers().remove(pUUID);
                return;
            }
            ROUND_DATA_MAP.put(pUUID, PlayerRoundDataInstance.create(player));
        });
    }

    void recordKillData(ServerPlayerEntity killer, ServerPlayerEntity victim, DamageSource damageSource) {
        if (!GameManager.getInstance().isGameStarted()) {
            throw new IllegalStateException("Game is not started!");
        }
        if (killer != null) {
            var killerData = Objects.requireNonNull(ROUND_DATA_MAP.get(killer.getUuid()));
            killerData.recordKillData(gameManager.gameInstanceManager.getElapsedTicks() / 20, victim, damageSource);
        }
        var victimData = Objects.requireNonNull(ROUND_DATA_MAP.get(victim.getUuid()));
        victimData.recordKillData(gameManager.gameInstanceManager.getElapsedTicks() / 20, killer, damageSource);
    }

    private Path getPlayerDataPath(String data) {
        return TTS_PATH.resolve("playerData").resolve(data + ".json");
    }

    private Path getRoundDataPath(Date date, String data) {
        return TTS_PATH.resolve("roundData").resolve(date.toString()).resolve( data + ".json");
    }

    public @NotNull PlayerDataInstance getData(ServerPlayerEntity player) {
        return getData(player.getUuid());
    }

    public @Nullable PlayerDataInstance getData(UUID uuid) {
        // this can not be null. all online player must have their data instance
        return (DATA_MAP.get(uuid));
    }
}
