package com.techgenius.tvoslauncher;

/** Closed-form damped spring: retargeting keeps position and velocity continuous. */
final class FocusSpring {
 private static final double STIFFNESS=260.0, DAMPING=0.58;
 double position,velocity;
 void snap(double value){position=value;velocity=0;}
 void shift(double delta){position+=delta;}
 boolean advance(double target,double seconds,double tolerance){
  if(seconds>0){
   double omega=Math.sqrt(STIFFNESS),decay=DAMPING*omega;
   double frequency=omega*Math.sqrt(1-DAMPING*DAMPING);
   double error=position-target,b=(velocity+decay*error)/frequency;
   double sin=Math.sin(frequency*seconds),cos=Math.cos(frequency*seconds),e=Math.exp(-decay*seconds);
   double wave=error*cos+b*sin;
   position=target+e*wave;
   velocity=e*(-decay*wave-error*frequency*sin+b*frequency*cos);
  }
  boolean settled=Math.abs(position-target)<tolerance&&Math.abs(velocity)<tolerance*12;
  if(settled)snap(target);
  return settled;
 }
}
