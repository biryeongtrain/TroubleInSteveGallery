package kim.biryeong.ttt.game.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import kim.biryeong.ttt.game.data.Date;
import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.game.data.PlayerRoundDataInstance;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@ApiStatus.Internal
final class GameDataManager {
    private static final String PLAYER_DATA_DIRECTORY = "playerData";
    private static final String ROUND_DATA_DIRECTORY = "roundData";
    private static final int TICKS_PER_SECOND = 20;

    private final Logger logger = LoggerFactory.getLogger("TTT_DataManager");
    private final Gson gson = new Gson();
    private final Map<UUID, PlayerDataInstance> playerDataByUuid = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRoundDataInstance> roundDataByUuid = new ConcurrentHashMap<>();
    private final Map<UUID, List<RoundCombatDamageData>> roundDamageDataByUuid = new ConcurrentHashMap<>();
    private final Map<UUID, String> roundPlayerNameByUuid = new ConcurrentHashMap<>();
    private final List<GameManager.RoundAccusationEvent> roundAccusationEvents =
            Collections.synchronizedList(new ArrayList<>());
    private final Path ttsPath;
    private final GameManager gameManager;

    GameDataManager(GameManager gameManager) {
        this.gameManager = gameManager;
        this.ttsPath = FabricLoader.getInstance().getGameDir().resolve("tts");
    }

    boolean tryToLoadPlayerData(UUID uuid) {
        if (!ensureDirectory(this.ttsPath, "base tts data")) {
            return false;
        }

        Path path = getPlayerDataPath(uuid);
        if (!Files.exists(path)) {
            return false;
        }

        try {
            String string = Files.readString(path);
            JsonElement json = this.gson.fromJson(string, JsonElement.class);
            PlayerDataInstance data = PlayerDataInstance.CODEC.decode(JsonOps.INSTANCE, json).getOrThrow().getFirst();
            this.playerDataByUuid.put(uuid, data);
            return true;
        } catch (Exception exception) {
            this.logger.error("Cannot read player data for {} from {}", uuid, path, exception);
            backupCorruptedPlayerData(uuid, path);
            return false;
        }
    }

    void createNewData(UUID uuid) {
        this.playerDataByUuid.put(uuid, PlayerDataInstance.createNew(uuid));
    }

    void saveAll() {
        for (UUID uuid : Set.copyOf(this.playerDataByUuid.keySet())) {
            saveData(uuid, false);
        }
    }

    void saveData(UUID uuid, boolean removeAfterSave) {
        if (!ensureDirectory(this.ttsPath.resolve(PLAYER_DATA_DIRECTORY), "player data")) {
            return;
        }

        PlayerDataInstance data = removeAfterSave
                ? this.playerDataByUuid.remove(uuid)
                : this.playerDataByUuid.get(uuid);

        if (data == null) {
            this.logger.warn("Cannot save player data: no loaded data exists for {}", uuid);
            return;
        }

        String encoded;
        try {
            encoded = PlayerDataInstance.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().toString();
        } catch (Exception exception) {
            this.logger.error("Cannot encode player data for {}", uuid, exception);
            return;
        }

        writeText(getPlayerDataPath(uuid), encoded, "player data", uuid.toString());
    }

    void saveRoundData() {
        if (this.roundDataByUuid.isEmpty()) {
            this.roundDamageDataByUuid.clear();
            this.roundPlayerNameByUuid.clear();
            this.roundAccusationEvents.clear();
            return;
        }

        Date date = Date.fromNow();
        Path roundDirectoryPath = this.ttsPath.resolve(ROUND_DATA_DIRECTORY).resolve(date.toString());
        if (!ensureDirectory(roundDirectoryPath, "round data")) {
            return;
        }

        this.roundDataByUuid.forEach((uuid, roundData) -> {
            String encoded;
            try {
                encoded = PlayerRoundDataInstance.CODEC.encodeStart(JsonOps.INSTANCE, roundData).getOrThrow().toString();
            } catch (Exception exception) {
                this.logger.error("Cannot encode round data for {}", uuid, exception);
                return;
            }
            writeText(getRoundDataPath(date, uuid), encoded, "round data", uuid.toString());
        });

        this.roundDataByUuid.clear();
        this.roundDamageDataByUuid.clear();
        this.roundPlayerNameByUuid.clear();
        this.roundAccusationEvents.clear();
    }

