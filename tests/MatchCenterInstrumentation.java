package com.techgenius.tvoslauncher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Future;

/** Actual Android DPAD, persistence, viewport and lifecycle checks using provider data only. */
public final class MatchCenterInstrumentation extends Instrumentation {
    private MainActivity host;private MatchCenterDialog dialog;private boolean offline;
    @Override public void onCreate(Bundle args){super.onCreate(args);offline=args!=null&&"true".equals(args.getString("offline"));start();}
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private void progress(String stage){Bundle b=new Bundle();b.putString("stream",stage+"\n");sendStatus(1,b);}
    private Object field(String name)throws Exception{Field f=MatchCenterDialog.class.getDeclaredField(name);f.setAccessible(true);return f.get(dialog);}
    private int id(){int[] value={-1};runOnMainSync(()->{if(dialog.getCurrentFocus()!=null)value[0]=dialog.getCurrentFocus().getId();});return value[0];}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private void cycle(int code){Set<Integer> visited=new HashSet<>();int first=id();for(int i=0;i<4;i++){visited.add(id());key(code);}check(visited.size()==4&&id()==first,"Tab traversal failed, key="+code);}
    private android.widget.TextView title(View v){
        if(v instanceof android.widget.TextView&&((android.widget.TextView)v).getText().toString().contains("مركز المباريات"))return (android.widget.TextView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){android.widget.TextView found=title(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private void unclipped(android.widget.TextView text,String label){
        check(text!=null&&text.getLayout()!=null,label+" layout missing");
        int available=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom();
        check(text.getLayout().getHeight()<=available,label+" line is vertically clipped");
        check(text.getLayout().getEllipsisCount(0)==0,label+" is ellipsized");
    }
    private View rowView(Object row)throws Exception{Field f=row.getClass().getDeclaredField("view");f.setAccessible(true);return (View)f.get(row);}
    private void allPages()throws Exception{
        int total=(Integer)field("total");if(total<=5){progress("MATCH_PAGING_SOURCE_COUNT_"+total);return;}
        Object[] rows=(Object[])field("rows");View first=rowView(rows[0]);runOnMainSync(first::requestFocus);
        for(int i=1;i<total;i++){key(KeyEvent.KEYCODE_DPAD_DOWN);check(((Integer)field("page"))==i/5&&id()==rowView(rows[i%5]).getId(),"DOWN did not reach match "+(i+1));}
        for(int i=total-2;i>=0;i--){key(KeyEvent.KEYCODE_DPAD_UP);check(((Integer)field("page"))==i/5&&id()==rowView(rows[i%5]).getId(),"UP did not return to match "+(i+1));}
        progress("MATCH_PAGING_ALL_"+total+"_DOWN_AND_UP_OK");
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context app=getTargetContext();app.getSharedPreferences("setup",0).edit().putBoolean("setup_done",true).commit();
            Profiles.homePrefs(app).edit().putBoolean("asked_default_home",true).commit();FootballScores.setEnabled(app,true);
            MatchCenterRepository repository=new MatchCenterRepository(app);
            if(!offline){Set<String> all=new HashSet<>();for(String[] l:MatchCenterData.LEAGUES)all.add(l[0]);repository.select(all);}
            Set<String> selected=repository.selected();String oldFootball=FootballScores.selectedLeagues(app).toString();
            progress("MATCH_UI_LAUNCHING");
            host=(MainActivity)startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();SystemClock.sleep(2000);
            View[] original={host.wallView,host.dockRow,host.footballShortcut,host.prayerCard};
            runOnMainSync(()->{host.showFootballPanel();dialog=host.matchCenterDialog;check(original[0]==host.wallView&&original[1]==host.dockRow&&original[2]==host.footballShortcut&&original[3]==host.prayerCard,"Window opening replaced Home views");});SystemClock.sleep(600);waitForIdleSync();check(dialog!=null&&dialog.isShowing(),"Dialog did not open");
            progress("MATCH_UI_DISPLAY_"+host.screenW+"x"+host.screenH);
            cycle(KeyEvent.KEYCODE_DPAD_LEFT);cycle(KeyEvent.KEYCODE_DPAD_RIGHT);
            key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER);check(((Integer)field("tab"))==1,"Live tab did not activate");
            key(KeyEvent.KEYCODE_DPAD_DOWN);check(id()!=-1,"DOWN lost focus");key(KeyEvent.KEYCODE_DPAD_LEFT);check(id()!=-1,"League navigation lost focus");
            View[] choices=(View[])field("choices");runOnMainSync(()->choices[0].requestFocus());key(KeyEvent.KEYCODE_DPAD_CENTER);
            Set<String> toggled=new HashSet<>(selected);toggled.remove(MatchCenterData.LEAGUES[0][0]);
            check(new MatchCenterRepository(app).selected().equals(toggled),"League checkbox did not persist");
            key(KeyEvent.KEYCODE_DPAD_CENTER);check(new MatchCenterRepository(app).selected().equals(selected),"League checkbox restore failed");
            // Restore the Upcoming tab explicitly without depending on the source having live games.
            View[] tabs=(View[])field("tabs");runOnMainSync(()->tabs[0].performClick());runOnMainSync(()->tabs[0].requestFocus());waitForIdleSync();
            Future<?> request=(Future<?>)field("request");long deadline=SystemClock.elapsedRealtime()+48000;
            while(request!=null&&!request.isDone()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(250);waitForIdleSync();SystemClock.sleep(2500);
            MatchCenterRepository.Snapshot data=(MatchCenterRepository.Snapshot)field("snapshot");
            check(data!=null,"No real-data or empty-state snapshot");
            if(offline&&data.hasCache){check(data.failed||data.stale,"Offline data not marked cached");}
            FrameLayoutInfo bounds=new FrameLayoutInfo();runOnMainSync(()->{
                android.widget.FrameLayout root=(android.widget.FrameLayout)dialog.getWindow().getDecorView().findViewById(android.R.id.content);
                View surface=root.getChildAt(0);int[] point=new int[2];surface.getLocationOnScreen(point);bounds.bottom=point[1]+surface.getHeight();bounds.width=surface.getWidth();bounds.height=surface.getHeight();
            });
            check(Math.abs(bounds.width-host.screenW*.625)<8,"Window width differs from reference");
            check(Math.abs(bounds.height-host.screenH*1110.0/2160)<8,"Window height differs from text-safe reference");
            double oldBottom=Math.round(host.screenH*.16f)+Math.round(host.screenH*1050f/2160f);
            check(Math.abs(bounds.bottom-oldBottom)<8,"Window bottom moved toward the app dock");
            check(bounds.bottom<host.screenH*.78,"Window covers the bottom app dock area");
            runOnMainSync(()->{try{unclipped(title(dialog.getWindow().getDecorView()),"Window title");unclipped((android.widget.TextView)field("footer"),"Last-update footer");}catch(Exception error){throw new RuntimeException(error);}});
            allPages();runOnMainSync(()->tabs[0].requestFocus());
            check(oldFootball.equals(FootballScores.selectedLeagues(app).toString()),"Existing football settings changed");
            check(selected.equals(new MatchCenterRepository(app).selected()),"League choices did not persist");
            Bitmap picture=getUiAutomation().takeScreenshot();check(picture!=null,"Screenshot unavailable");
            progress("MATCH_SCREENSHOT_"+picture.getWidth()+"x"+picture.getHeight());
            File file=new File(app.getExternalFilesDir(null),"match-center.png");try(FileOutputStream out=new FileOutputStream(file)){picture.compress(Bitmap.CompressFormat.PNG,100,out);}
            Bitmap preview=Bitmap.createScaledBitmap(picture,960,540,true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();preview.compress(Bitmap.CompressFormat.JPEG,78,bytes);
            result.putString("preview_base64",android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));preview.recycle();picture.recycle();
            key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);waitForIdleSync();check(!dialog.isShowing(),"BACK did not close");
            boolean[] restored={false};String[] actual={""};long focusDeadline=SystemClock.elapsedRealtime()+2000;
            while(!restored[0]&&SystemClock.elapsedRealtime()<focusDeadline){
                runOnMainSync(()->{restored[0]=host.getCurrentFocus()==host.footballShortcut;actual[0]=String.valueOf(host.getCurrentFocus());});
                if(!restored[0])SystemClock.sleep(100);
            }
            check(restored[0],"BACK did not restore the original match lens; actual="+actual[0]);
            result.putString("stream","MATCH_UI_OK: remote, original Home, persistence, viewport and BACK focus; offline="+offline+"\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","MATCH_UI_FAILED: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    private static final class FrameLayoutInfo {int width,height,bottom;}
}
