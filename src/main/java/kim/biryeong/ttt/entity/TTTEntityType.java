package kim.biryeong.ttt.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class TTTEntityType {
    public static final EntityType<CorpseEntity> CORPSE = register("corpse",
            EntityType.Builder.create(CorpseEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.8f)
                    .disableSaving()
                    .dropsNothing()
                    .spawnableFarFromPlayer()
                    .eyeHeight(1.62f)
                    .maxTrackingRange(32)
                    .trackingTickInterval(2)
    );

    public static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> builder) {
        var entity = builder.build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of("tts", id)));
        Registry.register(Registries.ENTITY_TYPE ,Identifier.of("tts", id), entity);
        PolymerEntityUtils.registerType(entity);

        return entity;
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(CORPSE, CorpseEntity.createCorpseEntityAttributes());
    }

}
