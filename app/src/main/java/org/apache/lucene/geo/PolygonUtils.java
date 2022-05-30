package org.apache.lucene.geo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public class PolygonUtils {
    public static String toWKT(Polygon polygon) {
        StringBuilder wkt = new StringBuilder("POLYGON(\n  ");
        addWKTPoints(wkt, polygon);
        for (int i = 0; i < polygon.numHoles(); i++) {
            wkt.append(",\n  ");
            addWKTPoints(wkt, polygon.getHole(i));
        }
        wkt.append("\n)");
        return wkt.toString();
    }

    public static void addWKTPoints(StringBuilder wkt, Polygon polygon) {
        double[] lons = polygon.getPolyLons();
        double[] lats = polygon.getPolyLats();
        wkt.append("(");
        for (int i = 0; i < lons.length; i++) {
            if (i > 0) {
                wkt.append(",");
                if (i % 4 == 0) wkt.append("\n   ");
            }
            wkt.append(lons[i]).append(" ").append(lats[i]);
        }
        wkt.append(")");
    }

    /**
     * reads a shape from file
     */
    public static String readShape(String name) throws IOException {
        return Loader.LOADER.readShape(name);
    }

    private static class Loader {

        static Loader LOADER = new Loader();

        String readShape(String name) throws IOException {
            InputStream is = getClass().getResourceAsStream(name);
            if (is == null) {
                throw new FileNotFoundException("classpath resource not found: " + name);
            }
            if (name.endsWith(".gz")) {
                is = new GZIPInputStream(is);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            reader.lines().forEach(s -> builder.append(s));
            return builder.toString();
        }
    }
}
