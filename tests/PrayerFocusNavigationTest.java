package com.techgenius.tvoslauncher;

public final class PrayerFocusNavigationTest {
    private static int checks;
    private static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("DPAD check " + checks); }
    public static void main(String[] args) {
        // Every icon must be reachable going only right, and going only left.
        for (int start=0; start<4; start++) {
            for (boolean right:new boolean[]{false,true}) {
                boolean[] visited=new boolean[4]; int index=start;
                for(int step=0;step<4;step++) { visited[index]=true;index=PrayerFocusNavigation.horizontal(index,right,4); }
                check(index==start);for(boolean seen:visited)check(seen);
            }
            check(PrayerFocusNavigation.horizontal(PrayerFocusNavigation.horizontal(start,true,4),false,4)==start);
        }
        check(PrayerFocusNavigation.horizontal(0,true,4)==3);
        check(PrayerFocusNavigation.horizontal(3,false,4)==0);
        check(PrayerFocusNavigation.horizontal(0,true,1)==0);
        check(PrayerFocusNavigation.horizontal(-1,true,4)==0);
        check(PrayerFocusNavigation.horizontal(0,true,0)==-1);
        System.out.println("Prayer remote navigation: "+checks+" checks passed");
    }
}
