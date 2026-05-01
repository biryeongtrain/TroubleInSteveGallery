package kim.biryeong.ttt.datagen;

import kim.biryeong.ttt.item.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;

public class TTTKoreanProvider extends FabricLanguageProvider {
    protected TTTKoreanProvider(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        super(dataOutput,"ko_kr" ,registryLookup);
    }

    @Override
    public void generateTranslations(RegistryWrapper.WrapperLookup wrapperLookup, TranslationBuilder translationBuilder) {
        translationBuilder.add(ModItems.TELEPORTER, "텔레포터");
        translationBuilder.add(ModItems.ASSASSIN_BOW, "암살용 활");
        translationBuilder.add(ModItems.DNA_SCANNER, "DNA 분석기");
        translationBuilder.add(ModItems.BAMBOO_DAGGER, "죽창");
        translationBuilder.add(ModItems.ROLE_CHECKER, "직업 확인기");
        translationBuilder.add(ModItems.SUICIDE_BOMB, "자살 폭탄");
        translationBuilder.add(ModItems.NORMAL_SWORD, "검");
        translationBuilder.add(ModItems.DAGGER, "단검");
        translationBuilder.add(ModItems.LONG_RANGED_SWORD, "대검");
        translationBuilder.add(ModItems.TRAITOR_DISRUPTOR, "교란기");
    }
}
