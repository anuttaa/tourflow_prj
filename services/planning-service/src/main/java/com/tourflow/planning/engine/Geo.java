package com.tourflow.planning.engine;

public final class Geo {
    private Geo() {}

    public static double km(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    /** Расстояние между концом a и началом b; 0, если координат нет. */
    public static double between(Seg a, Seg b) {
        if (a == null || b == null || a.toLat() == null || a.toLon() == null || b.fromLat() == null || b.fromLon() == null) return 0;
        return km(a.toLat(), a.toLon(), b.fromLat(), b.fromLon());
    }
}
