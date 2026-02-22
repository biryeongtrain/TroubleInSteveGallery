package kim.biryeong.ttt.ui.dialog.guide;

import de.tomalbrc.dialogutils.DialogUtils;
import kim.biryeong.ttt.ui.dialog.TTTDialogs;
import kim.biryeong.ttt.ui.dialog.body.AlignedMessage;
import kim.biryeong.ttt.ui.dialog.body.ImageBody;
import net.minecraft.dialog.AfterAction;
import net.minecraft.dialog.DialogActionButtonData;
import net.minecraft.dialog.DialogCommonData;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.type.MultiActionDialog;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class GuideInnocentDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuideInnocentDialog.class);
    private static final String TITLE = "이노센트 팁";
    private static final String EMPTY_CONTENT = "- 내용이 없습니다.";

    private GuideInnocentDialog() {
        throw new IllegalStateException("Utility class");
    }

    static void register() {
        DialogUtils.registerDialog(TTTDialogs.innocentGuideId(), buildDialog(1));
    }

    static int show(ServerPlayerEntity player) {
        return show(player, 1);
    }

    static int show(ServerPlayerEntity player, int page) {
        int normalizedPage = clampInnocentGuidePage(page, getInnocentGuidePageCount());
        return GuideDialogUiSupport.openDialog(
                player,
                TTTDialogs.innocentGuidePageId(normalizedPage),
                buildDialog(normalizedPage),
                LOGGER
        );
    }

    private static MultiActionDialog buildDialog(int page) {
        List<GuideDialogDataLoader.BasicPage> pages = buildInnocentGuidePages();
        int pageCount = pages.size();
        int normalizedPage = clampInnocentGuidePage(page, pageCount);
        GuideDialogDataLoader.BasicPage pageData = pages.get(normalizedPage - 1);

        List<DialogBody> bodies = buildBodies(pageData);
        DialogCommonData commonData = new DialogCommonData(
                Text.literal(TITLE),
                Optional.of(Text.literal(TITLE + " (" + normalizedPage + " / " + pageCount + ")")),
                true,
                false,
                AfterAction.CLOSE,
                List.copyOf(bodies),
                List.of()
        );

        List<DialogActionButtonData> pageButtons = new ArrayList<>();
        if (normalizedPage > 1) {
            pageButtons.add(
                    GuideDialogUiSupport.createCommandButton(
                            "<",
                            "/tts guide innocent " + (normalizedPage - 1),
                            GuideDialogUiSupport.NAV_BUTTON_WIDTH
                    )
            );
        }
        pageButtons.add(
                GuideDialogUiSupport.createCommandButton(
                        GuideDialogUiSupport.BACK_TO_MENU_LABEL,
                        "/tts guide",
                        GuideDialogUiSupport.MENU_BUTTON_WIDTH
                )
        );
        if (normalizedPage < pageCount) {
            pageButtons.add(
                    GuideDialogUiSupport.createCommandButton(
                            ">",
                            "/tts guide innocent " + (normalizedPage + 1),
                            GuideDialogUiSupport.NAV_BUTTON_WIDTH
                    )
            );
        }

        return new MultiActionDialog(
                commonData,
                List.copyOf(pageButtons),
                Optional.of(GuideDialogUiSupport.createCloseButton("닫기")),
                Math.min(3, Math.max(1, pageButtons.size()))
        );
    }

    static int getInnocentGuidePageCount() {
        return buildInnocentGuidePages().size();
    }

    static int clampInnocentGuidePage(int page, int pageCount) {
        return Math.max(1, Math.min(pageCount, page));
    }

    private static List<GuideDialogDataLoader.BasicPage> buildInnocentGuidePages() {
        List<GuideDialogDataLoader.BasicPage> pages = GuideDialogDataLoader.getInnocentPages();
        if (pages.isEmpty()) {
            pages = List.of(new GuideDialogDataLoader.BasicPage(
                    TITLE,
                    List.of(
                            "- 확실한 증거 없이 선공하지 말 것",
                            "- 탐정과 함께 다니며 정보 공유",
                            "- 의심 대상은 위치/행동을 팀에 즉시 전달",
                            "- 아군 오인 사격은 팀 전체에 큰 손해"
                    ),
                    Optional.empty(),
                    Optional.empty(),
                    AlignedMessage.Align.LEFT
            ));
        }
        return List.copyOf(pages);
    }

    private static List<DialogBody> buildBodies(GuideDialogDataLoader.BasicPage page) {
        List<DialogBody> bodies = new ArrayList<>();

        page.image().ifPresent(image -> {
            Optional<Text> imageDescription = page.imageDescription().map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(GuideDialogUiSupport::parseMiniMessage);
            bodies.add(new ImageBody(image, imageDescription));
            GuideDialogUiSupport.addEmptyLine(bodies);
        });

        Text title = GuideDialogUiSupport.parseMiniMessage(normalizeTitle(page.title())).copy().withColor(0xefe6ae);
        GuideDialogUiSupport.addAlignedBodyLine(bodies, title, page.align());

        List<String> lines = normalizeLines(page.lines());
        if (lines.isEmpty()) {
            GuideDialogUiSupport.addAlignedBodyLine(bodies, EMPTY_CONTENT, page.align());
        } else {
            for (String line : lines) {
                if (GuideDialogUiSupport.addDirectiveBodyFromToken(bodies, line)) {
                    continue;
                }
                GuideDialogUiSupport.addAlignedBodyLine(bodies, line, page.align());
            }
        }

        return List.copyOf(bodies);
    }

    private static List<String> normalizeLines(List<String> lines) {
        List<String> normalized = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeTitle(String title) {
        if (title == null) {
            return TITLE;
        }
        String trimmed = title.trim();
        return trimmed.isEmpty() ? TITLE : trimmed;
    }
}
