package kim.biryeong.ttt.util;

import eu.pb4.polymer.core.api.other.PolymerSoundEvent;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class Sounds {
    public static final SoundEvent JIHAD_BOMB_ACTIVE = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "item.jihad_bomb.active"));
    public static final SoundEvent TESTER_ITEM_USE = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "item.tester.use"));

    private static SoundEvent register(Identifier id) {
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
    public static void initialize() {
        PolymerSoundEvent.registerOverlay(JIHAD_BOMB_ACTIVE);
        PolymerSoundEvent.registerOverlay(TESTER_ITEM_USE);
    }
}
