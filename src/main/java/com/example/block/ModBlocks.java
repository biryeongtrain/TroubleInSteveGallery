package com.example.block;

import com.example.PolymerTemplateMod;
import eu.pb4.polymer.core.api.block.SimplePolymerBlock;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class ModBlocks {
    /*public static final Block EXAMPLE_BLOCK = register(
            "example_block",
            settings -> new SimplePolymerBlock(settings, Blocks.CLAY),
            AbstractBlock.Settings.create().sounds(BlockSoundGroup.GRAVEL),
            Items.CLAY
    );*/

    private static Block register(String name, Function<AbstractBlock.Settings, Block> blockFactory, AbstractBlock.Settings settings, Item polymerItem) {
        RegistryKey<Block> blockKey = keyOfBlock(name);
        Block block = blockFactory.apply(settings.registryKey(blockKey));

        if (polymerItem != null) {
            RegistryKey<Item> itemKey = keyOfItem(name);

            BlockItem blockItem = new PolymerBlockItem(block, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey(), polymerItem);
            Registry.register(Registries.ITEM, itemKey, blockItem);
        }

        return Registry.register(Registries.BLOCK, blockKey, block);
    }

    private static RegistryKey<Block> keyOfBlock(String name) {
        return RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(PolymerTemplateMod.MOD_ID, name));
    }

    private static RegistryKey<Item> keyOfItem(String name) {
        return RegistryKey.of(RegistryKeys.ITEM, Identifier.of(PolymerTemplateMod.MOD_ID, name));
    }

    public static void initialize() {}

}