package kim.biryeong.ttt.player.duck;

public interface InGameEventProvider {
    void tts$fuse(int ticks);
    boolean tts$isBombTriggered();
    void tts$clearFuse();
}
