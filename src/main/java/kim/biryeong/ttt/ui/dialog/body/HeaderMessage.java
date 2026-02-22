package kim.biryeong.ttt.ui.dialog.body;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.mapcanvas.api.font.DefaultFonts;
import eu.pb4.polymer.core.api.other.PolymerMapCodec;
import kim.biryeong.ttt.util.TextUncenterer;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;

public record HeaderMessage(Text contents, int width) implements DialogBody {
    private static final Identifier DEFAULT_FONT_ID = Identifier.ofVanilla("default");
    private static final int FONT_SIZE = 8;

    public static final MapCodec<HeaderMessage> MAP_CODEC = PolymerMapCodec.ofDialogBody(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    TextCodecs.CODEC.fieldOf("contents").forGetter(HeaderMessage::contents),
                    Dialog.WIDTH_CODEC.optionalFieldOf("width", 310).forGetter(HeaderMessage::width)
            ).apply(instance, HeaderMessage::new)),
            HeaderMessage::asVanillaBody
    );

    @Override
    public MapCodec<? extends DialogBody> getTypeCodec() {
        return MAP_CODEC;
    }

    public PlainMessageDialogBody asVanillaBody(PacketContext context) {
        Text title = Text.literal(" ")
                .append(this.contents)
                .append(" ");
        int sideWidth = Math.max(0, (this.width - getWidth(title) - 8) / 2);

        MutableText side = TextUncenterer.filler(sideWidth)
                .copy()
                .styled(style -> style.withStrikethrough(true).withShadowColor(0));

        Text text = Text.empty()
                .append(side)
                .append(title)
                .append(side.copy());

        return new PlainMessageDialogBody(text, this.width);
    }

    private static int getWidth(Text text) {
        final int[] width = {0};
        text.visit((style, string) -> {
            if (!string.isEmpty()) {
                width[0] += getTextWidth(style, string);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return width[0];
    }

    private static int getTextWidth(Style style, String string) {
        Identifier fontId = style.getFont() == null ? DEFAULT_FONT_ID : style.getFont();
        return DefaultFonts.REGISTRY.getDefaultedFont(fontId).getTextWidth(string, FONT_SIZE);
    }
}
