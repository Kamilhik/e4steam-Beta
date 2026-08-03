package link.e4steam;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Compact e4steam-owned pixel glyphs embedded in Minecraft's default font. */
public final class FriendsUiIcons {
    private static final String FRIENDS = "\ue400";
    private static final String INVITE = "\ue401";
    private static final String JOIN = "\ue402";
    private static final String PROFILE = "\ue403";
    private static final String REFRESH = "\ue404";

    private FriendsUiIcons() {
    }

    public static Component friends() {
        return icon(FRIENDS);
    }

    public static Component invite() {
        return icon(INVITE);
    }

    public static Component join() {
        return icon(JOIN);
    }

    public static Component profile() {
        return icon(PROFILE);
    }

    public static Component refresh() {
        return icon(REFRESH);
    }

    private static Component icon(String glyph) {
        return Mirror.withStyle(Mirror.literal(glyph), FriendsUiIcons::withIconFont);
    }

    /**
     * Font APIs changed twice across supported Minecraft versions. Keep that narrow
     * incompatibility here instead of leaking version checks into screens and mixins.
     */
    private static Style withIconFont(Style style) {
        for (Method method : Style.class.getMethods()) {
            if (method.getParameterCount() != 1 || !Style.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                Object descriptor = createFontArgument(method.getParameterTypes()[0]);
                if (descriptor != null) {
                    return (Style) method.invoke(style, descriptor);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next one-argument Style transformer.
            }
        }
        return style;
    }

    private static Object createFontArgument(Class<?> argumentType) throws ReflectiveOperationException {
        Object identifier = createIdentifier(argumentType);
        if (identifier != null) {
            return identifier;
        }
        if (!argumentType.isInterface() && !Modifier.isAbstract(argumentType.getModifiers())) {
            return null;
        }
        for (Class<?> implementation : argumentType.getDeclaredClasses()) {
            if (!argumentType.isAssignableFrom(implementation)
                    || implementation.isInterface()
                    || Modifier.isAbstract(implementation.getModifiers())) {
                continue;
            }
            for (Constructor<?> constructor : implementation.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 1) {
                    identifier = createIdentifier(constructor.getParameterTypes()[0]);
                    if (identifier != null) {
                        constructor.setAccessible(true);
                        return constructor.newInstance(identifier);
                    }
                }
            }
        }
        return null;
    }

    private static Object createIdentifier(Class<?> type) throws ReflectiveOperationException {
        String name = type.getName().toLowerCase();
        if (!name.contains("identifier") && !name.contains("resourcelocation") && !name.endsWith("class_2960")) {
            return null;
        }
        for (Method factory : type.getMethods()) {
            if (Modifier.isStatic(factory.getModifiers())
                    && type.isAssignableFrom(factory.getReturnType())
                    && factory.getParameterCount() == 2
                    && factory.getParameterTypes()[0] == String.class
                    && factory.getParameterTypes()[1] == String.class) {
                return factory.invoke(null, "e4steam_minecraft", "icons");
            }
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(String.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance("e4steam_minecraft", "icons");
        } catch (NoSuchMethodException ignored) {
            for (Method factory : type.getMethods()) {
                if (Modifier.isStatic(factory.getModifiers())
                        && type.isAssignableFrom(factory.getReturnType())
                        && factory.getParameterCount() == 1
                        && factory.getParameterTypes()[0] == String.class) {
                    return factory.invoke(null, "e4steam_minecraft:icons");
                }
            }
        }
        return null;
    }
}
