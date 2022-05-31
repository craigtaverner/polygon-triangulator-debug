package org.amanzi.lucene.geo;

import org.apache.lucene.geo.Point;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.TessellatorX;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public class TriangulationMonitor implements TessellatorX.Monitor {
    private final String name;
    private final Config config;
    private final Polygon polygon;
    private List<Point> leftPoints;
    private List<Point> rightPoints;
    private List<Point> diagonalPoints;
    private int index;
    private double left = 0;
    private double right = 0;
    private double top = 0;
    private double bottom = 0;
    //private List<Point> original;
    private final Color background = Color.decode("#a0d0f0");
    private final Color fillColor = Color.decode("#80d0d0");

    public TriangulationMonitor(String name, Polygon polygon, Config config) {
        this.name = name;
        this.polygon = polygon;
        this.config = config;
        this.index = 0;
        initializeScale(polygon);
        initializeImageDirectory();
        // Draw first slide with only polygon
        currentState(null, null, null);
    }

    private void initializeImageDirectory() {
        File dir = config.path.resolve(name).toFile();
        dir.mkdirs();
        int count = 0, deleted = 0;
        for (File file : Objects.requireNonNull(dir.listFiles((d, n) -> n.startsWith(name)))) {
            if (file.delete()) deleted++;
            count++;
        }
        if (deleted < count)
            throw new IllegalStateException("Failed to delete " + (count - deleted) + " of " + count + " files in " + dir);
    }

    private void initializeScale(Polygon polygon) {
        double latRange = polygon.maxLat - polygon.minLat;
        double lonRange = polygon.maxLon - polygon.minLon;
        double latMargin = latRange * config.margin / config.height;
        double lonMargin = lonRange * config.margin / config.width;
        this.left = polygon.minLon - lonMargin;
        this.right = polygon.maxLon + lonMargin;
        this.bottom = polygon.minLat - latMargin;
        this.top = polygon.maxLat + latMargin;
    }

    private int x(double lon) {
        double factor = (lon - left) / (right - left);
        return (int) (factor * config.width);
    }

    private int y(double lat) {
        double factor = (lat - bottom) / (top - bottom);
        return config.height - (int) (factor * config.height);
    }

    private String fileName() {
        String indexString = "0000" + index;
        return name + "-" + indexString.substring(indexString.length() - 5) + ".png";
    }

    private void drawTriangles(Graphics2D graphics, Color color, Stroke stroke, List<TessellatorX.Triangle> tessellation) {
        graphics.setStroke(stroke);
        for (TessellatorX.Triangle t : tessellation) {
            Path2D.Double triangle = new Path2D.Double();
            for (int j = 0; j < 3; j++) {
                int i = j % 3;
                if (j == 0) {
                    triangle.moveTo(x(t.getX(i)), y(t.getY(i)));
                } else {
                    triangle.lineTo(x(t.getX(i)), y(t.getY(i)));
                }
            }
            triangle.closePath();
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
            graphics.fill(new Area(triangle));
            graphics.setColor(color);
            graphics.draw(new Area(triangle));
        }
    }

    public static class Config {
        private final Path path;
        private final int width;
        private final int height;
        private final int margin;
        private final boolean verbose;
        private final boolean labels;

        public Config(Path path, int width, int height, int margin) {
            this(path, width, height, margin, false, false);
        }

        public Config(Path path, int width, int height, int margin, boolean verbose, boolean labels) {
            this.path = path;
            this.width = width;
            this.height = height;
            this.margin = margin;
            this.verbose = verbose;
            this.labels = labels;
        }

        public Config withLabels() {
            return new Config(this.path, this.width, this.height, this.margin, this.verbose, true);
        }

        public Config makeVerbose() {
            return new Config(this.path, this.width, this.height, this.margin, true, this.labels);
        }
    }

    private static class Label {
        private final String label;
        private final int x;
        private final int y;
        private final int mx;
        private final int my;
        private int ox;
        private int oy;
        private final int sign;
        private final float size;
        private final Color color;
        private final int offset = 14;

        private Label(String label, int x, int y, int mx, int my, int sign, float size, Color color) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.mx = mx;
            this.my = my;
            this.sign = sign;
            this.size = size;
            this.color = color;
            initOffsets(sign, sign);
        }

        private void initOffsets(int signx, int signy) {
            ox = (x < mx ? -offset : offset) * signx;
            oy = (y < my ? -offset : offset) * signy;
        }

        private int x() {
            return x + ox;
        }

        private int y() {
            return y + oy;
        }

        private double distance(Label other) {
            int dx = this.x() - other.x();
            int dy = this.y() - other.y();
            return Math.sqrt(dx * dx + dy * dy);
        }

        private boolean closeTo(Label other) {
            return distance(other) < 5;
        }

        private void draw(Graphics2D graphics) {
            Font font = graphics.getFont();
            Color orig = graphics.getColor();
            graphics.setColor(color);
            graphics.setFont(font.deriveFont(size));
            int labelWidth = graphics.getFontMetrics().stringWidth(label);
            int labelHeight = graphics.getFontMetrics().stringWidth("0");
            graphics.drawString(label, x() - labelWidth / 2, y() + labelHeight / 2);
            graphics.setFont(font);
            graphics.setColor(orig);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Label other) {
                return x() == other.x() && y() == other.y();
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(new int[]{x(), y()});
        }
    }

    private boolean closeToOneOf(Label label, List<Label> others) {
        for (Label other : others) {
            if (label.closeTo(other)) {
                return true;
            }
        }
        return false;
    }

    private void reduceCollisions(List<Label> labels) {
        HashMap<Label, ArrayList<Label>> clashes = new HashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            for (int j = i + 1; j < labels.size(); j++) {
                Label a = labels.get(i);
                Label b = labels.get(j);
                if (a.closeTo(b)) {
                    ArrayList<Label> clash = clashes.computeIfAbsent(a, k -> new ArrayList<>());
                    clash.add(b);
                    b.initOffsets(b.sign * -1, b.sign * -1);
                }
            }
        }
        for (Label label : clashes.keySet()) {
            ArrayList<Label> fixed = new ArrayList<>();
            fixed.add(label);
            for (Label other : clashes.get(label)) {
                if (closeToOneOf(other, fixed)) other.initOffsets(other.sign * -1, other.sign * -1);
                if (closeToOneOf(other, fixed)) other.initOffsets(other.sign * -1, other.sign);
                if (closeToOneOf(other, fixed)) other.initOffsets(other.sign, other.sign * -1);
                if (closeToOneOf(other, fixed)) other.initOffsets(other.sign, other.sign);
            }
        }
    }

    private void drawPolygon(Graphics2D graphics, List<Label> labels, Color color, Stroke stroke, boolean useLabels, boolean holeLabels) {
        drawPolygon(graphics, labels, color, fillColor, stroke, this.polygon, useLabels ? "S" : null, 1);
        for (Polygon hole : polygon.getHoles()) {
            drawPolygon(graphics, labels, color, background, stroke, hole, holeLabels ? "H" : null, -1);
        }
    }

    private void drawPolygon(Graphics2D graphics, List<Label> labels, Color color, Color fillColor, Stroke stroke, Polygon polygon, String labelPrefix, int sign) {
        double avLat = (polygon.maxLat + polygon.minLat) / 2;
        double avLon = (polygon.maxLon + polygon.minLon) / 2;
        double[] lats = polygon.getPolyLats();
        double[] lons = polygon.getPolyLons();
        Path2D shape = new Path2D.Double();
        for (int i = 0; i < lats.length; i++) {
            int x = x(lons[i]);
            int y = y(lats[i]);
            if (i == 0) {
                shape.moveTo(x, y);
            } else {
                shape.lineTo(x, y);
            }
            if (config.labels && labelPrefix != null)
                labels.add(new Label(labelPrefix + i, x, y, x(avLon), y(avLat), sign, 14f, Color.WHITE));
        }
        shape.closePath();
        graphics.setStroke(stroke);
        graphics.setColor(fillColor);
        graphics.fill(shape);
        graphics.setColor(color);
        graphics.draw(shape);
    }

    private void drawLines(Graphics2D graphics, List<Label> labels, Color color, Stroke stroke, List<Point> points, boolean useLabels) {
        graphics.setStroke(stroke);
        graphics.setColor(color);
        graphics.setBackground(Color.LIGHT_GRAY);
        int count = 0;
        int px = 0, py = 0, fx = 0, fy = 0;
        for (Point point : points) {
            int x = x(point.getLon());
            int y = y(point.getLat());
            if (count == 0) {
                fx = x;
                fy = y;
            } else {
                graphics.drawLine(px, py, x, y);
            }
            px = x;
            py = y;
            if (config.labels && useLabels)
                labels.add(new Label(Integer.toString(count), x, y, config.width / 2, config.height / 2, 1, 10f, Color.WHITE));
            count++;
        }
        graphics.drawLine(px, py, fx, fy);
    }

    private void drawBaseImage(Graphics2D graphics, String status, List<TessellatorX.Triangle> tessellation, List<Label> labels) {
        graphics.setColor(background);
        graphics.fillRect(0, 0, config.width, config.height);
        boolean ignoreHoleLabels = status != null && (status.contains("CURE") || status.contains("SPLIT"));
        drawPolygon(graphics, labels, Color.LIGHT_GRAY, new BasicStroke(8), true, !ignoreHoleLabels);
        if (tessellation != null) drawTriangles(graphics, Color.RED, new BasicStroke(2), tessellation);
        if (leftPoints != null) drawLines(graphics, labels, Color.CYAN, new BasicStroke(8), leftPoints, false);
        if (rightPoints != null) drawLines(graphics, labels, Color.GREEN, new BasicStroke(5), rightPoints, false);
        if (diagonalPoints != null) drawLines(graphics, labels, Color.BLUE, new BasicStroke(5), diagonalPoints, false);
    }

    private void drawLabels(Graphics2D graphics, String status, List<Label> labels) {
        Font font = graphics.getFont();
        graphics.setFont(font.deriveFont(12.0f));
        if (labels.size() > 0) {
            reduceCollisions(labels);
            for (Label label : labels) {
                label.draw(graphics);
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.setFont(font.deriveFont(40.0f));
        graphics.drawString(index + ": " + name + (status == null ? "" : ", " + status), config.width - 6 * config.margin, config.height - config.margin);
    }

    @Override
    public void currentState(String status, List<Point> points, List<TessellatorX.Triangle> tessellation) {
        Path imagePath = Path.of(config.path.resolve(name).toString(), fileName());
        if (config.verbose) System.out.println("Saving image: " + imagePath);
        BufferedImage bi = new BufferedImage(config.width, config.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = bi.createGraphics();
        ArrayList<Label> labels = new ArrayList<>();
        drawBaseImage(graphics, status, tessellation, labels);
        if (points != null) drawLines(graphics, labels, Color.WHITE, new BasicStroke(2), points, true);
        drawLabels(graphics, status, labels);
        try {
            ImageIO.write(bi, "png", imagePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write image '" + imagePath + "': " + e.getMessage(), e);
        }
        index++;
    }

    @Override
    public void startSplit(String status, List<Point> leftPolygon, List<Point> rightPolygon) {
        this.diagonalPoints = new ArrayList<>();
        this.leftPoints = leftPolygon;
        this.rightPoints = rightPolygon;
        currentState(status, null, null);
        currentState(status, null, null);
        currentState(status, null, null);
        this.diagonalPoints.add(leftPolygon.get(0));
        this.diagonalPoints.add(rightPolygon.get(0));
        currentState(status, null, null);
        currentState(status, null, null);
        currentState(status, null, null);
    }

    @Override
    public void endSplit(String status) {
        this.leftPoints = null;
        this.rightPoints = null;
        this.diagonalPoints = null;
    }
}
