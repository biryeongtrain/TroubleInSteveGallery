package kim.biryeong.ttt.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                    Identifier.CODEC.listOf().fieldOf("additionalMaps").forGetter(config -> config.additionalMaps),
                    RotatingGuideTip.CODEC.listOf()
                            .optionalFieldOf("rotatingGuideTips", defaultRotatingGuideTips())
                            .forGetter(config -> config.rotatingGuideTips)
            ).apply(instance, Config::new)
    );

    public static final AtomicReference<Config> config = new AtomicReference<>(tryLoadConfig());

    private Config() {}

    private Config(
            int gameStartCountdownSeconds,
            int minPlayersToStartGame,
            int playTimeSeconds,
            int overTimePerKills,
            int maxOverTimeSeconds,
            List<Identifier> additionalMaps,
            List<RotatingGuideTip> rotatingGuideTips
    ) {
        this.gameStartCountdownSeconds = gameStartCountdownSeconds;
        this.minPlayersToStartGame = minPlayersToStartGame;
        this.playTimeSeconds = playTimeSeconds;
        this.overTimePerKills = overTimePerKills;
        this.maxOverTimeSeconds = maxOverTimeSeconds;
        this.additionalMaps = additionalMaps;
        this.rotatingGuideTips = rotatingGuideTips;
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
    public List<RotatingGuideTip> rotatingGuideTips = defaultRotatingGuideTips();

    private static List<RotatingGuideTip> defaultRotatingGuideTips() {
        return List.of(
                new RotatingGuideTip("일반 팁", "식별되지 않은 시체를 찾았다면, 최대한 빨리 식별하세요."),
                new RotatingGuideTip("일반 팁", "F키를 눌러 상점을 열 수 있습니다."),
                new RotatingGuideTip("일반 팁", "오래 살아남거나 시체를 처음 식별하면 포인트를 획득합니다."),
                new RotatingGuideTip("일반 팁", "추가 시간에는 트레이터가 사살한 시민 수 만큼 추가 시간이 생깁니다."),
                new RotatingGuideTip("일반 팁", "트레이터는 모든 룰을 어길 수 있습니다. 유의하세요.")
        );
    }

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
                writeConfig(configPath, config);
            } catch (Exception e) {
                TroubleInTerroristTownMod.LOGGER.error("Failed to create config file.", e);
            }

            return config;
        }
    }

    private static void writeConfig(Path configPath, Config config) throws IOException {
        JsonElement encodedConfig = CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow();
        if (!(encodedConfig instanceof JsonObject configObject)) {
            throw new IllegalStateException("Config codec did not encode to a JsonObject.");
        }

        // Keep rotatingGuideTips materialized in newly created config files.
        JsonElement encodedTips = RotatingGuideTip.CODEC.listOf()
                .encodeStart(JsonOps.INSTANCE, config.rotatingGuideTips)
                .getOrThrow();
        configObject.add("rotatingGuideTips", encodedTips);

        Files.writeString(configPath, GSON.toJson(configObject));
    }

    public static void reload() {
        config.set(tryLoadConfig());
    }

    public static void initialize() {

    }

    public static Config getInstance() {
        return config.get();
    }

    public record RotatingGuideTip(String title, String message) {
        public static final Codec<RotatingGuideTip> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("title").forGetter(RotatingGuideTip::title),
                Codec.STRING.optionalFieldOf("message").forGetter(tip -> Optional.of(tip.message)),
                Codec.STRING.optionalFieldOf("actionBar").forGetter(tip -> Optional.empty())
        ).apply(instance, RotatingGuideTip::fromCodecFields));

        private static RotatingGuideTip fromCodecFields(
                String title,
                Optional<String> message,
                Optional<String> actionBar
        ) {
            return new RotatingGuideTip(title, message.orElseGet(() -> actionBar.orElse("")));
        }
    }
}
