package kim.biryeong.ttt.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExplosionUtil {
    public static ExplosionImpl createExplosion(@Nullable Entity attacker, Vec3d pos, @NotNull ServerWorld world, float power) {
        return new ExplosionImpl(
                world,
                attacker,
                world.getDamageSources().explosion(attacker, attacker),
                new NonBlockDamageExplosionBehavior(),
                pos,
                power,
                false,
                Explosion.DestructionType.TRIGGER_BLOCK
        );
    }
}
