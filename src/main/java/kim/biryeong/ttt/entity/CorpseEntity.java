package kim.biryeong.ttt.entity;

import com.mojang.authlib.GameProfile;
import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.danse.Danse;
import de.tomalbrc.danse.entity.StatuePlayerModelEntity;
import de.tomalbrc.danse.poly.StatuePlayerPartHolder;
import de.tomalbrc.danse.registry.PlayerModelRegistry;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.*;
import eu.pb4.polymer.virtualentity.mixin.accessors.EntityAccessor;
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
    }

    public static DefaultAttributeContainer.Builder createCorpseEntityAttributes() {
        return LivingEntity.createLivingAttributes()
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
        entity.playerUuid = player.getUuid();
        entity.fetchGameProfile(entity::setProfile);
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
    public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
        if (isBurning) return ActionResult.FAIL;
        if (player.getWorld().isClient()) return ActionResult.PASS;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        var result = super.interactAt(player, hitPos, hand);
        if (player.getMainHandStack().getItem() == Items.BLAZE_ROD) {
            this.setFireTicks(1000);
            this.isBurning = true;
            this.hitboxInteraction.getDataTracker().set(FLAGS, (byte) (1 << EntityAccessor.getON_FIRE_FLAG_INDEX()));
            return result;
        }

        // TODO SGUI Execute
        if (!isRevealed && GameManager.getInstance().isAlive(serverPlayer)) {
            var playerRole = ((InGamePlayerInfoProvider) GameManager.getInstance().getPlayer(this.gameProfile.getId())).tts$getRole();
            if (playerRole == null) playerRole = Role.SPECTATOR;
            this.hitboxInteraction.setCustomName(
                    GameManager.byMiniMessage(
                            "<#color>%s's Corpse</#color>"
                                    .formatted(this.gameProfile.getName())
                                    .replace("color", playerRole.hexColor)
                    )
            );
            this.hitboxInteraction.setCustomNameVisible(true);
            this.isRevealed = true;

            GameManager.getInstance().sendMessage("%s 님이 %s 님의 시체를 찾았습니다. 그는 <#color>%s</#color> 였습니다."
                    .formatted(player.getGameProfile().getName(), this.gameProfile.getName(), playerRole.krRoleName)
                    .replace("color", playerRole.hexColor)
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
        if (this.removeIfUnNessary()) {
            return;
        }
        super.tick();

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

        if (this.getWorld().getTime() % 20 == 1) {
            return;
        }

        this.holder.tick();
    }

    private boolean removeIfUnNessary() {
        if (!GameManager.getInstance().isGameStarted()) {
            this.remove(RemovalReason.DISCARDED);
            return true;
        }

        if (GameManager.getInstance().getPlayer(this.gameProfile.getId()) == null) {
            this.remove(RemovalReason.DISCARDED);
            return true;
        }

        if (GameManager.getInstance().isAlive(GameManager.getInstance().getPlayer(this.gameProfile.getId()))) {
            this.remove(RemovalReason.DISCARDED);
            return true;
        }

        return false;
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
