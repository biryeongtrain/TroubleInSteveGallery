package kim.biryeong.ttt.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import kim.biryeong.ttt.game.manager.GameManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin extends Entity {
    @Unique
    private static final int TTT$ARROW_DESPAWN_TICKS = 5 * 20;

    @Unique
    private boolean ttt$gravityFlagResent;

    public PersistentProjectileEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @ModifyReturnValue(method = "getGravity", at = @At("RETURN"))
    private double modifyGravity(double original) {
        return 0;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ttt$resendNoGravityFlag(CallbackInfo ci) {
        PersistentProjectileEntity projectile = (PersistentProjectileEntity) (Object) this;
        if (!(projectile.getWorld() instanceof ServerWorld) || this.ttt$gravityFlagResent) {
            return;
        }
        if (projectile.age > 1) {
            this.ttt$gravityFlagResent = true;
            return;
        }
        if (!projectile.hasNoGravity()) {
            this.ttt$gravityFlagResent = true;
            return;
        }

        // Mark no-gravity as dirty after spawn so tracking clients receive the flag update.
        projectile.setNoGravity(false);
        projectile.setNoGravity(true);
        this.ttt$gravityFlagResent = true;
        this.velocityModified = true;
        this.velocityDirty=true;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ttt$spawnArrowTrail(CallbackInfo ci) {
        PersistentProjectileEntity projectile = (PersistentProjectileEntity) (Object) this;
        if (!(projectile.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        if (projectile.isRemoved()) {
            return;
        }

        GameManager manager = GameManager.getInstance();
        if (projectile instanceof ArrowEntity
                && projectile.age >= TTT$ARROW_DESPAWN_TICKS) {
            projectile.remove(RemovalReason.DISCARDED);
            return;
        }

        if (projectile.isOnGround()) {
            return;
        }
        if (!(projectile.getOwner() instanceof ServerPlayerEntity owner)) {
            return;
        }

        // Keep projectile trail limited to active TTT combat rounds.
        if (!manager.getCurrentPhase().canShowRole() || !manager.isAlive(owner)) {
            return;
        }

        serverWorld.spawnParticles(
                ParticleTypes.END_ROD,
                projectile.getX(),
                projectile.getY(),
                projectile.getZ(),
                2,
                0.02,
                0.02,
                0.02,
                0.0
        );
    }
}
