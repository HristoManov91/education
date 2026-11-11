package bg.hristomanov.education.handles;

import java.lang.invoke.MethodHandle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HandleAccessLabTest {

    @Test
    void publicLookupCannotLinkPrivateMethod() {
        assertThrows(IllegalAccessException.class, HandleAccessLab::publicLookupAttempt);
    }

    @Test
    void trustedCreatorCanDelegatePrivateCapabilityWithoutCallerRechecking() throws Throwable {
        MethodHandle capability = HandleAccessLab.unsafeDelegatedCapabilityForLesson();
        HandleAccessLab.Vault receiver = new HandleAccessLab.Vault();

        assertEquals("ok:lesson-secret",
                HandleAccessLab.invokeGrantedHandle(capability, receiver, "ok"));
        // This is an explicit *security warning*, not a recommended API.
    }
}
