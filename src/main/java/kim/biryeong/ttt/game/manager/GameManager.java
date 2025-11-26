package kim.biryeong.ttt.game.manager;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import kim.biryeong.ttt.config.Config;
import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.player.duck.InGameEventProvider;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.sidebar.GameDefaultSidebar;
import kim.biryeong.ttt.util.ShopUtil;
import kim.biryeong.ttt.world.TTTMap;
import net.kyori.adventure.platform.modcommon.MinecraftAudiences;
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.util.thread.NameableExecutor;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 게임 매니저. 사실상 게임 관련한 내용은 얘가 관리한다 보면 됨
 */
@SuppressWarnings("unused")
public final class GameManager {
    private final NameableExecutor executor = new NameableExecutor(MoreExecutors.newDirectExecutorService());
    private static GameManager instance;
    private final AtomicReference<Phase> currentPhase = new AtomicReference<>(Phase.NOT_STARTED);
    private final AtomicReference<Xoroshiro128PlusPlusRandom> rand = new AtomicReference<>(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
    private final Object2IntOpenHashMap<UUID> playerPoints = new Object2IntOpenHashMap<>();
    final GameDataManager gameDataManager = new GameDataManager(this);
    final GameInstanceManager gameInstanceManager = new GameInstanceManager();
    final Map<Identifier, MapTemplate> templates = new Object2ObjectOpenHashMap<>();
    static MinecraftServer server;
    final Logger LOGGER = LoggerFactory.getLogger("TTS_GameManager");
    static MinecraftAudiences ADVENTURE;
    public static GameDefaultSidebar DEFAULT_SIDEBAR;
    boolean debugMode = false;
    TTTMap currentMap;

    private GameManager() {
        executor.named("TTS Game Manager");
    }

    public static void setServer(MinecraftServer initializedServer) {
        server = initializedServer;
        ADVENTURE = MinecraftServerAudiences.of(server);
        DEFAULT_SIDEBAR = new GameDefaultSidebar();
        getInstance().reloadMapData(getInstance().getAllMapIds());
    }

    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    public Phase getCurrentPhase() {
        return this.currentPhase.get();
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    void setPhase(Phase phase) {
        this.currentPhase.set(phase);
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

            this.currentPhase.set(Phase.INITIALIZE);

            sendMessage("<green>게임 전 초기화중... 인식 된 플레이어 수 : %s".formatted(availablePlayers.size()));
            LOGGER.info("Player lists : {}", availablePlayers.stream().map(p -> p.getGameProfile().getName()).toList());

            if (resetPoint) {
                sendMessage("<green> 게임 설정에 따라 모든 포인트를 초기화합니다...");
                this.playerPoints.clear();
            }
            availablePlayers.forEach(p -> {
                var info = (InGamePlayerInfoProvider) p;
                p.getAttributeInstance(EntityAttributes.WAYPOINT_RECEIVE_RANGE).setBaseValue(0);
                p.getAttributeInstance(EntityAttributes.WAYPOINT_TRANSMIT_RANGE).setBaseValue(25);
                info.tts$setRole(Role.SPECTATOR);
            });
            LOGGER.info("reset all points");

            this.gameInstanceManager.initialize(availablePlayers.stream().map(PlayerEntity::getUuid).toList());

            CompletableFuture.runAsync(
                    () -> this.startGame(availablePlayers),
                    this.executor).thenRun(() -> server.executeSync(() -> {
                        if (this.currentMap == null) {
                            this.currentMap = this.loadMap(Identifier.of("ttt:kitchen"));
                        }
                        currentMap.generateWorld(server);

                        ServerWorld world = currentMap.getWorld();
                        LOGGER.info("All background job are completed. starting game...");
                        gameInstanceManager.calculateAliveTraitors();
                        this.currentPhase.set(Phase.POST_GAME);
                        currentMap.spreadPlayers(availablePlayers);
                    })
            ).join();
        } catch (Exception e) {
            LOGGER.error("error occurred while starting game", e);
        }

    }

    public int getPlayerPoint(UUID uuid) {
        return this.playerPoints.getOrDefault(uuid, 0);
    }

    public void addPoint(UUID uuid, int amount) {
        this.playerPoints.addTo(uuid, amount);
    }

    public void clearPoints(UUID uuid) {
        this.playerPoints.removeInt(uuid);
    }

    public void stopGame(PlayerDataInstance.Result innocentResult) {
        this.currentPhase.set(Phase.END_GAME);
        // TODO STOP LOGIC
        sendMessage("<red>게임 결과를 저장중입니다. 나가지 마세요...");
        CompletableFuture.runAsync(() -> this.gameInstanceManager.getParticipants().forEach(u -> {
                    PlayerDataInstance data = this.gameDataManager.getData(u);
                    if (data == null) {
                        return;
                    }
                    ServerPlayerEntity player = getPlayer(u);
                    if (player == null) {
                        return;
                    }
                    InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
                    var result = innocentResult.getByRole(info.tts$getRole());
                    var playerResult = PlayerDataInstance.PlayerGameResult.create(info.tts$getRole(), result, result == PlayerDataInstance.Result.WIN ? 10 : 5);
                    data.addResult(playerResult);
                    info.tts$addPoints(playerResult.gainPoints(), playerResult.win() == PlayerDataInstance.Result.WIN ? InGamePlayerInfoProvider.PointReason.WIN : InGamePlayerInfoProvider.PointReason.LOSE);
                    this.gameDataManager.saveAll();
                    this.gameDataManager.saveRoundData();
                    InGameEventProvider provider = (InGameEventProvider) GameManager.getInstance().getPlayer(u);
                    if (provider == null) {
                        return;
                    }
                    player.getAttributeInstance(EntityAttributes.ARMOR).removeModifier(ShopUtil.ARMOR_ID);
                    provider.tts$clearFuse();
                }), this.executor)
                .thenRun(() -> server.executeSync(() -> {
                    this.gameInstanceManager.clear();
                    sendMessage("<green>게임 결과가 저장되었습니다.");
                    this.currentPhase.set(Phase.NOT_STARTED);
                    server.getPlayerManager().getPlayerList().forEach(u -> {
                        var pos = server.getWorld(World.OVERWORLD).getSpawnPos();
                        u.requestTeleportAndDismount(pos.getX(), pos.getY(), pos.getZ());
                        u.changeGameMode(GameMode.ADVENTURE);
                        if (u.getPermissionLevel() < 2) {
                            u.getInventory().clear();
                        }
                        u.getAttributeInstance(EntityAttributes.ARMOR).removeModifier(ShopUtil.ARMOR_ID);
                        // TODO : MOVE ALL PLAYER TO SPAWN
                    });
                    this.currentMap.closeMap(server);
                    this.currentMap = null;
                })).join();
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
            info.tts$addPoints(role == Role.DETECTIVE ? 5 : 2, InGamePlayerInfoProvider.PointReason.ROLE_PLAYING);
            builder.append(p.getGameProfile().getName()).append(", ");
            list.add(u);
        }
    }

