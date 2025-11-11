package kim.biryeong.ttt.game.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record SimpleUserData(UUID uuid, String name) {
    public static final Codec<SimpleUserData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Uuids.CODEC.fieldOf("uuid").forGetter(SimpleUserData::uuid),
                    Codec.STRING.fieldOf("name").forGetter(SimpleUserData::name)
            ).apply(instance, SimpleUserData::new)
    );

    public SimpleUserData(ServerPlayerEntity player) {
        this(player.getUuid(), player.getStringifiedName());
    }
}
