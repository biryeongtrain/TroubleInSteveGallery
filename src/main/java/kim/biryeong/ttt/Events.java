package kim.biryeong.ttt;

import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.item.detective.NonThrowable;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import xyz.nucleoid.stimuli.Stimuli;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.item.ItemThrowEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public final class Events {
    private Events() {
        throw new IllegalStateException("Utility class");
    }

    public static void registerEvents() {
        Stimuli.global().listen(ItemThrowEvent.EVENT, (player, slot, stack) -> {
            if (stack.getItem() instanceof NonThrowable) {
                return EventResult.DENY;
            }
            return EventResult.PASS;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            GameManager.getInstance().onPlayerJoined(handler.player);
            if (GameManager.getInstance().isGameStarted()) {
                var playerInfo = (InGamePlayerInfoProvider) handler.getPlayer();
                playerInfo.tts$setRole(Role.SPECTATOR);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GameManager.getInstance().onPlayerLeft(handler.player);
        });

        Stimuli.global().listen(PlayerDeathEvent.EVENT, (victim, damageSource) -> {
            if (GameManager.getInstance().isGameStarted()) {
                ServerPlayerEntity attacker = damageSource.getAttacker() instanceof ServerPlayerEntity player ? player : null;
                GameManager.getInstance().onKilled(attacker, victim, damageSource);
                victim.changeGameMode(GameMode.SPECTATOR);
                victim.heal(victim.getMaxHealth());
                victim.clearStatusEffects();
                // TODO : SPAWN COLLAPSE

                return EventResult.DENY;
            }
            return EventResult.DENY;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (GameManager.getInstance().isGameStarted()) {
                GameManager.getInstance().tick();
            }
        });
    }
}
