package com.techgenius.tvoslauncher;

import android.app.Dialog;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Future;

/** A news-only modal surface. All photographs/headlines come from the original RSS newsrooms. */
final class GlobalNewsDialog extends Dialog {
    private final MainActivity host;private final View anchor;
    private final FrameLayout surface,body,feature;
    private final ScrollView list;
    private final LinearLayout listContent;
    private final ImageView header,featureImage;
    private final TextView windowTitle,featureTitle,featureMeta,urgent,refreshButton,sourceNote;
    private final View close;
    private final View[] tabs=new View[5],dots=new View[5];
    private final List<Row> rows=new ArrayList<>();
    private final List<GlobalNewsData.Story> featured=new ArrayList<>();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final GlobalNewsRepository repository;
    private GlobalNewsRepository.Snapshot snapshot;
    private GlobalNewsData.Story featuredStory;
    private int section,featureIndex,lastRow,generation;
    private boolean closing;
    private Future<?> request;
    private FrameLayout details;private WebView web;
    private static final class Row {FrameLayout view;ImageView image;TextView title,meta;GlobalNewsData.Story story;}
    private final Runnable carousel=new Runnable(){@Override public void run(){
        if(!closing&&isShowing()&&details==null&&!featured.isEmpty()){
            View focus=getCurrentFocus();if(focus!=feature&&rowIndex(focus)<0&&index(dots,focus)<0){featureIndex=(featureIndex+1)%featured.size();showFeature(featured.get(featureIndex));}
        }if(!closing)ui.postDelayed(this,8000);
    }};
    private final Runnable poll=new Runnable(){@Override public void run(){if(!closing&&isShowing()){refresh(false);ui.postDelayed(this,GlobalNewsRepository.TTL);}}};
    private final Runnable ages=new Runnable(){@Override public void run(){if(!closing&&isShowing()){updateMetadata();ui.postDelayed(this,30000);}}};

