package kim.biryeong.ttt.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class Config {
    public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("gameStartCountdownSeconds").forGetter(config -> config.gameStartCountdownSeconds),
                    Codec.INT.fieldOf("minPlayersToStartGame").forGetter(config -> config.minPlayersToStartGame),
                    Codec.INT.fieldOf("playTimeSeconds").forGetter(config -> config.playTimeSeconds),
                    Codec.INT.fieldOf("overTimePerKills").forGetter(config -> config.overTimePerKills),
                    Codec.INT.fieldOf("maxOverTimeSeconds").forGetter(config -> config.maxOverTimeSeconds)
            ).apply(instance, Config::new)
    );

    private Config() {}

    private Config(int gameStartCountdownSeconds, int minPlayersToStartGame, int playTimeSeconds, int overTimePerKills, int maxOverTimeSeconds) {
        this.gameStartCountdownSeconds = gameStartCountdownSeconds;
        this.minPlayersToStartGame = minPlayersToStartGame;
        this.playTimeSeconds = playTimeSeconds;
        this.overTimePerKills = overTimePerKills;
        this.maxOverTimeSeconds = maxOverTimeSeconds;
    }

    public static Config createNew() {
        return new Config();
    }

    public int gameStartCountdownSeconds = 30;
    public int minPlayersToStartGame = 4;
    public int playTimeSeconds = 240;
    public int overTimePerKills = 5;
    public int maxOverTimeSeconds = 60;
}
