package com.andye.warmod.warhead.client.render;

import com.andye.warmod.WarMod;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Runtime Iris shader-pack detection without a hard Iris dependency.
 *
 * <p>Iris can remain installed while its shader pack is toggled on and off at
 * runtime. Renderer selection must therefore follow isShaderPackInUse(), not
 * merely the presence of the mod and not a value captured once at class load.</p>
 */
public final class IrisShaderState {
    private static final boolean IRIS_INSTALLED =
        FabricLoader.getInstance().isModLoaded("iris");
    private static boolean resolved;
    private static boolean warned;
    private static Object irisApi;
    private static Method shaderPackInUse;

    private IrisShaderState() { }

    public static boolean active() {
        if (!IRIS_INSTALLED || !resolve()) return false;
        try {
            return Boolean.TRUE.equals(shaderPackInUse.invoke(irisApi));
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnOnce("Iris shader state could not be queried; retaining the stable renderer", exception);
            return false;
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) return irisApi != null && shaderPackInUse != null;
        resolved = true;
        try {
            Class<?> irisApiClass = Class.forName(
                "net.irisshaders.iris.api.v0.IrisApi", false,
                IrisShaderState.class.getClassLoader());
            irisApi = irisApiClass.getMethod("getInstance").invoke(null);
            shaderPackInUse = irisApiClass.getMethod("isShaderPackInUse");
            return irisApi != null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            irisApi = null;
            shaderPackInUse = null;
            warnOnce("Iris is installed but its API could not be resolved; retaining the stable renderer", exception);
            return false;
        }
    }

    private static void warnOnce(final String message, final Throwable exception) {
        if (warned) return;
        warned = true;
        WarMod.LOGGER.warn(message, exception);
    }
}
