package physics;

// The LineSegment class represent a directed line segment.

public class LineSegment {
    public Point a;
    public Point b;
    
    public LineSegment(Point a,Point b)
    {
        this.a = a;
        this.b = b;
    }
    
    public void move(int deltaX,int deltaY)
    {
        a.x += deltaX;
        a.y += deltaY;
        b.x += deltaX;
        b.y += deltaY;
    }
}
