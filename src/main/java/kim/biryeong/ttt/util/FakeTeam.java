package kim.biryeong.ttt.util;

import net.minecraft.scoreboard.Team;

public class FakeTeam {
    public static final Team FAKE_TEAM = new Team() {
        @Override
        public String getName() {
            return "fake_team";
        }
    };
}
