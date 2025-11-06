package kim.biryeong.game.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import kim.biryeong.game.data.PlayerDataInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
final class GameDataManager {
    private final Gson GSON = new Gson();
    private final Map<UUID, PlayerDataInstance> DATA_MAP = new ConcurrentHashMap<>();
    private final Path TTS_PATH;
    private final GameManager gameManager;

    GameDataManager(GameManager gameManager) {
        this.gameManager = gameManager;
        TTS_PATH = GameManager.server.getSavePath(WorldSavePath.ROOT).resolve("tts");
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
            gameManager.LOGGER.error("cannot read player data", e);
            try {
                gameManager.LOGGER.warn("copy backup data to");
                Files.copy(path, getPlayerDataPath(uuid + "-bak.json"));
            } catch (IOException ex) {
                gameManager.LOGGER.error("cannot backup player data", ex);
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
        Path path = getPlayerDataPath(uuid.toString());
        var data = left ? DATA_MAP.remove(uuid) : DATA_MAP.get(uuid);
        var string = PlayerDataInstance.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().toString();
        try {
            Files.writeString(path, string);
        } catch (Exception e) {
            gameManager.LOGGER.error("cannot save player data", e);
            gameManager.LOGGER.info("unsaved data : \n {}", string);
        }
    }

    private Path getPlayerDataPath(String data) {
        return TTS_PATH.resolve(data + ".json");
    }

    public @NotNull PlayerDataInstance getData(ServerPlayerEntity player) {
        return getData(player.getUuid());
    }

    public @NotNull PlayerDataInstance getData(UUID uuid) {
        // this can not be null. all online player must have their data instance
        return Objects.requireNonNull(DATA_MAP.get(uuid));
    }
}
