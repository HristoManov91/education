package bg.hristomanov.education.jfr.context;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;

/**
 * A child event with NO traceId field: the JFR parser can associate it with
 * the surrounding @Contextual RuntimeTraceEvent by thread and time range.
 */
@Name(RuntimeWorkEvent.NAME)
@Label("Education Runtime Work")
@Category({"Education", "Runtime", "Context"})
@Description("Synthetic CPU phase within a contextual trace event")
@Threshold("0 ms")
@StackTrace(false)
public final class RuntimeWorkEvent extends Event {
    public static final String NAME = "bg.hristomanov.education.jfr.RuntimeWork";

    @Label("Phase")
    public String phase;

    @Label("Iterations")
    public int iterations;

    @Label("Checksum")
    public long checksum;
}
