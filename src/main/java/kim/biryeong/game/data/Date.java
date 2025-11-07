package kim.biryeong.game.data;

import com.google.common.base.Objects;
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
        String[] parts = str.split("-");
        return new Date(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
    }

    public @NotNull String toString() {
        return String.format("%d-%d-%d-%02d-%02d", year, month, day, hour, minute);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Date date = (Date) o;
        return day == date.day && year == date.year && hour == date.hour && month == date.month && minute == date.minute;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(year, month, day, hour, minute);
    }
}