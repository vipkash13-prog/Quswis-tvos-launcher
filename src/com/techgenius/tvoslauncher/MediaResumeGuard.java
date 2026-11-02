package com.techgenius.tvoslauncher;

/** Tracks whether an interrupted session still represents the paused film. */
final class MediaResumeGuard {
    static final int PAUSED=2, PLAYING=3, STOPPED=1, NONE=0;
    private final String original;
    private boolean paused, invalid;
    private long position;
    MediaResumeGuard(String original) { this.original=original; }
    void observe(int state,long pos,String content) {
        if (!original.equals(content)) invalid=true;
        if (state==PAUSED && !paused) { paused=true; position=pos; }
        else if (state!=PAUSED && paused) invalid=true;
        if (state==STOPPED || state==NONE) invalid=true;
    }
    void invalidate() { invalid=true; }
    boolean canResume(int state,long pos,String content) {
        return !invalid && paused && state==PAUSED && original.equals(content)
                && Math.abs(pos-position)<=2000;
    }
}
