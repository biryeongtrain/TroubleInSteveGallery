package kim.biryeong.ttt;

import kim.biryeong.ttt.entity.CorpseEntity;
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
import xyz.nucleoid.stimuli.event.player.PlayerConsumeHungerEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;
import xyz.nucleoid.stimuli.event.player.PlayerRegenerateEvent;

/**
 * Event는 2가지 방식이 있음. 패브릭에서 지원하는 Event 클래스가 있고, {@link <src = <a href="https://github.com/NucleoidMC/stimuli">Stimuli 에서 지원하는 이벤트가 있음</a>}
 * 그래서 이거 따라서 추가하면 됨. 모르면 물어보면 될듯
 */
public final class Events {
    private Events() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 이 메소드는 무조건 {@link TroubleInTerroristTownMod#onInitialize()} 에서 실행되어야함. 이때가 서버 켜지기 전인데 그때부터 등록해놔야 그 이후에 바로 등록해서 쓸수있는거지
     */
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

        Stimuli.global().listen(PlayerDamageEvent.EVENT, ((player, source, amount) -> {
            if (!GameManager.getInstance().isGameStarted()) {
                return EventResult.DENY;
            }
            return EventResult.PASS;
        }));

        Stimuli.global().listen(PlayerDeathEvent.EVENT, (victim, damageSource) -> {
            if (GameManager.getInstance().isGameStarted()) {
                ServerPlayerEntity attacker = damageSource.getAttacker() instanceof ServerPlayerEntity player ? player : null;
                GameManager.getInstance().onKilled(attacker, victim, damageSource);
                victim.changeGameMode(GameMode.SPECTATOR);
                victim.heal(victim.getMaxHealth());
                victim.clearStatusEffects();
                // TODO : SPAWN COLLAPSE
                var entity = CorpseEntity.createCorpse(victim.getEntityWorld(), victim, damageSource);
                victim.getEntityWorld().spawnEntity(entity);
                return EventResult.DENY;
            }
            return EventResult.DENY;
        });

        Stimuli.global().listen(PlayerConsumeHungerEvent.EVENT, ((player, foodLevel, saturation, exhaustion) -> EventResult.DENY));
        Stimuli.global().listen(PlayerRegenerateEvent.EVENT, ((player, amount) -> EventResult.DENY));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (GameManager.getInstance().isGameStarted()) {
                GameManager.getInstance().tick();
            }
        });
    }
}
