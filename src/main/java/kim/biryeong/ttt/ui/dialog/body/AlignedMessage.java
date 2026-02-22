package kim.biryeong.ttt.ui.dialog.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.other.PolymerMapCodec;
import kim.biryeong.ttt.util.TextUncenterer;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.StringIdentifiable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Locale;

public record AlignedMessage(Text contents, int width, Align align) implements DialogBody {
    public static final MapCodec<AlignedMessage> MAP_CODEC = PolymerMapCodec.ofDialogBody(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    TextCodecs.CODEC.fieldOf("contents").forGetter(AlignedMessage::contents),
                    Dialog.WIDTH_CODEC.optionalFieldOf("width", 200).forGetter(AlignedMessage::width),
                    StringIdentifiable.createCodec(Align::values).optionalFieldOf("align", Align.LEFT).forGetter(AlignedMessage::align)
            ).apply(instance, AlignedMessage::new)),
            AlignedMessage::asVanillaBody
    );

    public static final Codec<AlignedMessage> CODEC = Codec.withAlternative(
            MAP_CODEC.codec(),
            TextCodecs.CODEC.xmap(text -> new AlignedMessage(text, 280, Align.LEFT), AlignedMessage::contents)
    );

    @Override
    public MapCodec<? extends DialogBody> getTypeCodec() {
        return MAP_CODEC;
    }

    public PlainMessageDialogBody asVanillaBody(PacketContext context) {
        String language = context.getClientOptions() != null
                ? context.getClientOptions().language()
                : "en_us";
        int textWidth = Math.max(0, this.width - 8);

        Text alignedText = switch (this.align) {
            case LEFT -> ScreenTexts.joinLines(TextUncenterer.getLeftAligned(this.contents, textWidth, language));
            case RIGHT -> ScreenTexts.joinLines(TextUncenterer.getRightAligned(this.contents, textWidth, language));
            case CENTER -> this.contents;
        };

        return new PlainMessageDialogBody(alignedText, this.width);
    }

    public enum Align implements StringIdentifiable {
        LEFT,
        CENTER,
        RIGHT;

        @Override
        public String asString() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
