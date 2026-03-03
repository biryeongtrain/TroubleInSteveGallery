package kim.biryeong.ttt.util;

import eu.pb4.polymer.core.api.other.PolymerSoundEvent;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class Sounds {
    public static final SoundEvent MORNING_BGM_1 = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "bgm.morning_bgm1"));
    public static final SoundEvent MORNING_BGM_2 = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "bgm.morning_bgm2"));
    public static final SoundEvent MORNING_BGM_3 = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "bgm.morning_bgm3"));
    public static final SoundEvent MORNING_BGM_4 = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "bgm.morning_bgm4"));
    public static final SoundEvent MORNING_BGM_5 = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "bgm.morning_bgm5"));
    public static final SoundEvent BGM_1 = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "bgm.bgm_1"));
    public static final SoundEvent BGM_2 = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "bgm.bgm_2"));
    public static final SoundEvent BGM_3 = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "bgm.bgm_3"));
    public static final SoundEvent COUNTDOWN_5_SEC = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "countdown.5sec"));
    public static final SoundEvent COUNTDOWN_4_SEC = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "countdown.4sec"));
    public static final SoundEvent COUNTDOWN_3_SEC = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "countdown.3sec"));
    public static final SoundEvent COUNTDOWN_2_SEC = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "countdown.2sec"));
    public static final SoundEvent COUNTDOWN_1_SEC = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "countdown.1sec"));
    public static final SoundEvent JIHAD_BOMB_ACTIVE = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "item.jihad_bomb.active"));
    public static final SoundEvent TESTER_ITEM_USE = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "item.tester.use"));
    public static final SoundEvent SABOTAGE_ITEM_USE = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "item.sabotage.use"));
    public static final SoundEvent SABOTAGE_ITEM_BEEP = register(Identifier.of(TroubleInTerroristTownMod.MOD_ID, "item.sabotage.beep"));

    private static SoundEvent register(Identifier id) {
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
    public static void initialize() {
        PolymerSoundEvent.registerOverlay(MORNING_BGM_1);
        PolymerSoundEvent.registerOverlay(MORNING_BGM_2);
        PolymerSoundEvent.registerOverlay(MORNING_BGM_3);
        PolymerSoundEvent.registerOverlay(MORNING_BGM_4);
        PolymerSoundEvent.registerOverlay(MORNING_BGM_5);
        PolymerSoundEvent.registerOverlay(BGM_1);
        PolymerSoundEvent.registerOverlay(BGM_2);
        PolymerSoundEvent.registerOverlay(BGM_3);
        PolymerSoundEvent.registerOverlay(COUNTDOWN_5_SEC);
        PolymerSoundEvent.registerOverlay(COUNTDOWN_4_SEC);
        PolymerSoundEvent.registerOverlay(COUNTDOWN_3_SEC);
        PolymerSoundEvent.registerOverlay(COUNTDOWN_2_SEC);
        PolymerSoundEvent.registerOverlay(COUNTDOWN_1_SEC);
        PolymerSoundEvent.registerOverlay(JIHAD_BOMB_ACTIVE);
        PolymerSoundEvent.registerOverlay(TESTER_ITEM_USE);
        PolymerSoundEvent.registerOverlay(SABOTAGE_ITEM_USE);
        PolymerSoundEvent.registerOverlay(SABOTAGE_ITEM_BEEP);
    }
}
