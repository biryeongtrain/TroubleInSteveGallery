package kim.biryeong.ttt.game.manager;

import it.unimi.dsi.fastutil.ints.IntList;
import kim.biryeong.ttt.config.Config;
import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.Scheduler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
class GameInstanceManager {
    private final Logger LOGGER = LoggerFactory.getLogger(GameInstanceManager.class);
    private final Set<UUID> participants = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> corpseEntities = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> aliveParticipants = new HashSet<>();
    private int aliveTraitors = 0;
    private int gamePlayTimeTicks;
    private int overtime = 0;
    private int warmupTimeTick = 0;
    private int maxOverTimeTicks = 0;
    private int overtimePerKill = 0;
    private int elapsedTicks = 0;
    private boolean isOverTime = false;
    private boolean gameEndRequested = false;


    GameInstanceManager() {
        if (initialized) {
            throw new IllegalStateException("GamePointManager is a singleton and has already been initialized.");
        }
    }

    public void initialize(List<UUID> participantUuids) {
        participants.clear();
        participants.addAll(participantUuids);
        aliveParticipants.clear();
        aliveParticipants.addAll(participantUuids);
        elapsedTicks = 0;
        isOverTime = false;
        Config config = Config.getInstance();
        this.gamePlayTimeTicks = config.playTimeSeconds * 20;
        this.warmupTimeTick = config.gameStartCountdownSeconds * 20;
        this.maxOverTimeTicks = config.maxOverTimeSeconds * 20;
        this.overtimePerKill = config.overTimePerKills * 20;
    }

    void calculateAliveTraitors() {
        this.aliveTraitors = aliveParticipants.stream().filter(u -> {
            ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(u);
            if (player == null) {
                return false;
            }
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            return info.tts$getRole() == Role.TRAITOR;
        }).toList().size();
    }

    public int getTimeLeft() {
        return gamePlayTimeTicks - this.elapsedTicks;
    }

    public int getOvertime() {
        return this.overtime;
    }

    public int getPostGameWarmupTime() {
        return this.warmupTimeTick;
    }

    public void addParticipants(List<UUID> uuids) {
        participants.addAll(uuids);
    }

    public void onGameStopped() {

    }

    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    void tick() {
        if (GameManager.getInstance().getCurrentPhase() == GameManager.Phase.POST_GAME) {
            if (this.warmupTimeTick <= 0) {
                GameManager.getInstance().setPhase(GameManager.Phase.MIDDLE_GAME);
            }
            this.warmupTimeTick--;
            return;
        }
        if (this.elapsedTicks % 200 == 0) {
            LOGGER.info("Elapsed seconds: {}, Alive participants: {}/{}", elapsedTicks / 20, aliveParticipants.size(), participants.size());
        }

        if (!GameManager.getInstance().debugMode && this.elapsedTicks % 20 == 0) {
            if (aliveTraitors == 0) { // INNOCENT WINS
                // TODO Win logic
                GameManager.getInstance().sendMessage("<green> 이노센트 승리 !");
                this.requestToWin(PlayerDataInstance.Result.WIN);
            } else if (aliveTraitors == this.aliveParticipants.size()) {  // on alive traitors are half or more of alive participants
                // traitor wins
                // TODO : win logic
                GameManager.getInstance().sendMessage("<red> 트레이터 승리 !");
                this.requestToWin(PlayerDataInstance.Result.LOSE);
            }
        }

        if (this.elapsedTicks % 1200 == 0) {
            this.aliveParticipants.stream().filter((uuid) -> {
                ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(uuid);
                if (player == null) {
                    return true;
                }
                InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
                info.tts$addPoints(2, InGamePlayerInfoProvider.PointReason.PLAYED);
                return false;
            }).toList().forEach(this.aliveParticipants::remove);
        }


        if (isOverTime) {
            if (elapsedTicks >= gamePlayTimeTicks + overtime) {
                // time over, innocent wins
                GameManager.getInstance().sendMessage("<green> 시간 초과! 이노센트 승리 !");
                this.requestToWin(PlayerDataInstance.Result.WIN);

                return;
            }
        }

        if (elapsedTicks >= gamePlayTimeTicks) {
            this.isOverTime = true;
            if (overtime != 0) {
                GameManager.getInstance().sendMessage("<red> 추가시간! 트레이터는 시간 내 모든 이노센트를 처치하세요! </red>");
            }
        }

        elapsedTicks++;
    }

