package kim.biryeong.ttt.game.manager;

import com.google.common.util.concurrent.MoreExecutors;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import kim.biryeong.ttt.config.Config;
import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.item.ModItems;
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
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.util.thread.NameableExecutor;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused")
public final class GameManager {
    private static final Identifier LOBBY_MAP_ID = Identifier.of("ttt:lobby");
    private static final Identifier DEFAULT_GAME_MAP_ID = Identifier.of("ttt:kitchen");
    private static final int ROUND_WIN_POINTS = 10;
    private static final int ROUND_NON_WIN_POINTS = 5;
    private static final int LEAVE_DURING_ROUND_POINTS = 0;

    private static GameManager instance;

    private final NameableExecutor executor = new NameableExecutor(MoreExecutors.newDirectExecutorService());
    private final AtomicReference<Phase> currentPhase = new AtomicReference<>(Phase.NOT_STARTED);
    private final AtomicReference<Xoroshiro128PlusPlusRandom> rand =
            new AtomicReference<>(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
    private final Object2IntOpenHashMap<UUID> playerPoints = new Object2IntOpenHashMap<>();

    final GameDataManager gameDataManager = new GameDataManager(this);
    final GameInstanceManager gameInstanceManager = new GameInstanceManager();
    final RoundReplayRecorder roundReplayRecorder = new RoundReplayRecorder();
    final Map<Identifier, MapTemplate> templates = new Object2ObjectOpenHashMap<>();

    static MinecraftServer server;
    static final Logger LOGGER = LoggerFactory.getLogger("TTS_GameManager");
    static MinecraftAudiences ADVENTURE;

    public static GameDefaultSidebar DEFAULT_SIDEBAR;
    boolean debugMode = false;
    TTTMap currentMap;
    TTTMap spawnMap;

    private GameManager() {
        this.executor.named("TTS Game Manager");
    }

    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    public static void setServer(MinecraftServer initializedServer) {
        server = initializedServer;
        ADVENTURE = MinecraftServerAudiences.of(server);
        DEFAULT_SIDEBAR = new GameDefaultSidebar();

        GameManager manager = getInstance();
        manager.reloadMapData(manager.getAllMapIds());
        manager.loadLobbyMap();
    }

    private void loadLobbyMap() {
        try {
            this.spawnMap = new TTTMap(LOBBY_MAP_ID, MapTemplateSerializer.loadFromResource(server, LOBBY_MAP_ID));
        } catch (Exception exception) {
            LOGGER.error("Cannot load lobby map", exception);
        }
    }

    public ServerWorld getSpawnWorld() {
        if (this.spawnMap.getWorld() == null) {
            this.spawnMap.generateWorld(server, true);
        }

        return this.spawnMap.getWorld();
    }

    public TTTMap getCurrentWorld() {
        if (this.currentMap == null || !this.getCurrentPhase().isInProgress()) {
            return this.spawnMap;
        }
        return this.currentMap;
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
        ensureNotStarted();

        try {
            ensureCurrentMapReady();
            List<ServerPlayerEntity> availablePlayers = collectAvailablePlayers();
            if (!hasEnoughPlayersToStart(availablePlayers.size())) {
                return;
            }

            this.currentPhase.set(Phase.INITIALIZE);
            announceRoundInitialization(availablePlayers);

            if (resetPoint) {
                resetPlayerPoints();
            }

            preparePlayersForRound(availablePlayers);
            this.gameInstanceManager.initialize(extractPlayerUuids(availablePlayers));
            assignRoles(availablePlayers);
            startRound(availablePlayers);
        } catch (Exception exception) {
            handleStartFailure(exception);
        }
    }

    private void ensureNotStarted() {
        if (this.currentPhase.get() != Phase.NOT_STARTED) {
            throw new IllegalStateException("Game is already started.");
        }
    }

    private void ensureCurrentMapReady() {
        if (this.currentMap == null) {
            this.currentMap = this.loadMap(DEFAULT_GAME_MAP_ID);
            this.currentMap.generateWorld(server, true);
        }
    }

    private List<ServerPlayerEntity> collectAvailablePlayers() {
        return server.getPlayerManager().getPlayerList().stream()
                .filter(player -> !((InGamePlayerInfoProvider) player).tts$denyToPlay())
                .toList();
    }

    private boolean hasEnoughPlayersToStart(int availablePlayerCount) {
        int minPlayers = Math.max(1, Config.getInstance().minPlayersToStartGame);
        if (availablePlayerCount >= minPlayers) {
            return true;
        }

        sendMessage("<yellow>게임을 시작할 수 없습니다: 최소 %s명이 필요합니다.</yellow>".formatted(minPlayers));
        LOGGER.info("Start denied: available players = {}, min required = {}", availablePlayerCount, minPlayers);
        return false;
    }

    private void announceRoundInitialization(List<ServerPlayerEntity> availablePlayers) {
        sendMessage("<green>라운드를 준비하는 중입니다... 참가 가능 인원: %s명</green>".formatted(availablePlayers.size()));
        LOGGER.info("Round players: {}", availablePlayers.stream()
                .map(player -> player.getGameProfile().getName())
                .toList());
    }

    private void resetPlayerPoints() {
        sendMessage("<green>게임 설정에 따라 모든 플레이어 포인트를 초기화합니다.</green>");
        this.playerPoints.clear();
    }

    private void preparePlayersForRound(List<ServerPlayerEntity> availablePlayers) {
        for (ServerPlayerEntity player : availablePlayers) {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            player.getAttributeInstance(EntityAttributes.WAYPOINT_RECEIVE_RANGE).setBaseValue(0);
            player.getAttributeInstance(EntityAttributes.WAYPOINT_TRANSMIT_RANGE).setBaseValue(25);
            info.tts$setRole(Role.SPECTATOR);
        }
        LOGGER.info("Player round state has been reset.");
    }

    private void startRound(List<ServerPlayerEntity> availablePlayers) {
        LOGGER.info("Role assignment completed. Starting round.");
        this.gameInstanceManager.calculateAliveTraitors();
        this.currentPhase.set(Phase.POST_GAME);
        this.gameInstanceManager.onRoundStarted();
        this.roundReplayRecorder.startRoundRecordings(server, availablePlayers);

        this.currentMap.spreadPlayers(availablePlayers);
        for (ServerPlayerEntity player : availablePlayers) {
            giveRoundStarterItems(player);
        }
    }

    private static void giveRoundStarterItems(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.giveItemStack(ModItems.NORMAL_SWORD.getDefaultStack());
        player.giveItemStack(Items.BOW.getDefaultStack());
        player.giveItemStack(Items.LEAD.getDefaultStack().copyWithCount(5));
    }

    private void handleStartFailure(Exception exception) {
        this.roundReplayRecorder.stopRoundRecordings(server, false);
        this.roundReplayRecorder.clear();
        this.currentPhase.set(Phase.NOT_STARTED);
        this.gameInstanceManager.clear();
        LOGGER.error("Error occurred while starting game", exception);
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
        this.roundReplayRecorder.stopRoundRecordings(server, true);
        sendMessage("<red>라운드 결과를 저장하는 중입니다...</red>");

        for (UUID playerUuid : this.gameInstanceManager.getParticipants()) {
            applyRoundResult(playerUuid, innocentResult);
        }

        this.gameDataManager.saveAll();
        this.gameDataManager.saveRoundData();
        this.gameInstanceManager.clear();
        this.roundReplayRecorder.clear();

        sendMessage("<green>라운드 결과 저장이 완료되었습니다.</green>");
        this.currentPhase.set(Phase.NOT_STARTED);
        restoreAllPlayersToLobby();
    }

    private void applyRoundResult(UUID playerUuid, PlayerDataInstance.Result innocentResult) {
        PlayerDataInstance data = this.gameDataManager.getData(playerUuid);
        ServerPlayerEntity player = this.getPlayer(playerUuid);
        if (data == null) {
            LOGGER.warn("Cannot apply round result for {}: player data is missing", playerUuid);
            return;
        }
        if (player == null) {
            LOGGER.warn("Cannot apply round result for {}: player is offline", playerUuid);
            return;
        }

        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        PlayerDataInstance.PlayerGameResult playerResult = createRoundResult(info.tts$getRole(), innocentResult);

        data.addResult(playerResult);
        info.tts$addPoints(
                playerResult.gainPoints(),
                toRoundPointReason(playerResult.win())
        );

        applyKarmaPenalty(playerUuid, player, info);
        clearRoundState(player);
    }

    private static PlayerDataInstance.PlayerGameResult createRoundResult(
            Role role,
            PlayerDataInstance.Result innocentResult
    ) {
        PlayerDataInstance.Result resultByRole = innocentResult.getByRole(role);
        int gainPoints = calculateRoundResultPoints(resultByRole);
        return PlayerDataInstance.PlayerGameResult.create(role, resultByRole, gainPoints);
    }

    private static int calculateRoundResultPoints(PlayerDataInstance.Result roundResult) {
        return roundResult == PlayerDataInstance.Result.WIN ? ROUND_WIN_POINTS : ROUND_NON_WIN_POINTS;
    }

    private static InGamePlayerInfoProvider.PointReason toRoundPointReason(PlayerDataInstance.Result roundResult) {
        return roundResult == PlayerDataInstance.Result.WIN
                ? InGamePlayerInfoProvider.PointReason.WIN
                : InGamePlayerInfoProvider.PointReason.LOSE;
    }

    private void applyKarmaPenalty(UUID playerUuid, ServerPlayerEntity player, InGamePlayerInfoProvider info) {
        int karmaPenalty = this.gameInstanceManager.getKarmaPointPenalty(playerUuid);
        if (karmaPenalty <= 0) {
            return;
        }

        info.tts$addPoints(-karmaPenalty, InGamePlayerInfoProvider.PointReason.LOSE);
        player.sendMessage(Text.literal("카르마 페널티: -" + karmaPenalty + " 포인트"), false);
    }

    private void clearRoundState(ServerPlayerEntity player) {
        var armorAttribute = player.getAttributeInstance(EntityAttributes.ARMOR);
        if (armorAttribute != null) {
            armorAttribute.removeModifier(ShopUtil.MODIFIER_ID);
        }
        InGameEventProvider provider = (InGameEventProvider) player;
        provider.tts$clearFuse();
    }

    private void restoreAllPlayersToLobby() {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        for (ServerPlayerEntity player : players) {
            this.spawnMap.spawnPlayer(player);
            player.changeGameMode(GameMode.ADVENTURE);
            if (player.getPermissionLevel() < 2) {
                player.getInventory().clear();
            }
            var armorAttribute = player.getAttributeInstance(EntityAttributes.ARMOR);
            if (armorAttribute != null) {
                armorAttribute.removeModifier(ShopUtil.MODIFIER_ID);
            }
        }
        this.spawnMap.spreadPlayers(players);
    }

    private void assignRoles(List<ServerPlayerEntity> availablePlayers) {
        if (server == null) {
            throw new IllegalStateException("Server is not set yet.");
        }
        if (availablePlayers.isEmpty()) {
            throw new IllegalStateException("Cannot start game without active players.");
        }

        var seed = RandomSeed.createXoroshiroSeed(RandomSeed.getSeed());
        LOGGER.info("Selected random seed: {}", seed);
        Xoroshiro128PlusPlusRandom random = new Xoroshiro128PlusPlusRandom(seed);

        int detectiveCount = calculateDetectiveCount(availablePlayers.size());
        int traitorCount = calculateTraitorCount(availablePlayers.size());
        LOGGER.info("Required detective: {}, required traitor: {}", detectiveCount, traitorCount);

        List<UUID> participantPool = new ArrayList<>(availablePlayers.stream().map(PlayerEntity::getUuid).toList());
        StringBuilder detectiveNames = new StringBuilder();
        StringBuilder traitorNames = new StringBuilder();

        this.selectRoles(Role.DETECTIVE, detectiveCount, participantPool, detectiveNames, random);
        this.selectRoles(Role.TRAITOR, traitorCount, participantPool, traitorNames, random);

        for (ServerPlayerEntity player : availablePlayers) {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            if (info.tts$getRole() == Role.SPECTATOR) {
                info.tts$setRole(Role.INNOCENT);
            }
        }

        LOGGER.info("Detectives: {}", detectiveNames);
        LOGGER.info("Traitors: {}", traitorNames);

        this.gameDataManager.startToRecordKillData();
        this.rand.set(random);
    }

    static int calculateDetectiveCount(int playerCount) {
        if (playerCount <= 0) {
            return 0;
        }
        return playerCount / 8;
    }

    static int calculateTraitorCount(int playerCount) {
        if (playerCount <= 0) {
            return 0;
        }
        return Math.max(playerCount / 4, 1);
    }

    private void selectRoles(
            Role role,
            int amount,
            List<UUID> allParticipants,
            StringBuilder selectedNames,
            Xoroshiro128PlusPlusRandom random
    ) {
        if (amount <= 0) {
            return;
        }

        for (int i = 0; i < amount; i++) {
            if (allParticipants.isEmpty()) {
                LOGGER.warn("Cannot select more {} players; participant pool is empty.", role.asString());
                return;
            }

            int randomIndex = random.nextInt(allParticipants.size());
            UUID selectedUuid = allParticipants.remove(randomIndex);
            ServerPlayerEntity selectedPlayer = server.getPlayerManager().getPlayer(selectedUuid);
            if (selectedPlayer == null) {
                continue;
            }

            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) selectedPlayer;
            info.tts$setRole(role);
            info.tts$addPoints(
                    role == Role.DETECTIVE ? 5 : 2,
                    InGamePlayerInfoProvider.PointReason.ROLE_PLAYING
            );
            selectedNames.append(selectedPlayer.getGameProfile().getName()).append(", ");
        }
    }

