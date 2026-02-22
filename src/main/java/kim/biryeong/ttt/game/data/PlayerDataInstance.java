package kim.biryeong.ttt.game.data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kim.biryeong.ttt.player.role.Role;
import net.minecraft.util.StringIdentifiable;

import java.util.*;

public class PlayerDataInstance {
    public static final Codec<PlayerDataInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("uuid").forGetter(PlayerDataInstance::getUuid),
            PlayerGameResult.CODEC.listOf().fieldOf("results").forGetter(PlayerDataInstance::results),
            AccuseStats.CODEC.optionalFieldOf("accuseStats", AccuseStats.empty()).forGetter(PlayerDataInstance::accuseStats)
    ).apply(instance, PlayerDataInstance::new));

    private final UUID uuid;
    private final List<PlayerGameResult> results;
    private AccuseStats accuseStats;

    private PlayerDataInstance(UUID uuid, List<PlayerGameResult> results, AccuseStats accuseStats) {
        this.results = Lists.newArrayList(results);
        this.uuid = uuid;
        this.accuseStats = accuseStats == null ? AccuseStats.empty() : accuseStats.normalized();
    }

    public UUID getUuid() {
        return uuid;
    }
    private List<PlayerGameResult> results() {
        return results;
    }

    private AccuseStats accuseStats() {
        return accuseStats;
    }

    public List<PlayerGameResult> getResults() {
        return ImmutableList.copyOf(results);
    }

    public AccuseStats getAccuseStats() {
        return accuseStats;
    }

    public void addResult(PlayerGameResult result) {
        this.results.add(result);
    }

    public void recordAccuseResult(boolean hit) {
        this.accuseStats = this.accuseStats.record(hit);
    }

    public static PlayerDataInstance createNew(UUID uuid) {
        return new PlayerDataInstance(uuid, new ArrayList<>(), AccuseStats.empty());
    }

    public record AccuseStats(int attempts, int hits) {
        public static final Codec<AccuseStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("attempts").forGetter(AccuseStats::attempts),
                Codec.INT.fieldOf("hits").forGetter(AccuseStats::hits)
        ).apply(instance, AccuseStats::new));

        public static AccuseStats empty() {
            return new AccuseStats(0, 0);
        }

        public AccuseStats normalized() {
            int normalizedAttempts = Math.max(0, this.attempts);
            int normalizedHits = Math.max(0, Math.min(this.hits, normalizedAttempts));
            return new AccuseStats(normalizedAttempts, normalizedHits);
        }

        public AccuseStats record(boolean hit) {
            int nextAttempts = this.attempts + 1;
            int nextHits = this.hits + (hit ? 1 : 0);
            return new AccuseStats(nextAttempts, nextHits);
        }
    }

    public record PlayerGameResult(Date date, Role role, Result win, int gainPoints) {
        public static final Codec<PlayerGameResult> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                kim.biryeong.ttt.game.data.Date.CODEC.fieldOf("date").forGetter(PlayerGameResult::date),
                        Role.CODEC.fieldOf("role").forGetter(PlayerGameResult::role),
                        Result.CODEC.fieldOf("win").forGetter(PlayerGameResult::win),
                        Codec.INT.fieldOf("gainPoints").forGetter(PlayerGameResult::gainPoints)
                ).apply(instance, PlayerGameResult::new)
        );


        public static PlayerGameResult create(Role role, Result win, int gainPoints) {
            Calendar calendar = Calendar.getInstance();
            return new PlayerGameResult(new Date(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)),
                    role, win, gainPoints
            );

        }
    }

    public enum Result implements StringIdentifiable {
        WIN, LOSE, CANCELED;

        public static final Codec<Result> CODEC = StringIdentifiable.createCodec(Result::values);

        @Override
        public String asString() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public Result getByRole(Role role) {
            if (this == CANCELED || role == Role.SPECTATOR) {
                return this;
            }

            if (role == Role.INNOCENT || role == Role.DETECTIVE) {
                return this;
            }

            return this == WIN ? LOSE : WIN;
        }
    }
}
