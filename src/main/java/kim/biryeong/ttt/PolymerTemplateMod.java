package kim.biryeong.ttt;

import kim.biryeong.ttt.block.ModBlocks;
import kim.biryeong.ttt.command.CommandInitializer;
import kim.biryeong.ttt.entity.TTTEntityType;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.item.ModItems;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolymerTemplateMod implements ModInitializer {
	public static final String MOD_ID = "polymer-template-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.initialize();
		ModItems.initialize();
		TTTEntityType.initialize();
		Events.registerEvents();
		LOGGER.info("Hello Fabric world!");

		ServerLifecycleEvents.SERVER_STARTING.register(GameManager::setServer);

		CommandRegistrationCallback.EVENT.register((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> CommandInitializer.registerCommands(commandDispatcher));
	}
}