package org.education.jpms.framework;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public final class FrameworkReader {

    private FrameworkReader() {
    }

    public static String readViaQualifiedOpens() throws ReflectiveOperationException {
        // The library explicitly opens its internal package to THIS module.
        Class<?> vaultType = Class.forName("org.education.jpms.library.internal.Vault");
        Constructor<?> constructor = vaultType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object vault = constructor.newInstance();
        Field code = vaultType.getDeclaredField("code");
        code.setAccessible(true);
        return (String) code.get(vault);
    }
}
