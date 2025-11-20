package kim.biryeong.ttt.entity;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.danse.Danse;
import de.tomalbrc.danse.entity.StatuePlayerModelEntity;
import de.tomalbrc.danse.poly.StatuePlayerPartHolder;
import de.tomalbrc.danse.registry.PlayerModelRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.*;
import eu.pb4.polymer.virtualentity.mixin.accessors.EntityAccessor;
import eu.pb4.sidebars.api.Sidebar;
import eu.pb4.sidebars.api.SidebarInterface;
import eu.pb4.sidebars.impl.SidebarHolder;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.sidebar.CorpseSidebar;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("unused")
public class CorpseEntity extends StatuePlayerModelEntity implements AnimatedEntity, Leashable {
    private boolean isRevealed = false;
    private GameProfile gameProfile;
    private Role role;
    private LeashData leashData;
    private boolean isBurning = false;
    private int burnTicks = 0;
    private boolean animationPlayed = false;
    private InteractionElement hitboxInteraction;
    private DamageSource damageSource;

    public CorpseEntity(EntityType<CorpseEntity> type, World world) {
        super(type, world);
        this.role = Role.SPECTATOR;
        var properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures",
                "ewogICJ0aW1lc3RhbXAiIDogMTYxNDk0NDg4ODg4OSwKICAicHJvZmlsZUlkIiA6ICI1N2IzZGZiNWY4YTY0OWUyOGI1NDRlNGZmYzYzMjU2ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJYaWthcm8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzdmYzc1ZTBlYzAwNDAyMjMyOTZhYTRkMDhiZDI2YmU0ZDE3MmU4ZGUwNzE4NTU4ODgyMmZhZTM2M2QyMjMxOSIKICAgIH0KICB9Cn0=",
                "R/dm6ic4CYbsr66Iz859K5r1MVd7y08FUvOmJgKTE5KRcPdDNe71Vv61jzh0jQ9QeZJXsHe4+58RY2LiXn7LdPKWpNd+ljK2K4n00Yjp/MM9s6ppNOAQj32LY5UuwcXPUkTSQfr2GROM9zvY93lAuILr6xodvUoIrPcbBDHgxuN6FDiE1jKfFF5z2yZIHOVZXqJPJ+0ri1sw3mjMhbO3dPdpzTW24olgR3wqbXgfEwIeiMk1En+wBtce6ZnNHNXIaMj4fFDAsMmFKqvFcPY8SjfjW/jWBDYNFUCMpxTS2XduQGhSoSlNXG+OrI93Ya/iObGeqAp9WCqFvkV8azyG1VTFfegZCFrUwKV+819B8Q3H3JzJOzES9zvhX5CDKYaE4QvWAqGzTOVw7h0NxtOh9alFkbRR2lWFiBhUMT8EqRjkb+OyBVe9vGRJOU448aLQFyuEWLICje9FAmOHRH0JFpMDEKCLvAAZKZAOx9jceQKrcrcAS0f9nnqjWLLrWMK8lWh0CNcPN1P51rQsxMUlWddNEig+RyjOLHIz/fsv3EQ7yycWkeFfkxq0NAVZGajp4T3NhtWG+WlYywafy5Gtys0Mmv4CXu6xzoUdeLhtMjwgmqfdatQlAJGiZCuSMc1KwWis2inI1YDg5jIy8BTViFBGn76mks21iUEpL4JP8FU="
        )));
        this.gameProfile = new GameProfile(UUID.fromString("a6476ab8-e7d3-4ac8-8d65-ce03f6d5e6e2"), "andlist", properties);
        this.setProfile(gameProfile);
    }

    public static DefaultAttributeContainer.Builder createCorpseEntityAttributes() {
        return PlayerLikeEntity.createLivingAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.1);
    }


    @Override
    public void setModel(Model model) {
        if (this.holder != null) {
            this.holder.destroy();
        }

        this.holder = new StatuePlayerPartHolder<>(this, model) {
            @Override
            public void setupHitbox() {
                this.hitboxInteraction = InteractionElement.redirect(this.parent);
                this.hitboxInteraction.setSize(this.dimensions.height() / 2, this.dimensions.width() + 0.35f);
                this.hitboxInteraction.ignorePositionUpdates();
                this.hitboxInteraction.setOffset(new Vec3d(0, 1, 0));
                Danse.VIRTUAL_ENTITY_PICK_MAP.put(this.hitboxInteraction.getEntityId(), this.parent.getId());
                this.addPassengerElement(this.hitboxInteraction);
                ((CorpseEntity) parent).hitboxInteraction = this.hitboxInteraction;
            }
        };
        this.holder.setupHitbox();
        EntityAttachment.ofTicking(this.holder, this);
    }

    public static CorpseEntity createCorpse(World world, ServerPlayerEntity player, DamageSource source) {
        var entity = new CorpseEntity(TTTEntityType.CORPSE, world);
        entity.gameProfile = player.getGameProfile();
        entity.setProfile(entity.gameProfile);
        entity.role = ((InGamePlayerInfoProvider) player).tts$getRole();
        entity.damageSource = source;
        entity.setPosition(player.getSyncedPos());
        return entity;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInteractable() {
        return true;
    }

    @Override
    public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
        if (isBurning) return ActionResult.FAIL;
        if (player.getEntityWorld().isClient()) return ActionResult.PASS;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        var result = super.interactAt(player, hitPos, hand);
        if (result == ActionResult.FAIL) return result;
        if (player.getMainHandStack().getItem() == Items.BLAZE_ROD) {
            this.setFireTicks(1000);
            this.isBurning = true;
            this.hitboxInteraction.getDataTracker().set(FLAGS, (byte) (1 << EntityAccessor.getON_FIRE_FLAG_INDEX()));
            return result;
        }

        // TODO SGUI Execute
        if (!isRevealed) {
            var playerRole = ((InGamePlayerInfoProvider) GameManager.getInstance().getPlayer(this.gameProfile.id())).tts$getRole();
            if (playerRole == null) playerRole = Role.SPECTATOR;
            this.hitboxInteraction.setCustomName(
                    GameManager.byMiniMessage(
                            "<#color>%s's Corpse</#color>"
                                    .formatted(this.gameProfile.name())
                                    .replace("color", String.valueOf(playerRole.hexColor))
                    )
            );
            this.hitboxInteraction.setCustomNameVisible(true);
            this.isRevealed = true;

            GameManager.getInstance().sendMessage("%s 님이 %s 님의 시체를 찾았습니다. 그는 <#color>%s</#color> 였습니다."
                    .formatted(player.getStringifiedName(), this.gameProfile.name(), playerRole.name())
                    .replace("color", String.valueOf(playerRole.hexColor))
            );

            player.sendMessage(GameManager.byMiniMessage("사망한 시체 첫 조사를 통해 <green>2 포인트</green> 획득!"), false);
            InGamePlayerInfoProvider provider = (InGamePlayerInfoProvider) player;
            provider.tts$addPoints(2, InGamePlayerInfoProvider.PointReason.ROLE_PLAYING);
        }

        SidebarHolder sidebarHolder = SidebarHolder.of(serverPlayer.networkHandler);
        Optional<SidebarInterface> hasSidebar = sidebarHolder.sidebarApi$getAll().stream().filter(sidebar -> sidebar instanceof CorpseSidebar).findAny();

        hasSidebar.ifPresent(sidebarHolder::sidebarApi$remove);

        var sidebar = new CorpseSidebar(this, serverPlayer);


        if (player.getMainHandStack().getItem() == Items.STICK) {
        }

        return ActionResult.PASS;
    }


    @Override
    public void tick() {
        super.tick();
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() && !GameManager.getInstance().isGameStarted()) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }
        if (!this.animationPlayed) {
            this.holder
                    .getAnimator()
                    .playAnimation(
                            "death_damage2",
                            0,
                            (frame) -> {
                                if (frame == 60) this.holder.getAnimator().pauseAnimation("death_damage2");
                            },
                            (player) -> {}
                    );
            this.animationPlayed = true;
        }

        if (this.isBurning && burnTicks++ > 200) {
            this.remove(RemovalReason.DISCARDED);
        }

        if (this.getEntityWorld().getTime() % 20 == 1) {
            return;
        }

        this.holder.tick();
    }

    @Override
    public boolean canBeLeashedTo(Entity entity) {
        return true;
    }

    @Override
    public double getElasticLeashDistance() {
        return 2.0;
    }

    @Override
    public @Nullable LeashData getLeashData() {
        return this.leashData;
    }

    @Override
    public void setLeashData(@Nullable Leashable.LeashData leashData) {
        this.leashData = leashData;
    }

    public Role getRole() {
        return role;
    }

    public GameProfile getGameProfile() {
        return gameProfile;
    }

    public @Nullable DamageSource getDamageSource() {
        return damageSource;
    }

    {
        this.setModel(PlayerModelRegistry.getModel("death_damage2"));
    }
}
