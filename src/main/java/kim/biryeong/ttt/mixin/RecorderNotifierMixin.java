package kim.biryeong.ttt.mixin;

import me.senseiwells.replay.processor.RecorderNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecorderNotifier.class, remap = false)
@SuppressWarnings("unused")
public class RecorderNotifierMixin {
    @Inject(
            method = "onReplayRecorderStart(Lnet/casual/arcade/replay/events/ReplayRecorderStartEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ttt$suppressReplayStartNotification(CallbackInfo ci) {
        // Disable server-replay start broadcast to operators and console.
        ci.cancel();
    }

    @Inject(
            method = "onReplayRecorderSaved(Lnet/casual/arcade/replay/events/ReplayRecorderSaveEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ttt$suppressReplaySaveNotification(CallbackInfo ci) {
        // Disable server-replay save broadcast to operators and console.
        ci.cancel();
    }
}
