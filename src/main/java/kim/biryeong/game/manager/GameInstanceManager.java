package kim.biryeong.game.manager;

import kim.biryeong.game.data.PlayerDataInstance;
import kim.biryeong.player.role.InGamePlayerInfoProvider;
import kim.biryeong.player.role.Role;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

class GameInstanceManager {
    static boolean debug = true;

    private final Logger LOGGER = LoggerFactory.getLogger(GameInstanceManager.class);
    private final Set<UUID> participants = Collections.synchronizedSet(new HashSet<>());
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

    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    void tick() {
        elapsedTicks++;
        if (this.elapsedTicks % 200 == 0) {
            LOGGER.info("Elapsed seconds: {}, Alive participants: {}/{}", elapsedTicks / 20, aliveParticipants.size(), participants.size());
        }

        if (!debug && this.elapsedTicks % 20 == 0) {
            if (aliveTraitors == 0) { // INNOCENT WINS
                // TODO Win logic
                GameManager.getInstance().sendMessage("<green> 이노센트 승리 !");
                GameManager.getInstance().stopGame(PlayerDataInstance.Result.WIN);
            } else if (aliveTraitors * 2 > this.aliveParticipants.size()) {  // on alive traitors are half or more of alive participants
                // traitor wins
                // TODO : win logic
                GameManager.getInstance().sendMessage("<red> 트레이터 승리 !");
                GameManager.getInstance().stopGame(PlayerDataInstance.Result.LOSE);
            }
        }

        if (this.elapsedTicks % 400 == 0) {
            this.aliveParticipants.stream().forEach((uuid) -> {
                ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(uuid);
                if (player == null) {
                    this.aliveParticipants.remove(uuid);
                    return;
                }
                InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
                info.tts$addPoints(5, InGamePlayerInfoProvider.PointReason.PLAYED);
            });
        }

    }

    public boolean isInnocent(ServerPlayerEntity player) {
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        return info.tts$getRole() != Role.TRAITOR && info.tts$getRole() != Role.SPECTATOR;
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    public void clear() {
        participants.clear();
        elapsedTicks = 0;
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
