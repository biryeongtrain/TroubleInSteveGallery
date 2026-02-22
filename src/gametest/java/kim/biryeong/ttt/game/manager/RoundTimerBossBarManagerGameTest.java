package kim.biryeong.ttt.game.manager;

import kim.biryeong.ttt.player.role.Role;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

public final class RoundTimerBossBarManagerGameTest {
    @GameTest
    public void timerBossBarVisibilityMatchesRoundState(TestContext context) {
        context.assertFalse(
                RoundTimerBossBarManager.shouldShow(GameManager.Phase.NOT_STARTED, 200),
                Text.literal("not started should hide timer boss bar")
        );
        context.assertFalse(
                RoundTimerBossBarManager.shouldShow(GameManager.Phase.MIDDLE_GAME, Integer.MIN_VALUE),
                Text.literal("invalid timer ticks should hide timer boss bar")
        );
        context.assertTrue(
                RoundTimerBossBarManager.shouldShow(GameManager.Phase.POST_GAME, 200),
                Text.literal("warmup should show timer boss bar")
        );
        context.assertTrue(
                RoundTimerBossBarManager.shouldShow(GameManager.Phase.MIDDLE_GAME, 200),
                Text.literal("middle game should show timer boss bar")
        );
        context.assertTrue(
                RoundTimerBossBarManager.shouldShow(GameManager.Phase.OVER_TIME, 200),
                Text.literal("overtime should show timer boss bar")
        );
        context.complete();
    }

    @GameTest
    public void timerBossBarTitleMatchesRoleAndPhase(TestContext context) {
        context.assertEquals(
                "라운드 시작까지 8초",
                RoundTimerBossBarManager.resolveTitle(GameManager.Phase.POST_GAME, 160, Role.SPECTATOR),
                Text.literal("warmup title should use countdown text")
        );
        context.assertEquals(
                "남은 시간 15초",
                RoundTimerBossBarManager.resolveTitle(GameManager.Phase.MIDDLE_GAME, 300, Role.INNOCENT),
                Text.literal("middle game title should display remaining seconds")
        );
        context.assertEquals(
                "오버타임 12초",
                RoundTimerBossBarManager.resolveTitle(GameManager.Phase.OVER_TIME, 240, Role.TRAITOR),
                Text.literal("traitor should see overtime seconds")
        );
        context.assertEquals(
                "오버타임 진행 중",
                RoundTimerBossBarManager.resolveTitle(GameManager.Phase.OVER_TIME, 240, Role.INNOCENT),
                Text.literal("non-traitor should not see overtime seconds")
        );
        context.complete();
    }

    @GameTest
    public void timerBossBarProgressHandlesOvertimeVisibilityAndClamp(TestContext context) {
        context.assertTrue(
                isClose(0.5f, RoundTimerBossBarManager.calculateProgress(GameManager.Phase.MIDDLE_GAME, 100, 200, Role.INNOCENT)),
                Text.literal("middle game progress should use remaining ratio")
        );
        context.assertTrue(
                isClose(1.0f, RoundTimerBossBarManager.calculateProgress(GameManager.Phase.OVER_TIME, 50, 200, Role.INNOCENT)),
                Text.literal("non-traitor overtime progress should stay full")
        );
        context.assertTrue(
                isClose(0.25f, RoundTimerBossBarManager.calculateProgress(GameManager.Phase.OVER_TIME, 50, 200, Role.TRAITOR)),
                Text.literal("traitor overtime progress should use remaining ratio")
        );
        context.assertTrue(
                isClose(0.0f, RoundTimerBossBarManager.calculateProgress(GameManager.Phase.MIDDLE_GAME, -20, 200, Role.INNOCENT)),
                Text.literal("negative remaining ticks should clamp to zero")
        );
        context.complete();
    }

    private static boolean isClose(float expected, float actual) {
        return Math.abs(expected - actual) < 0.0001f;
    }
}
