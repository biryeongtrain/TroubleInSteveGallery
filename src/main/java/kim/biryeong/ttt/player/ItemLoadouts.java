package kim.biryeong.ttt.player;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import kim.biryeong.ttt.item.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static kim.biryeong.ttt.game.manager.GameManager.byMiniMessage;

public class ItemLoadouts {
    public static final Identifier DEFAULT_LOADOUT_KEY = Identifier.of("ttt:basic");
    public static final Map<Identifier, ItemLoadout> LOADOUTS = new Object2ObjectOpenHashMap<>();

    static {
        register(
                Identifier.of("ttt:long_ranged"),
                byMiniMessage("프리셋 1"),
                List.of(byMiniMessage("길고 강하지만 공격 속도가 낮은 무기를 지급받습니다.")),
                List.of(ModItems.LONG_RANGED_SWORD.getDefaultStack(), Items.BOW.getDefaultStack())
        );

        register(
                DEFAULT_LOADOUT_KEY,
                byMiniMessage("프리셋 2"),
                List.of(byMiniMessage("균형 잡힌 성능의 무기를 지급받습니다.")),
                List.of(ModItems.NORMAL_SWORD.getDefaultStack(), Items.BOW.getDefaultStack())
        );

        register(
                Identifier.of("ttt:dagger"),
                byMiniMessage("프리셋 3"),
                List.of(byMiniMessage("짧지만 빠른 공격 속도의 무기를 지급받습니다.")),
                List.of(ModItems.DAGGER.getDefaultStack(), Items.BOW.getDefaultStack())
        );
    }

    public static ItemLoadout get(Identifier id) {
        return Objects.requireNonNull(LOADOUTS.getOrDefault(id, LOADOUTS.get(DEFAULT_LOADOUT_KEY)));
    }

    public static List<ItemLoadout> getLoadouts() {
        return List.of(LOADOUTS.values().toArray(new ItemLoadout[0]));
    }

    private static void register(Identifier id, Text title, List<Text> description, List<ItemStack> items) {
        LOADOUTS.put(id, new ItemLoadout(title, description, id, items));
    }
}
