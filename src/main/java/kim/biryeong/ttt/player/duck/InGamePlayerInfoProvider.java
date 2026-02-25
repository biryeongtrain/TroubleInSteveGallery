package kim.biryeong.ttt.player.duck;

import kim.biryeong.ttt.player.ItemLoadout;
import kim.biryeong.ttt.player.role.Role;

/**
 * 플레이어 게임 정보를 가져오는 인터페이스임 <br>
 * 가져오는 법 <br>
 * <pre>
 * {@code
 *     ServerPlayerEntity player;
 *     InGamePlayerInfoProvider provider = (InGamePlayerInfoProvider) player;
 * }
 * </pre>
 */
public interface InGamePlayerInfoProvider {
    Role tts$getRole();
    int tts$getPoints();
    void tts$setRole(Role role);
    void tts$setDenyToPlay(boolean denyToPlay);
    void tts$setTipsEnabled(boolean enabled);
    void tts$setBgmEnabled(boolean enabled);
    /**
     * Updates the currently selected item loadout for this player.
     */
    void tts$setItemLoadout(ItemLoadout loadout);
    void tts$addPoints(int points, PointReason reason);
    void tts$clearPoints();
    boolean tts$isAlive();
    boolean tts$denyToPlay();
    boolean tts$tipsEnabled();
    boolean tts$bgmEnabled();
    void tts$clearSidebarTime();
    ItemLoadout tts$getItemLoadout();

    enum PointReason {
        KILL,
        ROLE_PLAYING,
        PLAYED,
        WIN,
        LOSE,
    }
}
