package kim.biryeong.ttt.ui.sidebar;

import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.hud.CorpseHud;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

public final class CorpseSidebarGameTest {
    @GameTest
    public void killerInfoRequiresDetectiveAndScanner(TestContext context) {
        context.assertTrue(
                CorpseHud.canRevealKillerInfo(Role.DETECTIVE, true),
                Text.literal("detective with scanner can reveal killer info")
        );
        context.assertFalse(
                CorpseHud.canRevealKillerInfo(Role.DETECTIVE, false),
                Text.literal("detective without scanner cannot reveal killer info")
        );
        context.assertFalse(
                CorpseHud.canRevealKillerInfo(Role.INNOCENT, true),
                Text.literal("non-detective cannot reveal killer info even with scanner")
        );
        context.assertFalse(
                CorpseHud.canRevealKillerInfo(Role.TRAITOR, true),
                Text.literal("traitor cannot reveal killer info even with scanner")
        );
        context.assertFalse(
                CorpseHud.canRevealKillerInfo(Role.SPECTATOR, true),
                Text.literal("spectator cannot reveal killer info even with scanner")
        );
        context.complete();
    }
}
