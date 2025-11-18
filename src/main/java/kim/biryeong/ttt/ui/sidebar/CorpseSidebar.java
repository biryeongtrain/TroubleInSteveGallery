package kim.biryeong.ttt.ui.sidebar;

import eu.pb4.sidebars.api.Sidebar;
import kim.biryeong.ttt.entity.CorpseEntity;
import kim.biryeong.ttt.player.role.Role;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.scoreboard.number.NumberFormat;
import org.jetbrains.annotations.Nullable;

public class CorpseSidebar extends Sidebar {
    private final String userName;
    private final Role role;
    private int autoCloseTicks = 100;

    public CorpseSidebar(CorpseEntity corpse) {
        super(Priority.HIGH);
        this.userName = corpse.getGameProfile().name();
        this.role = corpse.getRole();
    }



    {
        this.setDefaultNumberFormat(BlankNumberFormat.INSTANCE);
    }
}
