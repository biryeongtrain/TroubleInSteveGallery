package kim.biryeong.ttt.player.duck;

/**
 * 플레이어 인게임에서 사용되는 데이터를 가져오는 인터페이스임 <br>
 * 가져오는 법 <br>
 * <pre>
 * {@code
 *     ServerPlayerEntity player;
 *     InGameEventProvider provider = (InGameEventProvider) player;
 * }
 * </pre>
 */
public interface InGameEventProvider {
    void tts$fuse(int ticks);
    boolean tts$isBombTriggered();
    void tts$clearFuse();
    boolean ttt$isInCombat();
}
