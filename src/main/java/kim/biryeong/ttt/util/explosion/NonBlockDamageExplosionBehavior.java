package kim.biryeong.ttt.util.explosion;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;


/**
 * 자폭 폭탄 사용 시 이용되는 DamageExplosionBehavior. 범위 내 모든 대상에게 같은 피해를 줌. 블록 피해는 X
 */
public class NonBlockDamageExplosionBehavior extends ExplosionBehavior {
    @Override
    public boolean canDestroyBlock(Explosion explosion, BlockView world, BlockPos pos, BlockState state, float power) {
        return false;
    }

    @Override
    public float calculateDamage(Explosion explosion, Entity entity, float amount) {
        return 1557;
    }

    @Override
    public float getKnockbackModifier(Entity entity) {
        return 0;
    }
}
