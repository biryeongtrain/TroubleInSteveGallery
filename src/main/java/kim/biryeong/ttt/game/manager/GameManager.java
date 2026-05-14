package kim.biryeong.ttt.game.manager;

import com.google.common.util.concurrent.MoreExecutors;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import kim.biryeong.ttt.config.Config;
import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.game.data.PlayerRoundDataInstance;
import kim.biryeong.ttt.item.ModItems;
import kim.biryeong.ttt.player.ItemLoadout;
import kim.biryeong.ttt.player.duck.InGameEventProvider;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.dialog.log.DeathCombatLogDialog;
import kim.biryeong.ttt.ui.dialog.log.RoundSummaryDialog;
import kim.biryeong.ttt.ui.hud.RoundHudManager;
import kim.biryeong.ttt.ui.sidebar.GameDefaultSidebar;
import kim.biryeong.ttt.util.Scheduler;
import kim.biryeong.ttt.util.ShopUtil;
import kim.biryeong.ttt.util.Sounds;
import kim.biryeong.ttt.world.TTTMap;
import net.kyori.adventure.platform.modcommon.MinecraftAudiences;
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused")
public final class GameManager {
    private static final Identifier LOBBY_MAP_ID = Identifier.of("ttt:lobby");
    private static final Identifier DEFAULT_GAME_MAP_ID = Identifier.of("ttt:kitchen");
    private static final String MAP_TEMPLATE_RESOURCE_PATH = "map_template";
    private static final String MAP_TEMPLATE_EXTENSION = ".nbt";
    private static final int ROUND_WIN_POINTS = 5;
    private static final int ROUND_NON_WIN_POINTS = 3;
    private static final int LEAVE_DURING_ROUND_POINTS = 0;
    private static final int LOBBY_BGM_INTERVAL_TICKS = 3600;
    private static final int ROUND_START_BGM_DELAY_TICKS = 40;
    private static final int GUIDE_TIP_INTERVAL_TICKS = 600;
    private static final int DEATH_COMBAT_LOG_DELAY_TICKS = 30;
    private static final int ROLE_ENTROPY_BASELINE = 100;
    private static final int ROLE_REVEAL_TITLE_FADE_IN_TICKS = 10;
    private static final int ROLE_REVEAL_TITLE_STAY_TICKS = 40;
    private static final int ROLE_REVEAL_TITLE_FADE_OUT_TICKS = 10;
    private static final String FIRST_JOIN_WELCOME_LINE_1 =
            "<red>T</red><green>T</green><blue>T</blue> "
                    + "(<red>Trouble</red> <green>In</green> <blue>Terrorist Town</blue>) "
                    + "<yellow>에 오신 것을 환영합니다.";
    private static final String FIRST_JOIN_WELCOME_LINE_2 =
            "<yellow>처음 플레이하는 유저는 <green>G <yellow>키를 눌러 가이드를 읽어주세요.";
    private static final String UPDATE_NOTICE_LINE =
            "<yellow>[업데이트]</yellow> 새 변경사항이 있습니다. ";
    private static final List<SoundEvent> LOBBY_MORNING_BGM = List.of(
            Sounds.MORNING_BGM_2,
            Sounds.MORNING_BGM_3,
            Sounds.MORNING_BGM_4,
            Sounds.MORNING_BGM_5,
            Sounds.BGM_1,
            Sounds.BGM_2,
            Sounds.BGM_3
    );

    private static GameManager instance;

