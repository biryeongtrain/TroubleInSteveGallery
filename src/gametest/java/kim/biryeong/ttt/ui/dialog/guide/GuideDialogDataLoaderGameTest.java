package kim.biryeong.ttt.ui.dialog.guide;

import kim.biryeong.ttt.ui.dialog.body.AlignedMessage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

public final class GuideDialogDataLoaderGameTest {
    private static final Identifier EXPECTED_IMAGE = Identifier.of("ttt", "conveyor_example");
    private static final Identifier SECOND_IMAGE = Identifier.of("ttt", "overtime");
    private static final Identifier CLASSES_IMAGE = Identifier.of("ttt", "ttt_classes");
    private static final Identifier FIRST_ITEM = Identifier.ofVanilla("iron_sword");
    private static final Identifier SECOND_ITEM = Identifier.ofVanilla("compass");

    @GameTest
    public void basicCategoryLoadsTxtPageWithPageInfoMetadata(TestContext context) {
        Optional<GuideDialogDataLoader.BasicPage> page = findPageByTitle(
                GuideDialogDataLoader.getBasicPages(),
                "GT Basic Parser Page"
        );

        context.assertTrue(page.isPresent(), Text.literal("basic category includes gametest txt page"));
        context.assertEquals(Optional.of(EXPECTED_IMAGE), page.orElseThrow().image(), Text.literal("pageinfo image is parsed"));
        context.assertEquals(Optional.of("gt-basic-image"), page.orElseThrow().imageDescription(), Text.literal("pageinfo image description is parsed"));
        context.assertEquals(AlignedMessage.Align.LEFT, page.orElseThrow().align(), Text.literal("default align is left"));
        context.assertTrue(
                page.orElseThrow().lines().stream().anyMatch(GuideDialogUiSupport::isHeaderLineToken),
                Text.literal("header directive is preserved as header line token")
        );
        context.assertTrue(page.orElseThrow().lines().contains("- gt-basic-line-1"), Text.literal("basic body first line is parsed"));
        context.assertTrue(page.orElseThrow().lines().contains("- gt-basic-line-2"), Text.literal("basic body second line is parsed"));
        context.complete();
    }

    @GameTest
    public void basicClassesPageKeepsHeaderDirectiveAsToken(TestContext context) {
        Optional<GuideDialogDataLoader.BasicPage> page = GuideDialogDataLoader.getBasicPages().stream()
                .filter(candidate -> candidate.image().equals(Optional.of(CLASSES_IMAGE)))
                .findFirst();

        context.assertTrue(page.isPresent(), Text.literal("classes basic page is loaded"));
        context.assertTrue(
                page.orElseThrow().lines().stream().anyMatch(GuideDialogUiSupport::isHeaderLineToken),
                Text.literal("classes page header directive is parsed as header token")
        );
        context.complete();
    }

    @GameTest
    public void basicCategoryDoesNotContainRotatingTipPages(TestContext context) {
        boolean hasRotatingTipTitle = GuideDialogDataLoader.getBasicPages().stream()
                .map(GuideDialogDataLoader.BasicPage::title)
                .anyMatch(title -> title != null && title.startsWith("일반 팁"));

        context.assertFalse(hasRotatingTipTitle, Text.literal("basic category should only contain page files, not rotating tips"));
        context.complete();
    }

    @GameTest
    public void innocentCategoryLoadsTxtPage(TestContext context) {
        Optional<GuideDialogDataLoader.BasicPage> page = findPageByTitle(
                GuideDialogDataLoader.getInnocentPages(),
                "GT Innocent Page"
        );

        context.assertTrue(page.isPresent(), Text.literal("innocent category includes gametest txt page"));
        context.assertEquals(AlignedMessage.Align.LEFT, page.orElseThrow().align(), Text.literal("innocent default align is left"));
        context.assertTrue(page.orElseThrow().lines().contains("- gt-innocent-line-1"), Text.literal("innocent line is parsed"));
        context.complete();
    }

    @GameTest
    public void traitorCategoryUsesHeaderDirectiveAsTitleWhenMissing(TestContext context) {
        Optional<GuideDialogDataLoader.BasicPage> page = findPageByTitle(
                GuideDialogDataLoader.getTraitorPages(),
                "GT Traitor Header Title"
        );

        context.assertTrue(page.isPresent(), Text.literal("traitor category uses header directive as title"));
        context.assertEquals(AlignedMessage.Align.LEFT, page.orElseThrow().align(), Text.literal("traitor default align is left"));
        context.assertTrue(page.orElseThrow().lines().contains("- gt-traitor-line-1"), Text.literal("traitor line is parsed"));
        context.complete();
    }

