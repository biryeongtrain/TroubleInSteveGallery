package kim.biryeong.ttt.util.explosion;

import kim.biryeong.ttt.entity.CorpseEntity;
import kim.biryeong.ttt.game.manager.GameManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class ExplosionUtil {
    private static final float LETHAL_DAMAGE = 1557.0f;
    private static final double DAMAGE_RANGE_MULTIPLIER = 2.0d;
    private static final double PACKET_DISTANCE_SQUARED = 4096.0d;

    private ExplosionUtil() {
    }

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

    /**
     * TTT 전용 폭발 처리:
     * 서버 폭발 실행 + 환경별 폭발 데미지 누락 보정 + 클라이언트 폭발 패킷 전송.
     */
    public static void explodeWithFallback(
            @Nullable ServerPlayerEntity attacker,
            Vec3d pos,
            @NotNull ServerWorld world,
            float power
    ) {
        DamageSource source = world.getDamageSources().explosion(attacker, attacker);
        ExplosionImpl explosion = createExplosion(attacker, pos, world, power);
        explosion.explode();
        applyDamageFallback(attacker, world, pos, source, explosion.getPower());
        broadcastExplosionPacket(world, pos);
    }

    private static void applyDamageFallback(
            @Nullable ServerPlayerEntity attacker,
            ServerWorld world,
            Vec3d pos,
            DamageSource source,
            float power
    ) {
        GameManager manager = GameManager.getInstance();
        if (!manager.getCurrentPhase().canShowRole()) {
            return;
        }

        double maxDistance = power * DAMAGE_RANGE_MULTIPLIER;
        double maxDistanceSquared = maxDistance * maxDistance;
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (target.squaredDistanceTo(pos) > maxDistanceSquared) {
                continue;
            }
            if (!manager.isAlive(target)) {
                continue;
            }
            if (ExplosionImpl.calculateReceivedDamage(pos, target) <= 0.0f) {
                continue;
            }

            // Some environments acknowledge damage but do not propagate kill callbacks.
            boolean damaged = target.damage(world, source, LETHAL_DAMAGE);
            if (!manager.isAlive(target)) {
                continue;
            }
            if (!target.isAlive()) {
                forceRoundStateKill(attacker, world, manager, target, source);
                continue;
            }

            if (!damaged) {
                forceRoundStateKill(attacker, world, manager, target, source);
                continue;
            }

            // Damage was accepted but round-state kill callback did not run; force consistency.
            forceRoundStateKill(attacker, world, manager, target, source);
        }
    }

    private static void forceRoundStateKill(
            @Nullable ServerPlayerEntity attacker,
            ServerWorld world,
            GameManager manager,
            ServerPlayerEntity target,
            DamageSource source
    ) {
        if (!manager.isAlive(target)) {
            return;
        }

        ServerPlayerEntity resolvedAttacker = null;
        if (attacker != null && !target.getUuid().equals(attacker.getUuid())) {
            resolvedAttacker = attacker;
        }
        manager.onKilled(resolvedAttacker, target, source);
        world.spawnEntity(CorpseEntity.createCorpse(world, target, source));
    }

    private static void broadcastExplosionPacket(ServerWorld world, Vec3d pos) {
        for (ServerPlayerEntity serverPlayerEntity : world.getPlayers()) {
            if (serverPlayerEntity.squaredDistanceTo(pos) >= PACKET_DISTANCE_SQUARED) {
                continue;
            }
            serverPlayerEntity.networkHandler.sendPacket(new ExplosionS2CPacket(
                    pos,
                    Optional.empty(),
                    ParticleTypes.EXPLOSION_EMITTER,
                    SoundEvents.ENTITY_GENERIC_EXPLODE
            ));
        }
    }
}
