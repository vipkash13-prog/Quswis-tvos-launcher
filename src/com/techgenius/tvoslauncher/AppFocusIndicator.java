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
        // A MATCH_PARENT animated overlay damages the whole 4K window. Keep
        // this visual-only node bounded to its actual glow; spring coordinates
        // stay in root space and drawing translates them back into local space.
        int pad=Math.round(12f*unit);
        int left=(int)Math.floor(frame.left)-pad,top=(int)Math.floor(frame.top)-pad;
        int right=(int)Math.ceil(frame.right)+pad,bottom=(int)Math.ceil(frame.bottom)+pad;
        if(getLeft()!=left||getTop()!=top||getRight()!=right||getBottom()!=bottom)layout(left,top,right,bottom);
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
    // Glass slab (soft sheen, bright top specular, crisp rim, faint outer glow), drawn over the sliding frame.
    private final Paint fillPaint=new Paint(Paint.ANTI_ALIAS_FLAG),specPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Matrix shaderMatrix=new android.graphics.Matrix();
    private final android.graphics.LinearGradient fillShader=new android.graphics.LinearGradient(0,0,0,1,0x3DFFFFFF,0x12FFFFFF,android.graphics.Shader.TileMode.CLAMP);
    private final android.graphics.LinearGradient specShader=new android.graphics.LinearGradient(0,0,0,1,0x66FFFFFF,0x00FFFFFF,android.graphics.Shader.TileMode.CLAMP);
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(opacity<=0||frame.isEmpty())return;
        int saved=canvas.save();canvas.translate(-getLeft(),-getTop());
        if(!Scenes.isScenesLook(host)&&host.scroll!=null&&host.scroll.getScrollY()>host.dp(4)&&!host.heroRequested)
            canvas.clipRect(0,host.dp(80),host.screenW,host.screenH);
        float glowSpan=6f*unit;int rings=12;float step=glowSpan/rings;
        // Outer glow: thin rings, each a little fainter, so it fades out smoothly.
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(step+0.5f);
        for(int i=0;i<rings;i++){
            float o=step*(i+0.5f),t=1f-(float)i/rings;
            color(Math.round(0x30*t)<<24|0xFFFFFF);
            canvas.drawRoundRect(frame.left-o,frame.top-o,frame.right+o,frame.bottom+o,corner+o,corner+o,paint);
        }
        // Thin dark edge keeps the slab visible around white application icons.
        paint.setStrokeWidth(3f*unit);color(0x4A405060);canvas.drawRoundRect(frame,corner,corner,paint);
        // Translucent body: the application art stays visible through it.
        shaderMatrix.setScale(1f,frame.height());shaderMatrix.postTranslate(0f,frame.top);
        fillShader.setLocalMatrix(shaderMatrix);fillPaint.setShader(fillShader);fillPaint.setAlpha(Math.round(255*opacity));
        canvas.drawRoundRect(frame,corner,corner,fillPaint);
        // Sheen across the top 45%.
        shaderMatrix.setScale(1f,frame.height()*0.45f);shaderMatrix.postTranslate(0f,frame.top);
        specShader.setLocalMatrix(shaderMatrix);specPaint.setShader(specShader);specPaint.setAlpha(Math.round(255*opacity));
        int clip=canvas.save();
        canvas.clipRect(frame.left,frame.top,frame.right,frame.top+frame.height()*0.45f);
        canvas.drawRoundRect(frame,corner,corner,specPaint);
        canvas.restoreToCount(clip);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f*unit);color(0xCCFFFFFF);canvas.drawRoundRect(frame,corner,corner,paint);
        canvas.restoreToCount(saved);
    }
}
