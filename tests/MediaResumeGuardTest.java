package com.techgenius.tvoslauncher;

/** Regression cases for restoring only the movie interrupted by the adhan. */
public class MediaResumeGuardTest {
 private static int count;
 private static void check(boolean value){count++;if(!value)throw new AssertionError("check "+count);}
 private static MediaResumeGuard paused(){
  MediaResumeGuard g=new MediaResumeGuard("movie-A");g.observe(2,120000,"movie-A");return g;
 }
 public static void main(String[] args){
  MediaResumeGuard g=new MediaResumeGuard("movie-A");
  check(!g.canResume(2,120000,"movie-A")); // never acknowledged a pause
  g.observe(3,120000,"movie-A");g.observe(2,120150,"movie-A");
  check(g.canResume(2,120150,"movie-A")); // pause acknowledgement can be asynchronous
  check(!g.canResume(3,120150,"movie-A")); // already auto-resumed by audio focus
  check(!g.canResume(2,120150,"movie-B")); // another episode selected
  g=paused();g.observe(3,120000,"movie-A");g.observe(2,120000,"movie-A");
  check(!g.canResume(2,120000,"movie-A")); // user played then paused during adhan
  g=paused();g.observe(1,120000,"movie-A");
  check(!g.canResume(2,120000,"movie-A")); // user stopped playback
  g=paused();check(!g.canResume(2,180000,"movie-A")); // user sought elsewhere
  g=paused();g.invalidate();check(!g.canResume(2,120000,"movie-A")); // session destroyed or focus lost
  g=paused();g.observe(2,120000,"movie-B");g.observe(2,120000,"movie-A");
  check(!g.canResume(2,120000,"movie-A")); // content changed and then switched back
  g=paused();check(g.canResume(2,121000,"movie-A")); // minor player position reporting drift
  g=paused();g.observe(0,120000,"movie-A");check(!g.canResume(2,120000,"movie-A"));
  System.out.println("Media restoration: "+count+" checks passed");
 }
}
