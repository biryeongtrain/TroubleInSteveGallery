package kim.biryeong.ttt.util;

import kim.biryeong.ttt.TroubleInTerroristTownMod;
import kim.biryeong.ttt.item.ModItems;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.gui.ShopElement;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.BiConsumer;

import static kim.biryeong.ttt.game.manager.GameManager.byMiniMessage;

public class ShopUtil {
    public static final Identifier ARMOR_ID = Identifier.of(TroubleInTerroristTownMod.MOD_ID, "ttt_armor");
    public static final BiConsumer<Item, ServerPlayerEntity> DEFAULT = (item, player) -> {
        player.getInventory().insertStack(item.getDefaultStack());
    };

    public static final List<ShopElement> INNOCENT_ENTRIES = List.of(
        new ShopElement.Builder()
                .item(Items.DIAMOND_CHESTPLATE)
                .descriptions(List.of(
                        byMiniMessage("갑옷 포인트 20를 얻습니다."),
                        byMiniMessage("약 40%의 피해 감소 효과가 있습니다.")
                ))
                .point(8)
                .handler(((item, serverPlayerEntity) -> {
                    serverPlayerEntity.getAttributeInstance(EntityAttributes.ARMOR).addPersistentModifier(new EntityAttributeModifier(ARMOR_ID, 20, EntityAttributeModifier.Operation.ADD_VALUE));
                }))
                .build()
    );

    public static final List<ShopElement> TRAITOR_ENTRIES = List.of(
            new ShopElement.Builder()
                    .item(ModItems.ASSASSIN_BOW)
                    .descriptions(List.of(
                            byMiniMessage("암살자들이 주로 사용하는 활입니다."),
                            byMiniMessage(""),
                            byMiniMessage("<green> + </green> 화살이 일직선으로 날아갑니다."),
                            byMiniMessage("<green> + </green> 투사체 속도가 20배 증가합니다."),
                            byMiniMessage("<red> - </red> 치명타가 발생하지 않습니다. "),
                            byMiniMessage("<red> - </red> 내구도가 10으로 제한됩니다.")
                    ))
                    .point(11)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(ModItems.SUICIDE_BOMB)
                    .descriptions(List.of(
                            byMiniMessage("너도 나도 한번에 다같이."),
                            byMiniMessage(""),
                            byMiniMessage("<green> + </green> 5초 후 7*7*7 범위에 1557 데미지를 입힙니다."),
                            byMiniMessage("<red> - </red> 예외는 없습니다. 당신도 폭발 피해를 입습니다.")
                    ))
                    .point(25)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(ModItems.BAMBOO_DAGGER)
                    .descriptions(List.of(
                            byMiniMessage("너도 한방, 나도 한방."),
                            byMiniMessage(""),
                            byMiniMessage("<green> + </green> 적중한 적에게 1557 + 1 데미지를 입힙니다."),
                            byMiniMessage("<red> - </red> 1회용입니다."),
                            byMiniMessage("<red> - </red> 사거리가 2 감소합니다.")
                    ))
                    .point(9)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(Items.DIAMOND_CHESTPLATE)
                    .descriptions(List.of(
                            byMiniMessage("갑옷 포인트 20을 얻습니다."),
                            byMiniMessage("약 40%의 피해 감소 효과가 있습니다.")
                    ))
                    .point(2)
                    .handler(((item, serverPlayerEntity) -> {
                        serverPlayerEntity.getAttributeInstance(EntityAttributes.ARMOR).addPersistentModifier(new EntityAttributeModifier(ARMOR_ID, 20, EntityAttributeModifier.Operation.ADD_VALUE));
                    }))
                    .build()
            ,
            new ShopElement.Builder()
                    .item(ModItems.TELEPORTER)
                    .descriptions(List.of(
                            byMiniMessage("훌륭한 Plan B 수단입니다."),
                            byMiniMessage("첫 사용 시 위치를 저장합니다. 두 번째 사용 시 저장된 위치로 순간이동합니다."),
                            byMiniMessage("위치 저장 후 3초 후 재 사용 가능합니다.")
                    ))
                    .point(11)
                    .build()
    );

    public static final List<ShopElement> DETECTIVE_ENTRIES = List.of(
            new ShopElement.Builder()
                    .item(ModItems.TELEPORTER)
                    .descriptions(List.of(
                            byMiniMessage("훌륭한 Plan B 수단입니다."),
                            byMiniMessage("첫 사용 시 위치를 저장합니다. 두 번째 사용 시 저장된 위치로 순간이동합니다."),
                            byMiniMessage("위치 저장 후 3초 후 재 사용 가능합니다.")
                    ))
                    .point(7)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(Items.DIAMOND_CHESTPLATE)
                    .descriptions(List.of(
                            byMiniMessage("갑옷 포인트 20를 얻습니다."),
                            byMiniMessage("약 30%의 피해 감소 효과가 있습니다.")
                    ))
                    .point(2)
                    .handler(((item, serverPlayerEntity) -> {
                        serverPlayerEntity.getAttributeInstance(EntityAttributes.ARMOR).addPersistentModifier(new EntityAttributeModifier(ARMOR_ID, 20, EntityAttributeModifier.Operation.ADD_VALUE));
                    }))
                    .build()
            ,
            new ShopElement.Builder()
                    .item(ModItems.DNA_SCANNER)
                    .descriptions(List.of(
                            byMiniMessage("피해자의 DNA를 분석하여 최근 공격자를 찾아냅니다."),
                            byMiniMessage("시체 조사 시 처치한 사람의 닉네임이 공개됩니다."),
                            byMiniMessage("폭발 피해, 환경 데미지 등으로 인한 사망은 닉네임이 공개되지 않습니다.")
                    ))
                    .point(2)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(ModItems.ROLE_CHECKER)
                    .descriptions(List.of(
                            byMiniMessage("대상 플레이어의 역할을 확인합니다."),
                            byMiniMessage("사용 시 대상 플레이어의 역할이 공개됩니다."),
                            byMiniMessage("<green> + </green> 사용(우클릭) 시 당신에게만 상대의 직업이 공개됩니다."),
                            byMiniMessage("<red> - </red> 사거리가 2 감소합니다. ")
                    ))
                    .point(3)
                    .build()
    );

    public static List<ShopElement> getShopElements(Role role) {
        return switch (role) {
            case INNOCENT -> INNOCENT_ENTRIES;
            case TRAITOR -> TRAITOR_ENTRIES;
            case DETECTIVE -> DETECTIVE_ENTRIES;
            default -> List.of();
        };
    }
}
