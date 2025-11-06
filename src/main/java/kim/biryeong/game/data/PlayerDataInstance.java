package kim.biryeong.game.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kim.biryeong.player.role.Role;
import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerDataInstance {
    public static final Codec<PlayerDataInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("uuid").forGetter(PlayerDataInstance::getUuid),
            PlayerGameResult.CODEC.listOf().fieldOf("results").forGetter(PlayerDataInstance::getResults)
    ).apply(instance, PlayerDataInstance::new));

    private final UUID uuid;
    private final List<PlayerGameResult> results;

    private PlayerDataInstance(UUID uuid, List<PlayerGameResult> results) {
        this.results = results;
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
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
                        Date.CODEC.fieldOf("date").forGetter(PlayerGameResult::date),
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

    public record Date(int year, int month, int day, int hour, int minute) {
        public static final Codec<Date> CODEC = Codec.STRING.xmap(Date::fromString, Date::toString);

        public static Date fromString(String str) {
            String[] parts = str.split("/");
            return new Date(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
        }

        public @NotNull String toString() {
            return String.format("%d/%d/%d/%02d/%02d", year, month, day, hour, minute);
        }
    }

    public enum Result implements StringIdentifiable {
        WIN, LOSE, CANCELED;

        public static final Codec<Result> CODEC = StringIdentifiable.createCodec(Result::values);

        @Override
        public String asString() {
            return this.name().toLowerCase();
        }
    }
}
