package kim.biryeong.ttt;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import kim.biryeong.ttt.block.ModBlocks;
import kim.biryeong.ttt.command.CommandInitializer;
import kim.biryeong.ttt.entity.TTTEntityType;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.item.ModComponents;
import kim.biryeong.ttt.item.ModItems;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * ModInitializer 라고 해서 플러그인의 JavaPlugin 비슷한 거임.
 * 이벤트는 {@link Events} 에 있음.
 */
public class TroubleInTerroristTownMod implements ModInitializer {
	public static final String MOD_ID = "ttt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PolymerResourcePackUtils.addModAssets(MOD_ID);
		ModBlocks.initialize();
		ModItems.initialize();
		TTTEntityType.initialize();
		Events.registerEvents();
		ModComponents.initialize();
		LOGGER.info("Hello Fabric world!");

		ServerLifecycleEvents.SERVER_STARTING.register(GameManager::setServer);

		CommandRegistrationCallback.EVENT.register((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> CommandInitializer.registerCommands(commandDispatcher));
	}
}