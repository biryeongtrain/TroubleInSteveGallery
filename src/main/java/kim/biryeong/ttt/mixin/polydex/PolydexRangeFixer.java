package kim.biryeong.ttt.mixin.polydex;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import eu.pb4.polydex.api.v1.hover.PolydexTarget;
import eu.pb4.polydex.impl.display.PolydexTargetImpl;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;


@Mixin(PolydexTargetImpl.class)
public abstract class PolydexRangeFixer implements PolydexTarget {
    @ModifyConstant(method = "updateRaycast", constant = @Constant(doubleValue = 8.02))
    private double ttt$fixRange(double original) {
        return 32;
    }

    @WrapOperation(method = "updateRaycast", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/hit/HitResult;getType()Lnet/minecraft/util/hit/HitResult$Type;"))
    private HitResult.Type ttt$fixRaycastResult(HitResult instance, Operation<HitResult.Type> original) {
        return instance.getType() == HitResult.Type.BLOCK ? HitResult.Type.MISS : original.call(instance);
    }
}
