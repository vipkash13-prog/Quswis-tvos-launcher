package com.techgenius.tvoslauncher;

import android.app.Dialog;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.Future;

/** Native, self-contained glass window. It never changes the Home layout or football settings. */
final class MatchCenterDialog extends Dialog {
    private final MainActivity host;
    private final View anchor;
    private final FrameLayout surface,body;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final MatchCenterRepository repository;
    private MatchCenterRepository.Snapshot snapshot;
    private Set<String> chosen;
    private final View[] tabs=new View[4],choices=new View[MatchCenterData.LEAGUES.length];
    private final TextView[] checks=new TextView[MatchCenterData.LEAGUES.length];
    private final ImageView[] leagueLogos=new ImageView[MatchCenterData.LEAGUES.length];
    private final MatchRow[] rows=new MatchRow[5];
    private final TextView heading,footer,empty,pageLabel;
    private final TextView close;
    private int tab,generation,page,total;
    private boolean closing;
    private Bitmap stadium;
    private Future<?> request;
    private FrameLayout details;
    private final Runnable poll=new Runnable(){@Override public void run(){if(isShowing()&&!closing){refresh();ui.postDelayed(this,tab==MatchCenterData.LIVE?60000:300000);}}};
    private static final String[] LABELS={"المباريات القادمة","مباشر الآن  🔴","نتائج اليوم","الدوريات"};
    private static final class MatchRow {
        FrameLayout view;TextView league,home,away,date,score,state;ImageView leagueLogo,homeLogo,awayLogo;JSONObject data;
    }
    static void open(MainActivity host,View anchor){if(host.isFinishing())return;if(host.matchCenterDialog!=null&&host.matchCenterDialog.isShowing())return;new MatchCenterDialog(host,anchor).show();}
    private MatchCenterDialog(MainActivity activity,View returnTo){
        super(activity);host=activity;anchor=returnTo;repository=new MatchCenterRepository(host);chosen=repository.selected();
        requestWindowFeature(Window.FEATURE_NO_TITLE);setCancelable(false);
        surface=new FrameLayout(host);surface.setClipChildren(false);
        MainActivity.LiquidGlass glass=new MainActivity.LiquidGlass(px(22),0x9A0C1D31,host.glassSource,host.screenW,host.screenH,Ui.unit(host)).adaptive().darker(0x86);
        glass.lens=px(4);surface.setBackground(glass);round(surface,22);
        body=new FrameLayout(host);body.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);surface.addView(body,new FrameLayout.LayoutParams(-1,-1));

