package kim.biryeong.ttt;

import kim.biryeong.ttt.entity.CorpseEntity;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGameEventProvider;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.gui.ShopGUI;
import kim.biryeong.ttt.util.NonThrowable;
import kim.biryeong.ttt.util.Scheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.network.message.SentMessage;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
import xyz.nucleoid.stimuli.event.player.PlayerSwapWithOffhandEvent;

public final class Events {
    private Events() {
        throw new IllegalStateException("Utility class");
    }

    public static void registerEvents() {
        registerItemEvents();
        registerConnectionEvents();
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
            manager.onPlayerJoined(handler.player);
            GameManager.DEFAULT_SIDEBAR.addPlayer(handler);

            GameMode mode = manager.resolveJoinGameMode();
            handler.player.changeGameMode(mode);

            InGamePlayerInfoProvider playerInfo = (InGamePlayerInfoProvider) handler.getPlayer();
            playerInfo.tts$setRole(Role.SPECTATOR);

            Scheduler.INSTANCE.submit((s) -> {
                manager.resolveJoinMap().spawnPlayer(handler.player);
            }, 1);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                GameManager.getInstance().onPlayerLeft(handler.player)
        );
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
            if (!shouldRouteToSpectatorChat(manager.getCurrentPhase(), manager.isAlive(player))) {
                return EventResult.PASS;
            }

            var server = player.getServer();
            if (server == null) {
                return EventResult.DENY;
            }

            SignedMessage spectatorChatMessage = buildSpectatorChatMessage(message);
            SentMessage sentMessage = SentMessage.of(spectatorChatMessage);
            for (ServerPlayerEntity candidate : server.getPlayerManager().getPlayerList()) {
                InGamePlayerInfoProvider candidateInfo = (InGamePlayerInfoProvider) candidate;
                if (canReceiveSpectatorChat(manager.isAlive(candidate), candidateInfo.tts$getRole())) {
                    candidate.sendChatMessage(
                            sentMessage,
                            player.shouldFilterMessagesSentTo(candidate),
                            messageType
                    );
                }
            }
            return EventResult.DENY;
        });
    }

    private static void registerServerLifecycleEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (GameManager.getInstance().getCurrentPhase().isInProgress()) {
                GameManager.getInstance().tick();
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                GameManager.getInstance().getSpawnWorld()
        );
    }

    private static void registerUiEvents() {
        Stimuli.global().listen(PlayerSwapWithOffhandEvent.EVENT, player -> {
            if (!GameManager.getInstance().getCurrentPhase().canShowRole()) {
                return EventResult.PASS;
            }

            ShopGUI gui = new ShopGUI(player);
            gui.open();
            return EventResult.DENY;
        });
    }

    private static ServerPlayerEntity getRecentDamagePlayer(Entity attacker) {
        return attacker instanceof ServerPlayerEntity player ? player : null;
    }

    static boolean shouldRouteToSpectatorChat(GameManager.Phase phase, boolean senderAlive) {
        return phase.isInProgress() && !senderAlive;
    }

    static boolean canReceiveSpectatorChat(boolean candidateAlive, Role candidateRole) {
        return !candidateAlive || candidateRole == Role.SPECTATOR;
    }

    private static SignedMessage buildSpectatorChatMessage(SignedMessage message) {
        Text prefixedContent = Text.empty()
                .append(Text.literal("[관전자] ").formatted(Formatting.GRAY))
                .append(message.getContent().copy().formatted(Formatting.GRAY));
        return message.withUnsignedContent(prefixedContent);
    }
}