    private static List<UUID> extractPlayerUuids(List<ServerPlayerEntity> players) {
        return players.stream().map(PlayerEntity::getUuid).toList();
    }

    public Xoroshiro128PlusPlusRandom getRandom() {
        return this.rand.get();
    }

    public void onPlayerJoined(ServerPlayerEntity player) {
        boolean isPlayerLoaded = this.gameDataManager.tryToLoadPlayerData(player.getUuid());
        if (!isPlayerLoaded) {
            LOGGER.info("Player {} is not loaded. Creating new data...", player.getGameProfile().getName());
            this.gameDataManager.createNewData(player.getUuid());
        }
        this.gameInstanceManager.onAudienceChanged();
    }

    public void onPlayerLeft(ServerPlayerEntity player) {
        this.roundReplayRecorder.stopPlayerRecording(server, player, true);

        if (shouldJoinAsSpectator(this.currentPhase.get())) {
            recordLeaveRoundResult(player);
            this.gameInstanceManager.onPlayerLeft(player);
        }

        this.gameDataManager.saveData(player.getUuid(), true);
    }

    private void recordLeaveRoundResult(ServerPlayerEntity player) {
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        if (info.tts$getRole() == Role.SPECTATOR) {
            return;
        }

        PlayerDataInstance data = this.gameDataManager.getData(player.getUuid());
        if (data == null) {
            LOGGER.warn("Cannot append leave result for {}: player data is missing", player.getUuid());
            return;
        }

        data.addResult(PlayerDataInstance.PlayerGameResult.create(
                info.tts$getRole(),
                PlayerDataInstance.Result.LOSE,
                LEAVE_DURING_ROUND_POINTS
        ));
    }

