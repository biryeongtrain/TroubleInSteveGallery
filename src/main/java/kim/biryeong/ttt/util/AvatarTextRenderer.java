package kim.biryeong.ttt.util;

import de.tomalbrc.avatarrenderer.AvatarRendererMod;
import de.tomalbrc.avatarrenderer.impl.AvatarRenderer;
import de.tomalbrc.avatarrenderer.impl.SkinLoader;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AvatarTextRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AvatarTextRenderer.class);
    private static final int AVATAR_OFFSET = 24;
    private static final int AVATAR_SIZE = 34;
    private static final int SMALL_AVATAR_SIZE = 17;
    private static final String DEFAULT_AVATAR_KEY = "Steve";
    private static final Map<AvatarCacheKey, Text> SMALL_AVATAR_CACHE = new ConcurrentHashMap<>();

    private AvatarTextRenderer() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Resolves a small (kill-log sized) avatar text component from player identity data.
     */
    public static Text resolveSmallAvatar(@Nullable UUID uuid, @Nullable String keyOrName, boolean flipped) {
        Text avatar = resolveAvatarByUuid(uuid, flipped);
        if (avatar != null) {
            return avatar;
        }

        avatar = resolveAvatarByKey(keyOrName, flipped);
        if (avatar != null) {
            return avatar;
        }
        return resolveDefaultAvatar(flipped);
    }

    private static @Nullable Text resolveAvatarByUuid(@Nullable UUID uuid, boolean flipped) {
        if (uuid == null) {
            return null;
        }
        return resolveAvatarByKey(uuid.toString(), flipped);
    }

    private static @Nullable Text resolveAvatarByKey(@Nullable String key, boolean flipped) {
        if (key == null || key.isBlank()) {
            return null;
        }

        AvatarCacheKey cacheKey = new AvatarCacheKey(key, flipped);
        Text cachedAvatar = SMALL_AVATAR_CACHE.get(cacheKey);
        if (cachedAvatar != null) {
            return cachedAvatar;
        }

        Text renderedAvatar = renderSmallAvatar(key, flipped);
        if (renderedAvatar == null) {
            return null;
        }
        SMALL_AVATAR_CACHE.put(cacheKey, renderedAvatar);
        return renderedAvatar;
    }

    private static Text resolveDefaultAvatar(boolean flipped) {
        AvatarCacheKey defaultCacheKey = new AvatarCacheKey(DEFAULT_AVATAR_KEY, flipped);
        Text cachedDefault = SMALL_AVATAR_CACHE.get(defaultCacheKey);
        if (cachedDefault != null) {
            return cachedDefault;
        }

        BufferedImage steveSkin = loadDefaultSkin();
        if (steveSkin == null) {
            AvatarRendererMod.get(DEFAULT_AVATAR_KEY, AVATAR_OFFSET, flipped, AvatarRendererMod.NOOP);
            return AvatarRendererMod.getNow(DEFAULT_AVATAR_KEY, AVATAR_OFFSET, flipped);
        }

        Text defaultAvatar = AvatarRenderer.asTextComponent(scaleDown(AvatarRenderer.render(steveSkin, flipped)), AVATAR_OFFSET);
        SMALL_AVATAR_CACHE.put(defaultCacheKey, defaultAvatar);
        return defaultAvatar;
    }

    private static @Nullable Text renderSmallAvatar(String key, boolean flipped) {
        BufferedImage skin = loadSkin(key);
        if (skin == null) {
            return null;
        }
        BufferedImage avatar = AvatarRenderer.render(skin, flipped);
        return AvatarRenderer.asTextComponent(scaleDown(avatar), AVATAR_OFFSET);
    }

    private static @Nullable BufferedImage loadSkin(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return SkinLoader.load(UUID.fromString(key));
        } catch (IllegalArgumentException ignored) {
            // Fallback to username lookup when the key is not a UUID string.
        }

        Optional<BufferedImage> byName = SkinLoader.load(key);
        if (byName.isPresent()) {
            return byName.get();
        }
        return null;
    }

    private static @Nullable BufferedImage loadDefaultSkin() {
        try (var stream = AvatarRendererMod.class.getResourceAsStream("/steve.png")) {
            if (stream == null) {
                return null;
            }
            return ImageIO.read(stream);
        } catch (IOException exception) {
            LOGGER.warn("Failed to load default avatar skin.", exception);
            return null;
        }
    }

    private static BufferedImage scaleDown(BufferedImage source) {
        if (source.getWidth() == SMALL_AVATAR_SIZE && source.getHeight() == SMALL_AVATAR_SIZE) {
            return source;
        }
        BufferedImage resized = new BufferedImage(SMALL_AVATAR_SIZE, SMALL_AVATAR_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source, 0, 0, SMALL_AVATAR_SIZE, SMALL_AVATAR_SIZE, 0, 0, AVATAR_SIZE, AVATAR_SIZE, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private record AvatarCacheKey(String key, boolean flipped) {
    }
}
