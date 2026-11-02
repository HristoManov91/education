package bg.hristomanov.education.ffi;

/**
 * Runnable Java 25 example: no C compiler or ONNX library is required.
 * Launch with --enable-native-access=ALL-UNNAMED.
 */
public final class FfmDemo {

    private FfmDemo() {
    }

    public static void main(String[] args) {
        if (!NativeCStringLength.isSupported()) {
            throw new UnsupportedOperationException(
                    "The current platform does not expose 64-bit C strlen");
        }

        long utf8Bytes = NativeCStringLength.utf8Length("A😊");
        int sum = NativeIntBuffer.addExactly(20, 22);

        System.out.println("UTF-8 bytes via C strlen: " + utf8Bytes);
        System.out.println("Off-heap int sum: " + sum);
    }
}
