package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.List;

/** TV-remote friendly live-only IPTV screen; credentials never leave the device except to the provider. */
public final class XtreamLiveActivity extends Activity {
    private LinearLayout root, items;
    private EditText username, password;
    private TextView status;
    private String user, pass;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48,28,48,32);root.setBackgroundColor(Color.rgb(18,22,32));
        scroll.addView(root);setContentView(scroll);
        title("قنوات اشتراكي IPTV",26);
        TextView server=title("السيرفر: "+XtreamLiveClient.SERVER,15);
        server.setTextColor(Color.LTGRAY);
        username=new EditText(this);username.setSingleLine(true);username.setHint("اسم المستخدم");
        password=new EditText(this);password.setSingleLine(true);password.setHint("كلمة المرور");
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(username);root.addView(password);
        Button connect=button("اتصال وتحديث القنوات",()->connect());
        status=title("أدخل بيانات اشتراكك للاتصال",16);
        items=new LinearLayout(this);items.setOrientation(LinearLayout.VERTICAL);root.addView(items);
        username.requestFocus();
    }
    private TextView title(String value,int sp){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(Color.WHITE);
        t.setPadding(8,12,8,12);root.addView(t);return t;
    }
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setFocusable(true);
        root.addView(b);b.setOnClickListener(v->action.run());return b;
    }
    private void connect(){
        user=username.getText().toString().trim();pass=password.getText().toString();
        if(user.isEmpty()||pass.isEmpty()){status.setText("أدخل اسم المستخدم وكلمة المرور");return;}
        status.setText("جاري تحميل القنوات المباشرة...");
        items.removeAllViews();
        final String u=user,p=pass;
        new Thread(()->{
            try {
                List<XtreamLiveClient.Category> categories=XtreamLiveClient.categories(u,p);
                List<XtreamLiveClient.Channel> channels=XtreamLiveClient.channels(u,p);
                runOnUiThread(()->render(channels,categories));
            }catch(Exception ex){
                runOnUiThread(()->status.setText("تعذر الاتصال أو تحميل القنوات. تحقق من بيانات الاشتراك والشبكة، ثم أعد المحاولة."));
            }
        }).start();
    }
    private void render(List<XtreamLiveClient.Channel> channels,List<XtreamLiveClient.Category> categories){
        status.setText("القنوات المباشرة: "+channels.size());
        if(channels.isEmpty()){status.setText("لا توجد قنوات مباشرة متاحة في هذا الاشتراك");return;}
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("بحث عن قناة");
        items.addView(search);
        LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);items.addView(results);
        Runnable update=()->{
            results.removeAllViews();String query=search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            int count=0;
            for(XtreamLiveClient.Channel c:channels){
                if(!c.name.toLowerCase(java.util.Locale.ROOT).contains(query))continue;
                if(++count>250){TextView more=new TextView(this);more.setText("اعرض نتائج أقل باستخدام البحث");results.addView(more);break;}
                Button row=new Button(this);row.setAllCaps(false);row.setText(c.name);
                row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setFocusable(true);
                row.setOnClickListener(v->play(c));results.addView(row);
            }
        };
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){update.run();}
            public void afterTextChanged(android.text.Editable e){}
        });
        update.run();
    }
    private void play(XtreamLiveClient.Channel channel){
        try {
            String url=XtreamLiveClient.playbackUrl(user,pass,channel.id,true);
            Intent intent=new Intent(this,XtreamPlayerActivity.class);
            intent.putExtra("stream_url",url);
            intent.putExtra("stream_url_ts",XtreamLiveClient.playbackUrl(user,pass,channel.id,false));
            intent.putExtra("channel_name",channel.name);
            startActivity(intent);
        }catch(Exception ex){status.setText("تعذر فتح القناة");}
    }
}
