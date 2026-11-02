package com.techgenius.tvoslauncher;

/** Minimal scroll needed to expose the selected app inside a stable viewport. */
final class GridViewport {
 private GridViewport(){}
 static int target(int current,int minimum,int maximum,int appTop,int appBottom,int safeTop,int safeBottom){
  maximum=Math.max(0,maximum);minimum=Math.max(0,Math.min(minimum,maximum));
  int result=clamp(current,minimum,maximum);
  if(appTop-result<safeTop)result=appTop-safeTop;
  else if(appBottom-result>safeBottom)result=appBottom-safeBottom;
  return clamp(result,minimum,maximum);
 }
 private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
