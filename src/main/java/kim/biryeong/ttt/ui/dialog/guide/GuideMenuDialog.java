package kim.biryeong.ttt.ui.dialog.guide;

import de.tomalbrc.dialogutils.DialogUtils;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import kim.biryeong.ttt.ui.dialog.TTTDialogs;
import kim.biryeong.ttt.ui.dialog.body.HeaderMessage;
import kim.biryeong.ttt.ui.dialog.body.ImageBody;
import net.minecraft.dialog.AfterAction;
import net.minecraft.dialog.DialogActionButtonData;
import net.minecraft.dialog.DialogCommonData;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.dialog.type.MultiActionDialog;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static kim.biryeong.ttt.game.manager.GameManager.byMiniMessage;

final class GuideMenuDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuideMenuDialog.class);
    private static final String MENU_TITLE = "TTT 가이드 목록";
    private static final String SELF_STATS_TITLE = "자기 통계 보기";
    private static final String UPDATE_HISTORY_TITLE = "업데이트 내역";
    private static final String BASIC_TITLE = "기본 규칙";
    private static final String INNOCENT_TITLE = "이노센트 팁";
    private static final String TRAITOR_TITLE = "트레이터 팁";
    private static final String DETECTIVE_TITLE = "탐정 팁";

    private GuideMenuDialog() {
        throw new IllegalStateException("Utility class");
    }

    static void register() {
        DialogUtils.registerDialog(TTTDialogs.guideListId(), buildDialog());
        DialogUtils.registerQuickDialog(TTTDialogs.guideListId());
    }

    static int show(ServerPlayerEntity player) {
        return GuideDialogUiSupport.openDialog(player, TTTDialogs.guideListId(), buildDialog(), LOGGER);
    }

    private static MultiActionDialog buildDialog() {
        List<DialogBody> bodies = List.of(
                new HeaderMessage(Text.literal("Trouble In Terrorist Town 가이드"), 200),
                new ImageBody(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "ttt_title"), Optional.empty()),
                new PlainMessageDialogBody(Text.literal("카테고리를 선택하면 해당 가이드가 열립니다."), PlainMessageDialogBody.DEFAULT_WIDTH),
                new PlainMessageDialogBody(Text.literal("언제든 G 키로 이 메뉴를 다시 열 수 있습니다."), PlainMessageDialogBody.DEFAULT_WIDTH)
        );

        DialogCommonData commonData = new DialogCommonData(
                Text.literal(MENU_TITLE),
                Optional.empty(),
                true,
                false,
                AfterAction.CLOSE,
                bodies,
                List.of()
        );

        List<DialogActionButtonData> categoryButtons = List.of(
                GuideDialogUiSupport.createCommandButton(
                        BASIC_TITLE,
                        "/tts guide basic",
                        GuideDialogUiSupport.MENU_BUTTON_WIDTH
                ),
                GuideDialogUiSupport.createCommandButton(
                        INNOCENT_TITLE,
                        "/tts guide innocent",
                        GuideDialogUiSupport.MENU_BUTTON_WIDTH
                ),
                GuideDialogUiSupport.createCommandButton(
                        TRAITOR_TITLE,
                        "/tts guide traitor",
                        GuideDialogUiSupport.MENU_BUTTON_WIDTH
                ),
                GuideDialogUiSupport.createCommandButton(
                        DETECTIVE_TITLE,
                        "/tts guide detective",
                        GuideDialogUiSupport.MENU_BUTTON_WIDTH
                ),
                GuideDialogUiSupport.createCommandButton(
                        SELF_STATS_TITLE,
                        "/tts stats",
                        GuideDialogUiSupport.MENU_BUTTON_WIDTH
                ),
                GuideDialogUiSupport.createCommandButton(
                        UPDATE_HISTORY_TITLE,
                        "/tts guide update",
                        GuideDialogUiSupport.MENU_BUTTON_WIDTH
                )
        );

        return new MultiActionDialog(
                commonData,
                categoryButtons,
                Optional.of(GuideDialogUiSupport.createCloseButton("닫기")),
                2
        );
    }
}
