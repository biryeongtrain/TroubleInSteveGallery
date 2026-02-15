package kim.biryeong.ttt.ui.sidebar;

import kim.biryeong.ttt.player.role.Role;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

public final class CorpseSidebarGameTest {
    @GameTest
    public void killerInfoRequiresDetectiveAndScanner(TestContext context) {
        context.assertTrue(
                CorpseSidebar.canRevealKillerInfo(Role.DETECTIVE, true),
                Text.literal("detective with scanner can reveal killer info")
        );
        context.assertFalse(
                CorpseSidebar.canRevealKillerInfo(Role.DETECTIVE, false),
                Text.literal("detective without scanner cannot reveal killer info")
        );
        context.assertFalse(
                CorpseSidebar.canRevealKillerInfo(Role.INNOCENT, true),
                Text.literal("non-detective cannot reveal killer info even with scanner")
        );
        context.assertFalse(
                CorpseSidebar.canRevealKillerInfo(Role.TRAITOR, true),
                Text.literal("traitor cannot reveal killer info even with scanner")
        );
        context.assertFalse(
                CorpseSidebar.canRevealKillerInfo(Role.SPECTATOR, true),
                Text.literal("spectator cannot reveal killer info even with scanner")
        );
        context.complete();
    }
}