    void startToRecordKillData() {
        var playerManager = GameManager.server.getPlayerManager();
        Set<UUID> participants = Set.copyOf(GameManager.getInstance().getPlayers());
        this.roundDataByUuid.clear();
        this.roundDamageDataByUuid.clear();
        this.roundPlayerNameByUuid.clear();
        this.roundAccusationEvents.clear();

        for (UUID participantUuid : participants) {
            ServerPlayerEntity player = playerManager.getPlayer(participantUuid);
            if (player == null) {
                this.logger.warn(
                        "Cannot initialize round kill tracking for {}: player is not online",
                        participantUuid
                );
                continue;
            }
            this.roundPlayerNameByUuid.put(participantUuid, player.getGameProfile().getName());
            this.roundDataByUuid.put(participantUuid, PlayerRoundDataInstance.create(player));
            this.roundDamageDataByUuid.put(participantUuid, new ArrayList<>());
        }
    }

    void recordDamageData(@Nullable ServerPlayerEntity attacker, ServerPlayerEntity victim, float amount) {
        if (!GameManager.getInstance().isGameStarted()) {
            return;
        }
        if (attacker == null || attacker.getUuid().equals(victim.getUuid()) || amount <= 0.0f) {
            return;
        }

        int elapsedSeconds = this.gameManager.gameInstanceManager.getElapsedTicks() / TICKS_PER_SECOND;
        recordRoundDamageData(attacker.getUuid(), elapsedSeconds, victim, amount, true);
        recordRoundDamageData(victim.getUuid(), elapsedSeconds, attacker, amount, false);
    }

    void recordKillData(@Nullable ServerPlayerEntity killer, ServerPlayerEntity victim, DamageSource damageSource) {
        if (!GameManager.getInstance().isGameStarted()) {
            throw new IllegalStateException("Game is not started.");
        }

        int elapsedSeconds = this.gameManager.gameInstanceManager.getElapsedTicks() / 20;
        if (killer != null) {
            recordRoundKillData(killer.getUuid(), elapsedSeconds, victim, damageSource);
        }
        recordRoundKillData(victim.getUuid(), elapsedSeconds, killer, damageSource);
    }

    void recordAccuseResult(ServerPlayerEntity sender, ServerPlayerEntity target) {
        PlayerDataInstance senderData = this.playerDataByUuid.get(sender.getUuid());
        if (senderData == null) {
            this.logger.warn(
                    "Cannot record accusation for {} ({}): player data is not loaded",
                    sender.getGameProfile().getName(),
                    sender.getUuid()
            );
        } else {
            InGamePlayerInfoProvider targetInfo = (InGamePlayerInfoProvider) target;
            boolean hit = targetInfo.tts$getRole() == Role.TRAITOR;
            senderData.recordAccuseResult(hit);
        }

        recordRoundAccusationData(sender, target);
    }

    GameManager.RoundAccusationSnapshot getRoundAccusationSnapshot() {
        return summarizeRoundAccusations(copyRoundAccusationEvents());
    }

    CombatStatsSnapshot getCombatStatsSnapshot(UUID playerUuid) {
        CombatStatsAccumulator accumulator = new CombatStatsAccumulator();
        accumulatePersistedRoundCombatStats(playerUuid, accumulator);

        PlayerRoundDataInstance inProgressRoundData = this.roundDataByUuid.get(playerUuid);
        if (inProgressRoundData != null) {
            accumulateRoundCombatStats(inProgressRoundData, accumulator);
        }

        return accumulator.snapshot();
    }

    private void accumulatePersistedRoundCombatStats(UUID playerUuid, CombatStatsAccumulator accumulator) {
        Path roundDataRoot = this.ttsPath.resolve(ROUND_DATA_DIRECTORY);
        if (!Files.isDirectory(roundDataRoot)) {
            return;
        }

        try (Stream<Path> roundDirectories = Files.list(roundDataRoot)) {
            roundDirectories
                    .filter(Files::isDirectory)
                    .forEach(roundDirectory -> {
                        Path playerRoundDataPath = roundDirectory.resolve(playerUuid + ".json");
                        PlayerRoundDataInstance roundData = readRoundData(playerRoundDataPath);
                        if (roundData != null) {
                            accumulateRoundCombatStats(roundData, accumulator);
                        }
                    });
        } catch (IOException exception) {
            this.logger.error("Cannot list round data directories from {}", roundDataRoot, exception);
        }
    }

