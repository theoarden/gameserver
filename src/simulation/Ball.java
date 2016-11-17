package simulation;

import physics.*;

public class Ball {
    private static int counter = 0;
    private Ray r;
    public final int id;
    
    public Ball(int startX,int startY,int dX,int dY)
    {
        Vector v = new Vector(dX,dY);
        double speed = v.length();
        r = new Ray(new Point(startX,startY),v,speed);
        id = counter++;
    }
    
    public Ray getRay()
    {
        return r;
    }
    
    public void setRay(Ray r)
    {
        this.r = r;
    }
    
    public void move(double time)
    {
        r = new Ray(r.endPoint(time),r.v,r.speed);
    }
}