    public final NameableExecutor executor = new NameableExecutor(MoreExecutors.newDirectExecutorService());
    private final AtomicReference<Phase> currentPhase = new AtomicReference<>(Phase.NOT_STARTED);
    private final AtomicReference<Xoroshiro128PlusPlusRandom> rand =
            new AtomicReference<>(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
    private final Object2IntOpenHashMap<UUID> playerPoints = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<UUID> roleEntropyByUuid = new Object2IntOpenHashMap<>();
    private final Set<UUID> shopGuidePlayers = new HashSet<>();
    private final RoundTimerBossBarManager roundTimerBossBarManager = new RoundTimerBossBarManager();
    private final RoundHudManager roundHudManager = new RoundHudManager();

    final GameDataManager gameDataManager = new GameDataManager(this);
    final GameInstanceManager gameInstanceManager = new GameInstanceManager();
    final RoundReplayRecorder roundReplayRecorder = new RoundReplayRecorder();
    final Map<Identifier, MapTemplate> templates = new Object2ObjectOpenHashMap<>();

    static MinecraftServer server;
    static final Logger LOGGER = LoggerFactory.getLogger("TTT_GameManager");
    static MinecraftAudiences ADVENTURE;
    public static GameDefaultSidebar DEFAULT_SIDEBAR;

    boolean debugMode = false;
    TTTMap currentMap;
    TTTMap spawnMap;
    private boolean roundReplayEnabled = true;
    private boolean firstCorpseGuideShown = false;
    private boolean overtimeGuideShown = false;
    private int rotatingGuideIndex = 0;
    private int lobbyBgmCooldownTicks = LOBBY_BGM_INTERVAL_TICKS;

    private GameManager() {
        this.executor.named("TTT Game Manager");
        this.roleEntropyByUuid.defaultReturnValue(ROLE_ENTROPY_BASELINE);
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
            this.spawnMap.generateWorld(server);
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
        this.startGame(resetPoint, null, true);
    }

    public void startGame(boolean resetPoint, @Nullable Identifier mapId) {
        this.startGame(resetPoint, mapId, true);
    }

    public void startGame(boolean resetPoint, @Nullable Identifier mapId, boolean recordReplay) {
        ensureNotStarted();

        try {
            this.roundReplayEnabled = recordReplay;
            ensureCurrentMapReady(mapId);
            List<ServerPlayerEntity> allOnlinePlayers = server.getPlayerManager().getPlayerList();
            List<ServerPlayerEntity> availablePlayers = collectRoundParticipants(allOnlinePlayers);
            List<ServerPlayerEntity> spectatorPlayers = collectRoundSpectators(allOnlinePlayers);
            if (!hasEnoughPlayersToStart(availablePlayers.size())) {
                return;
            }

            this.currentPhase.set(Phase.INITIALIZE);
            resetRoundGuideState();
            announceRoundInitialization(availablePlayers);

            if (resetPoint) {
                resetPlayerPoints();
            }

            preparePlayersForRound(availablePlayers, spectatorPlayers);
            this.gameInstanceManager.initialize(extractPlayerUuids(availablePlayers));
            assignRoles(availablePlayers);
            startRound(availablePlayers, spectatorPlayers);
        } catch (Exception exception) {
            handleStartFailure(exception);
        }
    }

    private void ensureNotStarted() {
        if (this.currentPhase.get() != Phase.NOT_STARTED) {
            throw new IllegalStateException("Game is already started.");
        }
    }

    private void ensureCurrentMapReady(@Nullable Identifier requestedMapId) {
        Identifier mapId = resolveNextRoundMapId(requestedMapId);
        if (this.currentMap != null && !mapId.equals(this.currentMap.getId())) {
            this.currentMap.closeMap();
        }

        if (this.currentMap == null || !mapId.equals(this.currentMap.getId())) {
            this.currentMap = this.loadMap(mapId);
        }
        if (this.currentMap.getWorld() == null) {
            this.currentMap.generateWorld(server);
        }
    }

    /**
     * Resolves which round map id will be used when a new round starts.
     */
    public Identifier resolveNextRoundMapId(@Nullable Identifier requestedMapId) {
        if (requestedMapId != null) {
            if (!hasRoundMapTemplate(requestedMapId)) {
                throw new IllegalArgumentException("Map template not found: " + requestedMapId);
            }
            return requestedMapId;
        }

        if (this.currentMap != null && !LOBBY_MAP_ID.equals(this.currentMap.getId())) {
            return this.currentMap.getId();
        }

        return resolveDefaultRoundMapId();
    }

    private Identifier resolveDefaultRoundMapId() {
        if (hasRoundMapTemplate(DEFAULT_GAME_MAP_ID)) {
            return DEFAULT_GAME_MAP_ID;
        }

        return getRegisteredRoundMapIds().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No round maps are registered."));
    }

    static boolean shouldParticipateInRound(boolean denyToPlay) {
        return !denyToPlay;
    }

    static GameMode resolveRoundStartGameMode(boolean denyToPlay) {
        return denyToPlay ? GameMode.SPECTATOR : GameMode.ADVENTURE;
    }

    private static List<ServerPlayerEntity> collectRoundParticipants(List<ServerPlayerEntity> players) {
        return players.stream()
                .filter(player -> shouldParticipateInRound(((InGamePlayerInfoProvider) player).tts$denyToPlay()))
                .toList();
    }

    private static List<ServerPlayerEntity> collectRoundSpectators(List<ServerPlayerEntity> players) {
        return players.stream()
                .filter(player -> !shouldParticipateInRound(((InGamePlayerInfoProvider) player).tts$denyToPlay()))
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

    private void preparePlayersForRound(List<ServerPlayerEntity> availablePlayers, List<ServerPlayerEntity> spectatorPlayers) {
        for (ServerPlayerEntity player : availablePlayers) {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            player.getAttributeInstance(EntityAttributes.WAYPOINT_RECEIVE_RANGE).setBaseValue(0);
            player.getAttributeInstance(EntityAttributes.WAYPOINT_TRANSMIT_RANGE).setBaseValue(25);
            info.tts$setRole(Role.SPECTATOR);
        }
        for (ServerPlayerEntity spectator : spectatorPlayers) {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) spectator;
            info.tts$setRole(Role.SPECTATOR);
        }
        LOGGER.info("Player round state has been reset.");
    }

    private void startRound(List<ServerPlayerEntity> availablePlayers, List<ServerPlayerEntity> spectatorPlayers) {
        LOGGER.info("Role assignment completed. Starting round.");
        this.gameInstanceManager.calculateAliveTraitors();
        this.currentPhase.set(Phase.POST_GAME);
        this.gameInstanceManager.onRoundStarted();
        this.roundReplayRecorder.clear();
        if (!this.roundReplayEnabled) {
            LOGGER.info("Round replay recording is disabled for this round.");
        }

        this.currentMap.spreadPlayers(availablePlayers);
        this.currentMap.spreadPlayers(spectatorPlayers);
        this.currentMap.repairMissingBlockEntities();
        for (ServerPlayerEntity player : availablePlayers) {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            player.changeGameMode(resolveRoundStartGameMode(info.tts$denyToPlay()));
            giveRoundStarterItems(player);
        }
        for (ServerPlayerEntity spectator : spectatorPlayers) {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) spectator;
            spectator.changeGameMode(resolveRoundStartGameMode(info.tts$denyToPlay()));
        }

        resetLobbyBgmCooldown();
        scheduleRoundStartBgm();
    }

