package kim.biryeong.ttt.ui.dialog;

import de.tomalbrc.dialogutils.DialogUtils;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import kim.biryeong.ttt.ui.dialog.guide.GuideDialogDataLoader;
import kim.biryeong.ttt.ui.dialog.guide.GuideListDialog;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

@SuppressWarnings("unused")
public class TTTDialogs {
    private static boolean initialized;

    private TTTDialogs() {
        throw new IllegalStateException("Utility class");
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        GuideDialogDataLoader.initialize();
        GuideListDialog.register();
        DialogUtils.addCloseCommand();
    }

    public static Identifier deathCombatLogId(ServerPlayerEntity player) {
        String playerKey = player.getUuidAsString().replace("-", "");
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "death_log_" + playerKey);
    }

    public static Identifier roundSummaryLogId(ServerPlayerEntity player) {
        String playerKey = player.getUuidAsString().replace("-", "");
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "round_summary_" + playerKey);
    }

    public static Identifier playerStatsDialogId(ServerPlayerEntity player) {
        String playerKey = player.getUuidAsString().replace("-", "");
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "player_stats_" + playerKey);
    }

    public static Identifier loadoutDialogId(ServerPlayerEntity player) {
        String playerKey = player.getUuidAsString().replace("-", "");
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "loadout_" + playerKey);
    }

    public static Identifier guideListId() {
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "guide_list");
    }

    public static Identifier basicGuideId() {
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "guide_basic");
    }

    public static Identifier basicGuidePageId(int page) {
        if (page <= 1) {
            return basicGuideId();
        }
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "guide_basic_" + page);
    }

    public static Identifier innocentGuideId() {
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "guide_innocent");
    }

    public static Identifier innocentGuidePageId(int page) {
        if (page <= 1) {
            return innocentGuideId();
        }
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "guide_innocent_" + page);
    }

    public static Identifier traitorGuideId() {
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "guide_traitor");
    }

    public static Identifier traitorGuidePageId(int page) {
        if (page <= 1) {
            return traitorGuideId();
        }
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "guide_traitor_" + page);
    }

    public static Identifier detectiveGuideId() {
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "guide_detective");
    }

    public static Identifier detectiveGuidePageId(int page) {
        if (page <= 1) {
            return detectiveGuideId();
        }
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "guide_detective_" + page);
    }

    public static Identifier updateGuideId() {
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "guide_update");
    }

    public static Identifier updateGuidePageId(int page) {
        if (page <= 1) {
            return updateGuideId();
        }
        return Identifier.of(TroubleInTerroristTownMod.MOD_ID, "guide_update_" + page);
    }
}
