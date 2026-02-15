package kim.biryeong.ttt.game.manager;

import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.player.role.Role;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

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
}
