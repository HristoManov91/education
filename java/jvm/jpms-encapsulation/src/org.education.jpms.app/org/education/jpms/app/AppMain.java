package org.education.jpms.app;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import org.education.jpms.framework.FrameworkReader;
import org.education.jpms.library.api.Greetings;

public final class AppMain {

    private AppMain() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        if (args.length != 1 || !(args[0].equals("deny") || args[0].equals("allow"))) {
            throw new IllegalArgumentException("Expected: deny | allow");
        }
        System.out.println("PUBLIC=" + Greetings.welcome("Education"));
        System.out.println("FRAMEWORK=" + FrameworkReader.readViaQualifiedOpens());
        try {
            Class<?> vaultType = Class.forName("org.education.jpms.library.internal.Vault");
            Constructor<?> constructor = vaultType.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object vault = constructor.newInstance();
            Field code = vaultType.getDeclaredField("code");
            code.setAccessible(true);
            String revealed = (String) code.get(vault);
            if (!args[0].equals("allow")) {
                throw new AssertionError("Unexpected deep access without opens");
            }
            System.out.println("APP_REFLECTION_ALLOWED=" + revealed);
        } catch (InaccessibleObjectException exception) {
            if (!args[0].equals("deny")) {
                throw new AssertionError("Runtime --add-opens did not grant access", exception);
            }
            System.out.println("APP_REFLECTION_DENIED");
        }
    }
}
