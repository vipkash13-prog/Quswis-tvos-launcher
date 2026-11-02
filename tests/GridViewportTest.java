package com.techgenius.tvoslauncher;
public class GridViewportTest {
 static int checks;
 static void check(int actual,int expected){checks++;if(actual!=expected)throw new AssertionError("case "+checks+": "+actual+" != "+expected);}
 public static void main(String[] args){
  // Returning to the first app row keeps the grid visible instead of revealing the hero gap.
  check(GridViewport.target(500,300,1200,380,470,80,512),300);
  check(GridViewport.target(0,326,1200,406,496,80,516),326);
  check(GridViewport.target(500,326,1200,406,496,80,516),326);
  // No page movement for horizontal changes or another fully visible row.
  check(GridViewport.target(500,300,1200,620,730,80,512),500);
  check(GridViewport.target(500,300,1200,780,900,80,512),500);
  // Move only enough to expose the lower or upper edge, including the app label.
  check(GridViewport.target(500,300,1200,950,1060,80,512),548);
  check(GridViewport.target(600,300,1200,600,720,80,512),520);
  check(GridViewport.target(500,300,1200,580,1012,80,512),500);
  // Small app collections cannot be scrolled beyond their actual content extent.
  check(GridViewport.target(0,300,200,380,470,80,512),200);
  check(GridViewport.target(0,300,0,380,470,80,512),0);
  // Clamp excessive requests to the last valid content position.
  check(GridViewport.target(1100,300,1200,1600,1770,80,512),1200);
  check(GridViewport.target(-50,300,1200,380,470,80,512),300);
  // A repeated request can keep its in-flight destination; it need not restart at an intermediate Y.
  check(GridViewport.target(548,300,1200,950,1060,80,512),548);
  // Geometry scales consistently at 720p, 1080p and 4K without changing the layout.
  for(int scale:new int[]{1,2,4})check(GridViewport.target(500*scale,300*scale,1200*scale,950*scale,1060*scale,80*scale,512*scale),548*scale);
  System.out.println("Grid viewport: "+checks+" checks passed");
 }
}
