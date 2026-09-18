package bg.hristomanov.education.jfr.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;

/**
 * Application-specific JFR event (събитие, дефинирано от нашия код, а не от JVM).
 *
 * <p>Това е важната връзка между техническата telemetry картина и business операцията.
 * В същия recording можем да видим този OrderProcessing event, GC, allocations,
 * monitor contention и CPU samples по една обща времева ос.</p>
 */
@Name(OrderProcessingEvent.NAME)
@Label("Order Processing")
@Category({"Education", "Business"})
@Description("Tracks the duration and result of the demo order processing operation")
@StackTrace(true)
@Threshold("20 ms")
public class OrderProcessingEvent extends Event {

    public static final String NAME = "bg.hristomanov.education.jfr.OrderProcessing";

    @Label("Order ID")
    public String orderId;

    @Label("Item Count")
    public int itemCount;

    @Label("Result")
    public String result;
}
