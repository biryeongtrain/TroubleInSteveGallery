package kim.biryeong.ttt;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import kim.biryeong.ttt.block.ModBlocks;
import kim.biryeong.ttt.command.CommandInitializer;
import kim.biryeong.ttt.config.Config;
import kim.biryeong.ttt.entity.TTTEntityType;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.item.ModComponents;
import kim.biryeong.ttt.item.ModItems;
import kim.biryeong.ttt.resourcepack.ImageHandler;
import kim.biryeong.ttt.ui.dialog.body.AlignedItemBody;
import kim.biryeong.ttt.ui.dialog.TTTDialogs;
import kim.biryeong.ttt.ui.dialog.body.AlignedMessage;
import kim.biryeong.ttt.ui.dialog.body.HeaderMessage;
import kim.biryeong.ttt.ui.dialog.body.ImageBody;
import kim.biryeong.ttt.ui.sidebar.GameDefaultSidebar;
import kim.biryeong.ttt.util.Sounds;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


/**
 * ModInitializer 라고 해서 플러그인의 JavaPlugin 비슷한 거임.
 * 이벤트는 {@link Events} 에 있음.
 */
public class TroubleInTerroristTownMod implements ModInitializer {
	public static final String MOD_ID = "ttt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final List<Identifier> BUILT_IN_MAPS = List.of(
			Identifier.of("ttt:kitchen")
	);

	@Override
	public void onInitialize() {
		PolymerResourcePackUtils.addModAssets(MOD_ID);
		ModBlocks.initialize();
		ModItems.initialize();
		TTTEntityType.initialize();
		Events.registerEvents();
		ModComponents.initialize();
		Config.initialize();
		Sounds.initialize();
		TTTDialogs.initialize();
		ImageHandler.init();
		LOGGER.info("Hello Fabric world!");

		Registry.register(Registries.DIALOG_BODY_TYPE, Identifier.of("ttt", "aligned_message"), AlignedMessage.MAP_CODEC);
		Registry.register(Registries.DIALOG_BODY_TYPE, Identifier.of("ttt", "aligned_item"), AlignedItemBody.MAP_CODEC);
		Registry.register(Registries.DIALOG_BODY_TYPE, Identifier.of("ttt", "header_message"), HeaderMessage.MAP_CODEC);
		Registry.register(Registries.DIALOG_BODY_TYPE, Identifier.of("ttt", "image"), ImageBody.MAP_CODEC);
		ServerLifecycleEvents.SERVER_STARTING.register(GameManager::setServer);

		CommandRegistrationCallback.EVENT.register((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> CommandInitializer.registerCommands(commandDispatcher));

	}
}
