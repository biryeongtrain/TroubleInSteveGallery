package kim.biryeong.ttt.datagen;

import kim.biryeong.ttt.item.ModItems;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.client.data.BlockStateModelGenerator;
import net.minecraft.client.data.ItemModelGenerator;
import net.minecraft.client.data.Models;

public class ModelGenerator extends FabricModelProvider {
    public ModelGenerator(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {

    }

    @Override
    public void generateItemModels(ItemModelGenerator itemModelGenerator) {
        itemModelGenerator.registerBow(ModItems.ASSASSIN_BOW);
        itemModelGenerator.register(ModItems.DNA_SCANNER, Models.GENERATED);
        itemModelGenerator.register(ModItems.TELEPORTER, Models.GENERATED);
        itemModelGenerator.register(ModItems.SUICIDE_BOMB, Models.GENERATED);
        itemModelGenerator.register(ModItems.TRAITOR_DISRUPTOR, Models.GENERATED);
    }
}
