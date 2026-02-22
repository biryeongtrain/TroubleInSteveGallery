package kim.biryeong.ttt.ui.dialog.guide;

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
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class GuideBasicDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuideBasicDialog.class);
    private static final String BASIC_TITLE = "기본 규칙";
    private static final String EMPTY_CONTENT = "내용이 없습니다.";
    private static final int BASIC_RULES_PER_PAGE = 3;

    private GuideBasicDialog() {
        throw new IllegalStateException("Utility class");
    }

    static int show(ServerPlayerEntity player) {
        return show(player, 1);
    }

    static int show(ServerPlayerEntity player, int page) {
        int normalizedPage = clampBasicGuidePage(page, getBasicGuidePageCount());
        return GuideDialogUiSupport.openDialog(
                player,
                TTTDialogs.basicGuidePageId(normalizedPage),
                buildDialog(normalizedPage),
                LOGGER
        );
    }

    private static MultiActionDialog buildDialog(int page) {
        List<BasicGuidePage> pages = buildBasicGuidePages();
        int pageCount = pages.size();
        int normalizedPage = clampBasicGuidePage(page, pageCount);
        BasicGuidePage pageData = pages.get(normalizedPage - 1);

        List<DialogBody> bodies = new ArrayList<>();
        pageData.image().ifPresent(image -> {
            Optional<Text> imageDescription = pageData.imageDescription().map(GuideDialogUiSupport::parseMiniMessage);
            bodies.add(new ImageBody(image, imageDescription));
            GuideDialogUiSupport.addEmptyLine(bodies);
        });

        Text title = GuideDialogUiSupport.parseMiniMessage(pageData.title()).copy().withColor(0xefe6ae);
        GuideDialogUiSupport.addAlignedBodyLine(bodies, title, pageData.align());
        if (pageData.lines().isEmpty()) {
            GuideDialogUiSupport.addAlignedBodyLine(bodies, "- 내용이 없습니다.", pageData.align());
        } else {
            for (String line : pageData.lines()) {
                if (GuideDialogUiSupport.addDirectiveBodyFromToken(bodies, line)) {
                    continue;
                }
                GuideDialogUiSupport.addAlignedBodyLine(bodies, line, pageData.align());
            }
        }

        DialogCommonData commonData = new DialogCommonData(
                Text.literal(BASIC_TITLE),
                Optional.of(Text.literal(BASIC_TITLE + " (" + normalizedPage + " / " + pageCount + ")")),
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
                            "/tts guide basic " + (normalizedPage - 1),
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
                            "/tts guide basic " + (normalizedPage + 1),
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

    private static List<BasicGuidePage> buildBasicGuidePages() {
        List<BasicGuidePage> pages = new ArrayList<>();

        List<GuideDialogDataLoader.BasicPage> datapackPages = GuideDialogDataLoader.getBasicPages();
        if (datapackPages.isEmpty()) {
            pages.addAll(buildLegacyBasicGuidePages());
        } else {
            for (GuideDialogDataLoader.BasicPage datapackPage : datapackPages) {
                String title = normalizeTitle(datapackPage.title());
                List<String> lines = normalizeLines(datapackPage.lines());
                Optional<String> imageDescription = datapackPage.imageDescription()
                        .map(String::trim)
                        .filter(value -> !value.isEmpty());
                pages.add(new BasicGuidePage(title, lines, datapackPage.image(), imageDescription, datapackPage.align()));
            }
        }

        if (pages.isEmpty()) {
            pages.add(new BasicGuidePage(
                    BASIC_TITLE,
                    List.of("- " + EMPTY_CONTENT),
                    Optional.empty(),
                    Optional.empty(),
                    AlignedMessage.Align.LEFT
            ));
        }

        return List.copyOf(pages);
    }

    private static List<BasicGuidePage> buildLegacyBasicGuidePages() {
        List<String> normalizedLines = normalizeLines(buildBasicGuideLines());
        if (normalizedLines.isEmpty()) {
            return List.of(new BasicGuidePage(
                    BASIC_TITLE,
                    List.of("- " + EMPTY_CONTENT),
                    Optional.empty(),
                    Optional.empty(),
                    AlignedMessage.Align.LEFT
            ));
        }

        List<BasicGuidePage> pages = new ArrayList<>();
        for (int startIndex = 0; startIndex < normalizedLines.size(); startIndex += BASIC_RULES_PER_PAGE) {
            int endIndex = Math.min(normalizedLines.size(), startIndex + BASIC_RULES_PER_PAGE);
            List<String> pageLines = List.copyOf(normalizedLines.subList(startIndex, endIndex));
            String title = normalizeTitle(pageLines.getFirst());
            List<String> bodyLines = pageLines.size() > 1
                    ? List.copyOf(pageLines.subList(1, pageLines.size()))
                    : List.of();
            pages.add(new BasicGuidePage(
                    title,
                    bodyLines,
                    Optional.empty(),
                    Optional.empty(),
                    AlignedMessage.Align.LEFT
            ));
        }
        return List.copyOf(pages);
    }

    private static List<String> buildBasicGuideLines() {
        List<String> lines = new ArrayList<>();
        lines.add("- 라운드 목표");
        lines.add("  이노센트/탐정은 트레이터를 모두 제거하면 승리");
        lines.add("  트레이터는 반대로 이노센트/탐정을 모두 제거하면 승리");
        lines.add("- F 키로 상점을 열어 포인트를 사용");
        lines.add("- 시체를 먼저 식별하면 추가 포인트를 획득");
        lines.add("- 오버타임에서는 트레이터 처치 수에 따라 시간이 연장될 수 있음");
        return lines;
    }

    private static List<String> normalizeLines(List<String> lines) {
        List<String> normalized = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            normalized.add(trimmed);
        }
        return normalized;
    }

    private static String normalizeTitle(String title) {
        if (title == null) {
            return BASIC_TITLE;
        }
        String trimmed = title.trim();
        return trimmed.isEmpty() ? BASIC_TITLE : trimmed;
    }

    private static int getBasicGuidePageCount() {
        return buildBasicGuidePages().size();
    }

    private static int clampBasicGuidePage(int page, int pageCount) {
        return Math.max(1, Math.min(pageCount, page));
    }

    private record BasicGuidePage(
            String title,
            List<String> lines,
            Optional<Identifier> image,
            Optional<String> imageDescription,
            AlignedMessage.Align align
    ) {
    }
}
