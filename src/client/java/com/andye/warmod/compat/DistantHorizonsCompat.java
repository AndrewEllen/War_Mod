package com.andye.warmod.compat;

import com.andye.warmod.WarMod;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/** Optional compatibility entry point that never hard-loads the Distant Horizons API. */
public final class DistantHorizonsCompat {
    private static final String BRIDGE_CLASS =
        "com.andye.warmod.compat.distanthorizons.DistantHorizonsDepthBridge";
    private static Class<?> loadedBridge;
    private static Method closeMethod;

    private DistantHorizonsCompat() { }

    public static void register() {
        if (loadedBridge != null || !FabricLoader.getInstance().isModLoaded("distanthorizons")) {
            return;
        }

        try {
            Class<?> bridge = Class.forName(BRIDGE_CLASS, true,
                DistantHorizonsCompat.class.getClassLoader());
            bridge.getMethod("register").invoke(null);
            loadedBridge = bridge;
            closeMethod = bridge.getMethod("close");
        } catch (ReflectiveOperationException | LinkageError exception) {
            WarMod.LOGGER.warn(
                "Distant Horizons is installed but War Mod could not initialize depth compatibility",
                unwrap(exception));
        }
    }

    public static void close() {
        Method close = closeMethod;
        closeMethod = null;
        loadedBridge = null;
        if (close == null) return;

        try {
            close.invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            WarMod.LOGGER.warn("Failed to close Distant Horizons compatibility resources",
                unwrap(exception));
        }
    }

    private static Throwable unwrap(final Throwable throwable) {
        if (throwable instanceof InvocationTargetException invocation
            && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return throwable;
    }
}
