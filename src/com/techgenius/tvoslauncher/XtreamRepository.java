package com.techgenius.tvoslauncher;

import android.content.Context;import java.io.*;import java.net.*;import java.util.*;import org.json.*;

/** No credentials or authenticated playback URLs are written to ordinary channel preferences. */
final class XtreamRepository {
    private static volatile JSONObject configuration,catalogue;
    static synchronized JSONObject config(Context c)throws Exception{if(configuration==null)configuration=XtreamStore.read(c,"credentials");return configuration;}
    static synchronized void save(Context c,String server,String username,String password)throws Exception{
        if(username.trim().isEmpty()||password.isEmpty())throw new Exception("أدخل Username وPassword");String base=LiveTvData.web(server).replaceAll("/+$","");URI uri=new URI(base);if(base.isEmpty())throw new Exception("عنوان سيرفر غير صالح");if(uri.getRawQuery()!=null||uri.getRawFragment()!=null)throw new Exception("عنوان سيرفر غير صالح");
        JSONObject next=new JSONObject().put("server",base).put("username",username.trim()).put("password",password).put("account",XtreamData.account(base,username.trim()));
        JSONObject old=config(c);boolean changed=old==null||!old.optString("account").equals(next.getString("account"));
        XtreamStore.write(c,"credentials",next);configuration=next;if(changed){XtreamStore.remove(c,"catalogue");catalogue=null;}
    }
    static synchronized void clear(Context c){configuration=null;catalogue=null;XtreamStore.remove(c,"credentials");XtreamStore.remove(c,"catalogue");}
    static synchronized JSONObject saved(Context c)throws Exception{if(catalogue==null)catalogue=XtreamStore.read(c,"catalogue");return catalogue;}
    private static Object request(JSONObject config,String action)throws Exception{
        // Never attach the authenticated URL or the network exception as a cause/message.
        HttpURLConnection http=null;
        try{
            http=(HttpURLConnection)new URL(XtreamData.api(config.getString("server"),config.getString("username"),config.getString("password"),action)).openConnection();
            http.setConnectTimeout(10000);http.setReadTimeout(18000);http.setRequestProperty("User-Agent","Quswis-LiveTV");
            if(http.getResponseCode()!=200)throw new IOException();
            try(InputStream in=http.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] block=new byte[16384];int n;while((n=in.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();out.write(block,0,n);if(out.size()>48*1024*1024)throw new IOException();}
                String text=out.toString("UTF-8").trim();return text.startsWith("[")?new JSONArray(text):new JSONObject(text);
            }
        }catch(Exception ignored){throw new IOException("تعذّر الاتصال بسيرفر الاشتراك أو قراءة رده. تحقق من الاتصال وبيانات الدخول.");}
        finally{if(http!=null)http.disconnect();}
    }
    static LiveTvRepository.Snapshot read(Context c,boolean force){
        LiveTvRepository.Snapshot out=new LiveTvRepository.Snapshot();out.section=7;
        try{
            JSONObject cfg=config(c);if(cfg==null){out.message="أدخل بيانات اشتراك IPTV من إعدادات هذا التبويب";return out;}
            JSONObject cache=saved(c);boolean valid=cache!=null&&cfg.getString("account").equals(cache.optString("account"));
            if(force||!valid||System.currentTimeMillis()-cache.optLong("updated")>LiveTvData.DAY){
                try{
                    JSONObject auth=(JSONObject)request(cfg,"");List<String> formats=XtreamData.formats(auth);
                    JSONArray categories=(JSONArray)request(cfg,"get_live_categories"),streams=(JSONArray)request(cfg,"get_live_streams");
                    JSONObject next=XtreamData.catalogue(cfg.getString("account"),categories,streams,formats);
                    synchronized(XtreamRepository.class){if(configuration!=cfg)throw new IOException();XtreamStore.write(c,"catalogue",next);catalogue=cache=next;valid=true;}
                }catch(Exception error){out.failed=true;out.cached=valid;out.message=error.getMessage();}
            }else out.cached=true;
            if(valid){out.channels.addAll(XtreamData.channels(cache));out.updated=cache.optLong("updated");if(out.channels.isEmpty()&&out.message.isEmpty())out.message="لا توجد قنوات مباشرة متاحة لهذا الاشتراك";}
        }catch(Exception ignored){out.failed=true;out.message="تعذّر فتح بيانات الاشتراك المشفرة. أعد حفظ بيانات الدخول من الإعدادات.";}
        return out;
    }
    static synchronized String logo(Context c,String marker)throws Exception{
        URI uri=new URI(marker);JSONObject cfg=config(c),cache=saved(c);if(cfg==null||cache==null||!uri.getHost().equals(cfg.optString("account")))return "";String id=uri.getPath().substring(1);JSONArray rows=cache.getJSONArray("channels");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.getString("id").equals(id))return row.optString("logo");}return "";
    }
    static String playback(Context c,String marker)throws Exception{
        if(!marker.startsWith("xtream://"))return marker;JSONObject cfg=configuration;if(cfg==null)throw new IOException("أعد فتح تبويب الاشتراك أولًا");URI uri=new URI(marker);
        if(!uri.getHost().equals(cfg.getString("account"))||!uri.getPath().matches("/[0-9]+\\.(?:ts|m3u8)"))throw new IOException("هذه القناة تخص حساب اشتراك آخر. حدّث القائمة.");
        return cfg.getString("server")+"/live/"+XtreamData.segment(cfg.getString("username"))+"/"+XtreamData.segment(cfg.getString("password"))+uri.getPath();
    }
}