    private @Nullable PlayerRoundDataInstance readRoundData(Path path) {
        if (!Files.exists(path)) {
            return null;
        }

        try {
            String encodedRoundData = Files.readString(path);
            JsonElement json = this.gson.fromJson(encodedRoundData, JsonElement.class);
            return PlayerRoundDataInstance.CODEC.decode(JsonOps.INSTANCE, json).getOrThrow().getFirst();
        } catch (Exception exception) {
            this.logger.error("Cannot read round data from {}", path, exception);
            return null;
        }
    }

    private static void accumulateRoundCombatStats(
            PlayerRoundDataInstance roundData,
            CombatStatsAccumulator accumulator
    ) {
        Role recorderRole = roundData.role();
        for (PlayerRoundDataInstance.RoundKillData killData : roundData.roundKillData()) {
            if (killData.slainByVictim()) {
                accumulator.incrementDeaths();
                continue;
            }

            accumulator.incrementKills();
            if (GameInstanceManager.isFriendlyFire(recorderRole, killData.victimRole())) {
                accumulator.incrementTeamKills();
            }
        }
    }

    static GameManager.RoundAccusationSnapshot summarizeRoundAccusations(
            List<GameManager.RoundAccusationEvent> events
    ) {
        Map<UUID, TargetAccusationAccumulator> targetAccumulators = new LinkedHashMap<>();
        for (GameManager.RoundAccusationEvent event : events) {
            targetAccumulators.computeIfAbsent(
                    event.targetUuid(),
                    ignored -> new TargetAccusationAccumulator(event.targetUuid(), event.targetName())
            ).record(event.accuserUuid(), event.accuserName(), event.targetName());
        }

        List<GameManager.RoundAccusationTargetSummary> targetSummaries = targetAccumulators.values().stream()
                .map(TargetAccusationAccumulator::toSummary)
                .toList();
        return new GameManager.RoundAccusationSnapshot(List.copyOf(events), targetSummaries);
    }

    private void recordRoundKillData(
            UUID recorderUuid,
            int elapsedSeconds,
            @Nullable ServerPlayerEntity counterpartPlayer,
            DamageSource damageSource
    ) {
        PlayerRoundDataInstance roundData = this.roundDataByUuid.get(recorderUuid);
        if (roundData == null) {
            this.logger.warn(
                    "Cannot record kill data for {}: round data is not initialized",
                    recorderUuid
            );
            return;
        }

        roundData.recordKillData(elapsedSeconds, counterpartPlayer, damageSource);
    }

    private void recordRoundAccusationData(ServerPlayerEntity sender, ServerPlayerEntity target) {
        if (!GameManager.getInstance().isGameStarted()) {
            return;
        }

        InGamePlayerInfoProvider targetInfo = (InGamePlayerInfoProvider) target;
        int elapsedSeconds = this.gameManager.gameInstanceManager.getElapsedTicks() / TICKS_PER_SECOND;
        this.roundAccusationEvents.add(new GameManager.RoundAccusationEvent(
                elapsedSeconds,
                sender.getUuid(),
                sender.getGameProfile().getName(),
                target.getUuid(),
                target.getGameProfile().getName(),
                targetInfo.tts$getRole() == Role.TRAITOR
        ));
    }

