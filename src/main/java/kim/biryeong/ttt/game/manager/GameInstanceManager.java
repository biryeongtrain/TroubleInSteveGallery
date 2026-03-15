package kim.biryeong.ttt.game.manager;

import it.unimi.dsi.fastutil.ints.IntList;
import kim.biryeong.ttt.config.Config;
import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.FakeTeam;
import kim.biryeong.ttt.util.Sounds;
import kim.biryeong.ttt.util.Scheduler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
class GameInstanceManager {
    private static final int TICKS_PER_SECOND = 20;
    private static final int TRAITOR_POSITION_REVEAL_INTERVAL_TICKS = 30 * TICKS_PER_SECOND;
    private static final int TRAITOR_POSITION_REVEAL_DURATION_TICKS = 5 * TICKS_PER_SECOND;
    private static final int FINAL_INNOCENT_GLOW_DURATION_TICKS = Integer.MAX_VALUE;
    private static final int WARMUP_COUNTDOWN_SOUND_START_SECONDS = 5;
    private static final int WARMUP_COUNTDOWN_SOUND_START_TICKS = WARMUP_COUNTDOWN_SOUND_START_SECONDS * TICKS_PER_SECOND;
    private static final int ROUND_LOG_INTERVAL_TICKS = 200;
    private static final int WIN_CHECK_INTERVAL_TICKS = TICKS_PER_SECOND;
    private static final int PLAYED_POINT_INTERVAL_TICKS = 1200;
    private static final int PLAYED_POINT_AMOUNT = 2;
    private static final int KILL_POINT_AMOUNT = 2;

    private static final int DEFAULT_KARMA = 1000;
    private static final int MAX_KARMA = 1000;
    private static final int FRIENDLY_DAMAGE_PENALTY_PER_HEART = 12;
    private static final int FRIENDLY_KILL_PENALTY = 180;
    private static final int CLEAN_KILL_BONUS = 20;
    private static final Text KARMA_DEPLETED_MESSAGE = Text.literal("카르마가 0이 되어 탈락했습니다.");

    private final Logger logger = LoggerFactory.getLogger(GameInstanceManager.class);
    private final Set<UUID> participants = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> corpseEntities = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> aliveParticipants = new HashSet<>();
    private final Set<UUID> fakeTraitorTeamRecipients = new HashSet<>();
    private final Set<UUID> fakeDetectiveTeamRecipients = new HashSet<>();
    private final Set<UUID> fakeHiddenNameTagTeamRecipients = new HashSet<>();
    private final Set<UUID> managedGlowPlayerUuids = new HashSet<>();
    private final Map<UUID, Integer> karma = new HashMap<>();

    private int aliveTraitors = 0;
    private int gamePlayTimeTicks;
    private int overtime = 0;
    private int warmupTimeTick = 0;
    private int maxOverTimeTicks = 0;
    private int overtimePerKill = 0;
    private int elapsedTicks = 0;
    private boolean isOverTime = false;
    private boolean gameEndRequested = false;
    private boolean shouldTick = true;
    private Team fakeTraitorTeam;
    private Team fakeDetectiveTeam;
    private Team fakeHiddenNameTagTeam;
    private boolean traitorPositionRevealActive;
    private boolean finalInnocentGlobalGlowActive;

    public void initialize(List<UUID> participantUuids) {
        deactivateFinalInnocentGlobalGlow();
        this.participants.clear();
        this.participants.addAll(participantUuids);

        this.aliveParticipants.clear();
        this.aliveParticipants.addAll(participantUuids);

        this.karma.clear();
        participantUuids.forEach(uuid -> this.karma.put(uuid, DEFAULT_KARMA));

        this.elapsedTicks = 0;
        this.overtime = 0;
        this.isOverTime = false;
        this.gameEndRequested = false;
        this.shouldTick = true;
        this.fakeTraitorTeamRecipients.clear();
        this.fakeDetectiveTeamRecipients.clear();
        this.fakeHiddenNameTagTeamRecipients.clear();
        this.fakeTraitorTeam = null;
        this.fakeDetectiveTeam = null;
        this.fakeHiddenNameTagTeam = null;
        this.traitorPositionRevealActive = false;
        this.finalInnocentGlobalGlowActive = false;

        Config config = Config.getInstance();
        this.gamePlayTimeTicks = config.playTimeSeconds * TICKS_PER_SECOND;
        this.warmupTimeTick = config.gameStartCountdownSeconds * TICKS_PER_SECOND;
        this.maxOverTimeTicks = config.maxOverTimeSeconds * TICKS_PER_SECOND;
        this.overtimePerKill = config.overTimePerKills * TICKS_PER_SECOND;
    }

