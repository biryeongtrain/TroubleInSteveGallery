package kim.biryeong.ttt.player.role;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

/**
 * 직업들 목록. 이거 말고애초에 존재하지 않으니까 ㅇㅇ
 */
public enum Role implements StringIdentifiable {
    /**
     * 이노센트. 시민 포지션
     */
    INNOCENT("00A36C", "시민"),
    /**
     * 트레이터. 마피아 포지션
     */
    TRAITOR("D22B2B", "트레이터"),
    /**
     * 탐정. 경찰 포지션
     */
    DETECTIVE("0047AB", "탐정"),
    /**
     * 미참여자
     */
    SPECTATOR("818589", "관전자");

    public static final Codec<Role> CODEC = StringIdentifiable.createCodec(Role::values);

    public final String hexColor;
    public final String krRoleName;

    Role(String hexColor, String krRoleName) {
        this.hexColor = hexColor;
        this.krRoleName = krRoleName;
    }

    public int getHexAsInt() {
        return Integer.parseInt(this.hexColor, 16);
    }

    @Override
    public String asString() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
