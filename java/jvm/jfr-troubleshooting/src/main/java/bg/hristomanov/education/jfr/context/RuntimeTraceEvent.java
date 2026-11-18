package bg.hristomanov.education.jfr.context;

import jdk.jfr.Category;
import jdk.jfr.Contextual;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;

/**
 * Java 25 @Contextual field: JFR tools can show traceId on overlapping events
 * from the SAME thread. It does NOT propagate IDs between threads or services.
 * Only synthetic, allowlisted trace IDs are accepted by RuntimeContextLab.
 */
@Name(RuntimeTraceEvent.NAME)
@Label("Education Runtime Trace")
@Category({"Education", "Runtime", "Context"})
@Description("Synthetic trace scope for demonstrating Java 25 JFR contextual fields")
@Threshold("0 ms")
@StackTrace(false)
public final class RuntimeTraceEvent extends Event {
    public static final String NAME = "bg.hristomanov.education.jfr.RuntimeTrace";

    @Label("Trace ID")
    @Contextual
    public String traceId;

    @Label("Operation")
    public String operation;
}