    void calculateAliveTraitors() {
        this.aliveTraitors = (int) this.aliveParticipants.stream()
                .filter(this::isTraitor)
                .count();
    }

    private boolean isTraitor(UUID playerUuid) {
        ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(playerUuid);
        if (player == null) {
            return false;
        }
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        return info.tts$getRole() == Role.TRAITOR;
    }

    public int getTimeLeft() {
        return this.gamePlayTimeTicks - this.elapsedTicks;
    }

    public int getOvertime() {
        return this.overtime;
    }

    public int getPostGameWarmupTime() {
        return this.warmupTimeTick;
    }

    int getKarmaPointPenalty(UUID playerUuid) {
        int current = this.karma.getOrDefault(playerUuid, DEFAULT_KARMA);
        return calculateKarmaPointPenalty(current);
    }

    static int calculateKarmaPointPenalty(int currentKarma) {
        int clampedKarma = Math.max(0, Math.min(MAX_KARMA, currentKarma));
        int deficit = DEFAULT_KARMA - clampedKarma;
        if (deficit <= 0) {
            return 0;
        }
        return Math.max(1, deficit / 100);
    }

    static int calculateFriendlyDamageKarmaPenalty(float amount) {
        return Math.max(1, Math.round(amount * FRIENDLY_DAMAGE_PENALTY_PER_HEART));
    }

    static int extendOvertimeTicks(int currentOvertimeTicks, int overtimePerKillTicks, int maxOverTimeTicks) {
        int clampedMax = Math.max(0, maxOverTimeTicks);
        int clampedCurrent = Math.min(clampedMax, Math.max(0, currentOvertimeTicks));
        if (overtimePerKillTicks <= 0) {
            return clampedCurrent;
        }
        return Math.min(clampedMax, clampedCurrent + overtimePerKillTicks);
    }

    public void addParticipants(List<UUID> uuids) {
        this.participants.addAll(uuids);
    }

    public void onGameStopped() {
        deactivateFinalInnocentGlobalGlow();
    }

    void onRoundStarted() {
        refreshHiddenNameTagTeamPackets();
        refreshDetectiveTeamPackets();
        syncFinalInnocentGlobalGlow();
    }

    void onAudienceChanged() {
        refreshHiddenNameTagTeamPackets();
        refreshDetectiveTeamPackets();
        refreshTraitorTeamPackets();
        syncFinalInnocentGlobalGlow();
    }

    boolean canReceiveTraitorRevealPackets(ServerPlayerEntity player) {
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        return shouldReceiveTraitorRevealPackets(this.isAlive(player), info.tts$getRole());
    }

    static boolean shouldReceiveTraitorRevealPackets(boolean alive, Role role) {
        return role == Role.TRAITOR || !alive;
    }

    static boolean shouldEnableFinalInnocentGlobalGlow(
            GameManager.Phase phase,
            int aliveTraitors,
            int aliveInnocents
    ) {
        return phase.canShowRole() && aliveTraitors > 0 && aliveInnocents == 1;
    }

    static boolean shouldEnableTraitorPositionReveal(GameManager.Phase phase, int elapsedCombatTicks) {
        if (!phase.canShowRole() || elapsedCombatTicks < TRAITOR_POSITION_REVEAL_INTERVAL_TICKS) {
            return false;
        }

        int ticksSinceFirstReveal = elapsedCombatTicks - TRAITOR_POSITION_REVEAL_INTERVAL_TICKS;
        int cycleTick = Math.floorMod(ticksSinceFirstReveal, TRAITOR_POSITION_REVEAL_INTERVAL_TICKS);
        return cycleTick < TRAITOR_POSITION_REVEAL_DURATION_TICKS;
    }

    static boolean shouldSendDetectiveTeamPackets(GameManager.Phase phase) {
        return phase.canShowRole();
    }

    static boolean shouldSendHiddenNameTagTeamPackets(GameManager.Phase phase) {
        return phase.isInProgress();
    }

    static boolean shouldHidePlayerNameTag(GameManager.Phase phase, Role role) {
        if (!phase.canShowRole()) {
            return true;
        }
        return role != Role.DETECTIVE;
    }

    void onPlayerDamaged(@Nullable ServerPlayerEntity attacker, ServerPlayerEntity victim, float amount) {
        if (!isValidCombatPair(attacker, victim)) {
            return;
        }

        InGamePlayerInfoProvider attackerInfo = (InGamePlayerInfoProvider) attacker;
        InGamePlayerInfoProvider victimInfo = (InGamePlayerInfoProvider) victim;
        if (!isFriendlyFire(attackerInfo.tts$getRole(), victimInfo.tts$getRole())) {
            return;
        }

        int penalty = calculateFriendlyDamageKarmaPenalty(amount);
        this.changeKarma(attacker.getUuid(), -penalty, "friendly_damage");
    }