    public void sendMessage(String message) {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        Text text = ADVENTURE.asNative(miniMessage.deserialize(message));
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

    public GameMode resolveJoinGameMode() {
        return shouldJoinAsSpectator(this.currentPhase.get()) ? GameMode.SPECTATOR : GameMode.ADVENTURE;
    }

    public TTTMap resolveJoinMap() {
        return shouldJoinCurrentRoundWorld(this.currentPhase.get(), this.currentMap != null)
                ? this.currentMap
                : this.spawnMap;
    }

    static boolean shouldJoinAsSpectator(Phase phase) {
        return phase == Phase.INITIALIZE || phase.isInProgress();
    }

    static boolean shouldJoinCurrentRoundWorld(Phase phase, boolean hasCurrentMap) {
        return hasCurrentMap && shouldJoinAsSpectator(phase);
    }

    public boolean isGameInitializing() {
        return this.currentPhase.get() == Phase.INITIALIZE;
    }

    public @NotNull PlayerDataInstance getDataInstance(UUID uuid) {
        PlayerDataInstance data = this.gameDataManager.getData(uuid);
        if (data == null) {
            throw new IllegalStateException("Player data not loaded for uuid: " + uuid);
        }
        return data;
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

    public void onPlayerDamaged(@Nullable ServerPlayerEntity attacker, ServerPlayerEntity victim, float amount) {
        this.gameInstanceManager.onPlayerDamaged(attacker, victim, amount);
    }

    public void onKilled(@Nullable ServerPlayerEntity attacker, ServerPlayerEntity victim, DamageSource damageSource) {
        this.gameDataManager.recordKillData(attacker, victim, damageSource);
        this.gameInstanceManager.onPlayerKilled(attacker, victim, damageSource);

        applyDeathSpectatorState(victim);
    }

    private static void applyDeathSpectatorState(ServerPlayerEntity victim) {
        victim.changeGameMode(GameMode.SPECTATOR);
        victim.heal(victim.getMaxHealth());
        victim.clearStatusEffects();
        victim.getAttributes().resetToBaseValue(EntityAttributes.ARMOR);
    }

    @Contract("_ -> new")
    public static Text byMiniMessage(String message) {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        return ADVENTURE.asNative(miniMessage.deserialize(message));
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
            } catch (IOException exception) {
                LOGGER.error("Cannot load map template from resource: {}", identifier, exception);
            }
        }
    }

    public List<Identifier> getAllMapIds() {
        List<Identifier> mapIds = new ArrayList<>(TroubleInTerroristTownMod.BUILT_IN_MAPS);
        mapIds.addAll(Config.getInstance().additionalMaps);
        return mapIds;
    }

    TTTMap loadMap(Identifier id) {
        if (!this.templates.containsKey(id)) {
            throw new IllegalArgumentException("Map template not found: " + id);
        }

        try {
            MapTemplate template = MapTemplateSerializer.loadFromResource(server, id);
            return new TTTMap(id, template);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public enum Phase {
        NOT_STARTED("대기 중"),
        INITIALIZE("초기화 중"),
        POST_GAME("라운드 준비"),
        MIDDLE_GAME("게임 진행 중"),
        OVER_TIME("오버타임"),
        END_GAME("라운드 종료");

        public final String displayName;

        Phase(String displayName) {
            this.displayName = displayName;
        }

        public boolean isInProgress() {
            return this != NOT_STARTED && this != END_GAME && this != INITIALIZE;
        }

        public boolean canShowRole() {
            return this == OVER_TIME || this == MIDDLE_GAME;
        }
    }
}
