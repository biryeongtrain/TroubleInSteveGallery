package kim.biryeong.ttt;

import kim.biryeong.gcbserver.events.PlayerKeyStateChangedEvent;
import kim.biryeong.gcbserver.player.Key;
import kim.biryeong.ttt.entity.CorpseEntity;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGameEventProvider;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.gui.ShopGUI;
import kim.biryeong.ttt.util.AvatarTextRenderer;
import kim.biryeong.ttt.util.KoreanKeyboardConverter;
import kim.biryeong.ttt.util.MinimapClientModPacketDetector;
import kim.biryeong.ttt.util.NonThrowable;
import kim.biryeong.ttt.util.Scheduler;
import kim.biryeong.ttt.world.TTTMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.stimuli.Stimuli;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.entity.EntitySpawnEvent;
import xyz.nucleoid.stimuli.event.item.ItemThrowEvent;
import xyz.nucleoid.stimuli.event.player.PlayerChatEvent;
import xyz.nucleoid.stimuli.event.player.PlayerConsumeHungerEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;
import xyz.nucleoid.stimuli.event.player.PlayerRegenerateEvent;
import xyz.nucleoid.stimuli.event.player.PlayerC2SPacketEvent;
import xyz.nucleoid.stimuli.event.player.PlayerSwapWithOffhandEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Events {
    private static final int QUICK_CHAT_COOLDOWN_TICKS = 100;
    private static final double QUICK_TARGET_RANGE = 10.0;
    private static final Map<UUID, Long> QUICK_CHAT_LAST_USED_TICKS = new HashMap<>();

    private Events() {
        throw new IllegalStateException("Utility class");
    }

    public static void registerEvents() {
        registerItemEvents();
        registerConnectionEvents();
        registerPacketEvents();
        registerCombatEvents();
        registerEntityRules();
        registerPlayerStateEvents();
        registerServerLifecycleEvents();
        registerUiEvents();
    }

    private static void registerItemEvents() {
        Stimuli.global().listen(ItemThrowEvent.EVENT, (player, slot, stack) ->
                stack.getItem() instanceof NonThrowable ? EventResult.DENY : EventResult.PASS
        );
    }

    private static void registerConnectionEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            GameManager manager = GameManager.getInstance();
            AvatarTextRenderer.clearSmallAvatarCache(
                    handler.player.getUuid(),
                    handler.player.getGameProfile().getName()
            );
            manager.onPlayerJoined(handler.player);

            GameMode mode = manager.resolveJoinGameMode();
            handler.player.changeGameMode(mode);

            InGamePlayerInfoProvider playerInfo = (InGamePlayerInfoProvider) handler.getPlayer();
            playerInfo.tts$setRole(Role.SPECTATOR);
            if (playerInfo.tts$displayHudEnabled()) {
                manager.addRoundHudPlayer(handler.player);
            }
            if (playerInfo.tts$sidebarEnabled()) {
                manager.addDefaultSidebarPlayer(handler.player);
            }
            MinimapClientModPacketDetector.onPlayerJoined(handler.player);
            AvatarTextRenderer.prefetchSmallAvatarAsync(
                    handler.player.getUuid(),
                    handler.player.getGameProfile().getName()
            );

            Scheduler.INSTANCE.submit((s) -> {
                manager.resolveJoinMap().spawnPlayer(handler.player);
            }, 1);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            MinimapClientModPacketDetector.onPlayerDisconnected(handler.player);
            AvatarTextRenderer.clearSmallAvatarCache(
                    handler.player.getUuid(),
                    handler.player.getGameProfile().getName()
            );
            GameManager.getInstance().removeRoundHudPlayer(handler.player);
            GameManager.getInstance().removeDefaultSidebarPlayer(handler.player);
            QUICK_CHAT_LAST_USED_TICKS.remove(handler.player.getUuid());
            GameManager.getInstance().onPlayerLeft(handler.player);
        });
    }

    private static void registerPacketEvents() {
        Stimuli.global().listen(PlayerC2SPacketEvent.EVENT, (player, packet) -> {
            MinimapClientModPacketDetector.onPlayerPacket(player, packet);
            return EventResult.PASS;
        });
    }

    private static void registerCombatEvents() {
        Stimuli.global().listen(PlayerDamageEvent.EVENT, (player, source, amount) -> {
            GameManager manager = GameManager.getInstance();
            GameManager.Phase phase = manager.getCurrentPhase();
            if (!phase.canShowRole()) {
                return EventResult.DENY;
            }

            if (source.getAttacker() instanceof ServerPlayerEntity attacker
                    && !attacker.getUuid().equals(player.getUuid())) {
                manager.onPlayerDamaged(attacker, player, amount);
            }
            return EventResult.PASS;
        });

        Stimuli.global().listen(PlayerDeathEvent.EVENT, (victim, damageSource) -> {
            if (GameManager.getInstance().isGameStarted()) {
                ServerPlayerEntity attacker = resolveAttacker(victim, damageSource);
                GameManager.getInstance().onKilled(attacker, victim, damageSource);

                var corpse = CorpseEntity.createCorpse(victim.getWorld(), victim, damageSource);
                victim.getWorld().spawnEntity(corpse);
            }
            return EventResult.DENY;
        });
    }

    private static ServerPlayerEntity resolveAttacker(ServerPlayerEntity victim, net.minecraft.entity.damage.DamageSource damageSource) {
        if (damageSource.getAttacker() instanceof ServerPlayerEntity player) {
            return player;
        }
        if (damageSource.isOf(DamageTypes.EXPLOSION)) {
            return getRecentDamagePlayer(victim.getPrimeAdversary());
        }
        return null;
    }

    private static void registerEntityRules() {
        Stimuli.global().listen(EntitySpawnEvent.EVENT, entity ->
                entity instanceof PassiveEntity ? EventResult.DENY : EventResult.PASS
        );
    }

    private static void registerPlayerStateEvents() {
        Stimuli.global().listen(PlayerConsumeHungerEvent.EVENT, (player, foodLevel, saturation, exhaustion) -> EventResult.DENY);

        Stimuli.global().listen(PlayerRegenerateEvent.EVENT, (player, amount) -> {
            InGameEventProvider info = (InGameEventProvider) player;
            if (info.ttt$isInCombat()) {
                return EventResult.DENY;
            }
            if (player.age % 10 == 0 || player.getHealth() >= 14) {
                return EventResult.PASS;
            }
            return EventResult.DENY;
        });

        Stimuli.global().listen(PlayerChatEvent.EVENT, (player, message, messageType) -> {
            GameManager manager = GameManager.getInstance();
            GameManager.Phase phase = manager.getCurrentPhase();
            InGamePlayerInfoProvider senderInfo = (InGamePlayerInfoProvider) player;
            boolean senderAlive = manager.isAlive(player);
            Role senderRole = senderInfo.tts$getRole();
            var server = player.getServer();
            if (server == null) {
                return EventResult.DENY;
            }

            if (shouldRouteToSpectatorChat(phase, senderAlive)) {
                Text spectatorChatText = buildSpectatorChatText(
                        player.getDisplayName(),
                        convertInGameChannelChatContent(message.getContent())
                );
                for (ServerPlayerEntity candidate : server.getPlayerManager().getPlayerList()) {
                    InGamePlayerInfoProvider candidateInfo = (InGamePlayerInfoProvider) candidate;
                    if (canReceiveSpectatorChat(manager.isAlive(candidate), candidateInfo.tts$getRole())) {
                        candidate.sendMessage(spectatorChatText.copy(), false);
                    }
                }
                return EventResult.DENY;
            }

            if (shouldRouteToDetectiveBlueNameChat(phase, senderAlive, senderRole)) {
                Text detectiveChatText = buildDetectiveBlueNameChatText(
                        player.getDisplayName(),
                        convertInGameChannelChatContent(message.getContent())
                );
                server.getPlayerManager().broadcast(detectiveChatText, false);
                return EventResult.DENY;
            }

            return EventResult.PASS;
        });
    }

    private static void registerServerLifecycleEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            GameManager manager = GameManager.getInstance();
            manager.tickLobbyBgm();
            manager.tickGuideHints();
            TTTMap activeMap = manager.resolveJoinMap();
            if (activeMap != null) {
                activeMap.tickPortals();
            }
            if (manager.getCurrentPhase().isInProgress()) {
                manager.tick();
            }
            manager.tickRoundHud();
            manager.tickRoundTimerBossBar();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                GameManager.getInstance().getSpawnWorld()
        );

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            GameManager manager = GameManager.getInstance();
            manager.reloadMapData(manager.getAllMapIds());
        });

        PlayerKeyStateChangedEvent.EVENT.register((player, pushed, key) -> {
            if (!pushed || (key != Key.KEY_Z && key != Key.KEY_X && key != Key.KEY_C)) {
                return;
            }

            ServerPlayerEntity target = findLookedAtPlayer(player);
            if (target == null) {
                return;
            }
            long tick = player.getServer().getTicks();

            if (key == Key.KEY_Z) {
                player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(), "/tts accuse %s".formatted(target.getNameForScoreboard()));
            } else if (key == Key.KEY_X) {
                sendQuickChat(player, target, tick, "저는 %s님과 같이 있습니다.");
            } else if (key == Key.KEY_C) {
                sendQuickChat(player, target, tick, "%s 님은 시민입니다.");
            }
        });

    }

    private static void registerUiEvents() {
        Stimuli.global().listen(PlayerSwapWithOffhandEvent.EVENT, player -> {
            GameManager manager = GameManager.getInstance();
            if (!manager.getCurrentPhase().canShowRole()) {
                return EventResult.PASS;
            }

            manager.onShopOpened(player);
            ShopGUI gui = new ShopGUI(player);
            gui.open();
            return EventResult.DENY;
        });
    }

    private static ServerPlayerEntity getRecentDamagePlayer(Entity attacker) {
        return attacker instanceof ServerPlayerEntity player ? player : null;
    }

    private static ServerPlayerEntity findLookedAtPlayer(ServerPlayerEntity player) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d rotation = player.getRotationVec(1.0F);
        Vec3d end = start.add(rotation.multiply(QUICK_TARGET_RANGE));
        Box searchBox = player.getBoundingBox()
                .stretch(rotation.multiply(QUICK_TARGET_RANGE))
                .expand(1.0D);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                player,
                start,
                end,
                searchBox,
                entity -> entity instanceof ServerPlayerEntity target
                        && !target.getUuid().equals(player.getUuid())
                        && !target.isSpectator(),
                QUICK_TARGET_RANGE * QUICK_TARGET_RANGE
        );
        if (entityHit == null || !(entityHit.getEntity() instanceof ServerPlayerEntity target)) {
            return null;
        }

        HitResult blockHit = player.raycast(QUICK_TARGET_RANGE, 1.0F, false);
        if (blockHit.getType() != HitResult.Type.MISS
                && start.squaredDistanceTo(blockHit.getPos()) <= start.squaredDistanceTo(entityHit.getPos())) {
            return null;
        }

        return target;
    }

    static boolean shouldRouteToSpectatorChat(GameManager.Phase phase, boolean senderAlive) {
        return phase.isInProgress() && !senderAlive;
    }

    static boolean canReceiveSpectatorChat(boolean candidateAlive, Role candidateRole) {
        return !candidateAlive || candidateRole == Role.SPECTATOR;
    }

    static Text buildSpectatorChatText(Text senderName, Text messageContent) {
        return Text.empty()
                .append(Text.literal("[관전자] ").formatted(Formatting.GRAY))
                .append(senderName.copy().formatted(Formatting.GRAY))
                .append(Text.literal(": ").formatted(Formatting.GRAY))
                .append(messageContent.copy().formatted(Formatting.GRAY));
    }

    static boolean shouldRouteToDetectiveBlueNameChat(GameManager.Phase phase, boolean senderAlive, Role senderRole) {
        return phase.canShowRole() && senderAlive && senderRole == Role.DETECTIVE;
    }

    static Text buildDetectiveBlueNameChatText(Text senderName, Text messageContent) {
        return Text.empty()
                .append(senderName.copy().formatted(Formatting.BLUE))
                .append(Text.literal(": "))
                .append(messageContent.copy());
    }

    private static void sendQuickChat(ServerPlayerEntity sender, ServerPlayerEntity target, long tick, String messageFormat) {
        long lastUsedTick = QUICK_CHAT_LAST_USED_TICKS.getOrDefault(sender.getUuid(), (long) -QUICK_CHAT_COOLDOWN_TICKS);
        if (tick - lastUsedTick < QUICK_CHAT_COOLDOWN_TICKS) {
            sender.sendMessage(Text.literal("빠른 채팅은 5초마다 사용할 수 있습니다."), true);
            return;
        }

        QUICK_CHAT_LAST_USED_TICKS.put(sender.getUuid(), tick);
        sender.getServer().getPlayerManager().broadcast(
                buildQuickChatText(sender, target, messageFormat),
                false
        );
    }

    private static Text buildQuickChatText(ServerPlayerEntity sender, ServerPlayerEntity target, String messageFormat) {
        InGamePlayerInfoProvider senderInfo = (InGamePlayerInfoProvider) sender;
        String senderName = sender.getGameProfile().getName();
        String targetName = target.getGameProfile().getName();
        String senderLabel = "[" + senderName + "]";
        String senderPrefix = senderInfo.tts$getRole() == Role.DETECTIVE
                ? "<blue>[탐정] " + senderLabel + "</blue>"
                : senderLabel;

        return GameManager.byMiniMessage(senderPrefix + " : " + messageFormat.formatted(targetName));
    }

    static Text convertInGameChannelChatContent(Text messageContent) {
        return Text.literal(KoreanKeyboardConverter.convertChat(messageContent.getString()));
    }
}
