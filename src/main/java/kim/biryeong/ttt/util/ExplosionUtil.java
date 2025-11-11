package kim.biryeong.ttt.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExplosionUtil {

    /**
     * 폭발 데이터를 생성함.
     * @param attacker 공격자. 이거 없으면 tnt 같은 인위적이지 않은 폭발임
     * @param pos 중앙 포지션. 중심임
     * @param world 폭발 일으킬 월드
     * @param power x*2 y*2 z*2 의 범위로 터짐
     * @return 폭발 데이터. 이거 리턴된걸로 {@link ExplosionImpl#explode()} 하면 터짐. 이 코드로 만든 폭발은 반경 내 대상을 거리 상관없이 1557 뎀 박음. 사실상 죽으라는거
     */
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
