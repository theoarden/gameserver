package simulation;

import physics.*;

import java.util.ArrayList;

public class Box {
    private static int counter = 0;
    private ArrayList<LineSegment> walls;
    public int x;
    public int y;
    public int width;
    public int height;
    public final int id;

    // Set outward to true if you want a box with outward pointed normals
    public Box(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        walls = new ArrayList<LineSegment>();
        walls.add(new LineSegment(new Point(x, y), new Point(x + width, y)));
        walls.add(new LineSegment(new Point(x + width, y), new Point(x + width, y + height)));
        walls.add(new LineSegment(new Point(x + width, y + height), new Point(x, y + height)));
        walls.add(new LineSegment(new Point(x, y + height), new Point(x, y)));
        id = counter++;
    }

    public Ray bounceRay(Ray in, double time) {
        // For each of the walls, check to see if the Ray intersects the wall
        Point intersection = null;
        for (LineSegment wall : walls) {
            LineSegment seg = in.toSegment(time);
            intersection = wall.intersection(seg);
            if (intersection != null) {
                // If it intersects, find out when
                double t = in.getTime(intersection);
                // Reflect the Ray off the line segment
                Ray newRay = wall.reflect(seg, in.speed);
                // Figure out where we end up after the reflection.
                Point dest = newRay.endPoint(time - t);
                return new Ray(dest, newRay.v, in.speed);
            }
        }
        return null;
    }

    public void move(int deltaX, int deltaY) {
        for (LineSegment wall : walls) wall.move(deltaX, deltaY);
        x += deltaX;
        y += deltaY;
    }

    public boolean contains(Point p) {
        return p.x >= x && p.x <= x + width && p.y >= y && p.y <= y + height;
    }
}
