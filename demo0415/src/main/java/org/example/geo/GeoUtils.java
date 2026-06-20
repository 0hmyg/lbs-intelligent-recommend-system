package org.example.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public class GeoUtils {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private GeoUtils() {
    }

    public static Point point(double lng, double lat) {
        Point p = FACTORY.createPoint(new Coordinate(lng, lat));
        p.setSRID(4326);
        return p;
    }
}

