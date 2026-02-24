package kim.biryeong.ttt.mixin;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import kim.biryeong.ttt.game.manager.GameManager;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public abstract void send(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener);

    @Shadow
    @Final
    protected ClientConnection connection;

    @Shadow
    @Final
    protected MinecraftServer server;

    @Shadow
    protected abstract GameProfile getProfile();

    @Inject(method = "send", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"), cancellable = true)
    private void ttt$sendIfCanViewTratior(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, CallbackInfo ci) {
        ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(this.getProfile().getId());
        if (player == null) {
            return;
        }

        GameManager manager = GameManager.getInstance();
        if (!manager.getCurrentPhase().canShowRole()) {
            return;
        }
        // Glow override scope must stay in sync with GameInstanceManager traitor reveal rules.
        if (!manager.canReceiveTraitorRevealPackets(player)) {
            return;
        }

        Set<Integer> forcedGlowEntityIds = getForcedGlowEntityIds(manager, player);
        if (packet instanceof EntityTrackerUpdateS2CPacket trackerPacket) {
            if (!forcedGlowEntityIds.contains(trackerPacket.id())) {
                return;
            }

            packet = overrideGlowPacket(trackerPacket);

        } else if (packet instanceof BundleS2CPacket bundle) {
            var packets = bundle.getPackets();
            List<Packet<? super ClientPlayPacketListener>> newPackets = new ArrayList<>();

            for (var oldPacket : packets) {
                if (oldPacket instanceof EntityTrackerUpdateS2CPacket trackerPacket) {
                    if (!forcedGlowEntityIds.contains(trackerPacket.id())) {
                        newPackets.add(oldPacket);
                        continue;
                    }

                    newPackets.add(overrideGlowPacket(trackerPacket));
                    continue;
                }

                newPackets.add(oldPacket);
            }
            packet = new BundleS2CPacket(newPackets);
        }

        connection.send(packet, channelFutureListener);
        ci.cancel();
    }

    private Set<Integer> getAliveTraitorEntityIds() {
        GameManager manager = GameManager.getInstance();
    private Set<Integer> getForcedGlowEntityIds(GameManager manager, ServerPlayerEntity recipient) {
        return this.server.getPlayerManager().getPlayerList().stream()
                .filter(candidate -> manager.shouldForceTraitorRevealGlow(recipient, candidate))
                .map(ServerPlayerEntity::getId)
                .collect(Collectors.toSet());
    }

    private EntityTrackerUpdateS2CPacket overrideGlowPacket(EntityTrackerUpdateS2CPacket packet) {
        int targetId = packet.id();
        List<DataTracker.SerializedEntry<?>> trackedValues = new ArrayList<>();
        boolean hasFlagsEntry = false;

        for (var tracker : packet.trackedValues()) {
            if (tracker.id() == 0) {
                hasFlagsEntry = true;
                byte bitmask = (byte) tracker.value();
                bitmask |= 0x40;
                var entry = new DataTracker.SerializedEntry<>(0, (TrackedDataHandler<Byte>) tracker.handler(), bitmask);
                trackedValues.add(entry);
                continue;
            }

            trackedValues.add(tracker);
        }

        if (!hasFlagsEntry) {
            return packet;
        }

        return new EntityTrackerUpdateS2CPacket(targetId, trackedValues);
    }

}
