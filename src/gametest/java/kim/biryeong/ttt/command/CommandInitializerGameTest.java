package kim.biryeong.ttt.command;

import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.KoreanKeyboardConverter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.stream.Collectors;

public final class CommandInitializerGameTest {
    @GameTest
    public void accuseCommandAvailabilityRequiresAlivePlayerInCombat(TestContext context) {
        context.assertTrue(
                CommandInitializer.canUseAccuseCommand(GameManager.Phase.MIDDLE_GAME, true, Role.INNOCENT),
                Text.literal("middle game alive innocent can use accuse")
        );
        context.assertTrue(
                CommandInitializer.canUseAccuseCommand(GameManager.Phase.OVER_TIME, true, Role.DETECTIVE),
                Text.literal("overtime alive detective can use accuse")
        );
        context.assertFalse(
                CommandInitializer.canUseAccuseCommand(GameManager.Phase.NOT_STARTED, true, Role.INNOCENT),
                Text.literal("not started cannot use accuse")
        );
        context.assertFalse(
                CommandInitializer.canUseAccuseCommand(GameManager.Phase.POST_GAME, true, Role.INNOCENT),
                Text.literal("warmup cannot use accuse")
        );
        context.assertFalse(
                CommandInitializer.canUseAccuseCommand(GameManager.Phase.MIDDLE_GAME, false, Role.INNOCENT),
                Text.literal("dead players cannot use accuse")
        );
        context.assertFalse(
                CommandInitializer.canUseAccuseCommand(GameManager.Phase.MIDDLE_GAME, true, Role.SPECTATOR),
                Text.literal("spectators cannot use accuse")
        );
        context.complete();
    }

    @GameTest
    public void accuseBroadcastTextIncludesTargetAndExpectedFormatting(TestContext context) {
        Text rendered = CommandInitializer.buildAccuseBroadcastText(
                null,
                Text.literal("Alice"),
                null,
                Text.literal("Bob")
        );
        String renderedText = rendered.getString();
        context.assertTrue(
                renderedText.contains("Alice")
                        && renderedText.contains("Bob")
                        && renderedText.contains("트레이터로 지목했습니다."),
                Text.literal("accuse broadcast text should include sender, target, and accusation message")
        );
        context.complete();
    }

    @GameTest
    public void accuseBroadcastTextPrependsAvatarsWhenProvided(TestContext context) {
        Text rendered = CommandInitializer.buildAccuseBroadcastText(
                Text.literal("[A1]"),
                Text.literal("Alice"),
                Text.literal("[A2]"),
                Text.literal("Bob")
        );
        String renderedText = rendered.getString();
        context.assertTrue(
                renderedText.contains("[A1] Alice") && renderedText.contains("[A2] Bob"),
                Text.literal("avatar text should be inserted before sender and target names")
        );
        context.complete();
    }

    @GameTest
    public void accuseCooldownCalculationAndDisplayAreDeterministic(TestContext context) {
        int cooldownTicks = CommandInitializer.accuseCooldownTicks();
        context.assertEquals(100, cooldownTicks, Text.literal("accuse cooldown should be 5 seconds"));

        context.assertEquals(
                cooldownTicks,
                CommandInitializer.calculateAccuseCooldownRemainingTicks(200, 200, cooldownTicks),
                Text.literal("cooldown starts at full value")
        );
        context.assertEquals(
                80,
                CommandInitializer.calculateAccuseCooldownRemainingTicks(200, 220, cooldownTicks),
                Text.literal("1 second elapsed leaves 4 seconds")
        );
        context.assertEquals(
                0,
                CommandInitializer.calculateAccuseCooldownRemainingTicks(200, 320, cooldownTicks),
                Text.literal("cooldown expires after configured duration")
        );
        context.assertEquals(
                0,
                CommandInitializer.calculateAccuseCooldownRemainingTicks(200, 150, cooldownTicks),
                Text.literal("negative elapsed should not keep stale cooldown")
        );

        context.assertEquals(0, CommandInitializer.toCooldownDisplaySeconds(0), Text.literal("0 ticks -> 0 seconds"));
        context.assertEquals(1, CommandInitializer.toCooldownDisplaySeconds(1), Text.literal("1 tick -> 1 second"));
        context.assertEquals(1, CommandInitializer.toCooldownDisplaySeconds(20), Text.literal("20 ticks -> 1 second"));
        context.assertEquals(2, CommandInitializer.toCooldownDisplaySeconds(21), Text.literal("21 ticks -> 2 seconds"));

        Text cooldownText = CommandInitializer.buildAccuseCooldownText(21);
        context.assertEquals(
                "지목 채팅 쿨타임입니다. 2초 후 다시 시도하세요.",
                cooldownText.getString(),
                Text.literal("cooldown message should use rounded-up seconds")
        );
        context.complete();
    }