    @GameTest
    public void detectiveCategoryParsesMultipleImageDirectivesAsBodyTokens(TestContext context) {
        Optional<GuideDialogDataLoader.BasicPage> page = findPageByTitle(
                GuideDialogDataLoader.getDetectivePages(),
                "GT Detective Page"
        );

        context.assertTrue(page.isPresent(), Text.literal("detective category includes gametest txt page"));
        context.assertEquals(Optional.empty(), page.orElseThrow().image(), Text.literal("image directive no longer mutates pageinfo image"));
        context.assertEquals(Optional.empty(), page.orElseThrow().imageDescription(), Text.literal("image directive no longer mutates pageinfo image description"));
        context.assertEquals(AlignedMessage.Align.CENTER, page.orElseThrow().align(), Text.literal("align directive overrides default align"));

        List<String> imageLines = page.orElseThrow().lines().stream()
                .filter(GuideDialogUiSupport::isImageLineToken)
                .toList();
        context.assertEquals(2, imageLines.size(), Text.literal("detective page includes two image body tokens"));

        Optional<GuideDialogUiSupport.ImageLineToken> firstImage = GuideDialogUiSupport.extractImageLineToken(imageLines.getFirst());
        Optional<GuideDialogUiSupport.ImageLineToken> secondImage = GuideDialogUiSupport.extractImageLineToken(imageLines.getLast());
        context.assertTrue(firstImage.isPresent(), Text.literal("first image token is parsed"));
        context.assertTrue(secondImage.isPresent(), Text.literal("second image token is parsed"));
        context.assertEquals(EXPECTED_IMAGE, firstImage.orElseThrow().image(), Text.literal("first image token id is parsed"));
        context.assertEquals(Optional.of("gt-detective-image"), firstImage.orElseThrow().description(), Text.literal("first image token description is parsed"));
        context.assertEquals(SECOND_IMAGE, secondImage.orElseThrow().image(), Text.literal("second image token id is parsed"));
        context.assertEquals(Optional.empty(), secondImage.orElseThrow().description(), Text.literal("second image token without description is parsed"));

        List<String> itemLines = page.orElseThrow().lines().stream()
                .filter(GuideDialogUiSupport::isItemLineToken)
                .toList();
        context.assertEquals(2, itemLines.size(), Text.literal("detective page includes two item body tokens"));

        Optional<GuideDialogUiSupport.ItemLineToken> firstItem = GuideDialogUiSupport.extractItemLineToken(itemLines.getFirst());
        Optional<GuideDialogUiSupport.ItemLineToken> secondItem = GuideDialogUiSupport.extractItemLineToken(itemLines.getLast());
        context.assertTrue(firstItem.isPresent(), Text.literal("first item token is parsed"));
        context.assertTrue(secondItem.isPresent(), Text.literal("second item token is parsed"));
        context.assertEquals(FIRST_ITEM, firstItem.orElseThrow().item(), Text.literal("first item token id is parsed"));
        context.assertEquals(Optional.of("<green>gt-detective-item-1</green>"), firstItem.orElseThrow().description(), Text.literal("first item token description is parsed"));
        context.assertEquals(AlignedMessage.Align.RIGHT, firstItem.orElseThrow().align(), Text.literal("first item token align follows item align directive"));
        context.assertEquals(SECOND_ITEM, secondItem.orElseThrow().item(), Text.literal("second item token id is parsed"));
        context.assertEquals(Optional.of("gt-detective-item-2"), secondItem.orElseThrow().description(), Text.literal("second item token description is parsed"));
        context.assertEquals(AlignedMessage.Align.LEFT, secondItem.orElseThrow().align(), Text.literal("second item token align override is parsed"));

        context.assertTrue(page.orElseThrow().lines().contains("- gt-detective-line-1"), Text.literal("detective line is parsed"));
        context.complete();
    }

    @GameTest
    public void miniMessageParserConvertsTagsToFormattedText(TestContext context) {
        Text parsed = GuideDialogUiSupport.parseMiniMessage("<green>minimessage-test</green>");
        context.assertEquals("minimessage-test", parsed.getString(), Text.literal("minimessage tags are parsed"));
        context.complete();
    }

    private static Optional<GuideDialogDataLoader.BasicPage> findPageByTitle(
            List<GuideDialogDataLoader.BasicPage> pages,
            String title
    ) {
        return pages.stream().filter(page -> title.equals(page.title())).findFirst();
    }
}
