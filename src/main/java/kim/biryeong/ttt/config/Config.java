package kim.biryeong.ttt.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Config {
    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(TroubleInTerroristTownMod.MOD_ID);
    private static final Gson GSON = new GsonBuilder().create();
    public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("gameStartCountdownSeconds").forGetter(config -> config.gameStartCountdownSeconds),
                    Codec.INT.fieldOf("minPlayersToStartGame").forGetter(config -> config.minPlayersToStartGame),
                    Codec.INT.fieldOf("playTimeSeconds").forGetter(config -> config.playTimeSeconds),
                    Codec.INT.fieldOf("overTimePerKills").forGetter(config -> config.overTimePerKills),
                    Codec.INT.fieldOf("maxOverTimeSeconds").forGetter(config -> config.maxOverTimeSeconds),
                    Identifier.CODEC.listOf().fieldOf("additionalMaps").forGetter(config -> config.additionalMaps)
            ).apply(instance, Config::new)
    );

    public static final AtomicReference<Config> config = new AtomicReference<>(tryLoadConfig());

    private Config() {}

    private Config(int gameStartCountdownSeconds, int minPlayersToStartGame, int playTimeSeconds, int overTimePerKills, int maxOverTimeSeconds, List<Identifier> additionalMaps) {
        this.gameStartCountdownSeconds = gameStartCountdownSeconds;
        this.minPlayersToStartGame = minPlayersToStartGame;
        this.playTimeSeconds = playTimeSeconds;
        this.overTimePerKills = overTimePerKills;
        this.maxOverTimeSeconds = maxOverTimeSeconds;
        this.additionalMaps = additionalMaps;
    }

    public static Config createNew() {
        return new Config();
    }

    public int gameStartCountdownSeconds = 30;
    public int minPlayersToStartGame = 4;
    public int playTimeSeconds = 240;
    public int overTimePerKills = 5;
    public int maxOverTimeSeconds = 60;
    public List<Identifier> additionalMaps = new ArrayList<>();

    private static Config tryLoadConfig() {
        Path configPath = CONFIG_DIR.resolve("config.json");
        if (Files.exists(configPath)) {
            try {
                String file = Files.readString(configPath);
                return CODEC.decode(JsonOps.INSTANCE, GSON.fromJson(file, JsonElement.class)).getOrThrow().getFirst();
            } catch (Exception e) {
                TroubleInTerroristTownMod.LOGGER.error("Failed to read config file, using default config.", e);
                return createNew();
            }
        } else {
            Config config = new Config();
            try {
                Files.createDirectories(CONFIG_DIR);
                Files.writeString(configPath, CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow().toString());
            } catch (Exception e) {
                TroubleInTerroristTownMod.LOGGER.error("Failed to create config file.", e);
            }

            return config;
        }
    }

    public static void reload() {
        config.set(tryLoadConfig());
    }

    public static void initialize() {

    }

    public static Config getInstance() {
        return config.get();
    }
}