    private void requestToWin(PlayerDataInstance.Result result) {
        MinecraftServer server = GameManager.server;
        this.aliveParticipants.forEach(uuid -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
                return;
            }
            boolean shouldSpawnFirework = (result == PlayerDataInstance.Result.WIN) ^ isInnocent(player);
            if (!shouldSpawnFirework) {
                return;
            }
            int color = result == PlayerDataInstance.Result.WIN ? Role.INNOCENT.getHexAsInt() : Role.TRAITOR.getHexAsInt();
            var pos = player.getPos();
            var fireworkStack = Items.FIREWORK_ROCKET.asItem().getDefaultStack();
            fireworkStack.set(
                    DataComponentTypes.FIREWORKS,
                    new FireworksComponent(
                            1,
                            List.of(new FireworkExplosionComponent(
                                            FireworkExplosionComponent.Type.STAR,
                                            IntList.of(color),
                                            IntList.of(color),
                                            false,
                                            false
                                    )
                            )
                    )
            );
            FireworkRocketEntity firework = new FireworkRocketEntity(player.getWorld(), pos.x, pos.y, pos.z, fireworkStack);
            player.getWorld().spawnEntity(firework);
        });

        Scheduler.INSTANCE.submit((s) -> {GameManager.getInstance().stopGame(result);}, 100);

    }

    public boolean isInnocent(ServerPlayerEntity player) {
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        return info.tts$getRole() != Role.TRAITOR && info.tts$getRole() != Role.SPECTATOR;
    }

    public boolean isAlive(ServerPlayerEntity player) {
        return aliveParticipants.contains(player.getUuid());
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    public void clear() {
        participants.clear();
        elapsedTicks = 0;
        this.corpseEntities.forEach(uuid -> {
            var server = GameManager.server;
            AtomicBoolean isRemoved = new AtomicBoolean(false);
            server.getWorlds().forEach(world -> {
                if (isRemoved.get()) {
                    return;
                }
                var entity = world.getEntity(uuid);
                if (entity == null) {
                    return;
                }

                entity.remove(Entity.RemovalReason.DISCARDED);
                isRemoved.set(true);
            });
        });

        this.corpseEntities.clear();
        this.aliveTraitors = 0;
        this.aliveParticipants.clear();
    }

    public void onPlayerLeaved(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        InGamePlayerInfoProvider playerInfo = (InGamePlayerInfoProvider) player;
        LOGGER.info("Player {} ({}) has left the game.", player.getGameProfile().getName(), playerInfo.tts$getRole());
        if (playerInfo.tts$getRole() == Role.TRAITOR) {
            aliveTraitors--;
        }
        aliveParticipants.remove(playerUuid);
    }

    public void onPlayerKilled(@Nullable ServerPlayerEntity attacker, ServerPlayerEntity victim, DamageSource source) {
        UUID victimUuid = victim.getUuid();
        InGamePlayerInfoProvider victimInfo = (InGamePlayerInfoProvider) victim;
        if (attacker != null) {
            UUID attackerUuid = attacker.getUuid();
            InGamePlayerInfoProvider attackerInfo = (InGamePlayerInfoProvider) attacker;
            LOGGER.info("Player {} ({}) killed Player {} ({}). (Source: {})", attacker.getGameProfile().getName(), attackerInfo.tts$getRole(), victim.getGameProfile().getName(), victimInfo.tts$getRole(), source.getName());
            attackerInfo.tts$addPoints(2, InGamePlayerInfoProvider.PointReason.KILL);

            if (attackerInfo.tts$getRole() == Role.TRAITOR) {
                overtime += overtimePerKill * 20;
            }
        } else {
            LOGGER.info("Player {} ({}) was killed. (Source: {})", victim.getGameProfile().getName(), victimInfo.tts$getRole(), source.getName());
        }
        if (victimInfo.tts$getRole() == Role.TRAITOR) {
            aliveTraitors--;
        }
        aliveParticipants.remove(victimUuid);
    }

    private static boolean initialized = false;
}
