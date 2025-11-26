package kim.biryeong.ttt.mixin.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.block.WireOrientation;
import net.minecraft.world.tick.ScheduledTickView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.fantasy.RuntimeWorld;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class AbstractBlockStateMixin {
    @Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"), cancellable = true)
    private void cancelNeighborUpdate(WorldView world, ScheduledTickView tickView, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, Random random, CallbackInfoReturnable<BlockState> cir) {
        if (world instanceof RuntimeWorld) {
            cir.setReturnValue((BlockState) ((Object) this));
        }
    }

    @Inject(method = "neighborUpdate", at = @At("HEAD"), cancellable = true)
    private void cancelNeighborUpdate(World world, BlockPos pos, Block sourceBlock, WireOrientation wireOrientation, boolean notify, CallbackInfo ci) {
        if (world instanceof RuntimeWorld) {
            ci.cancel();
        }
    }

    @Inject(method = "updateNeighbors*", at = @At("HEAD"), cancellable = true)
    private void cancelNeighbors(WorldAccess world, BlockPos pos, int flags, CallbackInfo ci) {
        if (world instanceof RuntimeWorld) {
            ci.cancel();
        }
    }

    @Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
    private void cancelScheduledTick(ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (world instanceof RuntimeWorld) {
            ci.cancel();
        }
    }

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void passCanPlaceAt(WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (world instanceof RuntimeWorld) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void cancelRandomTick(ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (world instanceof RuntimeWorld) {
            ci.cancel();
        }
    }

    @Inject(method = "canReplace", at = @At("HEAD"), cancellable = true)
    private void setCanReplace(ItemPlacementContext context, CallbackInfoReturnable<Boolean> cir) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        VoxelShape shape = world.getBlockState(pos).getOutlineShape(world, pos);
        if (world instanceof RuntimeWorld && shape.isEmpty()) {
            cir.setReturnValue(false);
        }
    }
}
