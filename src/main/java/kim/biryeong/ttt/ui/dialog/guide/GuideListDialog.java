package kim.biryeong.ttt.ui.dialog.guide;

import net.minecraft.server.network.ServerPlayerEntity;

public final class GuideListDialog {
    private GuideListDialog() {
        throw new IllegalStateException("Utility class");
    }

    public static void register() {
        GuideMenuDialog.register();
    }

    public static int showMenu(ServerPlayerEntity player) {
        return GuideMenuDialog.show(player);
    }

    public static int showBasicGuide(ServerPlayerEntity player) {
        return GuideBasicDialog.show(player);
    }

    public static int showBasicGuide(ServerPlayerEntity player, int page) {
        return GuideBasicDialog.show(player, page);
    }

    public static int showInnocentGuide(ServerPlayerEntity player) {
        return GuideInnocentDialog.show(player);
    }

    public static int showInnocentGuide(ServerPlayerEntity player, int page) {
        return GuideInnocentDialog.show(player, page);
    }

    public static int showTraitorGuide(ServerPlayerEntity player) {
        return GuideTraitorDialog.show(player);
    }

    public static int showTraitorGuide(ServerPlayerEntity player, int page) {
        return GuideTraitorDialog.show(player, page);
    }

    public static int showDetectiveGuide(ServerPlayerEntity player) {
        return GuideDetectiveDialog.show(player);
    }

    public static int showDetectiveGuide(ServerPlayerEntity player, int page) {
        return GuideDetectiveDialog.show(player, page);
    }
}
