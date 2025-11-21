package kim.biryeong.ttt.ui.sidebar;

import eu.pb4.sidebars.api.Sidebar;
import eu.pb4.sidebars.api.lines.SimpleSidebarLine;
import kim.biryeong.ttt.game.manager.GameManager;

public class GameDefaultSidebar extends Sidebar {
    public GameDefaultSidebar() {
        super(Priority.MEDIUM);
    }

    {
        this.setTitle(GameManager.byMiniMessage("<red>Trouble</red> in <green>Terrorist</green> <blue>Town</blue>"));

    }
}
