package kim.biryeong.ttt.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 2-beolsik keyboard based English-to-Korean converter used by team chat commands.
 */
public final class KoreanKeyboardConverter {
    private static final int HANGUL_BASE = 0xAC00;
    private static final String CHOSEONG_JAMO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String JUNGSEONG_JAMO = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";

    private static final Map<String, Integer> INITIAL_MAP = buildInitialMap();
    private static final Map<String, Integer> MEDIAL_MAP = buildMedialMap();
    private static final Map<String, Integer> FINAL_MAP = buildFinalMap();

    private KoreanKeyboardConverter() {
        throw new IllegalStateException("Utility class");
    }

    public static String convertChat(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        if (containsKoreanAndEnglish(input)) {
            return input;
        }

        if (!input.contains("\"")) {
            return engToKor(input);
        }

        String[] segments = input.split("\"", -1);
        StringBuilder converted = new StringBuilder();
        for (int index = 0; index < segments.length; index++) {
            if (index % 2 == 0) {
                converted.append(engToKor(segments[index]));
            } else {
                converted.append(segments[index]);
            }

            if (index < segments.length - 1) {
                converted.append('"');
            }
        }

        return converted.toString();
    }

    static boolean containsKoreanAndEnglish(String input) {
        boolean hasKorean = false;
        boolean hasEnglish = false;
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (isKorean(current)) {
                hasKorean = true;
            } else if (isAsciiAlpha(current)) {
                hasEnglish = true;
            }

            if (hasKorean && hasEnglish) {
                return true;
            }
        }

