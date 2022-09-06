package org.amanzi.lucene.geo;

import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.PolygonUtils;
import org.apache.lucene.geo.TessellatorX;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

public class TriangulationMonitorApp {
    public static String DEFAULT_DIR = "/tmp/tessellation";
    public static int DEFAULT_WIDTH = 1500;
    public static int DEFAULT_HEIGHT = 1000;
    public static int DEFAULT_MARGIN = 100;

    public static void main(String[] args) {
        String dir = DEFAULT_DIR;
        boolean help = false;
        boolean verbose = false;
        boolean labels = false;
        int width = DEFAULT_WIDTH;
        int height = DEFAULT_HEIGHT;
        int margin = DEFAULT_MARGIN;
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                switch (args[i]) {
                    case "-h", "--help" -> help = true;
                    case "-v", "--verbose" -> verbose = true;
                    case "-l", "--labels" -> labels = true;
                    case "-D", "--dir" -> dir = args[++i];
                    case "-W", "--width" -> width = Integer.parseInt(args[++i]);
                    case "-H", "--height" -> height = Integer.parseInt(args[++i]);
                    case "-M", "--margin" -> margin = Integer.parseInt(args[++i]);
                    default -> System.err.println("Unknown option: " + args[i]);
                }
            } else {
                names.add(args[i]);
            }
        }
        if (help || names.size() < 1) {
            System.out.printf("""
                    usage: polygon-triangulator-debug <--options> name1 <name2...>
                    options:
                        -h | --help       Output this help
                        -v | --verbose    Verbose output: %b
                        -l | --labels     Add labels to images: %b
                        -D | --dir        Set the output directory for image files: '%s'
                        -W | --width      Set the image width: %d
                        -H | --height     Set the image height: %d
                        -M | --margin     Set the image margin: %d
                            
                    The names should either be paths to files, like src/main/resources/org/apache/lucene/geo/lucene-10563-1.geojson.gz
                    or the names (without path and extension) of files in the same package and classpath as the PolygonUtils
                    class, for example lucene-10563-1 exists in package org.apache.lucene.geo.
                    The tool will take each name and look for files called name.geojson.gz, import those as
                    GeoJSON polygons, and run the triangulation algorithm on the first polygon.
                    During triangulation, it will output as many images as there are steps in the triangulation
                    to the directory '%s' with filenames prefixed by the original name, and suffixed with an
                    incrementing counter, allowing a tool like 'ffmpeg' to be used to generate a video.
                            
                    For example, with a name like 'polygon-1' we will have files named 'polygon-1-0001.png' and can
                    generate a video with a command like:
                        ffmpeg -r 5 -i /tmp/tessellation/polygon-1/polygon-1-%%05d.png -c:v libx264 -vf fps=25 -pix_fmt yuv420p polygon-1.mp4
                    %n""", verbose, labels, dir, width, height, margin, dir);
        } else {
            TriangulationMonitor.Config imageConfig = new TriangulationMonitor.Config(Path.of(dir), width, height, margin, verbose, labels);
            TriangulationMonitorApp app = new TriangulationMonitorApp();
            for (String name : names) {
                try {
                    if (name.contains("/")) {
                        app.generateImagesForTriangulationFromPath(Path.of(name), imageConfig);
                    } else {
                        app.generateImagesForTriangulationFromName(name, imageConfig);
                    }
                } catch (Exception e) {
                    System.err.println("Failed triangulating " + name + ": " + e.getMessage());
                }
            }
        }
    }

    private void generateImagesForTriangulationFromName(String name, TriangulationMonitor.Config imageConfig) throws IOException, ParseException {
        String filename = name + ".geojson.gz";
        InputStream is = PolygonUtils.class.getResourceAsStream(filename);
        if (is == null) {
            throw new FileNotFoundException("classpath resource not found: " + filename);
        }
        String geoJson = readShapeFromInputStream(is, filename);
        generateImagesForTriangulationGeoJson(name, geoJson, imageConfig);
    }

    private void generateImagesForTriangulationFromPath(Path path, TriangulationMonitor.Config imageConfig) throws ParseException, IOException {
        String filename = path.getFileName().toString();
        String name = filename.split("\\.")[0];
        InputStream is = new FileInputStream(path.toFile());
        String geoJson = readShapeFromInputStream(is, filename);
        generateImagesForTriangulationGeoJson(name, geoJson, imageConfig);
    }

    private void generateImagesForTriangulationGeoJson(String name, String geoJson, TriangulationMonitor.Config imageConfig) throws ParseException {
        Polygon[] polygons = Polygon.fromGeoJSON(geoJson);
        for (Polygon polygon : polygons) {
            String wkt = PolygonUtils.toWKT(polygon);
            System.out.println(wkt);
            TessellatorX.tessellate(polygon, true, new TriangulationMonitor(name, polygon, imageConfig));
        }
    }

    private String readShapeFromInputStream(InputStream is, String filename) throws IOException {
        if (filename.endsWith(".gz")) {
            is = new GZIPInputStream(is);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        reader.lines().forEach(builder::append);
        return builder.toString();
    }
}
