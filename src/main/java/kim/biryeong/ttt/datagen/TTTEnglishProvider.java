package kim.biryeong.ttt.datagen;

import kim.biryeong.ttt.item.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;

public class TTTEnglishProvider extends FabricLanguageProvider {
    protected TTTEnglishProvider(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        super(dataOutput, "en_us", registryLookup);
    }

    @Override
    public void generateTranslations(RegistryWrapper.WrapperLookup wrapperLookup, TranslationBuilder translationBuilder) {
        translationBuilder.add(ModItems.TELEPORTER, "Teleporter");
        translationBuilder.add(ModItems.ASSASSIN_BOW, "Silent Bow");
        translationBuilder.add(ModItems.DNA_SCANNER, "DNA Scanner");
        translationBuilder.add(ModItems.BAMBOO_DAGGER, "Bamboo Dagger");
        translationBuilder.add(ModItems.ROLE_CHECKER, "Role Checker");
        translationBuilder.add(ModItems.SUICIDE_BOMB, "Suicide Bomb");
        translationBuilder.add(ModItems.NORMAL_SWORD, "Sword");
        translationBuilder.add(ModItems.DAGGER, "Dagger");
        translationBuilder.add(ModItems.LONG_RANGED_SWORD, "BroadSword");
        translationBuilder.add(ModItems.TRAITOR_DISRUPTOR, "Disruptor");
    }
}
