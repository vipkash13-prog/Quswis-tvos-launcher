package com.techgenius.tvoslauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

/** One transparent sliding frame for both app grids. Visual only; never receives input. */
final class AppFocusIndicator extends View {
    private final MainActivity host;
    private final float unit;
    private final RectF frame=new RectF(), destination=new RectF(), anchorBounds=new RectF();
    private final FocusSpring springX=new FocusSpring(),springY=new FocusSpring();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private View target,anchor;
    private ValueAnimator fade;
    private float opacity,corner,lastAnchorX,lastAnchorY;
    private long lastFrameNanos;
    private boolean shown;
    private final ViewTreeObserver.OnPreDrawListener follow=this::followFocus;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusChanged=(oldView,newView)->invalidate();

    AppFocusIndicator(MainActivity host){
        super(host);this.host=host;unit=Ui.unit(host);
        setFocusable(false);setFocusableInTouchMode(false);setClickable(false);setLongClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Elevated application artwork must not cover the frame's bright rim.
        setElevation(64f*unit);
    }
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();getViewTreeObserver().addOnPreDrawListener(follow);
        getViewTreeObserver().addOnGlobalFocusChangeListener(focusChanged);
    }
    @Override protected void onDetachedFromWindow(){
        if(getViewTreeObserver().isAlive()){
            getViewTreeObserver().removeOnPreDrawListener(follow);
            getViewTreeObserver().removeOnGlobalFocusChangeListener(focusChanged);
        }
        if(fade!=null)fade.cancel();fade=null;target=null;anchor=null;shown=false;opacity=0;lastFrameNanos=0;
        super.onDetachedFromWindow();
    }
    private boolean allowed(){
        return host.resumed&&hasWindowFocus()&&!host.idle&&!host.ambientShowcase
                &&host.pickerLayer==null&&host.menuLayer==null&&host.launchOverlay==null
                &&(!Scenes.isScenesLook(host)||host.sceneHome==null||!host.sceneHome.isSheetOpen());
    }
    private boolean isApp(View view){
        if(view==null||!view.isAttachedToWindow()||!view.isShown()||view.getWidth()==0||view.getHeight()==0)return false;
        return Scenes.isScenesLook(host)?host.sceneHome!=null&&host.sceneHome.dockTiles.contains(view):host.tiles.contains(view);
    }
    private boolean followFocus(){
        if(!allowed()){hide(true);return true;}
        View next=host.getCurrentFocus();
        if(!isApp(next)){hide(false);return true;}
        boolean scenes=Scenes.isScenesLook(host);
        View container=scenes?host.sceneHome.dockBox:host.content;
        anchorBounds.setEmpty();
        if(!mapToRoot(container,anchorBounds)){hide(true);return true;}
        boolean sameSpace=anchor==container;
        if(sameSpace){
            // Move both spring positions with the content immediately. Scrolling is not a new spring target.
            springX.shift(anchorBounds.left-lastAnchorX);
            springY.shift(anchorBounds.top-lastAnchorY);
        }
        lastAnchorX=anchorBounds.left;lastAnchorY=anchorBounds.top;anchor=container;
        destination.set(0,0,next.getWidth(),next.getHeight());
        if(!mapToRoot(next,destination)){hide(true);return true;}
        float cx=destination.centerX(),cy=destination.centerY();
        // Fit the final zoomed artwork immediately, as in the reference launcher.
        float scale=scenes?1.13f:1.035f;
        float width=destination.width()/Math.max(0.1f,next.getScaleX())*scale+6f*unit;
        float height=destination.height()/Math.max(0.1f,next.getScaleY())*scale+6f*unit;
        corner=(scenes?13f*unit:host.radius)*scale+3f*unit;
        long now=android.os.SystemClock.elapsedRealtimeNanos();
        double dt=lastFrameNanos==0?0:(now-lastFrameNanos)/1_000_000_000.0;
        if(next!=target&&dt>0.1)dt=0; // no first-frame jump after a long idle
        dt=Math.max(0,Math.min(0.064,dt));
        if(!shown||!sameSpace){
            springX.snap(cx);springY.snap(cy);fadeTo(1f,180);
        }
        shown=true;target=next;lastFrameNanos=now;
        boolean settledX=springX.advance(cx,dt,0.2f*unit);
        boolean settledY=springY.advance(cy,dt,0.2f*unit);
        frame.set((float)springX.position-width/2f,(float)springY.position-height/2f,
                (float)springX.position+width/2f,(float)springY.position+height/2f);
        if(!settledX||!settledY)postInvalidateOnAnimation();
        return true;
    }
    /** Map ancestor transforms and scrolling into the root's coordinate space. */
    private boolean mapToRoot(View view,RectF bounds){
        if(view==null)return false;View node=view;
        while(node!=host.root){
            node.getMatrix().mapRect(bounds);bounds.offset(node.getLeft(),node.getTop());
            ViewParent parent=node.getParent();if(!(parent instanceof View))return false;
            node=(View)parent;bounds.offset(-node.getScrollX(),-node.getScrollY());
        }
        return true;
    }
    private void hide(boolean immediate){
        if(!shown&&(!immediate||opacity==0))return;
        shown=false;target=null;anchor=null;lastFrameNanos=0;
        if(immediate){if(fade!=null)fade.cancel();fade=null;opacity=0;invalidate();}
        else fadeTo(0f,100);
    }
    private void fadeTo(float alpha,long duration){
        if(fade!=null)fade.cancel();
        ValueAnimator animation=ValueAnimator.ofFloat(opacity,alpha);fade=animation;
        animation.setDuration(duration);animation.addUpdateListener(a->{opacity=(float)a.getAnimatedValue();postInvalidateOnAnimation();});
        animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(fade==a)fade=null;}});
        animation.start();
    }
    private void color(int argb){paint.setColor(argb);paint.setAlpha(Math.round(Color.alpha(argb)*opacity));}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(opacity<=0||frame.isEmpty())return;
        int saved=canvas.save();
        if(!Scenes.isScenesLook(host)&&host.scroll!=null&&host.scroll.getScrollY()>host.dp(4)&&!host.heroRequested)
            canvas.clipRect(0,host.dp(80),getWidth(),getHeight());
        // A transparent centre preserves the app art; contrasting rims stay visible on white and dark icons.
        paint.setStyle(Paint.Style.FILL);color(0x12FFFFFF);canvas.drawRoundRect(frame,corner,corner,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5f*unit);color(0x30D6ECFF);canvas.drawRoundRect(frame,corner,corner,paint);
        paint.setStrokeWidth(3.5f*unit);color(0x80405060);canvas.drawRoundRect(frame,corner,corner,paint);
        paint.setStrokeWidth(1.8f*unit);color(0xF5FFFFFF);canvas.drawRoundRect(frame,corner,corner,paint);
        canvas.restoreToCount(saved);
    }
}
