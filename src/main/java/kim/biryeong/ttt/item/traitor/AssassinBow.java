package kim.biryeong.ttt.item.traitor;

import eu.pb4.polymer.core.api.item.PolymerItem;
import kim.biryeong.ttt.item.detective.NonThrowable;
import kim.biryeong.ttt.mixin.PersistentProjectileEntityAccessor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import xyz.nucleoid.packettweaker.PacketContext;

public class AssassinBow extends BowItem implements NonThrowable, PolymerItem {
    public AssassinBow(Settings settings) {
        super(settings);
    }

    @Override
    protected ProjectileEntity createArrowEntity(World world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical) {
        PersistentProjectileEntity arrow = (PersistentProjectileEntity) super.createArrowEntity(world, shooter, weaponStack, projectileStack, critical);
       arrow.setNoGravity(true);
       arrow.setDamage(0.3);
        PersistentProjectileEntityAccessor accessor = (PersistentProjectileEntityAccessor) arrow;
        accessor.ttt$setPierceLevel((byte) 2);
        arrow.speed *= 20;
       return arrow;
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.BOW;
    }
}
