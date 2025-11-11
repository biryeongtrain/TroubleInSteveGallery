package kim.biryeong.ttt.game.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kim.biryeong.ttt.game.manager.GameManager;
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
            var random = GameManager.getInstance().getRandom();
            var spawnPos = spawnPoints.get(random.nextInt(numOfSpawnPoints));
            var randomPos = spawnPos.add(random.nextBetween(-3, 3), 0, random.nextBetween(-3, 3));
            player.teleport(randomPos.getX() + 0.5, randomPos.getY(), randomPos.getZ() + 0.5, false);
        });
    }
}
