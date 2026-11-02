package com.techgenius.tvoslauncher;
public class FocusSpringTest {
 static int checks;
 static void check(boolean ok){checks++;if(!ok)throw new AssertionError("case "+checks);}
 static void near(double a,double b){check(Math.abs(a-b)<1e-7);}
 public static void main(String[] args){
  FocusSpring a=new FocusSpring();a.snap(0);a.advance(100,1.0/60,0.001);
  check(a.position>0&&a.position<100);check(a.velocity>0);
  double position=a.position,velocity=a.velocity;a.advance(-100,0,0.001);
  near(a.position,position);near(a.velocity,velocity); // retargeting does not teleport or reset momentum
  for(int i=0;i<120;i++)a.advance(-100,1.0/60,0.001);
  near(a.position,-100);near(a.velocity,0);
  a.snap(0);boolean overshot=false;for(int i=0;i<90;i++){a.advance(100,1.0/60,0.001);overshot|=a.position>100;}
  check(overshot); // reference's under-damped liquid motion
  FocusSpring b=new FocusSpring();a.snap(0);b.snap(0);
  for(int i=0;i<6;i++)a.advance(100,1.0/60,0.000001);
  for(int i=0;i<3;i++)b.advance(100,1.0/30,0.000001);
  near(a.position,b.position);near(a.velocity,b.velocity); // no frame-rate-dependent drift
  b.snap(0);for(int i=0;i<12;i++)b.advance(100,1.0/120,0.000001);
  near(a.position,b.position);
  double oldVelocity=b.velocity;b.shift(-400);near(b.velocity,oldVelocity);
  a.advance(100,1.0/60,0.000001);b.advance(-300,1.0/60,0.000001);
  near(a.position,b.position+400);near(a.velocity,b.velocity); // scrolling shifts the content, not the spring error
  a.snap(5);a.advance(5,0.1,0.001);near(a.position,5);near(a.velocity,0);
  a.snap(0);for(int i=0;i<500;i++)a.advance((i%4)*100,1.0/60,0.01);
  check(Double.isFinite(a.position)&&Double.isFinite(a.velocity));
  a.advance(200,10,0.01);near(a.position,200);near(a.velocity,0);
  System.out.println("Reference spring: "+checks+" checks passed");
 }
}
