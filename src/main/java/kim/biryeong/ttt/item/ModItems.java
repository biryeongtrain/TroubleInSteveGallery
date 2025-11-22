package kim.biryeong.ttt.item;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import kim.biryeong.ttt.item.detective.DNAScanner;
import kim.biryeong.ttt.item.traitor.AssassinBow;
import kim.biryeong.ttt.item.traitor.BambooDagger;
import kim.biryeong.ttt.item.traitor.SuicideBomb;
import kim.biryeong.ttt.item.traitor.TeleporterItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.UseCooldownComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Function;

/**
 * 아이템 등록하는 클래스임. 여기서 아이템을 등록해야 실제로 사용 가능함
 */
public class ModItems {
    public static final RegistryKey<ItemGroup> ITEM_GROUP_KEY = RegistryKey.of(Registries.ITEM_GROUP.getKey(), Identifier.of(TroubleInTerroristTownMod.MOD_ID, "item_group"));
    public static final ItemGroup ITEM_GROUP = PolymerItemGroupUtils.builder()
            .icon(() -> new ItemStack(Items.CLAY_BALL))
            .displayName(Text.translatable("itemGroup.polymer-template-mod"))
            .build();

    /*public static final Item EXAMPLE_ITEM = register(
            "example_item",
            settings -> new SimplePolymerItem(settings, Items.CLAY_BALL),
            new Item.Settings()
    );*/

    public static final Item SUICIDE_BOMB = register("jihad_bomb", SuicideBomb::new,
            new Item.Settings().maxCount(1)
    );

    public static final Item DNA_SCANNER = register("dna_scanner", DNAScanner::new,
            new Item.Settings().maxCount(1)
    );

    public static final Item ASSASSIN_BOW = register("assassin_bow", AssassinBow::new,
            new Item.Settings().maxCount(1).maxDamage(10).enchantable(1).component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
    );

    public static final Item BAMBOO_DAGGER = register("bamboo_dagger", BambooDagger::new,
            new Item.Settings().maxCount(1)
                    .sword(ToolMaterial.NETHERITE, 0, 0)
                    .maxDamage(1)
                    .component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .attributeModifiers(AttributeModifiersComponent.builder()
                            .add(EntityAttributes.ATTACK_DAMAGE, new EntityAttributeModifier(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID,1557, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.HAND)
                    .build())
    );

    public static final Item TELEPORTER = register("teleporter", TeleporterItem::new,
            new Item.Settings().maxCount(1).component(DataComponentTypes.USE_COOLDOWN, new UseCooldownComponent(3))
    );

    public static final Item ROLE_CHECKER = register("role_checker", kim.biryeong.ttt.item.detective.RoleChecker::new,
            new Item.Settings().maxCount(1).component(DataComponentTypes.USE_COOLDOWN, new UseCooldownComponent(0.5f))
    );

    public static Item register(String name, Function<Item.Settings, Item> itemFactory, Item.Settings settings) {
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TroubleInTerroristTownMod.MOD_ID, name));
        Item item = itemFactory.apply(settings.registryKey(itemKey));
        Registry.register(Registries.ITEM, itemKey, item);

        return item;
    }

    public static void initialize() {
        PolymerItemGroupUtils.registerPolymerItemGroup(ITEM_GROUP_KEY, ITEM_GROUP);

        ItemGroupEvents.modifyEntriesEvent(ITEM_GROUP_KEY).register(itemGroup -> {
            // Add items here:
            // itemGroup.add(EXAMPLE_ITEM);

            // Or blocks:
            // itemGroup.add(ModBlocks.EXAMPLE_BLOCK.asItem());
        });
    }
}