    private void spreadPlayers() {
        // TODO Implementation
    }

    public void onPlayerJoined(ServerPlayerEntity player) {
        boolean isPlayerLoaded = this.gameDataManager.tryToLoadPlayerData(player.getUuid());
        if (!isPlayerLoaded) {
            LOGGER.info("player {} seems not loaded. creating new one...", player.getGameProfile().getName());
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
            this.gameInstanceManager.onPlayerLeaved(player);
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
        return this.currentPhase.get() == Phase.INITIALIZE;
    }

    public @NotNull PlayerDataInstance getDataInstance(UUID uuid) {
        return this.gameDataManager.getData(uuid);
    }

    public @NotNull PlayerDataInstance getDataInstance(ServerPlayerEntity player) {
        return this.getDataInstance(player.getUuid());
    }

    public boolean isAlive(ServerPlayerEntity player) {
        return this.gameInstanceManager.isAlive(player);
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

        victim.changeGameMode(GameMode.SPECTATOR);
        victim.heal(victim.getMaxHealth());
        victim.clearStatusEffects();
        victim.getAttributes().resetToBaseValue(EntityAttributes.ARMOR);
    }

    public static Text byMiniMessage(String message) {
        MiniMessage mm = MiniMessage.miniMessage();
        return ADVENTURE.asNative(mm.deserialize(message));
    }

    public int getLeftTicks() {
        return switch (this.currentPhase.get()) {
            case MIDDLE_GAME -> this.gameInstanceManager.getTimeLeft();
            case OVER_TIME -> this.gameInstanceManager.getOvertime();
            case POST_GAME -> this.gameInstanceManager.getPostGameWarmupTime();
            default -> Integer.MIN_VALUE;
        };
    }

    public void reloadMapData(List<Identifier> mapIds) {
        this.templates.clear();
        for (Identifier identifier : mapIds) {
            try {
                this.templates.put(identifier, MapTemplateSerializer.loadFromResource(server, identifier));
            } catch (IOException e) {
                LOGGER.error("cannot load map template from resource : {}", identifier, e);
            }
        }
    }

    public List<Identifier> getAllMapIds() {
        ArrayList<Identifier> list = new ArrayList<>(TroubleInTerroristTownMod.BUILT_IN_MAPS);
        list.addAll(Config.getInstance().additionalMaps);

        return list;
    }

    TTTMap loadMap(Identifier id) {
        if (!this.templates.containsKey(id)) {
            throw new IllegalArgumentException("map template not found : " + id);
        }

        MapTemplate template = this.templates.get(id);
        return new TTTMap(id, template);
    }

    public enum Phase {
        NOT_STARTED("시작 전"),
        INITIALIZE("초기화 중"),
        POST_GAME("게임 준비 중"),
        MIDDLE_GAME("게임 진행 중"),
        OVER_TIME("추가 시간"),
        END_GAME("게임 종료");

        Phase(String displayName) {
            this.displayName = displayName;
        }

        public final String displayName;

        public boolean isInProgress() {
            return this != NOT_STARTED && this != END_GAME && this != INITIALIZE;
        }

        public boolean canShowRole() {
            return this == OVER_TIME || this == MIDDLE_GAME;
        }
    }
}
