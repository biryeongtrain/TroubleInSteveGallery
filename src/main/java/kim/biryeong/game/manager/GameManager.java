package kim.biryeong.game.manager;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import it.unimi.dsi.fastutil.objects.Object2IntRBTreeMap;
import kim.biryeong.game.data.PlayerDataInstance;
import kim.biryeong.player.role.InGamePlayerInfoProvider;
import kim.biryeong.player.role.Role;
import net.kyori.adventure.platform.modcommon.MinecraftAudiences;
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.util.thread.NameableExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class GameManager {
    private final NameableExecutor executor = new NameableExecutor(MoreExecutors.newDirectExecutorService());
    private static GameManager instance;
    private final AtomicReference<Phase> currentPhase = new AtomicReference<>(Phase.NOT_STARTED);
    private final AtomicReference<Xoroshiro128PlusPlusRandom> rand = new AtomicReference<>(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
    private final Object2IntRBTreeMap<UUID> playerWaitingPoints = Util.make(() -> {
        Object2IntRBTreeMap<UUID> map = new Object2IntRBTreeMap<>();
        map.defaultReturnValue(10);
        return map;
    });
    final GameDataManager gameDataManager = new GameDataManager(this);
    final GameInstanceManager gameInstanceManager = new GameInstanceManager();
    //    private final List<UUID> participants = Collections.synchronizedList(new ArrayList<>());
    static MinecraftServer server;
    final Logger LOGGER = LoggerFactory.getLogger("TTS_GameManager");
    static MinecraftAudiences ADVENTURE;

    private GameManager() {
        executor.named("TTS Game Manager");
    }

    public static void setServer(MinecraftServer initializedServer) {
        server = initializedServer;
        ADVENTURE = MinecraftServerAudiences.of(server);
    }

    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    public void startGame(boolean resetPoint) {
        if (this.currentPhase.get() != Phase.NOT_STARTED) {
            throw new IllegalStateException("Game is already started.");
        }
        try {
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            List<ServerPlayerEntity> availablePlayers = players
                    .stream()
                    .filter(p -> !((InGamePlayerInfoProvider) p).tts$denyToPlay())
                    .toList();

            this.currentPhase.set(Phase.POST_GAME);

            sendMessage("<green>게임 전 초기화중... 인식 된 플레이어 수 : %s".formatted(availablePlayers.size()));
            LOGGER.info("Player lists : {}", availablePlayers.stream().map(PlayerEntity::getStringifiedName).toList());

            if (resetPoint) {
                sendMessage("<green> 게임 설정에 따라 모든 포인트를 초기화합니다...");
                this.playerWaitingPoints.clear();
            }
            availablePlayers.forEach(p -> {
                var info = (InGamePlayerInfoProvider) p;
                if (resetPoint) info.tts$clearPoints();
                info.tts$setRole(Role.SPECTATOR);
            });
            LOGGER.info("reset all points");

            this.gameInstanceManager.initialize(availablePlayers.stream().map(PlayerEntity::getUuid).toList());

            CompletableFuture.runAsync(
                    () -> this.startGame(availablePlayers),
                    this.executor).thenRun(() -> server.executeSync(() -> {
                        LOGGER.info("All background job are completed. starting game...");
                        gameInstanceManager.calculateAliveTraitors();
                        this.currentPhase.set(Phase.MIDDLE_GAME);
                        // TODO : make teleport etc...
                    })
            ).join();
        } catch (Exception e) {
            LOGGER.error("error occurred while starting game", e);
        }

    }

    public void stopGame(PlayerDataInstance.Result result) {
        this.currentPhase.set(Phase.END_GAME);
        // TODO STOP LOGIC
        sendMessage("<red>게임 결과를 저장중입니다. 나가지 마세요...");
        CompletableFuture.runAsync(() -> {
            this.gameInstanceManager.getParticipants().forEach(u -> {
                PlayerDataInstance data = this.gameDataManager.getData(u);
                var player = server.getPlayerManager().getPlayer(u);
                InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
                var playerResult = PlayerDataInstance.PlayerGameResult.create(info.tts$getRole(), result, result == PlayerDataInstance.Result.WIN ? 10 : 5);
                data.addResult(playerResult);
                this.gameDataManager.saveAll();
                this.gameDataManager.saveRoundData();
            });
        }, this.executor).thenRun(() -> {
            server.executeSync(() -> {
                this.gameInstanceManager.clear();
                sendMessage("<green>게임 결과가 저장되었습니다.");
                this.currentPhase.set(Phase.NOT_STARTED);
            });
        }).join();
    }

    private void startGame(List<ServerPlayerEntity> availablePlayers) {
        if (server == null) {
            throw new IllegalStateException("Server is not set yet. this is must be bug.");
        }

        var seed = RandomSeed.createXoroshiroSeed(RandomSeed.getSeed());
        LOGGER.info("selected seed : {}", seed);
        Xoroshiro128PlusPlusRandom rnd = new Xoroshiro128PlusPlusRandom(seed);

        int numOfDetectives = availablePlayers.size() / 6;
        int numOfTraitors = Math.max(availablePlayers.size() / 3, 1);
        LOGGER.info("required detective : {}, required traitor = {}", numOfDetectives, numOfTraitors);

        ArrayList<UUID> detectives = new ArrayList<>();
        ArrayList<UUID> traitors = new ArrayList<>();
        ArrayList<UUID> users = Lists.newArrayList(availablePlayers.stream().map(PlayerEntity::getUuid).toList());

        StringBuilder detectiveNames = new StringBuilder();
        StringBuilder traitorNames = new StringBuilder();

        this.selectRoles(Role.DETECTIVE, numOfDetectives, users, detectives, detectiveNames, rnd);
        this.selectRoles(Role.TRAITOR, numOfTraitors, users, traitors, traitorNames, rnd);
        availablePlayers.forEach(p -> {
            var info = (InGamePlayerInfoProvider) p;
            if (info.tts$getRole() == Role.SPECTATOR) {
                info.tts$setRole(Role.INNOCENT);
            }
        });

        LOGGER.info("detective : {}", detectiveNames);
        LOGGER.info("traitor : {}", traitorNames);
        gameDataManager.startToRecordKillData();
        this.rand.set(rnd);
    }

    public Xoroshiro128PlusPlusRandom getRandom() {
        return this.rand.get();
    }

    private void selectRoles(Role role, int amount, List<UUID> allParticipants, List<UUID> list, StringBuilder builder, Xoroshiro128PlusPlusRandom rnd) {
        if (amount == 0) return;

        for (int i = 0; i < amount; i++) {
            int random = rnd.nextInt(allParticipants.size());
            UUID u = allParticipants.remove(random);
            var p = server.getPlayerManager().getPlayer(u);
            var info = (InGamePlayerInfoProvider) p;
            info.tts$setRole(role);
            info.tts$addPoints(role == Role.DETECTIVE ? 10 : 8, InGamePlayerInfoProvider.PointReason.ROLE_PLAYING);
            builder.append(p.getStringifiedName() + ", ");
            list.add(u);
        }
    }

    private void spreadPlayers() {

    }

    public void onPlayerJoined(ServerPlayerEntity player) {
        boolean isPlayerLoaded = this.gameDataManager.tryToLoadPlayerData(player.getUuid());
        if (!isPlayerLoaded) {
            LOGGER.info("player {} seems not loaded. creating new one...", player.getStringifiedName());
            this.gameDataManager.createNewData(player.getUuid());
        }
    }

    public void onPlayerLeft(ServerPlayerEntity player) {
        if (isGameStarted()) {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            if (info.tts$getRole() != Role.SPECTATOR) {
                var data = this.gameDataManager.getData(player);
                var result = PlayerDataInstance.PlayerGameResult.create(info.tts$getRole(), PlayerDataInstance.Result.LOSE, 0);
            }
        }
        this.gameDataManager.saveData(player.getUuid(), true);
    }

    public void sendMessage(String message) {
        MiniMessage mm = MiniMessage.miniMessage();
        var text = ADVENTURE.asNative(mm.deserialize(message));
        server.getPlayerManager().broadcast(text, false);
    }

    public void tick() {
        if (!server.isOnThread()) {
            throw new IllegalStateException("tick() must be called on the main server thread.");
        }
        this.gameInstanceManager.tick();

    }

    public boolean isGameStarted() {
        return this.currentPhase.get() != Phase.NOT_STARTED;
    }

    public boolean isGameInitializing() {
        return this.currentPhase.get() == Phase.POST_GAME;
    }

    public @NotNull PlayerDataInstance getDataInstance(UUID uuid) {
        return this.gameDataManager.getData(uuid);
    }

    public @NotNull PlayerDataInstance getDataInstance(ServerPlayerEntity player) {
        return this.getDataInstance(player.getUuid());
    }

    Set<UUID> getPlayers() {
        return this.gameInstanceManager.getParticipants();
    }

    public @Nullable ServerPlayerEntity getPlayer(UUID uuid) {
        return server.getPlayerManager().getPlayer(uuid);
    }

    public void onKilled(@Nullable ServerPlayerEntity attacker, ServerPlayerEntity victim, DamageSource damageSource) {
        this.gameDataManager.recordKillData(attacker, victim, damageSource);
        this.gameInstanceManager.onPlayerKilled(attacker, victim, damageSource);
    }

    public Text byMiniMessage(String message) {
        MiniMessage mm = MiniMessage.miniMessage();
        return ADVENTURE.asNative(mm.deserialize(message));
    }

    public enum Phase {
        NOT_STARTED,
        POST_GAME,
        MIDDLE_GAME,
        END_GAME,

    }
}