    @GameTest
    public void traitorChatAvailabilityAndRecipientsFollowAliveTraitorRules(TestContext context) {
        context.assertTrue(
                CommandInitializer.canUseTraitorChat(GameManager.Phase.MIDDLE_GAME, true, Role.TRAITOR),
                Text.literal("alive traitor should use traitor chat in middle game")
        );
        context.assertTrue(
                CommandInitializer.canUseTraitorChat(GameManager.Phase.OVER_TIME, true, Role.TRAITOR),
                Text.literal("alive traitor should use traitor chat in overtime")
        );
        context.assertFalse(
                CommandInitializer.canUseTraitorChat(GameManager.Phase.POST_GAME, true, Role.TRAITOR),
                Text.literal("warmup should not allow traitor chat")
        );
        context.assertFalse(
                CommandInitializer.canUseTraitorChat(GameManager.Phase.MIDDLE_GAME, false, Role.TRAITOR),
                Text.literal("dead traitor should not allow traitor chat")
        );
        context.assertFalse(
                CommandInitializer.canUseTraitorChat(GameManager.Phase.MIDDLE_GAME, true, Role.DETECTIVE),
                Text.literal("non-traitor should not allow traitor chat")
        );

        context.assertTrue(
                CommandInitializer.canReceiveTraitorChat(true, Role.TRAITOR),
                Text.literal("alive traitor should receive traitor chat")
        );
        context.assertFalse(
                CommandInitializer.canReceiveTraitorChat(false, Role.TRAITOR),
                Text.literal("dead traitor should not receive traitor chat")
        );
        context.assertFalse(
                CommandInitializer.canReceiveTraitorChat(true, Role.INNOCENT),
                Text.literal("innocent should not receive traitor chat")
        );
        context.complete();
    }

    @GameTest
    public void startCountdownMessagesIncludeMapAndSeconds(TestContext context) {
        Text startAnnouncement = CommandInitializer.buildStartCountdownAnnouncementText(
                Identifier.of("ttt", "kitchen"),
                10
        );
        context.assertEquals(
                "[TTT] 10초 후 게임이 시작됩니다. 맵: ttt:kitchen",
                startAnnouncement.getString(),
                Text.literal("start countdown announcement should include selected map id")
        );

        Text countdownTick = CommandInitializer.buildStartCountdownTickText(7);
        context.assertEquals(
                "[TTT] 게임 시작까지 7초",
                countdownTick.getString(),
                Text.literal("countdown tick text should include remaining seconds")
        );
        context.complete();
    }

    @GameTest
    public void traitorChatAppliesKoreanKeyboardConversion(TestContext context) {
        context.assertEquals(
                "안녕",
                KoreanKeyboardConverter.convertChat("dkssud"),
                Text.literal("english 2-set input should be converted to korean")
        );
        context.assertEquals(
                "한글 123!",
                KoreanKeyboardConverter.convertChat("gksrmf 123!"),
                Text.literal("converted sentence should keep spacing and non-letter symbols")
        );
        context.assertEquals(
                "한글 \"test\"",
                KoreanKeyboardConverter.convertChat("gksrmf \"test\""),
                Text.literal("quoted segment should be preserved")
        );
        context.assertEquals(
                "한글 english",
                KoreanKeyboardConverter.convertChat("한글 english"),
                Text.literal("mixed korean+english should remain unchanged")
        );
        context.complete();
    }

    @GameTest
    public void statsRateFormattingHandlesZeroAndClampCases(TestContext context) {
        context.assertEquals(
                "0.00",
                CommandInitializer.formatKillDeathRatio(0, 0),
                Text.literal("zero kill/death should render 0.00")
        );
        context.assertEquals(
                "INF",
                CommandInitializer.formatKillDeathRatio(5, 0),
                Text.literal("non-zero kills with zero deaths should render INF")
        );
        context.assertEquals(
                "2.50",
                CommandInitializer.formatKillDeathRatio(5, 2),
                Text.literal("kill/death ratio should render with two decimals")
        );

        context.assertEquals(
                "기록 없음",
                CommandInitializer.formatRateSummary(0, 0),
                Text.literal("empty denominator should render no-history text")
        );
        context.assertEquals(
                "1/4 (25.0%)",
                CommandInitializer.formatRateSummary(1, 4),
                Text.literal("normal rate summary should include percent")
        );
        context.assertEquals(
                "4/4 (100.0%)",
                CommandInitializer.formatRateSummary(8, 4),
                Text.literal("numerator should be clamped to denominator")
        );
        context.complete();
    }

    @GameTest
    public void statsLinesContainRequestedMetricsAndRoleCounts(TestContext context) {
        GameManager.PlayerStatisticsSnapshot snapshot = new GameManager.PlayerStatisticsSnapshot(
                7,
                3,
                2,
                5,
                3,
                20,
                11,
                6,
                3
        );

        List<Text> lines = CommandInitializer.buildStatsLines("Alice", snapshot);
        String rendered = lines.stream()
                .map(Text::getString)
                .collect(Collectors.joining("\n"));

        context.assertTrue(
                rendered.contains("킬 / 데스: 7 / 3")
                        && rendered.contains("지목 적중률:")
                        && rendered.contains("팀 킬 확률:")
                        && rendered.contains("플레이 횟수: 20")
                        && rendered.contains("이노센트 횟수: 11")
                        && rendered.contains("트레이터 횟수: 6")
                        && rendered.contains("탐정 횟수: 3"),
                Text.literal("stats lines should contain all requested metrics")
        );
        context.complete();
    }
}
