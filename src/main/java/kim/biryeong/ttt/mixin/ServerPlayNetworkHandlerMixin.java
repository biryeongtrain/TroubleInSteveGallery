package kim.biryeong.ttt.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import io.netty.channel.ChannelFutureListener;
import kim.biryeong.ttt.game.manager.GameManager;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.ServerLinksS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    private static final URI GUIDE_LINK_URI = URI.create(
            "https://github.com/biryeongtrain/TroubleInSteveGallery/blob/main/docs/guide-hints.md#guide-dialog-access"
    );
    private static final String GUIDE_LINK_URI_STRING = GUIDE_LINK_URI.toString();
    private static final Text GUIDE_LINK_TEXT = Text.literal("TTT 가이드 (/tts guide)");

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
        // ESC "Server Links" dialog only uses this packet; append guide entry once without altering existing links.
        if (packet instanceof ServerLinksS2CPacket linksPacket) {
            packet = appendGuideServerLink(linksPacket);
            if (packet != linksPacket) {
                this.connection.send(packet, channelFutureListener);
                ci.cancel();
                return;
            }
        }

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

    private ServerLinksS2CPacket appendGuideServerLink(ServerLinksS2CPacket packet) {
        boolean alreadyHasGuideLink = packet.links().stream()
                .anyMatch(link -> GUIDE_LINK_URI_STRING.equals(link.link()));
        if (alreadyHasGuideLink) {
            return packet;
        }

        List<ServerLinks.StringifiedEntry> links = new ArrayList<>(packet.links());
        links.add(new ServerLinks.StringifiedEntry(Either.right(GUIDE_LINK_TEXT), GUIDE_LINK_URI_STRING));
        return new ServerLinksS2CPacket(List.copyOf(links));
    }

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
