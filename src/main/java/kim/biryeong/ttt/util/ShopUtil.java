package kim.biryeong.ttt.util;

import kim.biryeong.ttt.TroubleInTerroristTownMod;
import kim.biryeong.ttt.item.ModItems;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.gui.ShopElement;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import static kim.biryeong.ttt.game.manager.GameManager.byMiniMessage;

public class ShopUtil {
    public static final Identifier MODIFIER_ID = Identifier.of(TroubleInTerroristTownMod.MOD_ID, "ttt_modifier");
    public static final BiConsumer<Item, ServerPlayerEntity> DEFAULT = (item, player) -> player.getInventory().insertStack(item.getDefaultStack());

    public static final List<ShopElement> INNOCENT_ENTRIES = List.of(
            new ShopElement.Builder()
                    .item(Items.DIAMOND_CHESTPLATE)
                    .name(byMiniMessage("<green>추가 갑옷!</green>"))
                    .descriptions(List.of(
                            byMiniMessage("갑옷 포인트 20를 얻습니다."),
                            byMiniMessage("약 40%의 피해 감소 효과가 있습니다."),
                            byMiniMessage("구매비용 : 11 포인트")
                    ))
                    .point(11)
                    .handler(((item, serverPlayerEntity) -> Objects.requireNonNull(serverPlayerEntity
                                    .getAttributeInstance(EntityAttributes.ARMOR))
                            .addPersistentModifier(new EntityAttributeModifier(
                                    MODIFIER_ID,
                                    20,
                                    EntityAttributeModifier.Operation.ADD_VALUE)
                            )
                    ))
                    .hideDefaultTooltip(true)
                    .build(),
            new ShopElement.Builder()
                    .item(Registries.ITEM.get(Identifier.of("glideaway:cherry_hang_glider")))
                    .name(byMiniMessage("<pink>글라이더!</pink>"))
                    .descriptions(List.of(
                            byMiniMessage("사용 가능한 글라이더를 지급받습니다."),
                            byMiniMessage("<red><bold>이걸로 맵뚫시 밴</red></bold>"),
                            byMiniMessage("구매 비용 : 7 포인트")
                    ))
                    .point(7)
                    .build(),
            new ShopElement.Builder()
                    .item(Registries.ITEM.get(Identifier.of("glideaway:wind_in_a_bottle")))
                    .name(byMiniMessage("<pink>바람이 든 병</pink>"))
                    .descriptions(List.of(
                            byMiniMessage("글라이더 사용시 우클릭 하면 높게 날 수 있습니다."),
                            byMiniMessage("<red><bold>이걸로 맵뚫시 밴</red></bold>"),
                            byMiniMessage("구매 비용 : 3 포인트")
                    ))
                    .point(3)
                    .build(),
            new ShopElement.Builder()
                    .item(Registries.ITEM.get(Identifier.of("polyfactory:chain_lift")))
                    .name(byMiniMessage("<pink>리프트 작동기</pink>"))
                    .descriptions(List.of(
                            byMiniMessage("Kitchen 맵에서만 사용할 수 있습니다. (지금은)"),
                            byMiniMessage("사슬 축에 우클릭하면 리프트가 생성됩니다."),
                            byMiniMessage("구매 비용 : 2 포인트")
                    ))
                    .point(2)
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
                            byMiniMessage("<red> - </red> 내구도가 10으로 제한됩니다."),
                            byMiniMessage("구매비용 : 9 포인트")
                    ))
                    .point(9)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(ModItems.SUICIDE_BOMB)
                    .descriptions(List.of(
                            byMiniMessage("너도 나도 한번에 다같이."),
                            byMiniMessage(""),
                            byMiniMessage("<green> + </green> 1.5초 후 7*7*7 범위에 1557 데미지를 입힙니다."),
                            byMiniMessage("<red> - </red> 예외는 없습니다. 당신도 폭발 피해를 입습니다."),
                            byMiniMessage("구매비용 : 17 포인트")
                    ))
                    .point(17)
                    .build(),
            new ShopElement.Builder()
                    .item(Registries.ITEM.get(Identifier.of("glideaway:cherry_hang_glider")))
                    .name(byMiniMessage("<pink>글라이더!</pink>"))
                    .descriptions(List.of(
                            byMiniMessage("사용 가능한 글라이더를 지급받습니다."),
                            byMiniMessage("<red><bold>이걸로 맵뚫시 밴</red></bold>"),
                            byMiniMessage("구매 비용 : 5 포인트")
                    ))
                    .point(5)
                    .build(),
            new ShopElement.Builder()
                    .item(Registries.ITEM.get(Identifier.of("glideaway:wind_in_a_bottle")))
                    .name(byMiniMessage("<pink>바람이 든 병</pink>"))
                    .descriptions(List.of(
                            byMiniMessage("글라이더 사용시 우클릭 하면 높게 날 수 있습니다."),
                            byMiniMessage("<red><bold>이걸로 맵뚫시 밴</red></bold>"),
                            byMiniMessage("구매 비용 : 2 포인트")
                    ))
                    .point(2)
                    .build(),
            new ShopElement.Builder()
                    .item(Registries.ITEM.get(Identifier.of("polyfactory:chain_lift")))
                    .name(byMiniMessage("<pink>리프트 작동기</pink>"))
                    .descriptions(List.of(
                            byMiniMessage("Kitchen 맵에서만 사용할 수 있습니다. (지금은)"),
                            byMiniMessage("사슬 축에 우클릭하면 리프트가 생성됩니다."),
                            byMiniMessage("구매 비용 : 1 포인트")
                    ))
                    .point(1)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(ModItems.BAMBOO_DAGGER)
                    .descriptions(List.of(
                            byMiniMessage("너도 한방, 나도 한방."),
                            byMiniMessage(""),
                            byMiniMessage("<green> + </green> 적중한 적에게 1557 + 1 데미지를 입힙니다."),
                            byMiniMessage("<red> - </red> 1회용입니다."),
                            byMiniMessage("<red> - </red> 사거리가 2 감소합니다."),
                            byMiniMessage("구매비용 : 11 포인트")
                    ))
                    .point(11)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(Items.DIAMOND_CHESTPLATE)
                    .name(byMiniMessage("<red>추가 갑옷!</red>"))
                    .descriptions(List.of(
                            byMiniMessage("갑옷 포인트 20을 얻습니다."),
                            byMiniMessage("약 40%의 피해 감소 효과가 있습니다."),
                            Text.empty(),
                            Text.literal("<red> 주의 ! 중복으로 구매해도 효과가 중첩되지 않습니다! </red>"),
                            byMiniMessage("구매비용 : 2 포인트")
                    ))
                    .point(2)
                    .handler(((item, serverPlayerEntity) -> {
                        try {
                            Objects.requireNonNull(serverPlayerEntity
                                            .getAttributeInstance(EntityAttributes.ARMOR))
                                    .addPersistentModifier(new EntityAttributeModifier(
                                            MODIFIER_ID,
                                            20,
                                            EntityAttributeModifier.Operation.ADD_VALUE
                                    ));
                        } catch (Exception ignored) {

                        }
                    }
                    ))
                    .hideDefaultTooltip(true)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(ModItems.TELEPORTER)
                    .descriptions(List.of(
                            byMiniMessage("훌륭한 예비 수단입니다."),
                            byMiniMessage("첫 사용 시 위치를 저장합니다. 두 번째 사용 시 저장된 위치로 순간이동합니다."),
                            byMiniMessage("위치 저장 후 3초 후 재 사용 가능합니다."),
                            byMiniMessage("구매비용 : 9 포인트")
                    ))
                    .point(9)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(Items.WIND_CHARGE)
                    .name(byMiniMessage("돌풍구"))
                    .descriptions(List.of(
                            byMiniMessage("문재인 게임 시작"),
                                    byMiniMessage("2포")
                    ))
                    .point(2)
                    .build(),
