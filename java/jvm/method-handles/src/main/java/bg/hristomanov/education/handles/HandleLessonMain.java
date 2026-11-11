package bg.hristomanov.education.handles;

/**
 * Standalone smoke: prove that the sample works without a Spring context,
 * native library, IDE or external service.
 */
public final class HandleLessonMain {

    private HandleLessonMain() {
    }

    public static void main(String[] args) throws Throwable {
        Point translated = HandleInvocationLab.linkedHandle(new Point(1, 2), 3, 4);
        System.out.println("POINT=" + translated);
        System.out.println("CONCAT=" + StringConcatLab.prelinkedCallSite("widget", 3));

        VersionedPointMarshaller.PointV2 record =
                new VersionedPointMarshaller.PointV2(3, 4, 5);
        Object roundTripped = VersionedPointMarshaller.decode(
                VersionedPointMarshaller.encode(record));
        System.out.println("ROUND_TRIP=" + roundTripped);
    }
}
