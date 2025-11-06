package kim.biryeong.player.role;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

public enum Role implements StringIdentifiable {
    INNOCENT,
    TRAITOR,
    DETECTIVE,
    SPECTATOR;

    public static final Codec<Role> CODEC = StringIdentifiable.createCodec(Role::values);

    @Override
    public String asString() {
        return this.name().toLowerCase();
    }
}
