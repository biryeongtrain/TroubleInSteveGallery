package kim.biryeong.ttt.datagen;

import kim.biryeong.ttt.block.ModBlocks;
import kim.biryeong.ttt.item.ModComponents;
import kim.biryeong.ttt.item.ModItems;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class PolymerTemplateModDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator generator) {
		ModItems.initialize();
		ModBlocks.initialize();
		ModComponents.initialize();

		var pack = generator.createPack();
		pack.addProvider(ModelGenerator::new);
		pack.addProvider(TTTKoreanProvider::new);
		pack.addProvider(TTTEnglishProvider::new);
	}
}
