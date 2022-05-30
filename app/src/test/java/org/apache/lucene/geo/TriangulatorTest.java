/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.geo;

import org.amanzi.lucene.geo.TriangulationMonitor;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Test case for the Polygon {@link TessellatorX} class
 */
public class TriangulatorTest {
    private final TriangulationMonitor.Config imageConfig = new TriangulationMonitor.Config(Path.of("/tmp/tessellation"), 1500, 1000, 100, false);

    @Test
    public void shouldTriangulateComplexPolygon_10563_1() throws Exception {
        String geoJson = PolygonUtils.readShape("lucene-10563-1.geojson.gz");
        Polygon[] polygons = Polygon.fromGeoJSON(geoJson);
        for (Polygon polygon : polygons) {
            String wkt = toWKT(polygon);
            System.out.println(wkt);
            List<TessellatorX.Triangle> tessellation = TessellatorX.tessellate(polygon, true, new TriangulationMonitor("lucene-10563-1", polygon, imageConfig));
            // calculate the area of big polygons have numerical error
            assertEquals(area(polygon), area(tessellation), 1e-11);
            for (TessellatorX.Triangle t : tessellation) {
                checkTriangleEdgesFromPolygon(polygon, t);
            }
        }
    }

    @Test
    public void shouldTriangulateComplexPolygon_10563_2() throws Exception {
        String geoJson = PolygonUtils.readShape("lucene-10563-2.geojson.gz");
        Polygon[] polygons = Polygon.fromGeoJSON(geoJson);
        for (Polygon polygon : polygons) {
            String wkt = toWKT(polygon);
            System.out.println(wkt);
            try {
                TessellatorX.tessellate(polygon, false, new TriangulationMonitor("lucene-10563-2", polygon, imageConfig));
                fail("Should not complete triangulation due to polygon containing crossing lines");
            } catch (IllegalArgumentException e) {
                assertThat("Expected the polygon to fail", e.getMessage(), containsString("Possible malformed shape detected"));
            }
        }
    }

    @Test
    public void shouldTriangulateComplexPolygon_10563_3() throws Exception {
        String geoJson = PolygonUtils.readShape("lucene-10563-3.geojson.gz");
        Polygon[] polygons = Polygon.fromGeoJSON(geoJson);
        for (Polygon polygon : polygons) {
            String wkt = toWKT(polygon);
            System.out.println(wkt);
            try {
                TessellatorX.tessellate(polygon, false, new TriangulationMonitor("lucene-10563-3", polygon, imageConfig));
                fail("Should not complete triangulation due to polygon containing crossing lines");
            } catch (IllegalArgumentException e) {
                assertThat("Expected the polygon to fail", e.getMessage(), containsString("Possible malformed shape detected"));
            }
        }
    }

    private void addWKTPoints(StringBuilder wkt, Polygon polygon) {
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

    private String toWKT(Polygon polygon) {
        StringBuilder wkt = new StringBuilder("POLYGON(\n  ");
        addWKTPoints(wkt, polygon);
        for (int i = 0; i < polygon.numHoles(); i++) {
            wkt.append(",\n  ");
            addWKTPoints(wkt, polygon.getHole(i));
        }
        wkt.append("\n)");
        return wkt.toString();
    }

    private double area(Polygon p) {
        double val = 0;
        for (int i = 0; i < p.numPoints() - 1; i++) {
            val += p.getPolyLon(i) * p.getPolyLat(i + 1) - p.getPolyLat(i) * p.getPolyLon(i + 1);
        }
        double area = Math.abs(val / 2.);
        for (Polygon hole : p.getHoles()) {
            area -= area(hole);
        }
        return area;
    }

    private double area(List<TessellatorX.Triangle> triangles) {
        double area = 0;
        for (TessellatorX.Triangle t : triangles) {
            double[] lats = new double[]{t.getY(0), t.getY(1), t.getY(2), t.getY(0)};
            double[] lons = new double[]{t.getX(0), t.getX(1), t.getX(2), t.getX(0)};
            area += area(new Polygon(lats, lons));
        }
        return area;
    }

    private void checkTriangleEdgesFromPolygon(Polygon p, TessellatorX.Triangle t) {
        // first edge
        assertEquals(t.isEdgefromPolygon(0), isEdgeFromPolygon(p, t.getX(0), t.getY(0), t.getX(1), t.getY(1)));
        // second edge
        assertEquals(t.isEdgefromPolygon(1), isEdgeFromPolygon(p, t.getX(1), t.getY(1), t.getX(2), t.getY(2)));
        // third edge
        assertEquals(t.isEdgefromPolygon(2), isEdgeFromPolygon(p, t.getX(2), t.getY(2), t.getX(0), t.getY(0)));
    }

    private boolean isEdgeFromPolygon(Polygon p, double aLon, double aLat, double bLon, double bLat) {
        for (int i = 0; i < p.getPolyLats().length - 1; i++) {
            if (isPointInLine(p.getPolyLon(i), p.getPolyLat(i), p.getPolyLon(i + 1), p.getPolyLat(i + 1), aLon, aLat) && isPointInLine(p.getPolyLon(i), p.getPolyLat(i), p.getPolyLon(i + 1), p.getPolyLat(i + 1), bLon, bLat)) {
                return true;
            }
            if (p.getPolyLon(i) != p.getPolyLon(i + 1) || p.getPolyLat(i) != p.getPolyLat(i + 1)) {
                // Check for co-planar points
                final int length = p.getPolyLats().length;
                final int offset = i + 2;
                int j = 0;
                int index = getIndex(length, j + offset);
                while (j < length && area(p.getPolyLon(i), p.getPolyLat(i), p.getPolyLon(i + 1), p.getPolyLat(i + 1), p.getPolyLon(index), p.getPolyLat(index)) == 0) {
                    if (isPointInLine(p.getPolyLon(i), p.getPolyLat(i), p.getPolyLon(index), p.getPolyLat(index), aLon, aLat) && isPointInLine(p.getPolyLon(i), p.getPolyLat(i), p.getPolyLon(index), p.getPolyLat(index), bLon, bLat)) {
                        return true;
                    }
                    index = getIndex(length, ++j + offset);
                }
            }
        }
        if (p.getHoles().length > 0) {
            for (Polygon hole : p.getHoles()) {
                if (isEdgeFromPolygon(hole, aLon, aLat, bLon, bLat)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int getIndex(int size, int index) {
        if (index < size) {
            return index;
        }
        return index - size;
    }

    /**
     * Compute signed area of triangle
     */
    private double area(final double aX, final double aY, final double bX, final double bY, final double cX, final double cY) {
        return (bY - aY) * (cX - bX) - (bX - aX) * (cY - bY);
    }

    private boolean isPointInLine(final double aX, final double aY, final double bX, final double bY, double lon, double lat) {
        double dxc = lon - aX;
        double dyc = lat - aY;

        double dxl = bX - aX;
        double dyl = bY - aY;

        if (dxc * dyl - dyc * dxl == 0) {
            if (Math.abs(dxl) >= Math.abs(dyl)) return dxl > 0 ? aX <= lon && lon <= bX : bX <= lon && lon <= aX;
            else return dyl > 0 ? aY <= lat && lat <= bY : bY <= lat && lat <= aY;
        }
        return false;
    }
}
