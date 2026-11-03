package com.techgenius.tvoslauncher;

public final class GridHeaderViewportTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int height : new int[]{720,1080,2160}) {
            float unit=height/540f;
            for (int widgets=0;widgets<=5;widgets++) {
                int header=Math.max(Math.round(80*unit),Math.round((24+Math.max(0,widgets-1)*64+52+16)*unit));
                int rowTop=Math.round(height*1.2f);
                int target=GridViewport.homeTarget(2,rowTop,height,height*5,header);
                check(rowTop-target>=header,"Focused app row moved behind header");
                check(GridViewport.homeTarget(0,rowTop,height,height*5,header)==0,"Original dock position changed");
                check(GridViewport.homeTarget(9,height*8,height,100,header)==100,"Scroll exceeded content boundary");
                check(GridViewport.homeTarget(1,10,height,100,header)==0,"Negative scroll destination");
            }
        }
        check(GridViewport.homeTarget(2,900,1080,1000)==576,"Original scroll behavior changed without header");
        System.out.println("Original dock and protected header viewport checks passed");
    }
}
