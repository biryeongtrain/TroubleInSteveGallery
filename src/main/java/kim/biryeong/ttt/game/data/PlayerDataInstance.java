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
            PlayerGameResult.CODEC.listOf().fieldOf("results").forGetter(PlayerDataInstance::results)
    ).apply(instance, PlayerDataInstance::new));

    private final UUID uuid;
    private final List<PlayerGameResult> results;

    private PlayerDataInstance(UUID uuid, List<PlayerGameResult> results) {
        this.results = Lists.newArrayList(results);
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }
    private List<PlayerGameResult> results() {
        return results;
    }

    public List<PlayerGameResult> getResults() {
        return ImmutableList.copyOf(results);
    }

    public void addResult(PlayerGameResult result) {
        this.results.add(result);
    }

    public static PlayerDataInstance createNew(UUID uuid) {

        return new PlayerDataInstance(uuid, new ArrayList<>());
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
    }
}
