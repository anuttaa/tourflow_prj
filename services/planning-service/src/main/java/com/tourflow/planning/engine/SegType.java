package com.tourflow.planning.engine;

public enum SegType {
    FLIGHT, TRAIN, BUS, HOTEL, TRANSFER, EXCURSION, INSURANCE, MEAL;

    public boolean transport() { return this == FLIGHT || this == TRAIN || this == BUS; }
    /** Фоновые сегменты (проживание, страховка) могут пересекаться с остальными (VR-01). */
    public boolean background() { return this == HOTEL || this == INSURANCE; }
    public static SegType of(String s) { return valueOf(s.toUpperCase()); }
}