    static void open(MainActivity host,View anchor){if(host.isFinishing()||host.globalNewsDialog!=null&&host.globalNewsDialog.isShowing())return;new GlobalNewsDialog(host,anchor).show();}
    private GlobalNewsDialog(MainActivity activity,View returnTo){
        super(activity);host=activity;anchor=returnTo;repository=new GlobalNewsRepository(host);section=repository.section();snapshot=repository.cached();
        requestWindowFeature(Window.FEATURE_NO_TITLE);setCancelable(false);
        surface=new FrameLayout(host);MainActivity.LiquidGlass glass=new MainActivity.LiquidGlass(px(22),0xB0081F38,host.glassSource,host.screenW,host.screenH,Ui.unit(host)).adaptive().darker(0x90);glass.lens=px(4);surface.setBackground(glass);round(surface,22);
        body=new FrameLayout(host);body.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);surface.addView(body,new FrameLayout.LayoutParams(-1,-1));
        header=new ImageView(host);header.setScaleType(ImageView.ScaleType.CENTER_CROP);
        UiArtwork.load(host,"global-news/globe-header.png",Math.round(host.screenW*.625f),Math.round(host.screenW*.21f),image->{if(!closing)header.setImageBitmap(image);});
        put(body,header,0,0,2400,300);
        View shade=new View(host);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x20081F38,0x10081F38,0xEE081F38}));put(body,shade,0,0,2400,310);
        windowTitle=text("مركز الأخبار العالمية",22,true);windowTitle.setShadowLayer(px(2),0,px(1),0xFF000000);
        float desired=Math.max(x(800),android.text.Layout.getDesiredWidth(windowTitle.getText(),windowTitle.getPaint())+px(4));
        FrameLayout.LayoutParams titleSize=new FrameLayout.LayoutParams((int)Math.ceil(desired),y(160),Gravity.TOP|Gravity.LEFT);titleSize.leftMargin=Math.round((x(2400)-desired-x(100))/2);body.addView(windowTitle,titleSize);
        GlobalNewsIcon newspaper=new GlobalNewsIcon(host,5);FrameLayout.LayoutParams newspaperSize=new FrameLayout.LayoutParams(x(80),y(80),Gravity.TOP|Gravity.LEFT);newspaperSize.leftMargin=titleSize.leftMargin+titleSize.width+x(20);newspaperSize.topMargin=y(40);body.addView(newspaper,newspaperSize);
        close=button("×",20,this::dismiss);close.setContentDescription("إغلاق مركز الأخبار العالمية");put(body,close,50,40,85,85);
        for(int i=0;i<5;i++){
            final int tab=i;FrameLayout tabView=new FrameLayout(host);GlobalNewsIcon icon=new GlobalNewsIcon(host,i);put(tabView,icon,352,20,60,60);
            TextView title=text(GlobalNewsData.SECTIONS[i],11,true);put(tabView,title,15,0,320,100);
            focus(tabView,()->selectSection(tab));tabView.setContentDescription(GlobalNewsData.SECTIONS[i]);tabs[i]=tabView;put(body,tabView,1900-i*460,175,440,100);
        }
        feature=new FrameLayout(host);round(feature,14);featureImage=new ImageView(host);featureImage.setScaleType(ImageView.ScaleType.CENTER_CROP);feature.addView(featureImage,new FrameLayout.LayoutParams(-1,-1));
        View featureShade=new View(host);featureShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0x15000000,0xFA000000}));feature.addView(featureShade,new FrameLayout.LayoutParams(-1,-1));
        featureTitle=text("",14,true);featureTitle.setSingleLine(false);featureTitle.setMaxLines(3);featureTitle.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);put(feature,featureTitle,40,335,920,240);
        featureMeta=text("",8.5f,false);featureMeta.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);put(feature,featureMeta,40,580,920,60);
        urgent=text("● عاجل",13,true);GradientDrawable red=new GradientDrawable();red.setColor(0xF2E00016);red.setCornerRadius(px(8));urgent.setBackground(red);put(feature,urgent,30,25,220,80);
        focus(feature,()->{if(featuredStory!=null)openStory(featuredStory);else refresh(true);});put(body,feature,40,285,1000,650);
        for(int i=0;i<5;i++){
            final int dot=i;TextView dotView=text("●",7,true);focus(dotView,()->chooseFeature(dot));dotView.setContentDescription("الخبر الرئيسي "+(i+1));dots[i]=dotView;put(body,dotView,385+i*65,940,50,28);
        }
        list=new ScrollView(host);list.setFillViewport(true);list.setVerticalScrollBarEnabled(true);list.setScrollbarFadingEnabled(false);list.setScrollBarSize(Math.max(2,x(10)));list.setPadding(x(10),0,x(24),0);list.setClipToPadding(false);
        listContent=new LinearLayout(host);listContent.setOrientation(LinearLayout.VERTICAL);listContent.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);list.addView(listContent,new ScrollView.LayoutParams(-1,-2));put(body,list,1080,285,1280,650);
        list.getViewTreeObserver().addOnScrollChangedListener(this::loadVisibleImages);
        View footerGlass=new View(host);footerGlass.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xB111304B,0xE00A1D32}));put(body,footerGlass,0,970,2400,80);
        refreshButton=text("",7.5f,true);focus(refreshButton,()->refresh(true));put(body,refreshButton,95,982,1480,55);
        sourceNote=text("أخبار من مصادر عالمية • توقيت السعودية",7.5f,false);put(body,sourceNote,1640,982,640,55);
        setContentView(surface);host.globalNewsDialog=this;
        setOnDismissListener(d->{generation++;ui.removeCallbacksAndMessages(null);if(request!=null)request.cancel(true);destroyWeb();header.setImageDrawable(null);host.globalNewsDialog=null;restoreLens();});
        render();
    }
    private void restoreLens(){
        final View target=host.newsShortcut!=null?host.newsShortcut:anchor;
        if(target==null)return;
        final View home=host.getWindow().getDecorView();
        // Dialog removal returns window focus asynchronously; Home also restores its own focus.
        home.post(new Runnable(){int attempts;@Override public void run(){
            if(host.isFinishing()||host.isDestroyed()||host.globalNewsDialog!=null||!target.isAttachedToWindow())return;
            if(host.hasWindowFocus()){target.requestFocus();return;}
            if(++attempts<25)home.postDelayed(this,40);
        }});
    }
    @Override protected void onStart(){
        super.onStart();Window window=getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setGravity(Gravity.TOP|Gravity.RIGHT);window.setLayout(x(2400),y(1050));WindowManager.LayoutParams p=window.getAttributes();p.x=Math.round(host.screenW*.028f);p.y=Math.round(host.screenH*.16f);window.setAttributes(p);window.setWindowAnimations(0);window.getDecorView().setSystemUiVisibility(host.getWindow().getDecorView().getSystemUiVisibility());}
        surface.setAlpha(0);surface.setScaleX(.9f);surface.setScaleY(.9f);surface.animate().alpha(1).scaleX(1).scaleY(1).setDuration(300).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        surface.post(()->tabs[section].requestFocus());refresh(false);ui.postDelayed(carousel,8000);ui.postDelayed(poll,GlobalNewsRepository.TTL);ui.postDelayed(ages,30000);
    }
    private void selectSection(int chosen){section=chosen;repository.section(section);featureIndex=0;lastRow=0;list.scrollTo(0,0);render();refresh(false);}
    private void refresh(boolean force){
        if(closing)return;generation++;int ticket=generation;if(request!=null)request.cancel(true);
        refreshButton.setText(snapshot.updated>0?"↻  آخر تحديث: "+GlobalNewsData.time(snapshot.updated,"h:mm a")+" • جارٍ التحقق — توقيت السعودية":"↻  جارٍ تحميل الأخبار — توقيت السعودية");
        request=repository.refresh(force,result->{if(!closing&&isShowing()&&ticket==generation){snapshot=result;render();}});
    }
    private void render(){
        View old=getCurrentFocus();String focusedUrl=null;int oldIndex=rowIndex(old);if(oldIndex>=0)focusedUrl=rows.get(oldIndex).story.url;
        List<GlobalNewsData.Story> stories=GlobalNewsData.select(snapshot.stories,section,System.currentTimeMillis());
        featured.clear();for(GlobalNewsData.Story s:stories)if(!s.image.isEmpty()&&featured.size()<5)featured.add(s);if(featured.isEmpty())for(int i=0;i<Math.min(5,stories.size());i++)featured.add(stories.get(i));
        featureIndex=Math.min(featureIndex,Math.max(0,featured.size()-1));showFeature(featured.isEmpty()?null:featured.get(featureIndex));
        for(int i=0;i<5;i++){tabs[i].setSelected(i==section);paint(tabs[i]);dots[i].setVisibility(i<featured.size()?View.VISIBLE:View.INVISIBLE);}
        rows.clear();listContent.removeAllViews();
        if(stories.isEmpty()){TextView empty=text(snapshot.failed&&snapshot.stories.isEmpty()?"تعذّر تحميل الأخبار. تحقق من الاتصال.":"لا توجد أخبار متاحة في هذا القسم حاليًا",12,true);empty.setSingleLine(false);empty.setMaxLines(3);listContent.addView(empty,new LinearLayout.LayoutParams(-1,y(500)));}
        int restore=-1;
        for(GlobalNewsData.Story story:stories){
            Row row=new Row();row.story=story;row.view=new FrameLayout(host);row.view.setClipChildren(false);row.image=new ImageView(host);row.image.setScaleType(ImageView.ScaleType.CENTER_CROP);round(row.image,4);put(row.view,row.image,1010,0,210,120);
            row.title=text(story.title,10,true);row.title.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.title.setSingleLine(false);row.title.setMaxLines(2);
            if(Build.VERSION.SDK_INT>=26)row.title.setAutoSizeTextTypeUniformWithConfiguration(Math.max(1,px(7)),px(10),Math.max(1,px(.5f)),android.util.TypedValue.COMPLEX_UNIT_PX);
            put(row.view,row.title,20,0,965,84);row.meta=text("",6,false);row.meta.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);put(row.view,row.meta,20,84,965,36);
            focus(row.view,()->openStory(story));row.view.setContentDescription(story.title+" • "+story.source);
            row.view.setOnFocusChangeListener((v,focused)->{paint(v);if(focused){lastRow=rows.indexOf(row);showFeature(story);list.post(this::loadVisibleImages);}});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,y(120));lp.bottomMargin=y(10);listContent.addView(row.view,lp);rows.add(row);if(story.url.equals(focusedUrl))restore=rows.size()-1;
        }
        updateMetadata();list.post(this::loadVisibleImages);
        if(focusedUrl!=null){final int target=restore;list.post(()->{if(target>=0&&target<rows.size())rows.get(target).view.requestFocus();else tabs[section].requestFocus();});}
    }
    private void loadVisibleImages(){if(closing)return;int top=list.getScrollY(),bottom=top+list.getHeight();for(Row row:rows)if(row.view.getBottom()>=top&&row.view.getTop()<=bottom)GlobalNewsImages.load(host,row.image,row.story.image,320);}
    private void updateMetadata(){
        long now=System.currentTimeMillis();for(Row row:rows)row.meta.setText(row.story.source+" • "+GlobalNewsData.age(row.story.published,now)+" • "+label(row.story)+(row.story.cached?" • محفوظ":""));
        if(featuredStory!=null)featureMeta.setText(featuredStory.source+" • "+GlobalNewsData.age(featuredStory.published,now)+(featuredStory.cached?" • محفوظ":""));
        String status=snapshot.failed?(snapshot.updated>0?"تحديث جزئي/بيانات محفوظة • ":"تعذّر التحديث • "):snapshot.cached?"بيانات محفوظة • ":"";
        refreshButton.setText("↻  "+status+"آخر تحديث: "+(snapshot.updated>0?GlobalNewsData.time(snapshot.updated,"d/MM h:mm a"):"غير متاح")+" — توقيت السعودية");
    }
    private String label(GlobalNewsData.Story story){if(section>0)return GlobalNewsData.SECTIONS[section];for(int i=1;i<5;i++)if((story.sections&(1<<i))!=0)return GlobalNewsData.SECTIONS[i];return GlobalNewsData.SECTIONS[0];}
    private void showFeature(GlobalNewsData.Story story){
        featuredStory=story;featureTitle.setText(story==null?"لا توجد أخبار متاحة في هذا القسم حاليًا":story.title);featureMeta.setText(story==null?"اختر قسمًا آخر أو حدّث المصادر":story.source+" • "+GlobalNewsData.age(story.published,System.currentTimeMillis())+(story.cached?" • محفوظ":""));
        urgent.setVisibility(story!=null&&story.urgent?View.VISIBLE:View.GONE);GlobalNewsImages.load(host,featureImage,story==null?"":story.image,1280);
        for(int i=0;i<5;i++){boolean current=story!=null&&i<featured.size()&&featured.get(i).url.equals(story.url);dots[i].setSelected(current);if(dots[i] instanceof TextView)((TextView)dots[i]).setTextColor(current?0xFF43DFFF:0xFFA6BBCF);paint(dots[i]);}
        feature.setContentDescription(story==null?"لا توجد أخبار":story.title+" • "+story.source);
    }
    private void chooseFeature(int index){if(index<featured.size()){featureIndex=index;showFeature(featured.get(index));}}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){if(web!=null)destroyWeb();else if(details!=null)closeStory();else dismiss();}return true;}if(closing)return true;
        if(details==null&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){
            if(event.getAction()==KeyEvent.ACTION_UP)return true;View focus=getCurrentFocus(),target=null;int tab=index(tabs,focus),dot=index(dots,focus),row=rowIndex(focus);
            if(focus==close)target=tabs[section];
            else if(tab>=0)target=key==KeyEvent.KEYCODE_DPAD_LEFT?tabs[(tab+1)%5]:key==KeyEvent.KEYCODE_DPAD_RIGHT?tabs[(tab+4)%5]:key==KeyEvent.KEYCODE_DPAD_UP?close:tab>=3||rows.isEmpty()?feature:rows.get(0).view;
            else if(focus==feature)target=key==KeyEvent.KEYCODE_DPAD_UP?tabs[section]:key==KeyEvent.KEYCODE_DPAD_RIGHT?rowOrTab():featured.isEmpty()?refreshButton:dots[Math.min(featureIndex,featured.size()-1)];
            else if(dot>=0){int count=featured.size();if(count==0)target=refreshButton;else if(key==KeyEvent.KEYCODE_DPAD_UP)target=feature;else if(key==KeyEvent.KEYCODE_DPAD_DOWN)target=refreshButton;else{int next=(dot+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:count-1))%count;chooseFeature(next);target=dots[next];}}
            else if(row>=0)target=key==KeyEvent.KEYCODE_DPAD_LEFT?feature:key==KeyEvent.KEYCODE_DPAD_RIGHT?rows.get(row).view:key==KeyEvent.KEYCODE_DPAD_UP?(row==0?tabs[section]:rows.get(row-1).view):(row+1<rows.size()?rows.get(row+1).view:refreshButton);
            else if(focus==refreshButton)target=key==KeyEvent.KEYCODE_DPAD_RIGHT?rowOrTab():key==KeyEvent.KEYCODE_DPAD_UP?feature:tabs[section];
            if(target==null)target=tabs[section];target.requestFocus();return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private View rowOrTab(){return rows.isEmpty()?tabs[section]:rows.get(Math.max(0,Math.min(lastRow,rows.size()-1))).view;}
    private int index(View[] values,View v){for(int i=0;i<values.length;i++)if(values[i]==v)return i;return -1;}
    private int rowIndex(View v){for(int i=0;i<rows.size();i++)if(rows.get(i).view==v)return i;return -1;}
    private void openStory(GlobalNewsData.Story story){
        if(details!=null)closeStory();body.setVisibility(View.GONE);details=new FrameLayout(host);details.setPadding(px(18),px(12),px(18),px(12));details.setBackground(glass(false,false,20));
        LinearLayout column=new LinearLayout(host);column.setOrientation(LinearLayout.VERTICAL);details.addView(column,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout toolbar=new LinearLayout(host);toolbar.setOrientation(LinearLayout.HORIZONTAL);TextView back=button("رجوع",11,this::closeStory);toolbar.addView(back,new LinearLayout.LayoutParams(px(70),px(32)));TextView caption=text(story.source+" • "+GlobalNewsData.age(story.published,System.currentTimeMillis()),10,true);toolbar.addView(caption,new LinearLayout.LayoutParams(0,px(32),1));column.addView(toolbar);
        ScrollView scroll=new ScrollView(host);scroll.setId(View.generateViewId());scroll.setFillViewport(true);scroll.setFocusable(true);LinearLayout article=new LinearLayout(host);article.setOrientation(LinearLayout.VERTICAL);article.setPadding(px(6),px(6),px(6),px(8));scroll.addView(article);
        TextView title=text(story.title,18,true);title.setSingleLine(false);title.setMaxLines(8);title.setEllipsize(null);article.addView(title,new LinearLayout.LayoutParams(-1,-2));
        if(!story.image.isEmpty()){ImageView photo=new ImageView(host);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);article.addView(photo,new LinearLayout.LayoutParams(-1,px(110)));GlobalNewsImages.load(host,photo,story.image,1280);}
        TextView summary=text((story.summary.isEmpty()?"الملخص غير متاح في الخلاصة؛ يمكنك قراءة الخبر من موقع المصدر.":story.summary)+"\n\nملخص من "+story.source+(story.published>0?" • "+GlobalNewsData.time(story.published,"d MMMM yyyy، h:mm a"):""),12,false);summary.setSingleLine(false);summary.setMaxLines(Integer.MAX_VALUE);summary.setEllipsize(null);summary.setLineSpacing(px(4),1f);summary.setGravity(Gravity.RIGHT);article.addView(summary,new LinearLayout.LayoutParams(-1,-2));
        column.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(host);buttons.setOrientation(LinearLayout.HORIZONTAL);TextView full=button("قراءة الخبر من المصدر داخل النافذة",10,()->openWeb(story));buttons.addView(full,new LinearLayout.LayoutParams(0,px(32),1));TextView external=button("فتح المصدر في المتصفح",10,()->openExternal(story.url));buttons.addView(external,new LinearLayout.LayoutParams(0,px(32),1));column.addView(buttons);
        surface.addView(details,new FrameLayout.LayoutParams(-1,-1));scroll.requestFocus();
    }
    private void openWeb(GlobalNewsData.Story story){
        try{
            destroyWeb();web=new WebView(host);web.getSettings().setJavaScriptEnabled(false);web.getSettings().setDomStorageEnabled(false);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.getSettings().setUseWideViewPort(true);web.getSettings().setLoadWithOverviewMode(true);web.getSettings().setTextZoom(130);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
            web.setBackgroundColor(0xFF0B1F38);web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !request.getUrl().toString().startsWith("https://");}@Override public boolean shouldOverrideUrlLoading(WebView view,String url){return !url.startsWith("https://");}@Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())Toast.makeText(host,"تعذّر عرض المصدر. اضغط Back للعودة إلى الملخص.",Toast.LENGTH_LONG).show();}});
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-1);p.setMargins(px(12),px(12),px(12),px(42));details.addView(web,p);web.loadUrl(story.url);web.requestFocus();
        }catch(Throwable error){destroyWeb();Toast.makeText(host,"عارض الويب غير متاح؛ يمكنك فتح المصدر في المتصفح.",Toast.LENGTH_LONG).show();}
    }
    private void openExternal(String url){try{host.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception error){Toast.makeText(host,"لا يوجد متصفح لفتح المصدر.",Toast.LENGTH_LONG).show();}}
    private void destroyWeb(){if(web!=null){web.stopLoading();if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}}
    private void closeStory(){destroyWeb();if(details!=null){surface.removeView(details);details=null;}body.setVisibility(View.VISIBLE);rowOrTab().requestFocus();}
    @Override public void dismiss(){if(closing)return;closing=true;generation++;ui.removeCallbacksAndMessages(null);if(request!=null)request.cancel(true);surface.animate().cancel();surface.animate().alpha(0).scaleX(.9f).scaleY(.9f).setDuration(220).withEndAction(()->super.dismiss()).start();}
    void dispose(){closing=true;surface.animate().cancel();super.dismiss();}
    private int px(float n){return Ui.px(host,n);}private int x(float n){return Math.round(n*host.screenW/3840f);}private int y(float n){return Math.round(n*host.screenH/2160f);}
    private void put(FrameLayout parent,View child,float left,float top,float width,float height){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(x(width),y(height),Gravity.TOP|Gravity.LEFT);p.leftMargin=x(left);p.topMargin=y(top);parent.addView(child,p);}
    private void round(View v,float radius){v.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),px(radius));}});v.setClipToOutline(true);}
    private TextView text(String value,float size,boolean bold){TextView t=new TextView(host);t.setText(value);Ui.textSize(t,size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);t.setIncludeFontPadding(true);if(Build.VERSION.SDK_INT>=28)t.setFallbackLineSpacing(true);t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView button(String value,float size,Runnable action){TextView t=text(value,size,true);focus(t,action);return t;}
    private GradientDrawable glass(boolean focused,boolean selected,float radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,focused?new int[]{0xD82B78C3,0xDB10365C}:selected?new int[]{0xBA1E3A8A,0xCF0B264B}:new int[]{0xA8294968,0xD0092037});d.setCornerRadius(px(radius));d.setStroke(px(focused?1.3f:.6f),focused?0xFFE3FAFF:selected?0xDD00A3FF:0x8FA9D3F0);return d;}
    private void paint(View v){boolean focus=v.hasFocus();v.setBackground(glass(focus,v.isSelected(),9));v.setElevation(px(focus?5:0));if(v instanceof FrameLayout){GradientDrawable rim=new GradientDrawable();rim.setColor(Color.TRANSPARENT);rim.setCornerRadius(px(v==feature?14:9));rim.setStroke(px(focus?1.3f:.5f),focus?0xFFB5F1FF:0x709CCBE7);((FrameLayout)v).setForeground(rim);}if(Build.VERSION.SDK_INT>=28){v.setOutlineAmbientShadowColor(0xFF00A3FF);v.setOutlineSpotShadowColor(0xFF00A3FF);}}
    private void focus(View v,Runnable action){v.setId(View.generateViewId());v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);v.setOnClickListener(w->{if(!closing)action.run();});v.setOnFocusChangeListener((w,focused)->paint(w));paint(v);}
}
