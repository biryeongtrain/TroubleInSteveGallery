package kim.biryeong.ttt.player.role;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

public enum Role implements StringIdentifiable {
    INNOCENT(0x00A36C),
    TRAITOR(0xD22B2B),
    DETECTIVE(0x0047AB),
    SPECTATOR(0x818589);

    public static final Codec<Role> CODEC = StringIdentifiable.createCodec(Role::values);

    public final int hexColor;

    Role(int hexColor) {
        this.hexColor = hexColor;
    }

    @Override
    public String asString() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
