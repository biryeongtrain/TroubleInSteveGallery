package kim.biryeong.ttt.player.duck;

import kim.biryeong.ttt.player.role.Role;

public interface InGamePlayerInfoProvider {
    Role tts$getRole();
    int tts$getPoints();
    void tts$setRole(Role role);
    void tts$addPoints(int points, PointReason reason);
    void tts$clearPoints();
    boolean tts$isAlive();
    boolean tts$denyToPlay();

    enum PointReason {
        KILL,
        ROLE_PLAYING,
        PLAYED,
        WIN,
        LOSE,
    }
}
