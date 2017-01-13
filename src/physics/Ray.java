package physics;

/** A class to facilitate physics of motion computations. To represent
 *  a moving object we need to specify its location, direction, and 
 *  speed. The Ray class stores all of that information. **/
public class Ray {
  public Point origin;
  public Vector v;
  public double speed;
  
  public Ray(Point origin,Vector v,double speed) {
      this.origin = origin;
      this.v = v;
      this.v.normalize();
      this.speed = speed;
  }

  // Compute the location after the given time span.
  public Point endPoint(double time)
  {
      double destX = origin.x + v.dX*time*speed;
      double destY = origin.y + v.dY*time*speed;
      return new Point(destX,destY);
  }
}