    private boolean isValidCombatPair(@Nullable ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        if (attacker == null || attacker.getUuid().equals(victim.getUuid())) {
            return false;
        }
        return this.isAlive(attacker) && this.isAlive(victim);
    }

    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(this.participants);
    }

    void tick() {
        if (!this.shouldTick) {
            return;
        }

        if (tickWarmupPhase()) {
            return;
        }

        syncTraitorPositionReveal();

        logRoundProgressIfNeeded();

        if (shouldCheckWinCondition() && !GameManager.getInstance().debugMode && checkWinConditionAndEndIfNeeded()) {
            return;
        }

        grantPlayedPointsIfNeeded();

        if (tickOvertimeAndEndIfNeeded()) {
            return;
        }

        this.elapsedTicks++;
    }

    private void syncTraitorPositionReveal() {
        boolean shouldEnable = shouldEnableTraitorPositionReveal(
                GameManager.getInstance().getCurrentPhase(),
                this.elapsedTicks
        );
        if (this.traitorPositionRevealActive == shouldEnable) {
            return;
        }

        this.traitorPositionRevealActive = shouldEnable;
        refreshTraitorTeamPackets();
    }

    private boolean tickWarmupPhase() {
        if (GameManager.getInstance().getCurrentPhase() != GameManager.Phase.POST_GAME) {
            return false;
        }

        playWarmupCountdownSoundIfNeeded(this.warmupTimeTick);

        if (this.warmupTimeTick <= 0) {
            GameManager manager = GameManager.getInstance();
            manager.setPhase(GameManager.Phase.MIDDLE_GAME);
            refreshHiddenNameTagTeamPackets();
            refreshDetectiveTeamPackets();
            refreshTraitorTeamPackets();
            manager.onCombatPhaseStarted();
        }
        this.warmupTimeTick--;
        return true;
    }

    private static void playWarmupCountdownSoundIfNeeded(int warmupTicksLeft) {
        if (!shouldPlayWarmupCountdownSound(warmupTicksLeft)) {
            return;
        }

        int secondsLeft = warmupTicksLeft / TICKS_PER_SECOND;
        SoundEvent soundEvent = getWarmupCountdownSound(secondsLeft);
        if (soundEvent == null || GameManager.server == null) {
            return;
        }

        for (ServerPlayerEntity player : GameManager.server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(soundEvent, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    static boolean shouldPlayWarmupCountdownSound(int warmupTicksLeft) {
        return warmupTicksLeft > 0
                && warmupTicksLeft <= WARMUP_COUNTDOWN_SOUND_START_TICKS
                && warmupTicksLeft % TICKS_PER_SECOND == 0;
    }

    static @Nullable SoundEvent getWarmupCountdownSound(int secondsLeft) {
        return switch (secondsLeft) {
            case 5 -> Sounds.COUNTDOWN_5_SEC;
            case 4 -> Sounds.COUNTDOWN_4_SEC;
            case 3 -> Sounds.COUNTDOWN_3_SEC;
            case 2 -> Sounds.COUNTDOWN_2_SEC;
            case 1 -> Sounds.COUNTDOWN_1_SEC;
            default -> null;
        };
    }

    private void logRoundProgressIfNeeded() {
        if (this.elapsedTicks % ROUND_LOG_INTERVAL_TICKS != 0) {
            return;
        }

        this.logger.info(
                "Elapsed seconds: {}, Alive participants: {}/{}",
                this.elapsedTicks / TICKS_PER_SECOND,
                this.aliveParticipants.size(),
                this.participants.size()
        );
    }

    private boolean shouldCheckWinCondition() {
        return this.elapsedTicks % WIN_CHECK_INTERVAL_TICKS == 0;
    }

    private boolean checkWinConditionAndEndIfNeeded() {
        int aliveInnocents = this.aliveParticipants.size() - this.aliveTraitors;
        syncFinalInnocentGlobalGlow(aliveInnocents);
        if (this.aliveTraitors <= 0) {
            GameManager.getInstance().sendMessage("<green>시민 팀 승리!</green>");
            this.requestToWin(PlayerDataInstance.Result.WIN);
            return true;
        }

        if (aliveInnocents <= 0) {
            GameManager.getInstance().sendMessage("<red>배신자 팀 승리!</red>");
            this.requestToWin(PlayerDataInstance.Result.LOSE);
            return true;
        }

        return false;
    }

    private void grantPlayedPointsIfNeeded() {
        if (this.elapsedTicks % PLAYED_POINT_INTERVAL_TICKS != 0) {
            return;
        }

        this.aliveParticipants.stream()
                .filter(uuid -> {
                    ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(uuid);
                    if (player == null) {
                        return true;
                    }

                    InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
                    info.tts$addPoints(PLAYED_POINT_AMOUNT, InGamePlayerInfoProvider.PointReason.PLAYED);
                    return false;
                })
                .toList()
                .forEach(this.aliveParticipants::remove);
    }

    private boolean tickOvertimeAndEndIfNeeded() {
        if (!this.isOverTime && this.elapsedTicks >= this.gamePlayTimeTicks) {
            this.isOverTime = true;
            if (this.overtime <= 0) {
                GameManager.getInstance().sendMessage("<green>시간이 종료되었습니다. 시민 팀 승리!</green>");
                this.requestToWin(PlayerDataInstance.Result.WIN);
                return true;
            }

            GameManager.getInstance().setPhase(GameManager.Phase.OVER_TIME);
            GameManager.getInstance().onOvertimeStarted();
            GameManager.getInstance().sendMessage("<red>오버타임 시작! 배신자가 처치로 추가 시간을 획득했습니다.</red>");
        }

        if (this.isOverTime && this.elapsedTicks >= this.gamePlayTimeTicks + this.overtime) {
            GameManager.getInstance().sendMessage("<green>오버타임 종료. 시민 팀 승리!</green>");
            this.requestToWin(PlayerDataInstance.Result.WIN);
            return true;
        }

        return false;
    }

    private void requestToWin(PlayerDataInstance.Result result) {
        if (this.gameEndRequested) {
            return;
        }
        this.gameEndRequested = true;

        spawnWinFireworks(result);
        this.shouldTick = false;
        Scheduler.INSTANCE.submit((s) -> {
            GameManager.getInstance().stopGame(result);
        }, 100);
    }

    private void spawnWinFireworks(PlayerDataInstance.Result result) {
        MinecraftServer server = GameManager.server;
        for (UUID uuid : this.aliveParticipants) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            if (!shouldSpawnFirework(player, result)) {
                continue;
            }

            int color = result == PlayerDataInstance.Result.WIN
                    ? Role.INNOCENT.getHexAsInt()
                    : Role.TRAITOR.getHexAsInt();
            FireworkRocketEntity firework = createFirework(player, color);
            player.getWorld().spawnEntity(firework);
        }
    }

    private boolean shouldSpawnFirework(ServerPlayerEntity player, PlayerDataInstance.Result result) {
        return (result == PlayerDataInstance.Result.WIN) ^ this.isInnocent(player);
    }

    private FireworkRocketEntity createFirework(ServerPlayerEntity player, int color) {
        var fireworkStack = Items.FIREWORK_ROCKET.getDefaultStack();
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
                        ))
                )
        );

        var pos = player.getPos();
        return new FireworkRocketEntity(player.getWorld(), pos.x, pos.y, pos.z, fireworkStack);
    }

    public boolean isInnocent(ServerPlayerEntity player) {
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        return info.tts$getRole() != Role.TRAITOR && info.tts$getRole() != Role.SPECTATOR;
    }

    public boolean isAlive(ServerPlayerEntity player) {
        return this.aliveParticipants.contains(player.getUuid());
    }

    public int getAliveParticipantCount() {
        return this.aliveParticipants.size();
    }

    public int getElapsedTicks() {
        return this.elapsedTicks;
    }

    public void clear() {
        this.traitorPositionRevealActive = false;
        deactivateFinalInnocentGlobalGlow();
        clearHiddenNameTagTeamPackets();
        clearTraitorTeamPackets();
        clearDetectiveTeamPackets();

        this.participants.clear();
        this.elapsedTicks = 0;
        this.overtime = 0;
        this.isOverTime = false;
        this.gameEndRequested = false;
        this.shouldTick = true;

        removeCorpseEntities();

        this.karma.clear();
        this.aliveTraitors = 0;
        this.aliveParticipants.clear();
    }

    private void removeCorpseEntities() {
        for (UUID corpseUuid : this.corpseEntities) {
            var server = GameManager.server;
            AtomicBoolean isRemoved = new AtomicBoolean(false);
            server.getWorlds().forEach(world -> {
                if (isRemoved.get()) {
                    return;
                }

                Entity entity = world.getEntity(corpseUuid);
                if (entity == null) {
                    return;
                }

                entity.remove(Entity.RemovalReason.DISCARDED);
                isRemoved.set(true);
            });
        }

        this.corpseEntities.clear();
    }

    public void onPlayerLeft(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        InGamePlayerInfoProvider playerInfo = (InGamePlayerInfoProvider) player;
        this.logger.info("Player {} ({}) has left the game.", player.getGameProfile().getName(), playerInfo.tts$getRole());

        this.participants.remove(playerUuid);
        this.karma.remove(playerUuid);
        this.fakeTraitorTeamRecipients.remove(playerUuid);
        this.fakeDetectiveTeamRecipients.remove(playerUuid);
        this.fakeHiddenNameTagTeamRecipients.remove(playerUuid);

        removeAliveParticipant(playerUuid, playerInfo.tts$getRole());
        syncFinalInnocentGlobalGlow();

        refreshHiddenNameTagTeamPackets();
        refreshDetectiveTeamPackets();
        refreshTraitorTeamPackets();
    }

    public void onPlayerKilled(@Nullable ServerPlayerEntity attacker, ServerPlayerEntity victim, DamageSource source) {
        UUID victimUuid = victim.getUuid();
        InGamePlayerInfoProvider victimInfo = (InGamePlayerInfoProvider) victim;

        if (attacker != null) {
            handleKillWithAttacker(attacker, victim, victimInfo, source);
        } else {
            this.logger.info(
                    "Player {} ({}) was killed. (Source: {})",
                    victim.getGameProfile().getName(),
                    victimInfo.tts$getRole(),
                    source.getName()
            );
        }

        removeAliveParticipant(victimUuid, victimInfo.tts$getRole());
        syncFinalInnocentGlobalGlow();

        refreshHiddenNameTagTeamPackets();
        refreshDetectiveTeamPackets();
        refreshTraitorTeamPackets();
    }

    private void handleKillWithAttacker(
            ServerPlayerEntity attacker,
            ServerPlayerEntity victim,
            InGamePlayerInfoProvider victimInfo,
            DamageSource source
    ) {
        InGamePlayerInfoProvider attackerInfo = (InGamePlayerInfoProvider) attacker;
        Role attackerRole = attackerInfo.tts$getRole();
        Role victimRole = victimInfo.tts$getRole();

        this.logger.info(
                "Player {} ({}) killed Player {} ({}). (Source: {})",
                attacker.getGameProfile().getName(),
                attackerRole,
                victim.getGameProfile().getName(),
                victimRole,
                source.getName()
        );

        boolean friendlyKill = isFriendlyFire(attackerRole, victimRole);
        applyKillRewards(attacker, attackerInfo, friendlyKill);
        extendOvertimeForTraitorKill(attackerRole, victimRole);
    }

    private void applyKillRewards(
            ServerPlayerEntity attacker,
            InGamePlayerInfoProvider attackerInfo,
            boolean friendlyKill
    ) {
        if (friendlyKill) {
            this.changeKarma(attacker.getUuid(), -FRIENDLY_KILL_PENALTY, "friendly_kill");
            return;
        }

        attackerInfo.tts$addPoints(KILL_POINT_AMOUNT, InGamePlayerInfoProvider.PointReason.KILL);
        this.changeKarma(attacker.getUuid(), CLEAN_KILL_BONUS, "clean_kill");
    }

    private void extendOvertimeForTraitorKill(Role attackerRole, Role victimRole) {
        boolean traitorKilledNonTraitor = attackerRole == Role.TRAITOR && victimRole != Role.TRAITOR;
        if (traitorKilledNonTraitor && this.overtimePerKill > 0) {
            this.overtime = extendOvertimeTicks(this.overtime, this.overtimePerKill, this.maxOverTimeTicks);
        }
    }

    private void removeAliveParticipant(UUID participantUuid, Role role) {
        boolean wasAlive = this.aliveParticipants.remove(participantUuid);
        if (wasAlive && role == Role.TRAITOR) {
            this.aliveTraitors = Math.max(0, this.aliveTraitors - 1);
        }
    }

    private void syncFinalInnocentGlobalGlow() {
        syncFinalInnocentGlobalGlow(this.aliveParticipants.size() - this.aliveTraitors);
    }

    private void syncFinalInnocentGlobalGlow(int aliveInnocents) {
        boolean shouldEnable = shouldEnableFinalInnocentGlobalGlow(
                GameManager.getInstance().getCurrentPhase(),
                this.aliveTraitors,
                aliveInnocents
        );
        if (shouldEnable) {
            this.finalInnocentGlobalGlowActive = true;
            applyManagedGlowToAllPlayers();
            return;
        }

        deactivateFinalInnocentGlobalGlow();
    }

    private void applyManagedGlowToAllPlayers() {
        if (GameManager.server == null) {
            return;
        }
        for (ServerPlayerEntity player : GameManager.server.getPlayerManager().getPlayerList()) {
            UUID playerUuid = player.getUuid();
            boolean hasManagedGlow = this.managedGlowPlayerUuids.contains(playerUuid);
            boolean currentlyGlowing = player.hasStatusEffect(StatusEffects.GLOWING);
            if (hasManagedGlow && currentlyGlowing) {
                continue;
            }

            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.GLOWING,
                    FINAL_INNOCENT_GLOW_DURATION_TICKS,
                    0,
                    false,
                    false,
                    false
            ));
            this.managedGlowPlayerUuids.add(playerUuid);
        }
    }

    private void deactivateFinalInnocentGlobalGlow() {
        this.finalInnocentGlobalGlowActive = false;
        clearManagedGlowFromOnlinePlayers();
    }

    private void clearManagedGlowFromOnlinePlayers() {
        if (GameManager.server == null || this.managedGlowPlayerUuids.isEmpty()) {
            return;
        }

        Iterator<UUID> iterator = this.managedGlowPlayerUuids.iterator();
        while (iterator.hasNext()) {
            UUID playerUuid = iterator.next();
            ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(playerUuid);
            if (player == null) {
                continue;
            }

            player.removeStatusEffect(StatusEffects.GLOWING);
            iterator.remove();
        }
    }

    private void changeKarma(UUID playerUuid, int delta, String reason) {
        int current = this.karma.getOrDefault(playerUuid, DEFAULT_KARMA);
        int next = Math.max(0, Math.min(MAX_KARMA, current + delta));
        if (next == current) {
            return;
        }

        this.karma.put(playerUuid, next);
        if (delta < 0) {
            this.logger.info("Karma decreased for {} by {} ({}) => {}", playerUuid, -delta, reason, next);
        } else {
            this.logger.info("Karma increased for {} by {} ({}) => {}", playerUuid, delta, reason, next);
        }

        if (shouldEliminateForKarma(current, next)) {
            eliminateForKarma(playerUuid, reason);
        }
    }

    static boolean shouldEliminateForKarma(int currentKarma, int nextKarma) {
        return currentKarma > 0 && nextKarma <= 0;
    }

    private void eliminateForKarma(UUID playerUuid, String reason) {
        if (!GameManager.getInstance().getCurrentPhase().isInProgress()) {
            return;
        }

        ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(playerUuid);
        if (player == null || !this.isAlive(player)) {
            return;
        }

        this.logger.info("Eliminating player {} for depleted karma. (reason={})", playerUuid, reason);
        player.sendMessage(KARMA_DEPLETED_MESSAGE, false);
        GameManager.getInstance().sendMessage(
                "<red>%s님은 카르마 고갈로 탈락했습니다.</red>".formatted(player.getGameProfile().getName())
        );
        GameManager.getInstance().onKilled(null, player, player.getDamageSources().generic());
    }

    static boolean isFriendlyFire(Role attackerRole, Role victimRole) {
        if (attackerRole == Role.SPECTATOR || victimRole == Role.SPECTATOR) {
            return false;
        }
        if (attackerRole == Role.TRAITOR) {
            return victimRole == Role.TRAITOR;
        }
        return victimRole != Role.TRAITOR;
    }

    private Team getOrCreateFakeTraitorTeam() {
        if (this.fakeTraitorTeam != null) {
            return this.fakeTraitorTeam;
        }

        Team team = new Team(GameManager.server.getScoreboard(), FakeTeam.TRAITOR_TEAM_NAME);
        team.setColor(Formatting.RED);
        team.setShowFriendlyInvisibles(true);
        team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
        this.fakeTraitorTeam = team;
        return team;
    }

    private Team getOrCreateFakeDetectiveTeam() {
        if (this.fakeDetectiveTeam != null) {
            return this.fakeDetectiveTeam;
        }

        Team team = new Team(GameManager.server.getScoreboard(), FakeTeam.DETECTIVE_TEAM_NAME);
        team.setColor(Formatting.BLUE);
        team.setShowFriendlyInvisibles(true);
        team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.ALWAYS);
        this.fakeDetectiveTeam = team;
        return team;
    }

    private Team getOrCreateFakeHiddenNameTagTeam() {
        if (this.fakeHiddenNameTagTeam != null) {
            return this.fakeHiddenNameTagTeam;
        }

        Team team = new Team(GameManager.server.getScoreboard(), FakeTeam.HIDDEN_NAMETAG_TEAM_NAME);
        team.setColor(Formatting.WHITE);
        team.setShowFriendlyInvisibles(true);
        team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
        this.fakeHiddenNameTagTeam = team;
        return team;
    }

    private void refreshHiddenNameTagTeamPackets() {
        if (!shouldSendHiddenNameTagTeamPackets(GameManager.getInstance().getCurrentPhase())) {
            clearHiddenNameTagTeamPackets();
            return;
        }

        Team team = getOrCreateFakeHiddenNameTagTeam();
        updateHiddenNameTagTeamMembers(team);

        Set<UUID> currentRecipients = GameManager.server.getPlayerManager().getPlayerList().stream()
                .map(ServerPlayerEntity::getUuid)
                .collect(Collectors.toSet());

        for (UUID previousRecipient : Set.copyOf(this.fakeHiddenNameTagTeamRecipients)) {
            if (currentRecipients.contains(previousRecipient)) {
                continue;
            }

            ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(previousRecipient);
            if (player != null) {
                player.networkHandler.sendPacket(TeamS2CPacket.updateRemovedTeam(team));
            }
        }

        for (UUID currentRecipient : currentRecipients) {
            ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(currentRecipient);
            if (player != null) {
                player.networkHandler.sendPacket(TeamS2CPacket.updateTeam(team, true));
            }
        }

        this.fakeHiddenNameTagTeamRecipients.clear();
        this.fakeHiddenNameTagTeamRecipients.addAll(currentRecipients);
    }

    private void refreshTraitorTeamPackets() {
        if (!GameManager.getInstance().getCurrentPhase().canShowRole()) {
            clearTraitorTeamPackets();
            return;
        }

        Team team = getOrCreateFakeTraitorTeam();
        updateFakeTraitorTeamMembers(team);

        Set<UUID> currentRecipients = GameManager.server.getPlayerManager().getPlayerList().stream()
                .filter(this::canReceiveTraitorRevealPackets)
                .map(ServerPlayerEntity::getUuid)
                .collect(Collectors.toSet());

        for (UUID previousRecipient : Set.copyOf(this.fakeTraitorTeamRecipients)) {
            if (currentRecipients.contains(previousRecipient)) {
                continue;
            }

            ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(previousRecipient);
            if (player != null) {
                player.networkHandler.sendPacket(TeamS2CPacket.updateRemovedTeam(team));
            }
        }

        for (UUID currentRecipient : currentRecipients) {
            ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(currentRecipient);
            if (player == null) {
                continue;
            }
            player.networkHandler.sendPacket(TeamS2CPacket.updateTeam(team, true));
            sendForcedGlowStatePackets(player);
        }

        this.fakeTraitorTeamRecipients.clear();
        this.fakeTraitorTeamRecipients.addAll(currentRecipients);
    }

    private void refreshDetectiveTeamPackets() {
        if (!shouldSendDetectiveTeamPackets(GameManager.getInstance().getCurrentPhase())) {
            clearDetectiveTeamPackets();
            return;
        }

        Team team = getOrCreateFakeDetectiveTeam();
        updateFakeDetectiveTeamMembers(team);

        Set<UUID> currentRecipients = GameManager.server.getPlayerManager().getPlayerList().stream()
                .map(ServerPlayerEntity::getUuid)
                .collect(Collectors.toSet());

        for (UUID previousRecipient : Set.copyOf(this.fakeDetectiveTeamRecipients)) {
            if (currentRecipients.contains(previousRecipient)) {
                continue;
            }

            ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(previousRecipient);
            if (player != null) {
                player.networkHandler.sendPacket(TeamS2CPacket.updateRemovedTeam(team));
            }
        }

        for (UUID currentRecipient : currentRecipients) {
            ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(currentRecipient);
            if (player != null) {
                player.networkHandler.sendPacket(TeamS2CPacket.updateTeam(team, true));
            }
        }

        this.fakeDetectiveTeamRecipients.clear();
        this.fakeDetectiveTeamRecipients.addAll(currentRecipients);
    }

    private void sendForcedGlowStatePackets(ServerPlayerEntity recipient) {
        for (UUID participantUuid : this.aliveParticipants) {
            ServerPlayerEntity target = GameManager.server.getPlayerManager().getPlayer(participantUuid);
            if (target == null) {
                continue;
            }

            boolean forceGlow = shouldForceTraitorRevealGlow(recipient, target);
            byte flags = getEntityFlags(target, forceGlow);
            EntityTrackerUpdateS2CPacket packet = new EntityTrackerUpdateS2CPacket(
                    target.getId(),
                    List.of(new DataTracker.SerializedEntry<>(0, TrackedDataHandlerRegistry.BYTE, flags))
            );
            recipient.networkHandler.sendPacket(packet);
        }
    }

    boolean shouldForceTraitorRevealGlow(ServerPlayerEntity recipient, ServerPlayerEntity target) {
        if (!this.aliveParticipants.contains(target.getUuid())) {
            return false;
        }

        Role targetRole = ((InGamePlayerInfoProvider) target).tts$getRole();
        if (targetRole == Role.TRAITOR) {
            return canReceiveTraitorRevealPackets(recipient);
        }

        if (!this.traitorPositionRevealActive || !this.isAlive(recipient)) {
            return false;
        }

        InGamePlayerInfoProvider recipientInfo = (InGamePlayerInfoProvider) recipient;
        return recipientInfo.tts$getRole() == Role.TRAITOR;
    }

    private static byte getEntityFlags(Entity entity, boolean forceGlow) {
        byte flags = 0;
        if (entity.isOnFire()) {
            flags |= 0x01;
        }
        if (entity.isSneaking()) {
            flags |= 0x02;
        }
        if (entity.isSprinting()) {
            flags |= 0x08;
        }
        if (entity.isSwimming()) {
            flags |= 0x10;
        }
        if (entity.isInvisible()) {
            flags |= 0x20;
        }
        if (entity.isGlowing() || forceGlow) {
            flags |= 0x40;
        }
        return flags;
    }

    private void updateFakeTraitorTeamMembers(Team team) {
        team.getPlayerList().clear();
        this.aliveParticipants.stream()
                .map(GameManager.server.getPlayerManager()::getPlayer)
                .filter(Objects::nonNull)
                .filter(player -> ((InGamePlayerInfoProvider) player).tts$getRole() == Role.TRAITOR)
                .map(ServerPlayerEntity::getNameForScoreboard)
                .forEach(team.getPlayerList()::add);
    }

    private void updateFakeDetectiveTeamMembers(Team team) {
        team.getPlayerList().clear();
        this.aliveParticipants.stream()
                .map(GameManager.server.getPlayerManager()::getPlayer)
                .filter(Objects::nonNull)
                .filter(player -> ((InGamePlayerInfoProvider) player).tts$getRole() == Role.DETECTIVE)
                .map(ServerPlayerEntity::getNameForScoreboard)
                .forEach(team.getPlayerList()::add);
    }

    private void updateHiddenNameTagTeamMembers(Team team) {
        GameManager.Phase phase = GameManager.getInstance().getCurrentPhase();
        team.getPlayerList().clear();
        this.participants.stream()
                .map(GameManager.server.getPlayerManager()::getPlayer)
                .filter(Objects::nonNull)
                .filter(player -> shouldHidePlayerNameTag(phase, ((InGamePlayerInfoProvider) player).tts$getRole()))
                .map(ServerPlayerEntity::getNameForScoreboard)
                .forEach(team.getPlayerList()::add);
    }

    private void clearHiddenNameTagTeamPackets() {
        if (this.fakeHiddenNameTagTeam == null) {
            this.fakeHiddenNameTagTeamRecipients.clear();
            return;
        }

        TeamS2CPacket removePacket = TeamS2CPacket.updateRemovedTeam(this.fakeHiddenNameTagTeam);
        for (UUID recipient : this.fakeHiddenNameTagTeamRecipients) {
            ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(recipient);
            if (player != null) {
                player.networkHandler.sendPacket(removePacket);
            }
        }

        this.fakeHiddenNameTagTeamRecipients.clear();
        this.fakeHiddenNameTagTeam = null;
    }

    private void clearTraitorTeamPackets() {
        if (this.fakeTraitorTeam == null) {
            this.fakeTraitorTeamRecipients.clear();
            return;
        }

        TeamS2CPacket removePacket = TeamS2CPacket.updateRemovedTeam(this.fakeTraitorTeam);
        for (UUID recipient : this.fakeTraitorTeamRecipients) {
            ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(recipient);
            if (player != null) {
                player.networkHandler.sendPacket(removePacket);
            }
        }

        this.fakeTraitorTeamRecipients.clear();
        this.fakeTraitorTeam = null;
    }

    private void clearDetectiveTeamPackets() {
        if (this.fakeDetectiveTeam == null) {
            this.fakeDetectiveTeamRecipients.clear();
            return;
        }

        TeamS2CPacket removePacket = TeamS2CPacket.updateRemovedTeam(this.fakeDetectiveTeam);
        for (UUID recipient : this.fakeDetectiveTeamRecipients) {
            ServerPlayerEntity player = GameManager.server.getPlayerManager().getPlayer(recipient);
            if (player != null) {
                player.networkHandler.sendPacket(removePacket);
            }
        }

        this.fakeDetectiveTeamRecipients.clear();
        this.fakeDetectiveTeam = null;
    }
}
