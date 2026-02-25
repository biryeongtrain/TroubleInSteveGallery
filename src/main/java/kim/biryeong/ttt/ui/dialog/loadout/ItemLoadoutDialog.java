package kim.biryeong.ttt.ui.dialog.loadout;

import de.tomalbrc.dialogutils.DialogUtils;
import kim.biryeong.ttt.player.ItemLoadout;
import kim.biryeong.ttt.player.ItemLoadouts;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.ui.dialog.TTTDialogs;
import kim.biryeong.ttt.ui.dialog.body.AlignedItemBody;
import kim.biryeong.ttt.ui.dialog.body.AlignedMessage;
import net.minecraft.dialog.AfterAction;
import net.minecraft.dialog.DialogActionButtonData;
import net.minecraft.dialog.DialogButtonData;
import net.minecraft.dialog.DialogCommonData;
import net.minecraft.dialog.action.SimpleDialogAction;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.dialog.type.MultiActionDialog;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ItemLoadoutDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemLoadoutDialog.class);
    private static final String DIALOG_TITLE = "로드아웃 설정";
    private static final int DESCRIPTION_WIDTH = 240;
    private static final int ITEM_SIZE = 16;

    private ItemLoadoutDialog() {
        throw new IllegalStateException("Utility class");
    }

    public static int show(ServerPlayerEntity player) {
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        ItemLoadout selectedLoadout = info.tts$getItemLoadout();
        Identifier dialogId = TTTDialogs.loadoutDialogId(player);
        MultiActionDialog dialog = buildDialog(selectedLoadout);
        DialogUtils.registerDialog(dialogId, dialog);
        return sendDialog(player, dialogId, dialog);
    }

    private static MultiActionDialog buildDialog(ItemLoadout selectedLoadout) {
        List<ItemLoadout> loadouts = ItemLoadouts.getLoadouts().stream()
                .sorted(
                        Comparator.comparing((ItemLoadout loadout) -> loadout.displayName().getString())
                                .thenComparing(loadout -> loadout.loadoutId().toString())
                )
                .toList();
        Identifier selectedLoadoutId = selectedLoadout.loadoutId();

        DialogCommonData commonData = new DialogCommonData(
                Text.literal(DIALOG_TITLE),
                Optional.of(buildCurrentLoadoutText(selectedLoadout)),
                true,
                false,
                AfterAction.CLOSE,
                buildBodies(loadouts, selectedLoadoutId),
                List.of()
        );

        List<DialogActionButtonData> loadoutButtons = buildButtons(loadouts, selectedLoadoutId);
        return new MultiActionDialog(
                commonData,
                loadoutButtons,
                Optional.of(createCloseButton()),
                Math.min(2, Math.max(1, loadoutButtons.size()))
        );
    }

    private static Text buildCurrentLoadoutText(ItemLoadout selectedLoadout) {
        return Text.empty()
                .append(Text.literal("현재 선택: ").formatted(Formatting.GRAY))
                .append(selectedLoadout.displayName().copy().formatted(Formatting.GOLD));
    }

    private static List<DialogBody> buildBodies(List<ItemLoadout> loadouts, Identifier selectedLoadoutId) {
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(new PlainMessageDialogBody(
                Text.literal("프리셋을 선택하면 이후 라운드 지급 무기 구성이 변경됩니다."),
                PlainMessageDialogBody.DEFAULT_WIDTH
        ));
        bodies.add(new PlainMessageDialogBody(Text.empty(), PlainMessageDialogBody.DEFAULT_WIDTH));

        for (ItemLoadout loadout : loadouts) {
            ItemStack representativeItem = resolveRepresentativeItem(loadout);
            AlignedMessage description = new AlignedMessage(
                    buildLoadoutDescription(loadout, selectedLoadoutId),
                    DESCRIPTION_WIDTH,
                    AlignedMessage.Align.LEFT
            );
            bodies.add(new AlignedItemBody(representativeItem, description, true, true, ITEM_SIZE, ITEM_SIZE));
            bodies.add(new PlainMessageDialogBody(Text.empty(), PlainMessageDialogBody.DEFAULT_WIDTH));
        }

        if (!bodies.isEmpty()) {
            bodies.removeLast();
        }
        return List.copyOf(bodies);
    }

    private static ItemStack resolveRepresentativeItem(ItemLoadout loadout) {
        if (loadout.items().isEmpty()) {
            return Items.BARRIER.getDefaultStack();
        }
        return loadout.items().getFirst().copy();
    }

    private static Text buildLoadoutDescription(ItemLoadout loadout, Identifier selectedLoadoutId) {
        MutableText description = Text.empty().append(loadout.displayName().copy());
        if (loadout.loadoutId().equals(selectedLoadoutId)) {
            description.append(Text.literal(" (선택됨)").formatted(Formatting.GREEN));
        }

        for (Text line : loadout.description()) {
            description.append(Text.literal("\n- ")).append(line.copy());
        }

        description.append(
                Text.literal("\nID: " + loadout.loadoutId())
                        .formatted(Formatting.DARK_GRAY)
        );
        return description;
    }

    private static List<DialogActionButtonData> buildButtons(List<ItemLoadout> loadouts, Identifier selectedLoadoutId) {
        List<DialogActionButtonData> buttons = new ArrayList<>(loadouts.size());
        for (ItemLoadout loadout : loadouts) {
            MutableText label = Text.empty();
            if (loadout.loadoutId().equals(selectedLoadoutId)) {
                label.append(Text.literal("✓ ").formatted(Formatting.GREEN));
            }
            label.append(loadout.displayName().copy());

            buttons.add(new DialogActionButtonData(
                    new DialogButtonData(label, DialogButtonData.DEFAULT_WIDTH),
                    Optional.of(new SimpleDialogAction(
                            new ClickEvent.RunCommand("/tts loadout set " + loadout.loadoutId())
                    ))
            ));
        }
        return List.copyOf(buttons);
    }

    private static DialogActionButtonData createCloseButton() {
        return new DialogActionButtonData(
                new DialogButtonData(Text.literal("닫기"), DialogButtonData.DEFAULT_WIDTH),
                Optional.empty()
        );
    }

    private static int sendDialog(ServerPlayerEntity player, Identifier dialogId, MultiActionDialog dialog) {
        var server = player.getServer();
        if (server == null) {
            LOGGER.warn(
                    "Cannot send loadout dialog for {} ({}): server is null.",
                    player.getGameProfile().getName(),
                    player.getUuid()
            );
            return 0;
        }

        var dialogRegistry = server.getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
        RegistryEntry<Dialog> dialogEntry = dialogRegistry.getEntry(dialogId)
                .<RegistryEntry<Dialog>>map(entry -> entry)
                .orElseGet(() -> dialogRegistry.getEntry(dialog));
        player.openDialog(dialogEntry);
        return 1;
    }
}
