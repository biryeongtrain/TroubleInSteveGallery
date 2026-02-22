package kim.biryeong.ttt.ui.dialog.guide;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

public final class GuideTraitorDialogGameTest {
    @GameTest
    public void innocentGuidePageCountMatchesLoadedPages(TestContext context) {
        int loadedPageCount = GuideDialogDataLoader.getInnocentPages().size();
        int expectedPageCount = Math.max(1, loadedPageCount);

        context.assertEquals(
                expectedPageCount,
                GuideInnocentDialog.getInnocentGuidePageCount(),
                Text.literal("innocent dialog page count should match loaded txt page count")
        );
        context.complete();
    }

    @GameTest
    public void innocentGuidePageClampUsesValidRange(TestContext context) {
        int pageCount = GuideInnocentDialog.getInnocentGuidePageCount();

        context.assertEquals(
                1,
                GuideInnocentDialog.clampInnocentGuidePage(0, pageCount),
                Text.literal("innocent guide page should clamp below range to first page")
        );
        context.assertEquals(
                pageCount,
                GuideInnocentDialog.clampInnocentGuidePage(pageCount + 10, pageCount),
                Text.literal("innocent guide page should clamp above range to last page")
        );
        context.complete();
    }

    @GameTest
    public void traitorGuidePageCountMatchesLoadedPages(TestContext context) {
        int loadedPageCount = GuideDialogDataLoader.getTraitorPages().size();
        int expectedPageCount = Math.max(1, loadedPageCount);

        context.assertEquals(
                expectedPageCount,
                GuideTraitorDialog.getTraitorGuidePageCount(),
                Text.literal("traitor dialog page count should match loaded txt page count")
        );
        context.complete();
    }

    @GameTest
    public void traitorGuidePageClampUsesValidRange(TestContext context) {
        int pageCount = GuideTraitorDialog.getTraitorGuidePageCount();

        context.assertEquals(
                1,
                GuideTraitorDialog.clampTraitorGuidePage(0, pageCount),
                Text.literal("traitor guide page should clamp below range to first page")
        );
        context.assertEquals(
                pageCount,
                GuideTraitorDialog.clampTraitorGuidePage(pageCount + 10, pageCount),
                Text.literal("traitor guide page should clamp above range to last page")
        );
        context.complete();
    }

    @GameTest
    public void detectiveGuidePageCountMatchesLoadedPages(TestContext context) {
        int loadedPageCount = GuideDialogDataLoader.getDetectivePages().size();
        int expectedPageCount = Math.max(1, loadedPageCount);

        context.assertEquals(
                expectedPageCount,
                GuideDetectiveDialog.getDetectiveGuidePageCount(),
                Text.literal("detective dialog page count should match loaded txt page count")
        );
        context.complete();
    }

    @GameTest
    public void detectiveGuidePageClampUsesValidRange(TestContext context) {
        int pageCount = GuideDetectiveDialog.getDetectiveGuidePageCount();

        context.assertEquals(
                1,
                GuideDetectiveDialog.clampDetectiveGuidePage(0, pageCount),
                Text.literal("detective guide page should clamp below range to first page")
        );
        context.assertEquals(
                pageCount,
                GuideDetectiveDialog.clampDetectiveGuidePage(pageCount + 10, pageCount),
                Text.literal("detective guide page should clamp above range to last page")
        );
        context.complete();
    }
}
