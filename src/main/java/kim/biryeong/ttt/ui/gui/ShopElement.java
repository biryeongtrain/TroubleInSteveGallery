package kim.biryeong.ttt.ui.gui;

import kim.biryeong.ttt.util.ShopUtil;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

public record ShopElement(Item item, int point, @Nullable Text name, List<Text> descriptions, BiConsumer<Item, ServerPlayerEntity> handler, boolean hideDefaultTooltip) {

    public static class Builder {
        private Item item;
        private int point;
        private List<Text> descriptions;
        private BiConsumer<Item, ServerPlayerEntity> handler = ShopUtil.DEFAULT;
        private Text name = null;
        private boolean hideDefaultTooltip = false;

        public Builder item(Item item) {
            this.item = item;
            return this;
        }

        public Builder name(Text name) {
            this.name = name;
            return this;
        }

        public Builder point(int point) {
            this.point = point;
            return this;
        }

        public Builder descriptions(List<Text> descriptions) {
            this.descriptions = descriptions;
            return this;
        }

        public Builder handler(BiConsumer<Item, ServerPlayerEntity> handler) {
            this.handler = handler;
            return this;
        }

        public Builder hideDefaultTooltip(boolean hideDefaultTooltip) {
            this.hideDefaultTooltip = hideDefaultTooltip;
            return this;
        }

        public ShopElement build() {
            if (item == null) {
                throw new IllegalStateException("Item must be set");
            }
            if (descriptions == null) {
                throw new IllegalStateException("Descriptions must be set");
            }
            return new ShopElement(item, point, name, descriptions, handler, hideDefaultTooltip);
        }
    }
}
