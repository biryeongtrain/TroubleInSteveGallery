package kim.biryeong.ttt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.server.network.ServerConfigurationNetworkHandler.class)
public class ServerConfigurationNetworkHandlerMixin {
    private static final String REJOIN_CONFIGURATION_LISTENER_CLASS =
            "net.casual.arcade.replay.recorder.rejoin.RejoinConfigurationPacketListener";

    @Inject(method = "queueSendResourcePackTask", at = @At("HEAD"), cancellable = true)
    private void ttt$skipResourcePackTaskForReplayRejoin(CallbackInfo ci) {
        // arcade replay rejoin listener has no PacketTweaker static context, so Polymer AutoHost task creation NPEs.
        if (REJOIN_CONFIGURATION_LISTENER_CLASS.equals(this.getClass().getName())) {
            ci.cancel();
        }
    }
}
