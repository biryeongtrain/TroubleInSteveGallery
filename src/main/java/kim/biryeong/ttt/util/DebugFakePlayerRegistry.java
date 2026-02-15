package kim.biryeong.ttt.util;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugFakePlayerRegistry {
    private static final Set<UUID> REGISTERED_FAKE_PLAYERS = ConcurrentHashMap.newKeySet();

    private DebugFakePlayerRegistry() {
        throw new IllegalStateException("Utility class");
    }

    public static void register(UUID uuid) {
        REGISTERED_FAKE_PLAYERS.add(uuid);
    }

    public static boolean unregister(UUID uuid) {
        return REGISTERED_FAKE_PLAYERS.remove(uuid);
    }

    public static boolean contains(UUID uuid) {
        return REGISTERED_FAKE_PLAYERS.contains(uuid);
    }

    public static Set<UUID> snapshot() {
        return Set.copyOf(REGISTERED_FAKE_PLAYERS);
    }
}
