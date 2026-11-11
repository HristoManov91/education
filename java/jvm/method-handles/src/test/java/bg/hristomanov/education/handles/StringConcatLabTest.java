package bg.hristomanov.education.handles;

import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringConcatLabTest {

    @Test
    void linkedCallSiteMatchesOrdinaryJavaStringConversion() throws Throwable {
        assertEquals(MethodType.methodType(String.class, String.class, int.class),
                StringConcatLab.linkedType());
        for (String sku : new String[]{"widget", "", "А", null}) {
            for (int count : new int[]{0, 3, -7}) {
                assertEquals(StringConcatLab.plainJava(sku, count),
                        StringConcatLab.prelinkedCallSite(sku, count));
            }
        }
    }
}