    private void recordRoundDamageData(
            UUID recorderUuid,
            int elapsedSeconds,
            ServerPlayerEntity counterpartPlayer,
            float amount,
            boolean dealtByRecorder
    ) {
        List<RoundCombatDamageData> damageData = this.roundDamageDataByUuid.get(recorderUuid);
        if (damageData == null) {
            this.logger.warn(
                    "Cannot record damage data for {}: round damage data is not initialized",
                    recorderUuid
            );
            return;
        }

        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) counterpartPlayer;
        damageData.add(new RoundCombatDamageData(
                elapsedSeconds,
                counterpartPlayer.getGameProfile().getName(),
                counterpartPlayer.getUuid(),
                info.tts$getRole(),
                amount,
                dealtByRecorder
        ));
    }

    @Nullable
    PlayerRoundDataInstance getRoundData(UUID uuid) {
        return this.roundDataByUuid.get(uuid);
    }

    List<RoundCombatDamageData> getDamageLogData(UUID uuid) {
        List<RoundCombatDamageData> allDamageData = this.roundDamageDataByUuid.get(uuid);
        if (allDamageData == null || allDamageData.isEmpty()) {
            return List.of();
        }
        return List.copyOf(allDamageData);
    }

    RoundSummarySnapshot getRoundSummarySnapshot() {
        List<RoundSummaryTraitorEntry> traitorEntries = this.roundDataByUuid.entrySet().stream()
                .filter(entry -> entry.getValue().role() == Role.TRAITOR)
                .map(entry -> new RoundSummaryTraitorEntry(
                        entry.getKey(),
                        resolveRoundPlayerName(entry.getKey())
                ))
                .sorted(Comparator.comparing(RoundSummaryTraitorEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<RoundSummaryKillEntry> killEntries = new ArrayList<>();
        this.roundDataByUuid.forEach((recorderUuid, roundData) -> {
            String recorderName = resolveRoundPlayerName(recorderUuid);
            for (PlayerRoundDataInstance.RoundKillData killData : roundData.roundKillData()) {
                if (!killData.slainByVictim()) {
                    killEntries.add(new RoundSummaryKillEntry(
                            killData.elapsedSeconds(),
                            recorderName,
                            roundData.role(),
                            killData.victimName(),
                            killData.victimRole()
                    ));
                    continue;
                }

                if ("Unknown".equalsIgnoreCase(killData.victimName())) {
                    killEntries.add(new RoundSummaryKillEntry(
                            killData.elapsedSeconds(),
                            "Unknown",
                            Role.SPECTATOR,
                            recorderName,
                            roundData.role()
                    ));
                }
            }
        });

        killEntries.sort(
                Comparator.comparingInt(RoundSummaryKillEntry::elapsedSeconds)
                        .thenComparing(RoundSummaryKillEntry::killerName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(RoundSummaryKillEntry::victimName, String.CASE_INSENSITIVE_ORDER)
        );

        return new RoundSummarySnapshot(List.copyOf(traitorEntries), List.copyOf(killEntries));
    }

    private String resolveRoundPlayerName(UUID playerUuid) {
        String recordedName = this.roundPlayerNameByUuid.get(playerUuid);
        if (recordedName != null && !recordedName.isBlank()) {
            return recordedName;
        }

        ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(playerUuid);
        if (player != null) {
            return player.getGameProfile().getName();
        }

        return playerUuid.toString();
    }

    List<String> getDamageLogLines(UUID uuid) {
        List<RoundCombatDamageData> allDamageData = getDamageLogData(uuid);
        if (allDamageData.isEmpty()) {
            return List.of("기록된 플레이어 간 피해가 없습니다.");
        }

        List<String> lines = new ArrayList<>();
        for (RoundCombatDamageData damageData : allDamageData) {
            lines.add(formatDamageLine(damageData));
        }
        return List.copyOf(lines);
    }

    private static String formatDamageLine(RoundCombatDamageData damageData) {
        String direction = damageData.dealtByRecorder() ? "DEALT" : "TAKEN";
        String preposition = damageData.dealtByRecorder() ? "to" : "from";
        String role = damageData.counterpartRole().asString();
        String time = formatElapsedSeconds(damageData.elapsedSeconds());
        String amount = String.format(Locale.ROOT, "%.1f", damageData.amount());
        return "[%s] %s %s %s (%s)".formatted(
                time,
                direction,
                amount,
                preposition + " " + damageData.counterpartName(),
                role
        );
    }

    private static String formatElapsedSeconds(int elapsedSeconds) {
        int clampedSeconds = Math.max(0, elapsedSeconds);
        int minutes = clampedSeconds / 60;
        int seconds = clampedSeconds % 60;
        return "%02d:%02d".formatted(minutes, seconds);
    }

    private List<GameManager.RoundAccusationEvent> copyRoundAccusationEvents() {
        synchronized (this.roundAccusationEvents) {
            return List.copyOf(this.roundAccusationEvents);
        }
    }

    private void backupCorruptedPlayerData(UUID uuid, Path sourcePath) {
        Path backupPath = getPlayerDataBackupPath(uuid);
        try {
            Files.copy(sourcePath, backupPath);
        } catch (IOException exception) {
            this.logger.error("Cannot backup corrupted player data {} to {}", sourcePath, backupPath, exception);
        }
    }

    private boolean ensureDirectory(Path path, String operationTarget) {
        try {
            Files.createDirectories(path);
            return true;
        } catch (IOException exception) {
            this.gameManager.LOGGER.error("Cannot create directory for {}", operationTarget, exception);
            return false;
        }
    }

    private void writeText(Path path, String encodedData, String dataType, String identifier) {
        try {
            Files.writeString(path, encodedData);
        } catch (IOException exception) {
            this.logger.error("Cannot save {} for {}", dataType, identifier, exception);
            this.logger.info("Unpersisted {} payload: {}", dataType, encodedData);
        }
    }

    private Path getPlayerDataPath(UUID uuid) {
        return this.ttsPath.resolve(PLAYER_DATA_DIRECTORY).resolve(uuid + ".json");
    }

    private Path getPlayerDataBackupPath(UUID uuid) {
        return this.ttsPath.resolve(PLAYER_DATA_DIRECTORY).resolve(uuid + "-bak.json");
    }

    private Path getRoundDataPath(Date date, UUID uuid) {
        return this.ttsPath.resolve(ROUND_DATA_DIRECTORY).resolve(date.toString()).resolve(uuid + ".json");
    }

    public @NotNull PlayerDataInstance getData(ServerPlayerEntity player) {
        return Objects.requireNonNull(
                getData(player.getUuid()),
                "Missing player data for " + player.getUuid()
        );
    }

    public @Nullable PlayerDataInstance getData(UUID uuid) {
        return this.playerDataByUuid.get(uuid);
    }

    static record CombatStatsSnapshot(
            int kills,
            int deaths,
            int teamKills
    ) {
    }

    static record RoundCombatDamageData(
            int elapsedSeconds,
            String counterpartName,
            UUID counterpartUuid,
            Role counterpartRole,
            float amount,
            boolean dealtByRecorder
    ) {
    }

    static record RoundSummarySnapshot(
            List<RoundSummaryTraitorEntry> traitorEntries,
            List<RoundSummaryKillEntry> killEntries
    ) {
    }

    static record RoundSummaryTraitorEntry(
            UUID uuid,
            String name
    ) {
    }

    static record RoundSummaryKillEntry(
            int elapsedSeconds,
            String killerName,
            Role killerRole,
            String victimName,
            Role victimRole
    ) {
    }

    private static final class TargetAccusationAccumulator {
        private final UUID targetUuid;
        private String targetName;
        private int accusationCount;
        private final Map<UUID, AccuserAccumulator> accusers = new LinkedHashMap<>();

        private TargetAccusationAccumulator(UUID targetUuid, String targetName) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
        }

        void record(UUID accuserUuid, String accuserName, String latestTargetName) {
            if (latestTargetName != null && !latestTargetName.isBlank()) {
                this.targetName = latestTargetName;
            }

            this.accusationCount++;
            this.accusers.computeIfAbsent(accuserUuid, ignored -> new AccuserAccumulator(accuserUuid, accuserName))
                    .increment(accuserName);
        }

        GameManager.RoundAccusationTargetSummary toSummary() {
            List<GameManager.RoundAccuserSummary> accuserSummaries = this.accusers.values().stream()
                    .map(AccuserAccumulator::toSummary)
                    .toList();
            return new GameManager.RoundAccusationTargetSummary(
                    this.targetUuid,
                    this.targetName,
                    this.accusationCount,
                    accuserSummaries
            );
        }
    }

    private static final class AccuserAccumulator {
        private final UUID accuserUuid;
        private String accuserName;
        private int accusationCount;

        private AccuserAccumulator(UUID accuserUuid, String accuserName) {
            this.accuserUuid = accuserUuid;
            this.accuserName = accuserName;
        }

        void increment(String latestName) {
            if (latestName != null && !latestName.isBlank()) {
                this.accuserName = latestName;
            }
            this.accusationCount++;
        }

        GameManager.RoundAccuserSummary toSummary() {
            return new GameManager.RoundAccuserSummary(
                    this.accuserUuid,
                    this.accuserName,
                    this.accusationCount
            );
        }
    }

    private static final class CombatStatsAccumulator {
        private int kills;
        private int deaths;
        private int teamKills;

        void incrementKills() {
            this.kills++;
        }

        void incrementDeaths() {
            this.deaths++;
        }

        void incrementTeamKills() {
            this.teamKills++;
        }

        CombatStatsSnapshot snapshot() {
            return new CombatStatsSnapshot(this.kills, this.deaths, this.teamKills);
        }
    }
}
