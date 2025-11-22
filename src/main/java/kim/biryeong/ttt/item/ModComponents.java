package kim.biryeong.ttt.item;

import eu.pb4.polymer.core.api.other.PolymerComponent;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class ModComponents {
    public static ComponentType<BlockPos> TELEPORTER_POS = register("teleporter_pos", ComponentType.<BlockPos>builder().codec(BlockPos.CODEC).build());

    public static <T> ComponentType<T> register(String id, ComponentType<T> componentType) {
        PolymerComponent.registerDataComponent(componentType);
        return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(TroubleInTerroristTownMod.MOD_ID, id), componentType);
    }

    public static void initialize() {
    }
}
