package com.techgenius.tvoslauncher;
public class GridViewportTest {
 static int checks;
 static void check(int actual,int expected){checks++;if(actual!=expected)throw new AssertionError("case "+checks+": "+actual+" != "+expected);}
 public static void main(String[] args){
  check(GridViewport.homeTarget(0,394,540,1200),0);
  check(GridViewport.homeTarget(0,788,1080,2400),0);
  check(GridViewport.homeTarget(1,528,540,1200),366);
  check(GridViewport.homeTarget(2,644,540,1200),482);
  check(GridViewport.homeTarget(3,760,540,1200),598);
  check(GridViewport.homeTarget(1,1056,1080,2400),732);
  check(GridViewport.homeTarget(2,1288,1080,2400),964);
  check(GridViewport.homeTarget(1,528,540,200),200);
  check(GridViewport.homeTarget(1,528,540,0),0);
  check(GridViewport.homeTarget(1,50,540,1200),0);
  check(GridViewport.homeTarget(0,394,540,-50),0);
  System.out.println("Restored home layout: "+checks+" checks passed");
 }
}
