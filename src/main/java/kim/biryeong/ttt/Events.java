package kim.biryeong.ttt;

import kim.biryeong.ttt.entity.CorpseEntity;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGameEventProvider;
import kim.biryeong.ttt.ui.gui.ShopGUI;
import kim.biryeong.ttt.util.NonThrowable;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import xyz.nucleoid.stimuli.Stimuli;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.item.ItemThrowEvent;
import xyz.nucleoid.stimuli.event.player.*;

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
            GameManager.DEFAULT_SIDEBAR.addPlayer(handler);
            if (GameManager.getInstance().isGameStarted()) {
                var playerInfo = (InGamePlayerInfoProvider) handler.getPlayer();
                playerInfo.tts$setRole(Role.SPECTATOR);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GameManager.getInstance().onPlayerLeft(handler.player);
        });

        Stimuli.global().listen(PlayerDamageEvent.EVENT, (player, source, amount) -> {
            if (!GameManager.getInstance().getCurrentPhase().isInProgress() || GameManager.getInstance().getCurrentPhase() == GameManager.Phase.POST_GAME) {
                return EventResult.DENY;
            }
            return EventResult.PASS;
        });

        Stimuli.global().listen(PlayerDeathEvent.EVENT, (victim, damageSource) -> {
            if (GameManager.getInstance().isGameStarted()) {
                ServerPlayerEntity attacker = damageSource.getAttacker() instanceof ServerPlayerEntity player ? player :
                        damageSource.isOf(DamageTypes.EXPLOSION) ? getRecentDamagePlayer(victim.getPrimeAdversary()) : null;
                GameManager.getInstance().onKilled(attacker, victim, damageSource);

                // TODO : SPAWN COLLAPSE
                var entity = CorpseEntity.createCorpse(victim.getWorld(), victim, damageSource);
                victim.getWorld().spawnEntity(entity);
                return EventResult.DENY;
            }
            return EventResult.DENY;
        });

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
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (GameManager.getInstance().getCurrentPhase().isInProgress()) {
                GameManager.getInstance().tick();
            }
        });

        Stimuli.global().listen(PlayerSwapWithOffhandEvent.EVENT, (player) -> {
            if (GameManager.getInstance().getCurrentPhase().canShowRole()) {
                ShopGUI gui = new ShopGUI(player);
                gui.open();
                return EventResult.DENY;
            } else {
                return EventResult.PASS;
            }
        });
    }

    private static ServerPlayerEntity getRecentDamagePlayer(Entity attacker) {
        return attacker instanceof ServerPlayerEntity player ? player : null;
    }
}
