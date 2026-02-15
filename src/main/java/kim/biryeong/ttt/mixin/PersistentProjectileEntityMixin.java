package kim.biryeong.ttt.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import kim.biryeong.ttt.game.manager.GameManager;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public class PersistentProjectileEntityMixin {
    @ModifyReturnValue(method = "getGravity", at = @At("RETURN"))
    private double modifyGravity(double original) {
        return 0;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ttt$spawnArrowTrail(CallbackInfo ci) {
        PersistentProjectileEntity projectile = (PersistentProjectileEntity) (Object) this;
        if (!(projectile.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        if (projectile.isRemoved() || projectile.isOnGround()) {
            return;
        }
        if (!(projectile.getOwner() instanceof ServerPlayerEntity owner)) {
            return;
        }

        GameManager manager = GameManager.getInstance();
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