        ImageView banner=new ImageView(host);UiArtwork.load(host,"match-center/stadium.png",Math.round(host.screenW*.625f),Math.round(host.screenW*.21f),b->{if(!closing){stadium=b;banner.setImageBitmap(b);}});banner.setScaleType(ImageView.ScaleType.CENTER_CROP);banner.setAlpha(.88f);put(body,banner,0,0,2400,300);
        View fade=new View(host);fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x5506152C,0x3306152C,0xDD0A1A2D}));put(body,fade,0,0,2400,310);
        TextView title=text("⚽  مركز المباريات",24,true);title.setShadowLayer(px(2),0,px(1),0xFF000000);
        // Arabic and emoji fallback metrics exceed the old 100-pixel title line box.
        title.setIncludeFontPadding(true);title.setEllipsize(null);
        if(Build.VERSION.SDK_INT>=28)title.setFallbackLineSpacing(true);
        float titleWidth=Math.max(x(700),android.text.Layout.getDesiredWidth(title.getText(),title.getPaint())+px(4));
        FrameLayout.LayoutParams titleBounds=new FrameLayout.LayoutParams((int)Math.ceil(titleWidth),y(160),Gravity.TOP|Gravity.LEFT);
        titleBounds.leftMargin=Math.round((x(2400)-titleWidth)/2);body.addView(title,titleBounds);
        close=button("×",26,this::dismiss);close.setContentDescription("إغلاق مركز المباريات");put(body,close,50,45,85,85);
        for(int i=0;i<4;i++){
            final int index=i;TextView button=button(LABELS[i],12,true,()->selectTab(index));tabs[i]=button;
            put(body,button,1790-i*450,165,430,95);
        }
        FrameLayout leaguePanel=new FrameLayout(host);leaguePanel.setBackground(skin(false,false,18));put(body,leaguePanel,40,310,620,690);
        TextView leagueTitle=text("إدارة الدوريات",14,true);put(leaguePanel,leagueTitle,15,8,590,65);
        for(int i=0;i<MatchCenterData.LEAGUES.length;i++){
            final int index=i;FrameLayout choice=new FrameLayout(host);choice.setContentDescription(MatchCenterData.LEAGUES[i][1]);
            checks[i]=text("",11,true);checks[i].setTextColor(0xFF8FD7FF);put(choice,checks[i],518,9,45,45);
            leagueLogos[i]=image();put(choice,leagueLogos[i],447,6,52,52);
            TextView label=text(MatchCenterData.LEAGUES[i][1],10,true);if(Build.VERSION.SDK_INT>=26)label.setAutoSizeTextTypeUniformWithConfiguration(Math.max(1,px(8.5f)),px(10),Math.max(1,px(.5f)),android.util.TypedValue.COMPLEX_UNIT_PX);label.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);put(choice,label,12,0,420,65);
            focus(choice,()->toggleLeague(index));choices[i]=choice;put(leaguePanel,choice,20,80+i*65,580,62);
        }
        FrameLayout games=new FrameLayout(host);games.setBackground(skin(false,false,18));put(body,games,700,310,1640,690);
        heading=text("المباريات القادمة • اليوم",14,true);heading.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);put(games,heading,475,4,1135,68);
        pageLabel=text("",7.5f,false);put(games,pageLabel,30,12,430,48);
        for(int i=0;i<5;i++){
            MatchRow row=new MatchRow();rows[i]=row;row.view=new FrameLayout(host);
            row.leagueLogo=image();put(row.view,row.leagueLogo,1460,20,70,70);
            row.league=text("",9,true);row.league.setSingleLine(false);row.league.setMaxLines(2);put(row.view,row.league,1100,5,330,100);
            row.home=text("",12,true);fitTeam(row.home);put(row.view,row.home,820,5,250,100);row.homeLogo=image();put(row.view,row.homeLogo,710,10,90,90);
            row.away=text("",12,true);fitTeam(row.away);put(row.view,row.away,35,5,260,100);row.awayLogo=image();put(row.view,row.awayLogo,310,10,90,90);
            row.date=text("",7.5f,false);put(row.view,row.date,420,0,260,29);
            row.score=text("",12,true);row.score.setTextDirection(View.TEXT_DIRECTION_LTR);put(row.view,row.score,420,27,260,50);
            row.state=text("",7.5f,false);put(row.view,row.state,420,77,260,30);
            focus(row.view,()->{if(row.data!=null)openDetails(row.data);});put(games,row.view,30,82+i*118,1580,110);
        }
        empty=text("لا توجد مباريات متاحة حاليًا",14,true);empty.setFocusable(false);put(games,empty,35,200,1570,220);
        footer=text("",8,false);footer.setIncludeFontPadding(true);
        if(Build.VERSION.SDK_INT>=28)footer.setFallbackLineSpacing(true);
        put(body,footer,65,1008,2270,80);
        setContentView(surface);host.matchCenterDialog=this;
        setOnDismissListener(d->{
            generation++;ui.removeCallbacksAndMessages(null);if(request!=null)request.cancel(true);
            host.matchCenterDialog=null;
            stadium=null;
            if(anchor!=null&&anchor.isAttachedToWindow())anchor.requestFocus();
        });
        snapshot=repository.cached(chosen,tab);render();
    }
    @Override protected void onStart(){
        super.onStart();Window w=getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // Add 60 reference pixels upward; keep the previous bottom edge and every row size.
            w.setGravity(Gravity.TOP|Gravity.RIGHT);w.setLayout(Math.round(host.screenW*.625f),y(1110));
            WindowManager.LayoutParams p=w.getAttributes();p.x=Math.round(host.screenW*.028f);p.y=Math.round(host.screenH*.16f)-y(60);w.setAttributes(p);w.setWindowAnimations(0);
            w.getDecorView().setSystemUiVisibility(host.getWindow().getDecorView().getSystemUiVisibility());}
        surface.setAlpha(0);surface.setScaleX(.9f);surface.setScaleY(.9f);surface.animate().alpha(1).scaleX(1).scaleY(1).setDuration(300).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        surface.post(()->tabs[0].requestFocus());refresh();ui.postDelayed(poll,300000);
    }
    private void selectTab(int index){
        if(details!=null)closeDetails();tab=index;page=0;ui.removeCallbacks(poll);snapshot=repository.cached(chosen,tab);render();refresh();
        ui.postDelayed(poll,tab==MatchCenterData.LIVE?60000:300000);
        if(tab==MatchCenterData.LEAGUE_TAB)choices[0].requestFocus();
    }
    private void toggleLeague(int index){String id=MatchCenterData.LEAGUES[index][0];if(!chosen.remove(id))chosen.add(id);repository.select(chosen);page=0;snapshot=repository.cached(chosen,tab);render();refresh();}
    private void refresh(){
        if(closing)return;generation++;final int ticket=generation;if(request!=null)request.cancel(true);
        if(chosen.isEmpty()){snapshot=repository.cached(chosen,tab);render();return;}
        footer.setText(snapshot.hasCache?"ESPN • بيانات محفوظة • "+MatchCenterData.format(snapshot.updatedAt,"d/MM HH:mm:ss")+" • جارٍ التحقق • توقيت السعودية":"ESPN • جارٍ تحديث البيانات • توقيت السعودية");
        request=repository.refresh(chosen,tab,result->{if(!closing&&isShowing()&&ticket==generation){snapshot=result;render();}});
    }
    private void render(){
        for(int i=0;i<4;i++){tabs[i].setSelected(i==tab);paintFocus(tabs[i]);}
        for(int i=0;i<8;i++){checks[i].setText(chosen.contains(MatchCenterData.LEAGUES[i][0])?"☑":"☐");MatchCenterImages.load(host,leagueLogos[i],snapshot.logos.get(MatchCenterData.LEAGUES[i][0]));}
        List<JSONObject> all=MatchCenterData.select(snapshot.matches,chosen,tab,System.currentTimeMillis(),tab==MatchCenterData.LEAGUE_TAB?5:Integer.MAX_VALUE);
        total=all.size();page=Math.min(page,Math.max(0,(total-1)/5));int from=page*5;
        List<JSONObject> matches=all.subList(from,Math.min(total,from+5));
        pageLabel.setText(total>5?(from+1)+"–"+Math.min(total,from+5)+" / "+total+" • ↑ ↓ للمزيد":"");
        heading.setText(tab==MatchCenterData.LIVE?"مباشر الآن  🔴":tab==MatchCenterData.RESULTS?"نتائج اليوم":tab==MatchCenterData.LEAGUE_TAB?"مباريات الدوريات المختارة":"المباريات القادمة • اليوم");
        empty.setVisibility(matches.isEmpty()?View.VISIBLE:View.GONE);
        View previous=getCurrentFocus();boolean lost=false;
        for(int i=0;i<5;i++){
            MatchRow row=rows[i];row.data=i<matches.size()?matches.get(i):null;row.view.setVisibility(row.data==null?View.INVISIBLE:View.VISIBLE);
            if(row.data==null){if(previous==row.view)lost=true;continue;}
            JSONObject f=row.data.optJSONObject("fixture"),teams=row.data.optJSONObject("teams"),league=row.data.optJSONObject("league"),goals=row.data.optJSONObject("goals");
            JSONObject home=teams.optJSONObject("home"),away=teams.optJSONObject("away");
            row.home.setText(MatchCenterData.teamName(home.optString("name")));row.away.setText(MatchCenterData.teamName(away.optString("name")));row.league.setText(league.optString("name"));
            MatchCenterImages.load(host,row.homeLogo,home.optString("logo"));MatchCenterImages.load(host,row.awayLogo,away.optString("logo"));MatchCenterImages.load(host,row.leagueLogo,league.optString("logo"));
            Date date=FootballData.parseDate(f.optString("date"));long at=date==null?0:date.getTime();
            row.date.setText(MatchCenterData.format(at,"d MMM"));
            boolean hasScore=f.optBoolean("hasScore")&&goals!=null&&!goals.isNull("home")&&!goals.isNull("away");
            row.score.setText(hasScore?goals.optString("away")+" - "+goals.optString("home"):f.optBoolean("timeTbd")?"يحدد لاحقًا":f.optBoolean("hasScore")?"—":MatchCenterData.clock(at));
            boolean stale=f.optBoolean("stale")||snapshot.failed;boolean live="in".equals(f.optString("state"));
            row.state.setText((stale?"محفوظ • ":live?"🔴 ":"")+f.optString("status")+(live&&!f.optString("clock").isEmpty()?" "+f.optString("clock"):""));
            row.state.setTextColor(live&&!stale?0xFFFFA8A8:0xFFD2E0EC);
            row.view.setContentDescription(row.home.getText()+"، "+row.away.getText()+"، "+row.score.getText()+"، "+row.league.getText()+"، "+row.state.getText());
        }
        if(lost)tabs[tab].requestFocus();
        String state=snapshot.failed?"تعذر تحديث بعض البيانات • بيانات محفوظة":snapshot.stale?"بيانات محفوظة":snapshot.hasCache?"آخر تحديث":"لم تصل بيانات بعد";
        footer.setText("ESPN • "+state+(snapshot.updatedAt>0?" "+MatchCenterData.format(snapshot.updatedAt,"d/MM HH:mm:ss"):"")+" • توقيت السعودية"+(chosen.isEmpty()?" • اختر دوريًا من القائمة":""));
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)dismiss();return true;}if(closing)return true;
        if(details==null&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)){
            if(event.getAction()==KeyEvent.ACTION_UP)return true;
            View current=getCurrentFocus();int index=index(tabs,current),league=index(choices,current),row=rowIndex(current);View target=null;
            if(current==close)target=tabs[tab];
            else if(index>=0){target=key==KeyEvent.KEYCODE_DPAD_LEFT?tabs[(index+1)%4]:key==KeyEvent.KEYCODE_DPAD_RIGHT?tabs[(index+3)%4]:key==KeyEvent.KEYCODE_DPAD_UP?close:firstRowOrLeague();}
            else if(league>=0){target=key==KeyEvent.KEYCODE_DPAD_UP?(league==0?tabs[3]:choices[league-1]):key==KeyEvent.KEYCODE_DPAD_DOWN?(league==7?tabs[3]:choices[league+1]):key==KeyEvent.KEYCODE_DPAD_RIGHT?firstRowOrTab():tabs[3];}
            else if(row>=0){
                if(key==KeyEvent.KEYCODE_DPAD_DOWN&&row==4&&(page+1)*5<total){page++;render();rows[0].view.requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_UP&&row==0&&page>0){page--;render();rows[4].view.requestFocus();return true;}
                target=key==KeyEvent.KEYCODE_DPAD_LEFT?choices[Math.min(row,7)]:key==KeyEvent.KEYCODE_DPAD_RIGHT?tabs[tab]:key==KeyEvent.KEYCODE_DPAD_UP?(row==0?tabs[tab]:rows[row-1].view):(row==4||rows[row+1].data==null?tabs[tab]:rows[row+1].view);}
            if(target==null)target=tabs[tab];target.requestFocus();return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private View firstRowOrLeague(){return tab==MatchCenterData.LEAGUE_TAB||rows[0].data==null?choices[0]:rows[0].view;}
    private View firstRowOrTab(){return rows[0].data==null?tabs[tab]:rows[0].view;}
    private int index(View[] array,View v){for(int i=0;i<array.length;i++)if(array[i]==v)return i;return -1;}
    private int rowIndex(View v){for(int i=0;i<rows.length;i++)if(rows[i].view==v)return i;return -1;}
    private void openDetails(JSONObject match){
        if(details!=null)closeDetails();body.setVisibility(View.GONE);details=new FrameLayout(host);details.setBackground(skin(false,false,20));details.setPadding(px(18),px(14),px(18),px(14));
        LinearLayout column=new LinearLayout(host);column.setOrientation(LinearLayout.VERTICAL);details.addView(column,new FrameLayout.LayoutParams(-1,-1));
        JSONObject f=match.optJSONObject("fixture"),teams=match.optJSONObject("teams"),league=match.optJSONObject("league");
        TextView title=text(MatchCenterData.teamName(teams.optJSONObject("home").optString("name"))+" × "+MatchCenterData.teamName(teams.optJSONObject("away").optString("name")),17,true);column.addView(title,new LinearLayout.LayoutParams(-1,px(35)));
        ScrollView scroll=new ScrollView(host);scroll.setFocusable(true);TextView content=text("",12,false);content.setSingleLine(false);content.setMaxLines(Integer.MAX_VALUE);content.setEllipsize(null);content.setLineSpacing(px(5),1f);
        StringBuilder description=new StringBuilder(league.optString("name")).append("\n").append(f.optString("status")).append(" • ").append(MatchCenterData.format(FootballData.parseDate(f.optString("date")).getTime(),"d MMMM، h:mm a")).append(" • توقيت السعودية\n").append(match.optString("venue"));
        JSONArray events=match.optJSONArray("events");if(events==null||events.length()==0)description.append("\nتفاصيل الأحداث غير متاحة من المصدر");else for(int i=0;i<events.length();i++)description.append("\n").append(events.optString(i));
        content.setText(description);scroll.addView(content);column.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView back=button("رجوع إلى مركز المباريات",12,()->closeDetails());column.addView(back,new LinearLayout.LayoutParams(-1,px(30)));
        surface.addView(details,new FrameLayout.LayoutParams(-1,-1));scroll.requestFocus();
    }
    private void closeDetails(){if(details!=null){surface.removeView(details);details=null;}body.setVisibility(View.VISIBLE);firstRowOrTab().requestFocus();}
    @Override public void dismiss(){if(closing)return;closing=true;generation++;ui.removeCallbacksAndMessages(null);if(request!=null)request.cancel(true);surface.animate().cancel();surface.animate().alpha(0).scaleX(.9f).scaleY(.9f).setDuration(220).withEndAction(()->super.dismiss()).start();}
    void dispose(){closing=true;surface.animate().cancel();super.dismiss();}
    private int px(float n){return Ui.px(host,n);}
    private int x(float n){return Math.round(n*host.screenW/3840f);}
    private int y(float n){return Math.round(n*host.screenH/2160f);}
    private void put(FrameLayout parent,View child,float left,float top,float width,float height){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(x(width),y(height),Gravity.LEFT|Gravity.TOP);p.leftMargin=x(left);p.topMargin=y(top);parent.addView(child,p);}
    private void round(View v,float radius){v.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,Outline out){out.setRoundRect(0,0,view.getWidth(),view.getHeight(),px(radius));}});v.setClipToOutline(true);}
    private void fitTeam(TextView text){text.setSingleLine(false);text.setMaxLines(2);if(Build.VERSION.SDK_INT>=26)text.setAutoSizeTextTypeUniformWithConfiguration(Math.max(1,px(8)),px(12),Math.max(1,px(.5f)),android.util.TypedValue.COMPLEX_UNIT_PX);}
    private ImageView image(){ImageView v=new ImageView(host);v.setScaleType(ImageView.ScaleType.FIT_CENTER);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
    private TextView text(String value,float size,boolean bold){TextView t=new TextView(host);t.setText(value);Ui.textSize(t,size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);t.setSingleLine(true);t.setIncludeFontPadding(false);t.setEllipsize(android.text.TextUtils.TruncateAt.END);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView button(String value,float size,Runnable action){return button(value,size,true,action);}
    private TextView button(String value,float size,boolean bold,Runnable action){TextView b=text(value,size,bold);focus(b,action);return b;}
    private GradientDrawable skin(boolean focused,boolean selected,float radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,focused?new int[]{0xD73D82B7,0xD21A3555}:selected?new int[]{0xB02E597F,0xC4162A41}:new int[]{0xA0435365,0xC3182636});d.setCornerRadius(px(radius));d.setStroke(px(focused?1.4f:.6f),focused?0xFFE1FAFF:selected?0xB37FD8FF:0x6B9AC6E5);return d;}
    private void paintFocus(View v){v.setBackground(skin(v.hasFocus(),v.isSelected(),12));v.setElevation(px(v.hasFocus()?5:0));if(Build.VERSION.SDK_INT>=28){v.setOutlineAmbientShadowColor(0xFF5BBFFF);v.setOutlineSpotShadowColor(0xFF5BBFFF);}}
    private void focus(View v,Runnable action){v.setId(View.generateViewId());v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);v.setOnClickListener(w->{if(!closing)action.run();});v.setOnFocusChangeListener((w,focused)->paintFocus(w));paintFocus(v);}
}
