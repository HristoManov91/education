// Expected compiler failure: the 'internal' package is NOT exported.
import org.education.jpms.library.internal.Vault;

public final class DeniedCompile {
    private final Vault forbiddenDependency = new Vault();
}
