package bg.hristomanov.education.patterns.creational.prototype;

import java.util.Map;
import java.util.Set;

/**
 * Prototype contract: client-ът може да копира конфигуриран object,
 * без да зависи от concrete class-а му.
 */
public interface ReportTemplate {

    String name();

    Set<String> columns();

    Map<String, String> filters();

    void putFilter(String key, String value);

    ReportTemplate copy();
}
