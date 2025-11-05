package com.example;

import com.example.block.ModBlocks;
import com.example.item.ModItems;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolymerTemplateMod implements ModInitializer {
	public static final String MOD_ID = "polymer-template-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.initialize();
		ModItems.initialize();

		LOGGER.info("Hello Fabric world!");
	}
}