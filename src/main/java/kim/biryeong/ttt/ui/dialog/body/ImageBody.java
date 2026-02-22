package kim.biryeong.ttt.ui.dialog.body;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.other.PolymerMapCodec;
import kim.biryeong.ttt.resourcepack.ImageHandler;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;

public record ImageBody(Identifier id, Optional<Text> description) implements DialogBody {
    public static final MapCodec<ImageBody> MAP_CODEC = PolymerMapCodec.ofDialogBody(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Identifier.CODEC.fieldOf("image").forGetter(ImageBody::id),
                    TextCodecs.CODEC.optionalFieldOf("description").forGetter(ImageBody::description)
            ).apply(instance, ImageBody::new)),  ImageBody::asVanillaBody
    );

    @Override
    public MapCodec<? extends DialogBody> getTypeCodec() {
        return MAP_CODEC;
    }

    public PlainMessageDialogBody asVanillaBody(PacketContext context) {
        var image = ImageHandler.getImage(this.id);
        Text text = image.text();
        if (description.isPresent()) {
            text = Text.empty().append(text).append("\n").append(description.get());
        }

        return new PlainMessageDialogBody(text, Math.min(image.width(), 1024));
    }
}
