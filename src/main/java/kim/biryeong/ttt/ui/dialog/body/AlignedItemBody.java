package kim.biryeong.ttt.ui.dialog.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.other.PolymerMapCodec;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.ItemDialogBody;
import net.minecraft.item.ItemStack;
import net.minecraft.util.dynamic.Codecs;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;

public record AlignedItemBody(
        ItemStack item,
        AlignedMessage description,
        boolean showDecorations,
        boolean showTooltip,
        int width,
        int height
) implements DialogBody {
    public static final MapCodec<AlignedItemBody> MAP_CODEC = PolymerMapCodec.ofDialogBody(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ItemStack.VALIDATED_CODEC.fieldOf("item").forGetter(AlignedItemBody::item),
                    AlignedMessage.CODEC.fieldOf("description").forGetter(AlignedItemBody::description),
                    Codec.BOOL.optionalFieldOf("show_decorations", true).forGetter(AlignedItemBody::showDecorations),
                    Codec.BOOL.optionalFieldOf("show_tooltip", true).forGetter(AlignedItemBody::showTooltip),
                    Codecs.rangedInt(1, 256).optionalFieldOf("width", 16).forGetter(AlignedItemBody::width),
                    Codecs.rangedInt(1, 256).optionalFieldOf("height", 16).forGetter(AlignedItemBody::height)
            ).apply(instance, AlignedItemBody::new)),
            AlignedItemBody::asVanillaBody
    );

    @Override
    public MapCodec<? extends DialogBody> getTypeCodec() {
        return MAP_CODEC;
    }

    public ItemDialogBody asVanillaBody(PacketContext context) {
        return new ItemDialogBody(
                this.item,
                Optional.of(this.description.asVanillaBody(context)),
                this.showDecorations,
                this.showTooltip,
                this.width,
                this.height
        );
    }
}
