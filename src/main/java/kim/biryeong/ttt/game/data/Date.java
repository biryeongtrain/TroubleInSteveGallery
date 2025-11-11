package kim.biryeong.ttt.game.data;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.NotNull;

import java.util.Calendar;

public record Date(int year, int month, int day, int hour, int minute) {
    public static final Codec<Date> CODEC = Codec.STRING.xmap(Date::fromString, Date::toString);

    public static Date fromNow() {
        Calendar calendar = Calendar.getInstance();
        return new Date(
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)
        );
    }

    public static Date fromString(String str) {
        String[] parts = Splitter.on("-").splitToStream(str).toArray(String[]::new);
        return new Date(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
    }

    @Override
    public @NotNull String toString() {
        return String.format("%d-%d-%d-%02d-%02d", year, month, day, hour, minute);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Date(int year1, int month1, int day1, int hour1, int minute1))) return false;;
        return day == day1 && year == year1 && hour == hour1 && month == month1 && minute == minute1;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(year, month, day, hour, minute);
    }
}