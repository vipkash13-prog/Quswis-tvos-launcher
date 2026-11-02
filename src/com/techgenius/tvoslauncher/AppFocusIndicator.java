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
    private final RectF frame=new RectF(), from=new RectF(), destination=new RectF();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private View target;
    private ValueAnimator motion,fade;
    private float progress=1f,opacity,corner,fromCorner,toCorner;
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
        cancelMotion();if(fade!=null)fade.cancel();fade=null;target=null;shown=false;opacity=0;
        super.onDetachedFromWindow();
    }
    private boolean allowed(){
        return host.resumed&&hasWindowFocus()&&!host.idle&&!host.ambientShowcase
                &&host.pickerLayer==null&&host.menuLayer==null&&host.launchOverlay==null
                &&(host.sceneHome==null||!host.sceneHome.isSheetOpen());
    }
    private boolean isApp(View view){
        if(view==null||!view.isAttachedToWindow()||!view.isShown()||view.getWidth()==0||view.getHeight()==0)return false;
        return Scenes.isScenesLook(host)?host.sceneHome!=null&&host.sceneHome.dockTiles.contains(view):host.tiles.contains(view);
    }
    private boolean followFocus(){
        if(!allowed()){hide(true);return true;}
        View next=host.getCurrentFocus();
        if(!isApp(next)){hide(false);return true;}
        boolean changed=next!=target||!shown;
        if(changed){
            cancelMotion();from.set(frame);fromCorner=corner;
            target=next;shown=true;
        }
        if(!measureTarget(next)){hide(true);return true;}
        toCorner=(Scenes.isScenesLook(host)?13f*unit:host.radius)*Math.max(next.getScaleX(),next.getScaleY())+3f*unit;
        if(changed){
            if(opacity<0.01f||frame.isEmpty()){
                frame.set(destination);corner=toCorner;progress=1f;
            }else{
                progress=0f;
                ValueAnimator animation=ValueAnimator.ofFloat(0f,1f);motion=animation;
                animation.setDuration(220);animation.setInterpolator(MainActivity.EASE);
                animation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();postInvalidateOnAnimation();});
                animation.addListener(new AnimatorListenerAdapter(){
                    @Override public void onAnimationEnd(Animator a){if(motion==a){motion=null;progress=1f;}}
                });
                animation.start();
            }
            fadeTo(1f,130);
        }
        updateFrame();
        return true;
    }
    /** Map all ancestor matrices and scroll offsets into the root's coordinate space. */
    private boolean measureTarget(View view){
        destination.set(0,0,view.getWidth(),view.getHeight());View node=view;
        while(node!=host.root){
            node.getMatrix().mapRect(destination);destination.offset(node.getLeft(),node.getTop());
            ViewParent parent=node.getParent();if(!(parent instanceof View))return false;
            node=(View)parent;destination.offset(-node.getScrollX(),-node.getScrollY());
        }
        destination.inset(-3f*unit,-3f*unit);return true;
    }
    private void updateFrame(){
        if(!shown)return;
        if(motion==null){frame.set(destination);corner=toCorner;}
        else{
            frame.set(lerp(from.left,destination.left),lerp(from.top,destination.top),
                    lerp(from.right,destination.right),lerp(from.bottom,destination.bottom));
            corner=lerp(fromCorner,toCorner);
        }
    }
    private float lerp(float a,float b){return a+(b-a)*progress;}
    private void cancelMotion(){if(motion!=null)motion.cancel();motion=null;}
    private void hide(boolean immediate){
        if(!shown&&(!immediate||opacity==0))return;
        shown=false;target=null;cancelMotion();
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
        updateFrame();
        // A transparent centre preserves the app art; contrasting rims stay visible on white and dark icons.
        paint.setStyle(Paint.Style.FILL);color(0x12FFFFFF);canvas.drawRoundRect(frame,corner,corner,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5f*unit);color(0x30D6ECFF);canvas.drawRoundRect(frame,corner,corner,paint);
        paint.setStrokeWidth(3.5f*unit);color(0x80405060);canvas.drawRoundRect(frame,corner,corner,paint);
        paint.setStrokeWidth(1.8f*unit);color(0xF5FFFFFF);canvas.drawRoundRect(frame,corner,corner,paint);
    }
}