    private static void giveRoundStarterItems(ServerPlayerEntity player) {
        player.getInventory().clear();
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        resolveRoundStarterItems(info.tts$getItemLoadout()).stream()
                .map(ItemStack::copy)
                .forEach(player::giveItemStack);
    }

    static List<ItemStack> resolveRoundStarterItems(@Nullable ItemLoadout selectedLoadout) {
        if (selectedLoadout == null || selectedLoadout.items().isEmpty()) {
            return createRoundStarterItems();
        }
        return selectedLoadout.items();
    }

    static List<ItemStack> createRoundStarterItems() {
        return List.of(
                ModItems.NORMAL_SWORD.getDefaultStack(),
                Items.BOW.getDefaultStack(),
                Items.SPYGLASS.getDefaultStack(),
                Items.LEAD.getDefaultStack().copyWithCount(5)
        );
    }

    private static void giveMissingStarterSpyglass(ServerPlayerEntity player) {
        if (player.getInventory().containsAny(Set.of(Items.SPYGLASS))) {
            return;
        }
        player.giveItemStack(Items.SPYGLASS.getDefaultStack());
    }

    private void handleStartFailure(Exception exception) {
        this.roundReplayRecorder.stopRoundRecordings(server, false);
        this.roundReplayRecorder.clear();
        this.currentPhase.set(Phase.NOT_STARTED);
        this.gameInstanceManager.clear();
        resetRoundGuideState();
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
        GameDataManager.RoundSummarySnapshot roundSummarySnapshot = this.gameDataManager.getRoundSummarySnapshot();

        for (UUID playerUuid : this.gameInstanceManager.getParticipants()) {
            applyRoundResult(playerUuid, innocentResult);
        }
        applyRoundEntropyGrowth();

        this.gameDataManager.saveAll();
        this.gameDataManager.saveRoundData();
        this.gameInstanceManager.clear();
        this.roundReplayRecorder.clear();
        resetRoundGuideState();

        sendMessage("<green>라운드 결과 저장이 완료되었습니다.</green>");
        this.currentPhase.set(Phase.NOT_STARTED);
        restoreAllPlayersToLobby();
        closeCurrentRoundMap();
        scheduleRoundSummaryDialogs(roundSummarySnapshot);
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

    private void closeCurrentRoundMap() {
        if (this.currentMap == null) {
            return;
        }

        this.currentMap.closeMap();
    }

    private void scheduleRoundSummaryDialogs(GameDataManager.RoundSummarySnapshot roundSummarySnapshot) {
        List<RoundSummaryDialog.RoundSummaryTraitorEntry> traitorEntries = roundSummarySnapshot.traitorEntries().stream()
                .map(entry -> new RoundSummaryDialog.RoundSummaryTraitorEntry(
                        entry.uuid(),
                        entry.name()
                ))
                .toList();
        List<RoundSummaryDialog.RoundSummaryKillEntry> killEntries = roundSummarySnapshot.killEntries().stream()
                .map(entry -> new RoundSummaryDialog.RoundSummaryKillEntry(
                        entry.elapsedSeconds(),
                        entry.killerName(),
                        entry.killerRole(),
                        entry.victimName(),
                        entry.victimRole()
                ))
                .toList();

        Scheduler.INSTANCE.submit((s) -> {
            if (this.currentPhase.get() != Phase.NOT_STARTED) {
                return;
            }
            for (ServerPlayerEntity player : s.getPlayerManager().getPlayerList()) {
                RoundSummaryDialog.showRoundSummary(player, killEntries, traitorEntries);
            }
        }, 40);
    }

    private void assignRoles(List<ServerPlayerEntity> availablePlayers) {
        if (server == null) {
            throw new IllegalStateException("Server is not set yet.");
        }
        if (availablePlayers.isEmpty()) {
            throw new IllegalStateException("Cannot start game without active players.");
        }

        availablePlayers.forEach(player -> ensurePlayerEntropyInitialized(player.getUuid()));

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
        return playerCount / 6;
    }

    static int calculateTraitorCount(int playerCount) {
        if (playerCount <= 0) {
            return 0;
        }
        return Math.max((int) Math.floor(playerCount / 3.5d), 1);
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

            UUID selectedUuid = selectHighestEntropyCandidate(allParticipants, random);
            if (!allParticipants.remove(selectedUuid)) {
                LOGGER.warn("Cannot remove selected {} candidate {} from participant pool.", role.asString(), selectedUuid);
                continue;
            }
            ServerPlayerEntity selectedPlayer = server.getPlayerManager().getPlayer(selectedUuid);
            if (selectedPlayer == null) {
                continue;
            }

            int entropyBeforeReset = getRoleEntropy(selectedUuid);
            resetRoleEntropyInternal(selectedUuid);

            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) selectedPlayer;
            info.tts$setRole(role);
            info.tts$addPoints(
                    role == Role.DETECTIVE ? 2 : 1,
                    InGamePlayerInfoProvider.PointReason.ROLE_PLAYING
            );
            selectedNames.append(selectedPlayer.getGameProfile().getName()).append(", ");
            LOGGER.info(
                    "Selected {} ({}) as {} by entropy {}. Entropy reset to {}.",
                    selectedPlayer.getGameProfile().getName(),
                    selectedUuid,
                    role.asString(),
                    entropyBeforeReset,
                    ROLE_ENTROPY_BASELINE
            );
        }
    }

    private UUID selectHighestEntropyCandidate(List<UUID> candidates, Xoroshiro128PlusPlusRandom random) {
        List<UUID> highestEntropyCandidates = collectHighestEntropyCandidates(
                candidates,
                buildEntropySnapshot(candidates),
                ROLE_ENTROPY_BASELINE
        );
        if (highestEntropyCandidates.isEmpty()) {
            return candidates.get(0);
        }
        return highestEntropyCandidates.get(random.nextInt(highestEntropyCandidates.size()));
    }

    private Map<UUID, Integer> buildEntropySnapshot(List<UUID> candidates) {
        Map<UUID, Integer> entropyByUuid = new Object2ObjectOpenHashMap<>();
        for (UUID candidateUuid : candidates) {
            entropyByUuid.put(candidateUuid, getRoleEntropy(candidateUuid));
        }
        return entropyByUuid;
    }

    static List<UUID> collectHighestEntropyCandidates(
            List<UUID> candidates,
            Map<UUID, Integer> entropyByUuid,
            int defaultEntropy
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        int normalizedDefaultEntropy = Math.max(0, defaultEntropy);
        int highestEntropy = Integer.MIN_VALUE;
        List<UUID> highestCandidates = new ArrayList<>();
        for (UUID candidate : candidates) {
            int entropy = Math.max(0, entropyByUuid.getOrDefault(candidate, normalizedDefaultEntropy));
            if (entropy > highestEntropy) {
                highestEntropy = entropy;
                highestCandidates.clear();
                highestCandidates.add(candidate);
                continue;
            }

            if (entropy == highestEntropy) {
                highestCandidates.add(candidate);
            }
        }

        return List.copyOf(highestCandidates);
    }

    private void applyRoundEntropyGrowth() {
        Xoroshiro128PlusPlusRandom random = this.rand.get();
        Config config = Config.getInstance();
        int minGain = config.roleEntropyGainMin;
        int maxGain = config.roleEntropyGainMax;
        for (UUID participantUuid : Set.copyOf(this.gameInstanceManager.getParticipants())) {
            ensurePlayerEntropyInitialized(participantUuid);
            int gain = calculateRoundEntropyGain(random, minGain, maxGain);
            int nextEntropy = addRoleEntropy(participantUuid, gain);
            LOGGER.info("Round entropy increased for {} by {} => {}", participantUuid, gain, nextEntropy);
        }
    }

    static int calculateRoundEntropyGain(
            Xoroshiro128PlusPlusRandom random,
            int minGain,
            int maxGain
    ) {
        int normalizedMinGain = Math.max(0, Math.min(minGain, maxGain));
        int normalizedMaxGain = Math.max(normalizedMinGain, Math.max(minGain, maxGain));
        return normalizedMinGain + random.nextInt(normalizedMaxGain - normalizedMinGain + 1);
    }

    public int getRoleEntropy(UUID playerUuid) {
        return Math.max(0, this.roleEntropyByUuid.getInt(playerUuid));
    }

    public int getRoleEntropy(ServerPlayerEntity player) {
        return getRoleEntropy(player.getUuid());
    }

    public void resetRoleEntropy(UUID playerUuid) {
        resetRoleEntropyInternal(playerUuid);
    }

    public void resetRoleEntropy(ServerPlayerEntity player) {
        resetRoleEntropyInternal(player.getUuid());
    }

    private int addRoleEntropy(UUID playerUuid, int amount) {
        int normalizedAmount = Math.max(0, amount);
        int nextEntropy = getRoleEntropy(playerUuid) + normalizedAmount;
        this.roleEntropyByUuid.put(playerUuid, nextEntropy);
        return nextEntropy;
    }

    private void resetRoleEntropyInternal(UUID playerUuid) {
        this.roleEntropyByUuid.put(playerUuid, ROLE_ENTROPY_BASELINE);
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
        ensurePlayerEntropyInitialized(player.getUuid());
        sendFirstJoinWelcomeMessage(player);
        sendUpdateNoticeMessage(player);
        this.gameInstanceManager.onAudienceChanged();
    }

    private void ensurePlayerEntropyInitialized(UUID playerUuid) {
        if (!this.roleEntropyByUuid.containsKey(playerUuid)) {
            this.roleEntropyByUuid.put(playerUuid, ROLE_ENTROPY_BASELINE);
            return;
        }

        int entropy = getRoleEntropy(playerUuid);
        if (entropy < ROLE_ENTROPY_BASELINE) {
            this.roleEntropyByUuid.put(playerUuid, ROLE_ENTROPY_BASELINE);
        }
    }

    private static void sendFirstJoinWelcomeMessage(ServerPlayerEntity player) {

        player.sendMessage(Text.literal("\n").append(byMiniMessage(FIRST_JOIN_WELCOME_LINE_1)), false);
        player.sendMessage(byMiniMessage(FIRST_JOIN_WELCOME_LINE_2), false);
    }

    private static void sendUpdateNoticeMessage(ServerPlayerEntity player) {
        Text openUpdateGuide = Text.literal("[업데이트 내역 열기]")
                .styled(style -> style
                        .withColor(0x55FF55)
                        .withClickEvent(new ClickEvent.RunCommand("/tts guide update"))
                        .withUnderline(true));
        player.sendMessage(
                byMiniMessage(UPDATE_NOTICE_LINE)
                        .copy()
                        .append(openUpdateGuide),
                false
        );
    }

    public void onPlayerLeft(ServerPlayerEntity player) {
        this.roundTimerBossBarManager.removePlayer(player);

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

    public void tickRoundTimerBossBar() {
        if (server == null) {
            return;
        }

        this.roundTimerBossBarManager.tick(server, this.currentPhase.get(), this.getLeftTicks());
    }

    public void addRoundHudPlayer(ServerPlayerEntity player) {
        this.roundHudManager.addPlayer(player);
    }

    public void removeRoundHudPlayer(ServerPlayerEntity player) {
        this.roundHudManager.removePlayer(player);
    }

    public void addDefaultSidebarPlayer(ServerPlayerEntity player) {
        if (DEFAULT_SIDEBAR == null) {
            return;
        }

        DEFAULT_SIDEBAR.addPlayer(player.networkHandler);
    }

    public void removeDefaultSidebarPlayer(ServerPlayerEntity player) {
        if (DEFAULT_SIDEBAR == null) {
            return;
        }

        DEFAULT_SIDEBAR.removePlayer(player.networkHandler);
    }

    public void tickRoundHud() {
        if (server == null) {
            return;
        }

        this.roundHudManager.tick(server, this);
    }

    public void tickGuideHints() {
        List<Config.RotatingGuideTip> rotatingGuideTips = Config.getInstance().rotatingGuideTips;
        if (server == null || rotatingGuideTips.isEmpty()) {
            return;
        }

        if (server.getTicks() % GUIDE_TIP_INTERVAL_TICKS != 0) {
            return;
        }

        if (this.rotatingGuideIndex >= rotatingGuideTips.size()) {
            this.rotatingGuideIndex = 0;
        }

        Config.RotatingGuideTip tip = rotatingGuideTips.get(this.rotatingGuideIndex);
        this.rotatingGuideIndex = (this.rotatingGuideIndex + 1) % rotatingGuideTips.size();
        broadcastGuideTip(tip.title(), tip.message());
    }

    public void tickLobbyBgm() {
        if (server == null) {
            return;
        }

        Phase phase = this.currentPhase.get();
        if (!shouldTickLobbyBgm(phase)) {
            this.lobbyBgmCooldownTicks = LOBBY_BGM_INTERVAL_TICKS;
            return;
        }

        if (this.lobbyBgmCooldownTicks > 0) {
            this.lobbyBgmCooldownTicks--;
            return;
        }

        playRandomLobbyBgm(server);
        resetLobbyBgmCooldown();
    }

    private void scheduleRoundStartBgm() {
        Scheduler.INSTANCE.submit(scheduledServer -> {
            if (!this.currentPhase.get().isInProgress()) {
                return;
            }
            playRandomLobbyBgm(scheduledServer);
            resetLobbyBgmCooldown();
        }, ROUND_START_BGM_DELAY_TICKS);
    }

    private void resetLobbyBgmCooldown() {
        this.lobbyBgmCooldownTicks = LOBBY_BGM_INTERVAL_TICKS;
    }

    private void playRandomLobbyBgm(MinecraftServer currentServer) {
        SoundEvent bgm = selectLobbyMorningBgm(this.rand.get().nextInt(LOBBY_MORNING_BGM.size()));
        for (ServerPlayerEntity player : currentServer.getPlayerManager().getPlayerList()) {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            if (!shouldPlayLobbyBgmForPlayer(info.tts$bgmEnabled())) {
                continue;
            }
            player.playSoundToPlayer(bgm, SoundCategory.MUSIC, 1557.0f, 1.0f);
        }
    }

    static boolean shouldTickLobbyBgm(Phase phase) {
        return phase == Phase.NOT_STARTED || phase == Phase.MIDDLE_GAME;
    }

    static SoundEvent selectLobbyMorningBgm(int index) {
        int normalizedIndex = Math.floorMod(index, LOBBY_MORNING_BGM.size());
        return LOBBY_MORNING_BGM.get(normalizedIndex);
    }

    static boolean shouldPlayLobbyBgmForPlayer(boolean bgmEnabled) {
        return bgmEnabled;
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

    public int getAliveParticipantCount() {
        return this.gameInstanceManager.getAliveParticipantCount();
    }

    public int getConfirmedRemainingParticipantCount() {
        return this.gameInstanceManager.getConfirmedRemainingParticipantCount();
    }

    public int getRoundElapsedSeconds() {
        return this.gameInstanceManager.getElapsedTicks() / 20;
    }

    /**
     * Whether the recipient should see traitor fake-team/glow reveal packets during combat phases.
     */
    public boolean canReceiveTraitorRevealPackets(ServerPlayerEntity player) {
        return this.gameInstanceManager.canReceiveTraitorRevealPackets(player);
    }

    /**
     * Whether the target should be forced glowing for this recipient under traitor reveal rules.
     */
    public boolean shouldForceTraitorRevealGlow(ServerPlayerEntity recipient, ServerPlayerEntity target) {
        return this.gameInstanceManager.shouldForceTraitorRevealGlow(recipient, target);
    }

    Set<UUID> getPlayers() {
        return this.gameInstanceManager.getParticipants();
    }

    public @Nullable ServerPlayerEntity getPlayer(UUID uuid) {
        return server.getPlayerManager().getPlayer(uuid);
    }

    public void onPlayerDamaged(@Nullable ServerPlayerEntity attacker, ServerPlayerEntity victim, float amount) {
        this.gameDataManager.recordDamageData(attacker, victim, amount);
        this.gameInstanceManager.onPlayerDamaged(attacker, victim, amount);
    }

    public void recordAccuseResult(ServerPlayerEntity sender, ServerPlayerEntity target) {
        this.gameDataManager.recordAccuseResult(sender, target);
    }

    /**
     * Returns the current round accusation log and per-target aggregation for dialog rendering.
     */
    public @NotNull RoundAccusationSnapshot getRoundAccusationSnapshot() {
        return this.gameDataManager.getRoundAccusationSnapshot();
    }

    /**
     * Builds a snapshot of lifetime player stats using persisted per-round logs and player data.
     */
    public @NotNull PlayerStatisticsSnapshot getPlayerStatisticsSnapshot(UUID playerUuid) {
        PlayerDataInstance data = this.gameDataManager.getData(playerUuid);
        if (data == null) {
            throw new IllegalStateException("Player data not loaded for uuid: " + playerUuid);
        }

        GameDataManager.CombatStatsSnapshot combatStats = this.gameDataManager.getCombatStatsSnapshot(playerUuid);
        RoleRoundStats roleRoundStats = summarizeRoleRounds(data);
        PlayerDataInstance.AccuseStats accuseStats = data.getAccuseStats();
        return new PlayerStatisticsSnapshot(
                combatStats.kills(),
                combatStats.deaths(),
                combatStats.teamKills(),
                accuseStats.attempts(),
                accuseStats.hits(),
                roleRoundStats.playCount(),
                roleRoundStats.innocentCount(),
                roleRoundStats.traitorCount(),
                roleRoundStats.detectiveCount()
        );
    }

    /**
     * Convenience overload for {@link #getPlayerStatisticsSnapshot(UUID)}.
     */
    public @NotNull PlayerStatisticsSnapshot getPlayerStatisticsSnapshot(ServerPlayerEntity player) {
        return getPlayerStatisticsSnapshot(player.getUuid());
    }

    private static RoleRoundStats summarizeRoleRounds(PlayerDataInstance data) {
        int innocentCount = 0;
        int traitorCount = 0;
        int detectiveCount = 0;

        for (PlayerDataInstance.PlayerGameResult result : data.getResults()) {
            Role role = result.role();
            switch (role) {
                case INNOCENT -> innocentCount++;
                case TRAITOR -> traitorCount++;
                case DETECTIVE -> detectiveCount++;
                case SPECTATOR -> {
                    // Spectator result is not counted as a played role round.
                }
            }
        }

        return new RoleRoundStats(
                innocentCount + traitorCount + detectiveCount,
                innocentCount,
                traitorCount,
                detectiveCount
        );
    }

    public void onKilled(@Nullable ServerPlayerEntity attacker, ServerPlayerEntity victim, DamageSource damageSource) {
        this.gameDataManager.recordKillData(attacker, victim, damageSource);
        this.gameInstanceManager.onPlayerKilled(attacker, victim, damageSource);
        scheduleDeathCombatDialog(victim.getUuid());
        releaseHeldLeashes(victim);

        applyDeathSpectatorState(victim);
    }

    public void onCorpseDiscovered(UUID deadPlayerUuid) {
        this.gameInstanceManager.onCorpseDiscovered(deadPlayerUuid);
    }

    private static void releaseHeldLeashes(ServerPlayerEntity victim) {
        for (Leashable leashable : Leashable.collectLeashablesHeldBy(victim)) {
            leashable.detachLeashWithoutDrop();
        }
    }

    private void scheduleDeathCombatDialog(UUID victimUuid) {
        Scheduler.INSTANCE.submit((s) -> {
            ServerPlayerEntity victim = s.getPlayerManager().getPlayer(victimUuid);
            if (victim == null) {
                return;
            }
            showDeathCombatDialog(victim);
        }, DEATH_COMBAT_LOG_DELAY_TICKS);
    }

    private void showDeathCombatDialog(ServerPlayerEntity victim) {
        UUID victimUuid = victim.getUuid();
        PlayerRoundDataInstance roundData = this.gameDataManager.getRoundData(victimUuid);
        if (roundData == null) {
            LOGGER.warn(
                    "Cannot show death combat dialog for {} ({}): round data is missing.",
                    victim.getGameProfile().getName(),
                    victimUuid
            );
            return;
        }

        List<DeathCombatLogDialog.DamageEntry> damageEntries = this.gameDataManager.getDamageLogData(victimUuid).stream()
                .map(data -> new DeathCombatLogDialog.DamageEntry(
                        data.elapsedSeconds(),
                        data.counterpartName(),
                        data.counterpartUuid(),
                        data.counterpartRole(),
                        data.amount(),
                        data.dealtByRecorder()
                ))
                .toList();
        DeathCombatLogDialog.showKillLog(victim, roundData, damageEntries);
    }

    void onCombatPhaseStarted() {
        if (!this.currentPhase.get().canShowRole()) {
            return;
        }
        if (this.roundReplayEnabled) {
            if (this.currentMap != null && this.currentMap.getWorld() != null) {
                this.roundReplayRecorder.startRoundChunkRecording(
                        server,
                        this.currentMap.getWorld(),
                        this.currentMap.getTemplateBounds(),
                        this.currentMap.getId()
                );
            } else {
                LOGGER.warn("Cannot start chunk replay recording: current round map world is not ready.");
            }
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            giveMissingStarterSpyglass(player);
            sendRoleRevealTitle(player);
            sendRoleGuide(player);
        }
    }

    private static void sendRoleRevealTitle(ServerPlayerEntity player) {
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        Role role = info.tts$getRole();
        if (role == Role.SPECTATOR) {
            return;
        }

        var bundle = new BundleS2CPacket(List.of(
                new TitleS2CPacket(byMiniMessage(resolveRoleRevealTitle(role))),
                new SubtitleS2CPacket(byMiniMessage(resolveRoleRevealObjective(role))),
                new TitleFadeS2CPacket(
                        ROLE_REVEAL_TITLE_FADE_IN_TICKS,
                        ROLE_REVEAL_TITLE_STAY_TICKS,
                        ROLE_REVEAL_TITLE_FADE_OUT_TICKS
                )

        ));
        player.networkHandler.sendPacket(bundle);
    }

    static String resolveRoleRevealTitle(Role role) {
        return "당신은 " + resolveRoleRevealName(role) + " 입니다";
    }

    static String resolveRoleRevealObjective(Role role) {
        return switch (role) {
            case INNOCENT -> "모든 <red>트레이터</red>를 처치하세요!";
            case TRAITOR -> "모든 <green>시민팀</green>을 처치하세요!";
            case DETECTIVE -> "정보를 취합하여 모든 <red>트레이터</red>를 처치하세요!";
            case SPECTATOR -> "";
        };
    }

    private static String resolveRoleRevealName(Role role) {
        return switch (role) {
            case INNOCENT -> "<green>시민</green>";
            case TRAITOR -> "<red>트레이터</red>";
            case DETECTIVE -> "<blue>탐정</blue>";
            case SPECTATOR -> "관전자";
        };
    }

    public void onShopOpened(ServerPlayerEntity player) {
        if (!this.currentPhase.get().canShowRole()) {
            return;
        }

        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        if (!info.tts$tipsEnabled()) {
            return;
        }
        if (!this.shopGuidePlayers.add(player.getUuid())) {
            return;
        }

        sendGuideChat(
                player,
                "SHOP TIP",
                "Spend your points on utility and role-specific tools."
        );
    }

    public void onFirstCorpseDiscovered() {
        if (!this.currentPhase.get().canShowRole() || this.firstCorpseGuideShown) {
            return;
        }

        this.firstCorpseGuideShown = true;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    public void onOvertimeStarted() {
        if (this.overtimeGuideShown) {
            return;
        }

        this.overtimeGuideShown = true;
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
        Set<Identifier> mapIds = new LinkedHashSet<>();
        mapIds.addAll(TroubleInTerroristTownMod.BUILT_IN_MAPS);
        mapIds.addAll(Config.getInstance().additionalMaps);
        mapIds.addAll(scanMapTemplateIds());
        mapIds.remove(LOBBY_MAP_ID);

        return mapIds.stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
    }

    public List<Identifier> getRegisteredRoundMapIds() {
        return this.templates.keySet().stream()
                .filter(identifier -> !LOBBY_MAP_ID.equals(identifier))
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
    }

    public boolean hasRoundMapTemplate(Identifier mapId) {
        return !LOBBY_MAP_ID.equals(mapId) && this.templates.containsKey(mapId);
    }

    List<Identifier> scanMapTemplateIds() {
        if (server == null) {
            return List.of();
        }

        Map<Identifier, Resource> resources = server.getResourceManager().findResources(
                MAP_TEMPLATE_RESOURCE_PATH,
                identifier -> identifier.getPath().endsWith(MAP_TEMPLATE_EXTENSION)
        );

        return resources.keySet().stream()
                .map(GameManager::toMapIdentifier)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
    }

    private static Optional<Identifier> toMapIdentifier(Identifier resourceId) {
        String resourcePath = resourceId.getPath();
        String prefix = MAP_TEMPLATE_RESOURCE_PATH + "/";
        if (!resourcePath.startsWith(prefix) || !resourcePath.endsWith(MAP_TEMPLATE_EXTENSION)) {
            return Optional.empty();
        }

        String mapPath = resourcePath.substring(
                prefix.length(),
                resourcePath.length() - MAP_TEMPLATE_EXTENSION.length()
        );
        if (mapPath.isBlank()) {
            LOGGER.warn("Skipping invalid map template resource '{}': empty map path.", resourceId);
            return Optional.empty();
        }

        Identifier mapId = Identifier.tryParse(resourceId.getNamespace() + ":" + mapPath);
        if (mapId == null) {
            LOGGER.warn("Skipping invalid map template resource '{}': invalid map identifier.", resourceId);
            return Optional.empty();
        }
        return Optional.of(mapId);
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

    private void sendRoleGuide(ServerPlayerEntity player) {
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        if (!info.tts$tipsEnabled()) {
            return;
        }

        Role role = info.tts$getRole();
        switch (role) {
            case INNOCENT -> {
                sendGuideChat(
                        player,
                        "역할: <green>이노센트</green>",
                        "<blue>탐정</blue>을 도와 <red>트레이터</red>를 처치하세요. 탐정을 제외한 모두를 의심하세요."
                );
            }
            case TRAITOR -> {
                sendGuideChat(
                        player,
                        "역할: <red>트레이터</red>",
                        "의심을 피해 <green>이노센트</green>와 </blue>탐정</blue>를 모두 사살하세요."
                );
            }
            case DETECTIVE -> {
                sendGuideChat(
                        player,
                        "역할: <blue>탐정</blue>",
                        "단서를 찾아 <green>이노센트</green>들과 함께 <red>트레이터</red>들을 찾고 제거하세요."
                );
            }
            case SPECTATOR -> {
                // No guide needed.
            }
        }
    }

    private void broadcastGuideTip(String title, String message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            if (!info.tts$tipsEnabled()) {
                continue;
            }
            sendGuideChat(player, title, message);
        }
    }

    private void broadcastGuideTipWithPadding(String title, String message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            if (!info.tts$tipsEnabled()) {
                continue;
            }
            sendGuideChatWithPadding(player, title, message);
        }
    }

    private static void sendGuideChat(ServerPlayerEntity player, String title, String message) {
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedTitle.isEmpty() && normalizedMessage.isEmpty()) {
            return;
        }

        String chatMessage = normalizedTitle.isEmpty()
                ? "<yellow>[TIP]</yellow> " + normalizedMessage
                : "<yellow>[TIP]</yellow> " + normalizedTitle + " - " + normalizedMessage;
//        player.sendMessage(byMiniMessage(chatMessage), false);
    }

    private static void sendGuideChatWithPadding(ServerPlayerEntity player, String title, String message) {
        sendGuideBlankLine(player);
        sendGuideChat(player, title, message);
        sendGuideBlankLine(player);
    }

    private static void sendGuideBlankLine(ServerPlayerEntity player) {
        player.sendMessage(Text.literal(" "), false);
    }

    private void resetRoundGuideState() {
        this.firstCorpseGuideShown = false;
        this.overtimeGuideShown = false;
        this.shopGuidePlayers.clear();
        this.rotatingGuideIndex = 0;
    }

    public record PlayerStatisticsSnapshot(
            int kills,
            int deaths,
            int teamKills,
            int accuseAttempts,
            int accuseHits,
            int playCount,
            int innocentCount,
            int traitorCount,
            int detectiveCount
    ) {
    }

    public record RoundAccusationSnapshot(
            List<RoundAccusationEvent> events,
            List<RoundAccusationTargetSummary> targetSummaries
    ) {
    }

    public record RoundAccusationEvent(
            int elapsedSeconds,
            UUID accuserUuid,
            String accuserName,
            UUID targetUuid,
            String targetName,
            boolean hit
    ) {
    }

    public record RoundAccusationTargetSummary(
            UUID targetUuid,
            String targetName,
            int accusationCount,
            List<RoundAccuserSummary> accusers
    ) {
    }

    public record RoundAccuserSummary(
            UUID accuserUuid,
            String accuserName,
            int accusationCount
    ) {
    }

    private record RoleRoundStats(
            int playCount,
            int innocentCount,
            int traitorCount,
            int detectiveCount
    ) {
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
