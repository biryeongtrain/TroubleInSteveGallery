package kim.biryeong.ttt.game.manager;

import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.player.duck.InGameEventProvider;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
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

    private int elapsedTicks = 0;

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


    public void addParticipants(List<UUID> uuids) {
        participants.addAll(uuids);
    }

    public void onGameStopped() {

    }

    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    void tick() {
        elapsedTicks++;
        if (this.elapsedTicks % 200 == 0) {
            LOGGER.info("Elapsed seconds: {}, Alive participants: {}/{}", elapsedTicks / 20, aliveParticipants.size(), participants.size());
        }

        if (!GameManager.getInstance().debugMode && this.elapsedTicks % 20 == 0) {
            if (aliveTraitors == 0) { // INNOCENT WINS
                // TODO Win logic
                GameManager.getInstance().sendMessage("<green> 이노센트 승리 !");
                GameManager.getInstance().stopGame(PlayerDataInstance.Result.WIN);
            } else if (aliveTraitors == this.aliveParticipants.size()) {  // on alive traitors are half or more of alive participants
                // traitor wins
                // TODO : win logic
                GameManager.getInstance().sendMessage("<red> 트레이터 승리 !");
                GameManager.getInstance().stopGame(PlayerDataInstance.Result.LOSE);
            }
        }

        if (this.elapsedTicks % 400 == 0) {
            this.aliveParticipants.stream().filter((uuid) -> {
                ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(uuid);
                if (player == null) {
                    return true;
                }
                InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
                info.tts$addPoints(5, InGamePlayerInfoProvider.PointReason.PLAYED);
                return false;
            }).toList().forEach(this.aliveParticipants::remove);
        }

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

    public void onPlayerKilled(@Nullable ServerPlayerEntity attacker, ServerPlayerEntity victim, DamageSource source) {
        UUID victimUuid = victim.getUuid();
        InGamePlayerInfoProvider victimInfo = (InGamePlayerInfoProvider) victim;
        if (attacker != null) {
            UUID attackerUuid = attacker.getUuid();
            InGamePlayerInfoProvider attackerInfo = (InGamePlayerInfoProvider) attacker;
            LOGGER.info("Player {} ({}) killed Player {} ({}). (Source: {})", attacker.getStringifiedName(), attackerInfo.tts$getRole(), victim.getStringifiedName(), victimInfo.tts$getRole(), source.getName());
            attackerInfo.tts$addPoints(10, InGamePlayerInfoProvider.PointReason.KILL);
        } else {
            LOGGER.info("Player {} ({}) was killed. (Source: {})", victim.getStringifiedName(), victimInfo.tts$getRole() ,source.getName());
        }
        if (victimInfo.tts$getRole() == Role.TRAITOR) {
            aliveTraitors--;
        }
        aliveParticipants.remove(victimUuid);
    }

    private static boolean initialized = false;
}
