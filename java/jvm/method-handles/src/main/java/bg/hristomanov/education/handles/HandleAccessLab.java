package bg.hristomanov.education.handles;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * A MethodHandle acts as a capability. Lookup checks rights at creation time;
 * do not hand privileged handles (or private Lookup objects) to untrusted code.
 */
public final class HandleAccessLab {

    private HandleAccessLab() {
    }

    public static final class Vault {
        private String reveal(String prefix) {
            return prefix + ":lesson-secret";
        }
    }

    public static MethodHandle publicLookupAttempt()
            throws ReflectiveOperationException {
        return MethodHandles.publicLookup().findVirtual(
                Vault.class, "reveal", MethodType.methodType(String.class, String.class));
    }

    /**
     * Intentionally returns a privileged handle only for this SECURITY LESSON.
     * An application should keep it private and enforce its own authorization.
     */
    public static MethodHandle unsafeDelegatedCapabilityForLesson()
            throws ReflectiveOperationException {
        // Outer class and nested Vault are nestmates.
        return MethodHandles.lookup().findVirtual(
                Vault.class, "reveal", MethodType.methodType(String.class, String.class));
    }

    public static String invokeGrantedHandle(
            MethodHandle capability, Vault vault, String prefix) throws Throwable {
        return (String) capability.invokeExact(vault, prefix);
    }
}
