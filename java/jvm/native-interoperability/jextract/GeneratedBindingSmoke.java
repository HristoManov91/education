package bg.hristomanov.education.ffi.manual;

import static bg.hristomanov.education.ffi.generated.native_math_h.edu_add;
import static bg.hristomanov.education.ffi.generated.native_math_h.edu_square;

/**
 * Optional jextract lab source. Deliberately outside src/main and src/test:
 * it must only compile after an explicit jextract invocation.
 */
public final class GeneratedBindingSmoke {

    private GeneratedBindingSmoke() {
    }

    public static void main(String[] args) {
        if (edu_square(7) != 49 || edu_add(12, 30) != 42) {
            throw new AssertionError("Generated jextract bindings returned wrong results");
        }
        System.out.println("PASS: Java reached two functions in the local C library via jextract");
    }
}
