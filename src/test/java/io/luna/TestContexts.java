package io.luna;

import io.luna.game.plugin.PluginBootstrap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Builds the context a booted server owns, for tests that need real Luna objects. */
public final class TestContexts {

    private TestContexts() {
    }

    /**
     * A new context that is also the one Kotlin content reaches through {@code api.predef}. The predef reads that
     * binding once per JVM, so every test in the JVM has to share the context this returns.
     */
    public static LunaContext newBoundContext() {
        LunaContext context = new LunaContext();
        try {
            Method setBindings = PluginBootstrap.class.getDeclaredMethod("setBindings", LunaContext.class);
            setBindings.setAccessible(true);
            setBindings.invoke(null, context);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("PluginBootstrap no longer binds the context the way this helper expects", e);
        }
        return context;
    }
}
