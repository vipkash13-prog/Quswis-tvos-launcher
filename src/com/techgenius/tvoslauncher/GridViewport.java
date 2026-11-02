package com.techgenius.tvoslauncher;

/** Release-182 scroll destinations, bounded by the measured content extent. */
final class GridViewport {
 private GridViewport(){}
 static int homeTarget(int row,int rowTop,int screenHeight,int maximum){
  int target=row==0?0:rowTop-(int)(screenHeight*0.30f);
  return Math.max(0,Math.min(Math.max(0,maximum),target));
 }
}
