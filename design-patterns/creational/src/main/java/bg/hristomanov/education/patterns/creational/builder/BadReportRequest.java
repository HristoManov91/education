package bg.hristomanov.education.patterns.creational.builder;

import java.time.LocalDate;
import java.util.Set;

/**
 * BAD/naive вариант: positional constructor с няколко параметъра от сходни типове.
 *
 * <p>Код като {@code new BadReportRequest(from, to, true, false, "PDF", filters)}
 * се чете трудно без IDE hints и става по-рисков при добавяне на optional полета.</p>
 */
public record BadReportRequest(
        LocalDate from,
        LocalDate to,
        boolean includeDetails,
        boolean includeArchived,
        String format,
        Set<String> filters
) {
}
