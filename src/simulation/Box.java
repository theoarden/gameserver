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

    public void move(int deltaX, int deltaY) {
        for (int n = 0; n < walls.size(); n++)
            walls.get(n).move(deltaX, deltaY);
        x += deltaX;
        y += deltaY;
    }

    public boolean contains(Point p) {
        return p.x >= x && p.x <= x + width && p.y >= y && p.y <= y + height;
    }
}
