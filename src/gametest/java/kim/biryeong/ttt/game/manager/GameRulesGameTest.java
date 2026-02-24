package kim.biryeong.ttt.game.manager;

import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.dialog.log.RoundSummaryDialog;
import kim.biryeong.ttt.util.Sounds;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GameRulesGameTest {
    @GameTest
    public void roleDistributionMatchesExpectedRules(TestContext context) {
        context.assertEquals(0, GameManager.calculateDetectiveCount(0), Text.literal("detectives for 0 players"));
        context.assertEquals(0, GameManager.calculateDetectiveCount(7), Text.literal("detectives for 7 players"));
        context.assertEquals(1, GameManager.calculateDetectiveCount(8), Text.literal("detectives for 8 players"));
        context.assertEquals(2, GameManager.calculateDetectiveCount(16), Text.literal("detectives for 16 players"));

        context.assertEquals(0, GameManager.calculateTraitorCount(0), Text.literal("traitors for 0 players"));
        context.assertEquals(1, GameManager.calculateTraitorCount(4), Text.literal("traitors for 4 players"));
        context.assertEquals(2, GameManager.calculateTraitorCount(8), Text.literal("traitors for 8 players"));
        context.assertEquals(3, GameManager.calculateTraitorCount(12), Text.literal("traitors for 12 players"));
        context.complete();
    }

    @GameTest
    public void entropyCandidateSelectionCollectsHighestEntropyCandidates(TestContext context) {
        UUID lowEntropy = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID highEntropyA = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID highEntropyB = UUID.fromString("33333333-3333-3333-3333-333333333333");
        List<UUID> candidates = List.of(lowEntropy, highEntropyA, highEntropyB);
        Map<UUID, Integer> entropyByUuid = Map.of(
                lowEntropy, 120,
                highEntropyA, 180,
                highEntropyB, 180
        );

        List<UUID> highestCandidates = GameManager.collectHighestEntropyCandidates(
                candidates,
                entropyByUuid,
                100
        );
        context.assertEquals(2, highestCandidates.size(), Text.literal("two highest entropy candidates should be returned"));
        context.assertTrue(highestCandidates.contains(highEntropyA), Text.literal("first highest candidate should be included"));
        context.assertTrue(highestCandidates.contains(highEntropyB), Text.literal("second highest candidate should be included"));
        context.assertFalse(highestCandidates.contains(lowEntropy), Text.literal("lower entropy candidate should be excluded"));
        context.complete();
    }

    @GameTest
    public void roundEntropyGainStaysWithinConfiguredBounds(TestContext context) {
        Xoroshiro128PlusPlusRandom random = new Xoroshiro128PlusPlusRandom(12345L);
        for (int i = 0; i < 64; i++) {
            int gain = GameManager.calculateRoundEntropyGain(random, 10, 35);
            context.assertTrue(gain >= 10 && gain <= 35, Text.literal("entropy gain should stay in configured range"));
        }

        int swappedBoundsGain = GameManager.calculateRoundEntropyGain(random, 35, 10);
        context.assertTrue(swappedBoundsGain >= 10 && swappedBoundsGain <= 35, Text.literal("swapped min/max should be normalized"));
        context.assertEquals(0, GameManager.calculateRoundEntropyGain(random, -5, 0), Text.literal("negative min should clamp to zero"));
        context.complete();
    }

    @GameTest
    public void entropyCandidateSelectionUsesDefaultForMissingEntropyEntries(TestContext context) {
        UUID candidateA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID candidateB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID candidateC = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        List<UUID> highestCandidates = GameManager.collectHighestEntropyCandidates(
                List.of(candidateA, candidateB, candidateC),
                Map.of(
                        candidateA, 80,
                        candidateB, 120
                ),
                100
        );

        context.assertEquals(1, highestCandidates.size(), Text.literal("highest list should contain one candidate"));
        context.assertTrue(highestCandidates.contains(candidateB), Text.literal("explicit highest entropy candidate should win"));
        context.complete();
    }

    @GameTest
    public void friendlyFireClassificationMatchesRoles(TestContext context) {
        context.assertTrue(GameInstanceManager.isFriendlyFire(Role.INNOCENT, Role.DETECTIVE), Text.literal("innocent -> detective is friendly fire"));
        context.assertTrue(GameInstanceManager.isFriendlyFire(Role.DETECTIVE, Role.INNOCENT), Text.literal("detective -> innocent is friendly fire"));
        context.assertTrue(GameInstanceManager.isFriendlyFire(Role.TRAITOR, Role.TRAITOR), Text.literal("traitor -> traitor is friendly fire"));

        context.assertFalse(GameInstanceManager.isFriendlyFire(Role.INNOCENT, Role.TRAITOR), Text.literal("innocent -> traitor is not friendly fire"));
        context.assertFalse(GameInstanceManager.isFriendlyFire(Role.TRAITOR, Role.INNOCENT), Text.literal("traitor -> innocent is not friendly fire"));
        context.assertFalse(GameInstanceManager.isFriendlyFire(Role.SPECTATOR, Role.INNOCENT), Text.literal("spectator cannot trigger friendly fire"));
        context.assertFalse(GameInstanceManager.isFriendlyFire(Role.INNOCENT, Role.SPECTATOR), Text.literal("spectator victim does not trigger friendly fire"));
        context.complete();
    }

    @GameTest
    public void roundSummarySameTeamKillClassificationMatchesRoleTeams(TestContext context) {
        context.assertTrue(
                RoundSummaryDialog.isSameTeamKill(Role.INNOCENT, Role.DETECTIVE),
                Text.literal("innocent -> detective should be classified as same-team kill")
        );
        context.assertTrue(
                RoundSummaryDialog.isSameTeamKill(Role.DETECTIVE, Role.INNOCENT),
                Text.literal("detective -> innocent should be classified as same-team kill")
        );
        context.assertTrue(
                RoundSummaryDialog.isSameTeamKill(Role.TRAITOR, Role.TRAITOR),
                Text.literal("traitor -> traitor should be classified as same-team kill")
        );
        context.assertFalse(
                RoundSummaryDialog.isSameTeamKill(Role.INNOCENT, Role.TRAITOR),
                Text.literal("innocent -> traitor should not be classified as same-team kill")
        );
        context.assertFalse(
                RoundSummaryDialog.isSameTeamKill(Role.TRAITOR, Role.DETECTIVE),
                Text.literal("traitor -> detective should not be classified as same-team kill")
        );
        context.assertFalse(
                RoundSummaryDialog.isSameTeamKill(Role.SPECTATOR, Role.TRAITOR),
                Text.literal("spectator killer should not be classified as same-team kill")
        );
        context.complete();
    }

    @GameTest
    public void traitorRevealPacketRecipientsMatchAliveAndRole(TestContext context) {
        context.assertTrue(
                GameInstanceManager.shouldReceiveTraitorRevealPackets(true, Role.TRAITOR),
                Text.literal("alive traitor should receive traitor reveal packets")
        );
        context.assertFalse(
                GameInstanceManager.shouldReceiveTraitorRevealPackets(true, Role.INNOCENT),
                Text.literal("alive innocent should not receive traitor reveal packets")
        );
        context.assertFalse(
                GameInstanceManager.shouldReceiveTraitorRevealPackets(true, Role.DETECTIVE),
                Text.literal("alive detective should not receive traitor reveal packets")
        );
        context.assertFalse(
                GameInstanceManager.shouldReceiveTraitorRevealPackets(false, Role.TRAITOR),
                Text.literal("dead traitor should not receive traitor reveal packets")
        );
        context.assertTrue(
                GameInstanceManager.shouldReceiveTraitorRevealPackets(false, Role.INNOCENT),
                Text.literal("dead innocent should receive traitor reveal packets")
        );
        context.assertTrue(
                GameInstanceManager.shouldReceiveTraitorRevealPackets(false, Role.DETECTIVE),
                Text.literal("dead detective should receive traitor reveal packets")
        );
        context.assertTrue(
                GameInstanceManager.shouldReceiveTraitorRevealPackets(false, Role.SPECTATOR),
                Text.literal("spectator should receive traitor reveal packets")
        );
        context.complete();
    }

    @GameTest
    public void detectiveTeamPacketsAreSentOnlyInCombatPhases(TestContext context) {
        context.assertFalse(
                GameInstanceManager.shouldSendDetectiveTeamPackets(GameManager.Phase.NOT_STARTED),
                Text.literal("not started should not send detective team packets")
        );
        context.assertFalse(
                GameInstanceManager.shouldSendDetectiveTeamPackets(GameManager.Phase.INITIALIZE),
                Text.literal("initialize should not send detective team packets")
        );
        context.assertFalse(
                GameInstanceManager.shouldSendDetectiveTeamPackets(GameManager.Phase.POST_GAME),
                Text.literal("warmup should not send detective team packets")
        );
        context.assertTrue(
                GameInstanceManager.shouldSendDetectiveTeamPackets(GameManager.Phase.MIDDLE_GAME),
                Text.literal("middle game should send detective team packets")
        );
        context.assertTrue(
                GameInstanceManager.shouldSendDetectiveTeamPackets(GameManager.Phase.OVER_TIME),
                Text.literal("overtime should send detective team packets")
        );
        context.assertFalse(
                GameInstanceManager.shouldSendDetectiveTeamPackets(GameManager.Phase.END_GAME),
                Text.literal("end game should not send detective team packets")
        );
        context.complete();
    }

    @GameTest
    public void finalInnocentGlobalGlowIsEnabledOnlyForCombatClutchState(TestContext context) {
        context.assertFalse(
                GameInstanceManager.shouldEnableFinalInnocentGlobalGlow(GameManager.Phase.NOT_STARTED, 1, 1),
                Text.literal("not started should never enable final innocent glow")
        );
        context.assertFalse(
                GameInstanceManager.shouldEnableFinalInnocentGlobalGlow(GameManager.Phase.POST_GAME, 1, 1),
                Text.literal("warmup should not enable final innocent glow")
        );
        context.assertFalse(
                GameInstanceManager.shouldEnableFinalInnocentGlobalGlow(GameManager.Phase.MIDDLE_GAME, 0, 1),
                Text.literal("no alive traitor means no clutch glow")
        );
        context.assertFalse(
                GameInstanceManager.shouldEnableFinalInnocentGlobalGlow(GameManager.Phase.MIDDLE_GAME, 2, 2),
                Text.literal("more than one innocent should not enable clutch glow")
        );
        context.assertTrue(
                GameInstanceManager.shouldEnableFinalInnocentGlobalGlow(GameManager.Phase.MIDDLE_GAME, 2, 1),
                Text.literal("middle game with one innocent left should enable clutch glow")
        );
        context.assertTrue(
                GameInstanceManager.shouldEnableFinalInnocentGlobalGlow(GameManager.Phase.OVER_TIME, 1, 1),
                Text.literal("overtime with one innocent left should enable clutch glow")
        );
        context.complete();
    }

    @GameTest
    public void traitorPositionRevealWindowFollowsThirtySecondCycle(TestContext context) {
        context.assertFalse(
                GameInstanceManager.shouldEnableTraitorPositionReveal(GameManager.Phase.NOT_STARTED, 600),
                Text.literal("not started should not enable timed traitor reveal")
        );
        context.assertFalse(
                GameInstanceManager.shouldEnableTraitorPositionReveal(GameManager.Phase.MIDDLE_GAME, 0),
                Text.literal("timed reveal should not start immediately")
        );
        context.assertFalse(
                GameInstanceManager.shouldEnableTraitorPositionReveal(GameManager.Phase.MIDDLE_GAME, 599),
                Text.literal("timed reveal should stay off before 30 seconds")
        );
        context.assertTrue(
                GameInstanceManager.shouldEnableTraitorPositionReveal(GameManager.Phase.MIDDLE_GAME, 600),
                Text.literal("timed reveal should start at 30 seconds")
        );
        context.assertTrue(
                GameInstanceManager.shouldEnableTraitorPositionReveal(GameManager.Phase.MIDDLE_GAME, 699),
                Text.literal("timed reveal should stay on for 5 seconds")
        );
        context.assertFalse(
                GameInstanceManager.shouldEnableTraitorPositionReveal(GameManager.Phase.MIDDLE_GAME, 700),
                Text.literal("timed reveal should end after 5 seconds")
        );
        context.assertFalse(
                GameInstanceManager.shouldEnableTraitorPositionReveal(GameManager.Phase.MIDDLE_GAME, 1199),
                Text.literal("timed reveal should stay off until next cycle")
        );
        context.assertTrue(
                GameInstanceManager.shouldEnableTraitorPositionReveal(GameManager.Phase.MIDDLE_GAME, 1200),
                Text.literal("timed reveal should restart every 30 seconds")
        );
        context.assertTrue(
                GameInstanceManager.shouldEnableTraitorPositionReveal(GameManager.Phase.OVER_TIME, 600),
                Text.literal("overtime should use the same timed reveal schedule")
        );
        context.complete();
    }

    @GameTest
    public void hiddenNameTagPacketsAreSentOnlyDuringRoundPhases(TestContext context) {
        context.assertFalse(
                GameInstanceManager.shouldSendHiddenNameTagTeamPackets(GameManager.Phase.NOT_STARTED),
                Text.literal("not started should not send hidden nametag team packets")
        );
        context.assertFalse(
                GameInstanceManager.shouldSendHiddenNameTagTeamPackets(GameManager.Phase.INITIALIZE),
                Text.literal("initialize should not send hidden nametag team packets")
        );
        context.assertTrue(
                GameInstanceManager.shouldSendHiddenNameTagTeamPackets(GameManager.Phase.POST_GAME),
                Text.literal("warmup should send hidden nametag team packets")
        );
        context.assertTrue(
                GameInstanceManager.shouldSendHiddenNameTagTeamPackets(GameManager.Phase.MIDDLE_GAME),
                Text.literal("middle game should send hidden nametag team packets")
        );
        context.assertTrue(
                GameInstanceManager.shouldSendHiddenNameTagTeamPackets(GameManager.Phase.OVER_TIME),
                Text.literal("overtime should send hidden nametag team packets")
        );
        context.assertFalse(
                GameInstanceManager.shouldSendHiddenNameTagTeamPackets(GameManager.Phase.END_GAME),
                Text.literal("end game should not send hidden nametag team packets")
        );
        context.complete();
    }

    @GameTest
    public void hiddenNameTagTargetsDependOnPhaseAndRole(TestContext context) {
        context.assertTrue(
                GameInstanceManager.shouldHidePlayerNameTag(GameManager.Phase.POST_GAME, Role.INNOCENT),
                Text.literal("innocent nametag should be hidden")
        );
        context.assertTrue(
                GameInstanceManager.shouldHidePlayerNameTag(GameManager.Phase.POST_GAME, Role.TRAITOR),
                Text.literal("traitor nametag should be hidden")
        );
        context.assertTrue(
                GameInstanceManager.shouldHidePlayerNameTag(GameManager.Phase.POST_GAME, Role.SPECTATOR),
                Text.literal("spectator nametag should be hidden")
        );
        context.assertTrue(
                GameInstanceManager.shouldHidePlayerNameTag(GameManager.Phase.POST_GAME, Role.DETECTIVE),
                Text.literal("detective nametag should be hidden during early game")
        );
        context.assertFalse(
                GameInstanceManager.shouldHidePlayerNameTag(GameManager.Phase.MIDDLE_GAME, Role.DETECTIVE),
                Text.literal("detective nametag should be visible in middle game")
        );
        context.assertFalse(
                GameInstanceManager.shouldHidePlayerNameTag(GameManager.Phase.OVER_TIME, Role.DETECTIVE),
                Text.literal("detective nametag should stay visible in overtime")
        );
        context.assertTrue(
                GameInstanceManager.shouldHidePlayerNameTag(GameManager.Phase.MIDDLE_GAME, Role.TRAITOR),
                Text.literal("traitor nametag should stay hidden in middle game")
        );
        context.complete();
    }

    @GameTest
    public void warmupCountdownSoundTriggersEverySecondForLastFiveSeconds(TestContext context) {
        context.assertTrue(
                GameInstanceManager.shouldPlayWarmupCountdownSound(100),
                Text.literal("5 seconds left should trigger countdown sound")
        );
        context.assertTrue(
                GameInstanceManager.shouldPlayWarmupCountdownSound(80),
                Text.literal("4 seconds left should trigger countdown sound")
        );
        context.assertTrue(
                GameInstanceManager.shouldPlayWarmupCountdownSound(60),
                Text.literal("3 seconds left should trigger countdown sound")
        );
        context.assertTrue(
                GameInstanceManager.shouldPlayWarmupCountdownSound(40),
                Text.literal("2 seconds left should trigger countdown sound")
        );
        context.assertTrue(
                GameInstanceManager.shouldPlayWarmupCountdownSound(20),
                Text.literal("1 second left should trigger countdown sound")
        );
        context.assertFalse(
                GameInstanceManager.shouldPlayWarmupCountdownSound(120),
                Text.literal("more than 5 seconds left should not trigger countdown sound")
        );
        context.assertFalse(
                GameInstanceManager.shouldPlayWarmupCountdownSound(90),
                Text.literal("non-second boundary should not trigger countdown sound")
        );
        context.assertFalse(
                GameInstanceManager.shouldPlayWarmupCountdownSound(0),
                Text.literal("0 seconds left should not trigger countdown sound")
        );

        context.assertEquals(
                Sounds.COUNTDOWN_5_SEC,
                GameInstanceManager.getWarmupCountdownSound(5),
                Text.literal("5 seconds sound is mapped")
        );
        context.assertEquals(
                Sounds.COUNTDOWN_4_SEC,
                GameInstanceManager.getWarmupCountdownSound(4),
                Text.literal("4 seconds sound is mapped")
        );
        context.assertEquals(
                Sounds.COUNTDOWN_3_SEC,
                GameInstanceManager.getWarmupCountdownSound(3),
                Text.literal("3 seconds sound is mapped")
        );
        context.assertEquals(
                Sounds.COUNTDOWN_2_SEC,
                GameInstanceManager.getWarmupCountdownSound(2),
                Text.literal("2 seconds sound is mapped")
        );
        context.assertEquals(
                Sounds.COUNTDOWN_1_SEC,
                GameInstanceManager.getWarmupCountdownSound(1),
                Text.literal("1 second sound is mapped")
        );
        context.assertTrue(
                GameInstanceManager.getWarmupCountdownSound(6) == null,
                Text.literal("out of range countdown sound is null")
        );
        context.complete();
    }

    @GameTest
    public void lobbyMorningBgmSelectionMatchesIndexAndPhase(TestContext context) {
        context.assertTrue(
                GameManager.shouldTickLobbyBgm(GameManager.Phase.NOT_STARTED),
                Text.literal("not started should tick lobby bgm")
        );
        context.assertFalse(
                GameManager.shouldTickLobbyBgm(GameManager.Phase.INITIALIZE),
                Text.literal("initialize should not tick lobby bgm")
        );
        context.assertFalse(
                GameManager.shouldTickLobbyBgm(GameManager.Phase.POST_GAME),
                Text.literal("warmup should not tick lobby bgm")
        );
        context.assertTrue(
                GameManager.shouldTickLobbyBgm(GameManager.Phase.MIDDLE_GAME),
                Text.literal("middle game should tick lobby bgm")
        );

        context.assertEquals(
                Sounds.MORNING_BGM_1,
                GameManager.selectLobbyMorningBgm(0),
                Text.literal("index 0 maps to morning bgm 1")
        );
        context.assertEquals(
                Sounds.MORNING_BGM_5,
                GameManager.selectLobbyMorningBgm(4),
                Text.literal("index 4 maps to morning bgm 5")
        );
        context.assertEquals(
                Sounds.BGM_1,
                GameManager.selectLobbyMorningBgm(5),
                Text.literal("index 5 maps to bgm 1")
        );
        context.assertEquals(
                Sounds.BGM_2,
                GameManager.selectLobbyMorningBgm(6),
                Text.literal("index 6 maps to bgm 2")
        );
        context.assertEquals(
                Sounds.BGM_3,
                GameManager.selectLobbyMorningBgm(-1),
                Text.literal("negative index wraps to bgm 3")
        );
        context.assertEquals(
                Sounds.MORNING_BGM_1,
                GameManager.selectLobbyMorningBgm(8),
                Text.literal("index overflow wraps to morning bgm 1")
        );
        context.assertTrue(
                GameManager.shouldPlayLobbyBgmForPlayer(true),
                Text.literal("bgm-enabled player should receive lobby bgm")
        );
        context.assertFalse(
                GameManager.shouldPlayLobbyBgmForPlayer(false),
                Text.literal("bgm-disabled player should not receive lobby bgm")
        );
        context.complete();
    }

    @GameTest
    public void roundResultMappingMatchesRoleTeams(TestContext context) {
        context.assertEquals(
                PlayerDataInstance.Result.WIN,
                PlayerDataInstance.Result.WIN.getByRole(Role.INNOCENT),
                Text.literal("innocent keeps innocent team result")
        );
        context.assertEquals(
                PlayerDataInstance.Result.WIN,
                PlayerDataInstance.Result.WIN.getByRole(Role.DETECTIVE),
                Text.literal("detective keeps innocent team result")
        );
        context.assertEquals(
                PlayerDataInstance.Result.LOSE,
                PlayerDataInstance.Result.WIN.getByRole(Role.TRAITOR),
                Text.literal("traitor receives opposite result when innocents win")
        );
        context.assertEquals(
                PlayerDataInstance.Result.CANCELED,
                PlayerDataInstance.Result.CANCELED.getByRole(Role.TRAITOR),
                Text.literal("canceled result remains unchanged")
        );
        context.assertEquals(
                PlayerDataInstance.Result.WIN,
                PlayerDataInstance.Result.WIN.getByRole(Role.SPECTATOR),
                Text.literal("spectator result remains unchanged")
        );
        context.complete();
    }

    @GameTest
    public void roleRevealMessagesMatchRoleObjectives(TestContext context) {
        context.assertEquals(
                "당신은 <green>시민</green> 입니다",
                GameManager.resolveRoleRevealTitle(Role.INNOCENT),
                Text.literal("innocent title should match reveal format")
        );
        context.assertEquals(
                "당신은 <red>트레이터</red> 입니다",
                GameManager.resolveRoleRevealTitle(Role.TRAITOR),
                Text.literal("traitor title should match reveal format")
        );
        context.assertEquals(
                "당신은 <blue>탐정</blue> 입니다",
                GameManager.resolveRoleRevealTitle(Role.DETECTIVE),
                Text.literal("detective title should match reveal format")
        );
        context.assertEquals(
                "모든 <red>트레이터</red>를 처치하세요!",
                GameManager.resolveRoleRevealObjective(Role.INNOCENT),
                Text.literal("innocent objective should match reveal subtitle")
        );
        context.assertEquals(
                "모든 <green>시민팀</green>을 처치하세요!",
                GameManager.resolveRoleRevealObjective(Role.TRAITOR),
                Text.literal("traitor objective should match reveal subtitle")
        );
        context.assertEquals(
                "정보를 취합하여 모든 <red>트레이터</red>를 처치하세요!",
                GameManager.resolveRoleRevealObjective(Role.DETECTIVE),
                Text.literal("detective objective should match reveal subtitle")
        );
        context.complete();
    }

    @GameTest
    public void karmaPenaltyConversionMatchesScorePenalty(TestContext context) {
        context.assertEquals(0, GameInstanceManager.calculateKarmaPointPenalty(1000), Text.literal("no penalty at full karma"));
        context.assertEquals(1, GameInstanceManager.calculateKarmaPointPenalty(999), Text.literal("any deficit starts at 1 point"));
        context.assertEquals(1, GameInstanceManager.calculateKarmaPointPenalty(950), Text.literal("small deficit keeps minimum penalty"));
        context.assertEquals(3, GameInstanceManager.calculateKarmaPointPenalty(700), Text.literal("300 deficit gives 3 points"));
        context.assertEquals(10, GameInstanceManager.calculateKarmaPointPenalty(0), Text.literal("max deficit gives 10 points"));
        context.complete();
    }

    @GameTest
    public void overtimeGainIsCappedByConfig(TestContext context) {
        context.assertEquals(200, GameInstanceManager.extendOvertimeTicks(0, 200, 600), Text.literal("first overtime gain"));
        context.assertEquals(600, GameInstanceManager.extendOvertimeTicks(500, 200, 600), Text.literal("gain is clamped to cap"));
        context.assertEquals(600, GameInstanceManager.extendOvertimeTicks(700, 200, 600), Text.literal("current value above cap is normalized"));
        context.assertEquals(300, GameInstanceManager.extendOvertimeTicks(300, 0, 600), Text.literal("no gain when per-kill overtime is disabled"));
        context.complete();
    }

    @GameTest
    public void friendlyDamagePenaltyScalesWithDamage(TestContext context) {
        context.assertEquals(1, GameInstanceManager.calculateFriendlyDamageKarmaPenalty(0.01f), Text.literal("very small damage still costs at least 1 karma"));
        context.assertEquals(12, GameInstanceManager.calculateFriendlyDamageKarmaPenalty(1.0f), Text.literal("1 point of damage costs 12 karma"));
        context.assertEquals(30, GameInstanceManager.calculateFriendlyDamageKarmaPenalty(2.5f), Text.literal("2.5 damage costs 30 karma"));
        context.assertEquals(48, GameInstanceManager.calculateFriendlyDamageKarmaPenalty(4.0f), Text.literal("4 damage costs 48 karma"));
        context.complete();
    }

    @GameTest
    public void depletedKarmaTriggersEliminationThreshold(TestContext context) {
        context.assertTrue(GameInstanceManager.shouldEliminateForKarma(10, 0), Text.literal("positive karma dropping to zero triggers elimination"));
        context.assertTrue(GameInstanceManager.shouldEliminateForKarma(1, -5), Text.literal("positive karma dropping below zero triggers elimination"));
        context.assertFalse(GameInstanceManager.shouldEliminateForKarma(0, 0), Text.literal("already zero karma should not re-trigger elimination"));
        context.assertFalse(GameInstanceManager.shouldEliminateForKarma(100, 20), Text.literal("remaining positive karma should not eliminate"));
        context.complete();
    }

    @GameTest
    public void joinRoutingMatchesRoundState(TestContext context) {
        context.assertTrue(GameManager.shouldJoinAsSpectator(GameManager.Phase.INITIALIZE), Text.literal("initialize joins as spectator"));
        context.assertTrue(GameManager.shouldJoinAsSpectator(GameManager.Phase.POST_GAME), Text.literal("warmup joins as spectator"));
        context.assertTrue(GameManager.shouldJoinAsSpectator(GameManager.Phase.MIDDLE_GAME), Text.literal("middle game joins as spectator"));
        context.assertTrue(GameManager.shouldJoinAsSpectator(GameManager.Phase.OVER_TIME), Text.literal("overtime joins as spectator"));
        context.assertFalse(GameManager.shouldJoinAsSpectator(GameManager.Phase.NOT_STARTED), Text.literal("not started joins as adventure"));
        context.assertFalse(GameManager.shouldJoinAsSpectator(GameManager.Phase.END_GAME), Text.literal("end game joins as adventure"));

        context.assertTrue(
                GameManager.shouldJoinCurrentRoundWorld(GameManager.Phase.MIDDLE_GAME, true),
                Text.literal("active round with map joins round world")
        );
        context.assertFalse(
                GameManager.shouldJoinCurrentRoundWorld(GameManager.Phase.MIDDLE_GAME, false),
                Text.literal("active round without map falls back to spawn")
        );
        context.assertFalse(
                GameManager.shouldJoinCurrentRoundWorld(GameManager.Phase.NOT_STARTED, true),
                Text.literal("no active round joins spawn even if previous map exists")
        );
        context.complete();
    }

    @GameTest
    public void roundParticipationAndStartGameModeFollowSpectatorOptIn(TestContext context) {
        context.assertTrue(
                GameManager.shouldParticipateInRound(false),
                Text.literal("players without spectator opt-in should participate")
        );
        context.assertFalse(
                GameManager.shouldParticipateInRound(true),
                Text.literal("players with spectator opt-in should not participate")
        );
        context.assertEquals(
                GameMode.ADVENTURE,
                GameManager.resolveRoundStartGameMode(false),
                Text.literal("non-spectator players should start in adventure mode")
        );
        context.assertEquals(
                GameMode.SPECTATOR,
                GameManager.resolveRoundStartGameMode(true),
                Text.literal("spectator opt-in players should start in spectator mode")
        );
        context.complete();
    }

    @GameTest
    public void combatAvailabilityMatchesRoundState(TestContext context) {
        context.assertFalse(
                GameManager.Phase.NOT_STARTED.canShowRole(),
                Text.literal("not started phase should not allow combat")
        );
        context.assertFalse(
                GameManager.Phase.INITIALIZE.canShowRole(),
                Text.literal("initialize phase should not allow combat")
        );
        context.assertFalse(
                GameManager.Phase.POST_GAME.canShowRole(),
                Text.literal("warmup phase should not allow combat")
        );
        context.assertTrue(
                GameManager.Phase.MIDDLE_GAME.canShowRole(),
                Text.literal("middle game phase should allow combat")
        );
        context.assertTrue(
                GameManager.Phase.OVER_TIME.canShowRole(),
                Text.literal("overtime phase should allow combat")
        );
        context.assertFalse(
                GameManager.Phase.END_GAME.canShowRole(),
                Text.literal("end game phase should not allow combat")
        );
        context.complete();
    }

    @GameTest
    public void roundMapIdsIncludeScannedTemplates(TestContext context) {
        GameManager manager = GameManager.getInstance();
        List<Identifier> mapIds = manager.getAllMapIds();

        context.assertTrue(
                mapIds.contains(Identifier.of("ttt", "kitchen")),
                Text.literal("kitchen map should be registered")
        );
        context.assertTrue(
                mapIds.contains(Identifier.of("ttt", "office_test")),
                Text.literal("office_test map should be discovered from map_template scan")
        );
        context.assertTrue(
                mapIds.contains(Identifier.of("ttt", "inferno")),
                Text.literal("inferno map should be discovered from map_template scan")
        );
        context.assertFalse(
                mapIds.contains(Identifier.of("ttt", "lobby")),
                Text.literal("lobby should not be registered as a round map")
        );

        long uniqueCount = mapIds.stream().distinct().count();
        context.assertEquals(
                (int) uniqueCount,
                mapIds.size(),
                Text.literal("round map ids should not contain duplicates")
        );
        context.complete();
    }
}