//            new ShopElement.Builder()
//                    .item(Items.COMPASS)
//                    .name(byMiniMessage("<red>플레이어 추적기</red>"))
//                    .descriptions(List.of(
//                            byMiniMessage("네비게이터를 활성화하여 50블록 이내의 플레이어를 탐지합니다."),
//                            byMiniMessage("<red>주의 ! 중복으로 구매해도 효과가 중첩되지 않습니다!</red>"),
//                            byMiniMessage("구매비용 : 5 포인트")
//                    ))
//                    .point(5)
//                    .handler((item, player) -> {
//                                try {
//                                    Objects.requireNonNull(player
//                                                    .getAttributeInstance(EntityAttributes.WAYPOINT_RECEIVE_RANGE))
//                                            .addPersistentModifier(new EntityAttributeModifier(
//                                                    MODIFIER_ID,
//                                                    50,
//                                                    EntityAttributeModifier.Operation.ADD_VALUE)
//                                            );
//                                } catch (Exception ignored) {
//
//                                }
//                            }
//                    )
//                    .hideDefaultTooltip(true)
//                    .build(),

            new ShopElement.Builder()
                    .item(Items.BLAZE_ROD)
                    .name(byMiniMessage("<red>시체 제거기</red>"))
                    .descriptions(List.of(
                            byMiniMessage("플레이어의 시체를 지웁니다."),
                            byMiniMessage("약 5초동안 불탄 다음, <red><bold>흔적도 없이 사라지게 만듭니다.</red>"),
                            byMiniMessage("불타고 있는 시체는 <red><bold>더 이상 조사할 수 없습니다.</red>"),
                            byMiniMessage("구매 비용 : 3 포인트")
                    ))
                    .point(3)
                    .build(),

            new ShopElement.Builder()
                    .item(ModItems.CORPSE_SABOTAGE)
                    .name(byMiniMessage("<red>시체 폭탄</red>"))
                    .descriptions(List.of(
                            byMiniMessage("<red>시체에 폭탄을 심습니다.</red>"),
                            byMiniMessage("심어진 폭탄은 <bold>트레이터가 아닌 유저가 조사할 때 폭발합니다.</bold>"),
                            byMiniMessage("폭발 반경은 자폭 폭탄보다 조금 좁습니다."),
                            byMiniMessage("구매 비용 : 9 포인트")
                    ))
                    .point(9)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(ModItems.TRAITOR_DISRUPTOR)
                    .name(byMiniMessage("<red>교란기</red>"))
                    .descriptions(List.of(
                            byMiniMessage("주변 생존 비-트레이터의 시야를 10초간 교란합니다."),
                            byMiniMessage("직접 피해는 없지만 추격, 조사, 지목 타이밍을 흐릴 수 있습니다."),
                            byMiniMessage("범위 : 16블록"),
                            byMiniMessage("구매 비용 : 6 포인트")
                    ))
                    .point(6)
                    .build()
    );

    public static final List<ShopElement> DETECTIVE_ENTRIES = List.of(
            new ShopElement.Builder()
                    .item(ModItems.TELEPORTER)
                    .descriptions(List.of(
                            byMiniMessage("훌륭한 예비 수단입니다."),
                            byMiniMessage("첫 사용 시 위치를 저장합니다. 두 번째 사용 시 저장된 위치로 순간이동합니다."),
                            byMiniMessage("위치 저장 후 3초 후 재 사용 가능합니다."),
                            byMiniMessage("구매비용 : 7 포인트")
                    ))
                    .point(7)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(Items.DIAMOND_CHESTPLATE)
                    .name(byMiniMessage("<blue>추가 갑옷!</blue>"))
                    .descriptions(List.of(
                            byMiniMessage("갑옷 포인트 20를 얻습니다."),
                            byMiniMessage("약 30%의 피해 감소 효과가 있습니다."),
                            byMiniMessage("구매비용 : 2 포인트")
                    ))
                    .point(2)
                    .handler(((item, serverPlayerEntity) -> Objects.requireNonNull(serverPlayerEntity
                                    .getAttributeInstance(EntityAttributes.ARMOR))
                            .addPersistentModifier(new EntityAttributeModifier(
                                    MODIFIER_ID,
                                    20,
                                    EntityAttributeModifier.Operation.ADD_VALUE)
                            )
                    ))
                    .hideDefaultTooltip(true)
                    .build()
            ,
            new ShopElement.Builder()
                    .item(ModItems.DNA_SCANNER)
                    .descriptions(List.of(
                            byMiniMessage("피해자의 DNA를 분석하여 최근 공격자를 찾아냅니다."),
                            byMiniMessage("시체 조사 시 처치한 사람의 닉네임이 공개됩니다."),
                            byMiniMessage("폭발 피해, 환경 데미지 등으로 인한 사망은 닉네임이 공개되지 않습니다."),
                            byMiniMessage("구매비용 : 2 포인트")
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
            ,
//            new ShopElement.Builder()
//                    .item(Items.COMPASS)
//                    .name(byMiniMessage("<red>플레이어 추적기</red>"))
//                    .descriptions(List.of(
//                            byMiniMessage("네비게이터를 활성화하여 50블록 이내의 플레이어를 탐지합니다."),
//                            byMiniMessage("<red> 주의 ! 중복으로 구매해도 효과가 중첩되지 않습니다! </red>")
//                    ))
//                    .point(5)
//                    .handler((item, player) -> Objects.requireNonNull(player
//                                    .getAttributeInstance(EntityAttributes.WAYPOINT_RECEIVE_RANGE))
//                            .addPersistentModifier(new EntityAttributeModifier(
//                                            MODIFIER_ID,
//                                            50,
//                                            EntityAttributeModifier.Operation.ADD_VALUE
//                                    )
//                            )
//                    )
//                    .hideDefaultTooltip(true)
//                    .build(),

            new ShopElement.Builder()
                    .item(Registries.ITEM.get(Identifier.of("glideaway:cherry_hang_glider")))
                    .name(byMiniMessage("<yellow>글라이더!</yellow>"))
                    .descriptions(List.of(
                            byMiniMessage("사용 가능한 글라이더를 지급받습니다."),
                            byMiniMessage("<red><bold>이걸로 맵뚫시 밴</red>"),
                            byMiniMessage("구매 비용 : 5 포인트")
                    ))
                    .point(5)
                    .build(),
            new ShopElement.Builder()
                    .item(Registries.ITEM.get(Identifier.of("glideaway:wind_in_a_bottle")))
                    .name(byMiniMessage("<yellow>바람이 든 병</yellow>"))
                    .descriptions(List.of(
                            byMiniMessage("글라이더 사용시 우클릭 하면 높게 날 수 있습니다."),
                            byMiniMessage("<red><bold>이걸로 맵뚫시 밴</red>"),
                            byMiniMessage("구매 비용 : 2 포인트")
                    ))
                    .point(2)
                    .build(),
            new ShopElement.Builder()
                    .item(Registries.ITEM.get(Identifier.of("polyfactory:chain_lift")))
                    .name(byMiniMessage("<pink>리프트 작동기</pink>"))
                    .descriptions(List.of(
                            byMiniMessage("Kitchen 맵에서만 사용할 수 있습니다. (지금은)"),
                            byMiniMessage("사슬 축에 우클릭하면 리프트가 생성됩니다."),
                            byMiniMessage("구매 비용 : 1 포인트")
                    ))
                    .point(1)
                    .build(),
            new ShopElement.Builder()
                    .item(Items.WIND_CHARGE)
                    .name(byMiniMessage("돌풍구"))
                    .descriptions(List.of(
                            byMiniMessage("문재인 게임 시작"),
                            byMiniMessage("2포")
                    ))
                    .point(2)
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
