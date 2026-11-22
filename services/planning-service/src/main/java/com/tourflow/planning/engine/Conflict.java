package com.tourflow.planning.engine;

/** Найденное нарушение правила: сегменты, правило, уровень, величина нарушения в минутах, подсказка (ТЗ 7.2). */
public record Conflict(String rule, Severity severity, Long segA, Long segB, long violationMin, String hint) {
    public boolean involves(long id) { return (segA != null && segA == id) || (segB != null && segB == id); }
}
