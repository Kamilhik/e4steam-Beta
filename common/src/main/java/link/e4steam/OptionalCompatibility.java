package link.e4steam;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolates optional UI and command integrations from changes made by other
 * mods. Core networking hooks deliberately do not use this guard: silently
 * losing those hooks would leave a world advertised without a working
 * transport.
 */
public final class OptionalCompatibility {
    private static final Set<String> REPORTED_HOOKS = ConcurrentHashMap.newKeySet();

    private OptionalCompatibility() {
    }

    public static void run(String hook, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError failure) {
            if (REPORTED_HOOKS.add(hook)) {
                E4steamClient.LOGGER.warn(
                        "Disabled optional e4steam integration '{}' because another mod or Minecraft version changed it",
                        hook,
                        failure
                );
            }
        }
    }
}
