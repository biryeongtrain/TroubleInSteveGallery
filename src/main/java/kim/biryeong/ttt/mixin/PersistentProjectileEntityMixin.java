package kim.biryeong.ttt.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import kim.biryeong.ttt.game.manager.GameManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.fantasy.RuntimeWorld;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

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

    @Inject(method = "onBlockHit", at = @At("HEAD"))
    private void ttt$breakGlassOnArrowImpact(BlockHitResult hitResult, CallbackInfo ci) {
        PersistentProjectileEntity projectile = (PersistentProjectileEntity) (Object) this;
        if (!(projectile instanceof ArrowEntity)) {
            return;
        }
        if (!(projectile.getWorld() instanceof ServerWorld serverWorld) || !(serverWorld instanceof RuntimeWorld)) {
            return;
        }

        BlockState hitState = serverWorld.getBlockState(hitResult.getBlockPos());
        if (!ttt$isBreakableGlass(hitState)) {
            return;
        }

        ttt$breakConnectedGlass(serverWorld, hitResult.getBlockPos(), projectile);
    }

    @Unique
    private static boolean ttt$isBreakableGlass(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.GLASS
                || block == Blocks.TINTED_GLASS
                || block == Blocks.GLASS_PANE
                || block instanceof StainedGlassBlock
                || block instanceof StainedGlassPaneBlock;
    }

    @Unique
    private static void ttt$breakConnectedGlass(ServerWorld world, BlockPos origin, PersistentProjectileEntity projectile) {
        if (!ttt$isBreakableGlass(world.getBlockState(origin))) {
            return;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            BlockState currentState = world.getBlockState(current);
            if (!ttt$isBreakableGlass(currentState)) {
                continue;
            }

            world.breakBlock(current, false, projectile);
            for (Direction direction : Direction.values()) {
                BlockPos next = current.offset(direction);
                if (!visited.add(next)) {
                    continue;
                }
                if (!ttt$isBreakableGlass(world.getBlockState(next))) {
                    continue;
                }
                queue.add(next);
            }
        }
    }
}
