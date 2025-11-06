package kim.biryeong.game.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public record PlayerSpawnPoints(Identifier id, List<BlockPos> spawnPoints) {
    public static final Codec<PlayerSpawnPoints> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC.fieldOf("id").forGetter(PlayerSpawnPoints::id),
                    BlockPos.CODEC.listOf().fieldOf("pos").forGetter(PlayerSpawnPoints::spawnPoints)
            ).apply(instance, PlayerSpawnPoints::new)
    );

    public void spreadPlayers(List<ServerPlayerEntity> players, ServerWorld world) {
        int numOfSpawnPoints = spawnPoints.size();
        players.forEach(player -> {

        });
    }
}
