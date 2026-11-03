package com.techgenius.tvoslauncher;

/** Release-182 scroll destinations, bounded by the measured content extent. */
final class GridViewport {
 private GridViewport(){}
 static int homeTarget(int row,int rowTop,int screenHeight,int maximum){
  return homeTarget(row,rowTop,screenHeight,maximum,0);
 }
 static int homeTarget(int row,int rowTop,int screenHeight,int maximum,int headerBottom){
  int anchor=Math.max((int)(screenHeight*0.30f),headerBottom);
  int target=row==0?0:rowTop-anchor;
  return Math.max(0,Math.min(Math.max(0,maximum),target));
 }
}