        return false;
    }

    private static boolean isKorean(char current) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(current);
        return Character.UnicodeBlock.HANGUL_SYLLABLES.equals(block)
                || Character.UnicodeBlock.HANGUL_JAMO.equals(block)
                || Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO.equals(block)
                || Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_A.equals(block)
                || Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_B.equals(block);
    }

    private static String engToKor(String value) {
        StringBuilder converted = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            int tokenStart = index;
            char current = value.charAt(index);
            if (!isAsciiAlpha(current)) {
                converted.append(current);
                continue;
            }

            int initialCode = getInitial(value, index);
            if (initialCode >= 0) {
                index++;
            }

            int medialCode;
            int tempMedialCode = getDoubleMedial(value, index);
            if (tempMedialCode >= 0) {
                medialCode = tempMedialCode;
                index += 2;
            } else {
                medialCode = getSingleMedial(value, index);
                if (medialCode >= 0) {
                    index++;
                }
            }

            int finalCode = -1;
            if (initialCode < 0 || medialCode < 0) {
                index--;
            } else {
                int tempFinalCode = getDoubleFinal(value, index);
                if (tempFinalCode >= 0) {
                    finalCode = tempFinalCode;
                    int nextMedialCode = getSingleMedial(value, index + 2);
                    if (nextMedialCode >= 0) {
                        finalCode = getSingleFinal(value, index);
                    } else {
                        index++;
                    }
                } else {
                    int nextMedialCode = getSingleMedial(value, index + 1);
                    if (nextMedialCode >= 0) {
                        finalCode = -1;
                        index--;
                    } else if (isInRange(value, index) && !isAsciiAlpha(value.charAt(index))) {
                        finalCode = -1;
                        index--;
                    } else {
                        finalCode = getSingleFinal(value, index);
                    }
                }
            }

            if (initialCode >= 0 && medialCode >= 0) {
                converted.append((char) (HANGUL_BASE + initialCode * 21 * 28 + medialCode * 28 + finalCode + 1));
            } else if (initialCode >= 0) {
                converted.append(CHOSEONG_JAMO.charAt(initialCode));
            } else if (medialCode >= 0) {
                converted.append(JUNGSEONG_JAMO.charAt(medialCode));
            } else {
                converted.append(value.charAt(tokenStart));
            }
        }

        return converted.toString();
    }

    private static int getInitial(String value, int index) {
        return getSingle(INITIAL_MAP, value, index);
    }

    private static int getSingleMedial(String value, int index) {
        return getSingle(MEDIAL_MAP, value, index);
    }

    private static int getDoubleMedial(String value, int index) {
        return getDouble(MEDIAL_MAP, value, index);
    }

    private static int getSingleFinal(String value, int index) {
        return getSingle(FINAL_MAP, value, index);
    }

    private static int getDoubleFinal(String value, int index) {
        return getDouble(FINAL_MAP, value, index);
    }

    private static int getSingle(Map<String, Integer> map, String value, int index) {
        if (!isInRange(value, index)) {
            return -1;
        }
        return map.getOrDefault(value.substring(index, index + 1), -1);
    }

    private static int getDouble(Map<String, Integer> map, String value, int index) {
        if (index < 0 || index + 2 > value.length()) {
            return -1;
        }
        return map.getOrDefault(value.substring(index, index + 2), -1);
    }

    private static boolean isInRange(String value, int index) {
        return index >= 0 && index < value.length();
    }

    private static boolean isAsciiAlpha(char current) {
        return (current >= 'a' && current <= 'z') || (current >= 'A' && current <= 'Z');
    }

    private static Map<String, Integer> buildInitialMap() {
        Map<String, Integer> map = new HashMap<>();
        put(map, 0, "r");
        put(map, 1, "R");
        put(map, 2, "s", "S");
        put(map, 3, "e");
        put(map, 4, "E");
        put(map, 5, "f", "F");
        put(map, 6, "a", "A");
        put(map, 7, "q");
        put(map, 8, "Q");
        put(map, 9, "t");
        put(map, 10, "T");
        put(map, 11, "d", "D");
        put(map, 12, "w");
        put(map, 13, "W");
        put(map, 14, "c", "C");
        put(map, 15, "z", "Z");
        put(map, 16, "x", "X");
        put(map, 17, "v", "V");
        put(map, 18, "g", "G");
        return Map.copyOf(map);
    }

    private static Map<String, Integer> buildMedialMap() {
        Map<String, Integer> map = new HashMap<>();
        put(map, 0, "k", "K");
        put(map, 1, "o");
        put(map, 2, "i", "I");
        put(map, 3, "O");
        put(map, 4, "j", "J");
        put(map, 5, "p");
        put(map, 6, "u", "U");
        put(map, 7, "P");
        put(map, 8, "h", "H");
        put(map, 9, "hk", "HK", "hK", "Hk");
        put(map, 10, "ho", "Ho");
        put(map, 11, "hl", "HL", "hL", "Hl");
        put(map, 12, "y", "Y");
        put(map, 13, "n", "N");
        put(map, 14, "nj", "NJ", "nJ", "Nj");
        put(map, 15, "np", "Np");
        put(map, 16, "nl", "NL", "nL", "Nl");
        put(map, 17, "b", "B");
        put(map, 18, "m", "M");
        put(map, 19, "ml", "ML", "mL", "Ml");
        put(map, 20, "l", "L");
        return Map.copyOf(map);
    }

    private static Map<String, Integer> buildFinalMap() {
        Map<String, Integer> map = new HashMap<>();
        put(map, 0, "r");
        put(map, 1, "R");
        put(map, 2, "rt");
        put(map, 3, "s", "S");
        put(map, 4, "sw", "Sw");
        put(map, 5, "sg", "SG", "sG", "Sg");
        put(map, 6, "e");
        put(map, 7, "f", "F");
        put(map, 8, "fr", "Fr");
        put(map, 9, "fa", "FA", "fA", "Fa");
        put(map, 10, "fq", "Fq");
        put(map, 11, "ft", "Ft");
        put(map, 12, "fx", "FX", "fX", "Fx");
        put(map, 13, "fv", "FV", "fV", "Fv");
        put(map, 14, "fg", "FG", "fG", "Fg");
        put(map, 15, "a", "A");
        put(map, 16, "q");
        put(map, 17, "qt");
        put(map, 18, "t");
        put(map, 19, "T");
        put(map, 20, "d", "D");
        put(map, 21, "w");
        put(map, 22, "c", "C");
        put(map, 23, "z", "Z");
        put(map, 24, "x", "X");
        put(map, 25, "v", "V");
        put(map, 26, "g", "G");
        return Map.copyOf(map);
    }

    private static void put(Map<String, Integer> map, int index, String... tokens) {
        for (String token : tokens) {
            map.put(token, index);
        }
    }
}
