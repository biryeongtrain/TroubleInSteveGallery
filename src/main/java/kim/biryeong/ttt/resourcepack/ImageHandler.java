package kim.biryeong.ttt.resourcepack;

import com.google.common.collect.Maps;
import de.tomalbrc.avatarrenderer.impl.Color;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.resourcepack.extras.api.format.font.BitmapProvider;
import eu.pb4.polymer.resourcepack.extras.api.format.font.FontAsset;
import eu.pb4.polymer.resourcepack.extras.api.format.font.SpaceProvider;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * copied from PolyFactory by Patbox
 */
public class ImageHandler {
    private static final Map<Identifier, TextWidth> IMAGES = Maps.newHashMap();
    private static final TextWidth MISSING = new TextWidth(Text.literal("<NO IMAGE>").formatted(Formatting.DARK_RED), 300);

    public static TextWidth getImage(Identifier id) {
        return IMAGES.getOrDefault(id, MISSING);
    }

    public static void init() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(ImageHandler::createImage);
    }

    private static void createImage(ResourcePackBuilder builder) {
        Identifier fontId = Identifier.of(TroubleInTerroristTownMod.MOD_ID, "image_hack");
        Style style = Style.EMPTY.withColor(0xFFFFFF).withFont(fontId).withShadowColor(0);
        char[] character = new char[]{'\u0100'};
        var fontBuilder = FontAsset.builder();
        IMAGES.clear();

        char n1 = 'a';
        fontBuilder.add(SpaceProvider.builder().add(n1, -1));
        fontBuilder.add(SpaceProvider.builder().add('b', 1));

        builder.forEachFile((path, resource) -> {
            String ogPath = path;
            if (!path.startsWith("assets/")) {
                return;
            }

            path = path.substring("assets/".length());
            int separator = path.indexOf("/");
            if (separator == -1) {
                return;
            }

            String namespace = path.substring(0, separator);
            path = path.substring(separator + 1);
            if (!path.startsWith("textures/guide/image/") || !path.endsWith(".png")) {
                return;
            }
            Identifier id = Identifier.of(namespace, path.substring("textures/guide/image/".length(), path.length() - ".png".length()));
            StringBuilder imageString = new StringBuilder();
            var b = BitmapProvider.builder(Identifier.of(namespace, path.substring("textures/".length())));
            BufferedImage image;
            try {
                 image = ImageIO.read(new ByteArrayInputStream(resource));
            } catch (IOException e) {
                TroubleInTerroristTownMod.LOGGER.warn("Can not read image from {}!", path);
                return;
            }
            b.height(9);
            b.ascent(7);

            var scale = MathHelper.ceil(image.getWidth() / 292f);
            var dy = 9;
            var dx = Math.min(128 / scale, 16);

            var width = MathHelper.ceil((double) image.getWidth() / scale / dx) * dx;
            var height = Math.ceil((double) image.getHeight() / scale / dy) * dy;

            for (var y = 0; y < height; y += dy) {
                var line = new StringBuilder();
                var ix = 0;
                for (; ix < width / 2; ix += dx) {
                    imageString.append('b');
                }

                for (var x = 0; x < width; x += dx) {
                    imageString.append(character[0]);
                    imageString.append('a');
                    line.append(character[0]++);
                    if (character[0] >= 0x0600 && character[0] < 0x0700) {
                        character[0] = '\u0700';
                    }
                }
                for (; ix < width; ix += dx) {
                    imageString.append('b');
                }
                b.chars(line.toString());

                if (y + dy < height) {
                    imageString.append("\n");
                }
            }
            fontBuilder.add(b);

            IMAGES.put(id, new TextWidth(Text.literal(imageString.toString()).setStyle(style), width + width / dx + 8));

            {
                var newImage = new BufferedImage(width * scale, (int) (height * scale), BufferedImage.TYPE_INT_ARGB);
                var yOffset = (height * scale - image.getHeight()) / 2;
                for (var y = 0; y < image.getHeight(); y++) {
                    for (var x = 0; x < image.getWidth(); x++) {
                        var color = image.getRGB(x, y);
                        if (color >>> 24 == 0) { // alpha
                            color |= 0x01000000;
                        }
                        newImage.setRGB(x, (int) (y + yOffset), color);
                    }
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    ImageIO.write(newImage, "png", baos);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                builder.addData(ogPath, baos.toByteArray());
            }
        });

        builder.addData("assets/ttt/font/image_hack.json", fontBuilder.build());
    }

    public record TextWidth(Text text, int width) {

    }
}
