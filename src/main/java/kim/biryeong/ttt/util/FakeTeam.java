package kim.biryeong.ttt.util;

public final class FakeTeam {
    public static final String TRAITOR_TEAM_NAME = "ttt:traitor_team";
    public static final String DETECTIVE_TEAM_NAME = "ttt:detective_team";

    private FakeTeam() {
        throw new IllegalStateException("Utility class");
    }
}
