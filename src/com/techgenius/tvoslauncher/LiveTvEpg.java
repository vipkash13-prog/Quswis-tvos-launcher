package com.techgenius.tvoslauncher;

import android.content.Context;import android.os.*;import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;import java.io.*;import java.net.*;import java.text.*;import java.util.*;import java.util.concurrent.*;import java.util.zip.GZIPInputStream;

/** Optional real XMLTV. No invented programme titles or fallback schedules. */
final class LiveTvEpg {
 private static final ExecutorService WORK=Executors.newSingleThreadExecutor();private static final Handler MAIN=new Handler(Looper.getMainLooper());
 static final class Programme {String title="";long start,end;}
 static final class Guide {Programme now,next;boolean cached;String message="دليل البرامج غير متاح لهذه القناة";}
 interface Callback{void result(Guide guide);}
 static Future<?> load(Context context,String url,String channel,Callback callback){Context app=context.getApplicationContext();return WORK.submit(()->{Guide guide=new Guide();try{
  String safe=LiveTvData.web(url);if(safe.isEmpty()){Guide empty=guide;MAIN.post(()->callback.result(empty));return;}String key=Integer.toHexString(safe.hashCode());File file=new File(app.getFilesDir(),"live-tv-center/epg-"+key+".xml");
  if(!file.exists()||System.currentTimeMillis()-file.lastModified()>LiveTvData.DAY){File tmp=new File(file+".tmp");HttpURLConnection connection=null;try{connection=(HttpURLConnection)new URL(safe).openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(8000);if(connection.getResponseCode()!=200)throw new IOException();InputStream input=connection.getInputStream();PushbackInputStream peek=new PushbackInputStream(input,2);int first=peek.read(),second=peek.read();if(second>=0)peek.unread(second);if(first>=0)peek.unread(first);InputStream decoded=first==31&&second==139?new GZIPInputStream(peek):peek;try(InputStream in=decoded;FileOutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[16384];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>32*1024*1024||Thread.currentThread().isInterrupted())throw new IOException("Guide too large/interrupted");out.write(b,0,n);}}
    parse(tmp,channel,System.currentTimeMillis());if(!tmp.renameTo(file))throw new IOException();
   }catch(Exception e){tmp.delete();guide.cached=true;}finally{if(connection!=null)connection.disconnect();}}
  if(file.exists()){Guide parsed=parse(file,channel,System.currentTimeMillis());parsed.cached=guide.cached;guide=parsed;}
 }catch(Exception ignored){guide.message="تعذّر قراءة دليل البرامج؛ لا توجد مواعيد مؤكدة";}
 final Guide result=guide;if(!Thread.currentThread().isInterrupted())MAIN.post(()->callback.result(result));});}
 static Guide parse(File file,String id,long now)throws Exception{Guide guide=new Guide();try(InputStream input=new FileInputStream(file)){XmlPullParser parser=Xml.newPullParser();parser.setInput(input,"UTF-8");Programme active=null;boolean selected=false,validRoot=false;String tag="";int event;
   while((event=parser.nextToken())!=XmlPullParser.END_DOCUMENT){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(event==XmlPullParser.DOCDECL)throw new IOException("DTD not accepted");
    if(event==XmlPullParser.START_TAG){tag=parser.getName();if(tag.equals("tv"))validRoot=true;if(tag.equals("programme")){String ch=parser.getAttributeValue(null,"channel");selected=ch!=null&&(ch.equals(id)||ch.split("@",2)[0].equals(id));active=selected?new Programme():null;if(active!=null){active.start=date(parser.getAttributeValue(null,"start"));active.end=date(parser.getAttributeValue(null,"stop"));}}}
    else if((event==XmlPullParser.TEXT||event==XmlPullParser.CDSECT)&&active!=null&&tag.equals("title")){String text=parser.getText();if(active.title.length()<1000)active.title+=text;}
    else if(event==XmlPullParser.END_TAG){if(parser.getName().equals("programme")&&active!=null&&!active.title.trim().isEmpty()){if(active.start>0&&active.end>now&&active.start<=now)guide.now=active;else if(active.start>now&&(guide.next==null||active.start<guide.next.start))guide.next=active;active=null;}tag="";}
   }
   if(!validRoot)throw new IOException("Not XMLTV");
  }if(guide.now!=null||guide.next!=null)guide.message="";return guide;}
 static long date(String raw){if(raw==null)return 0;for(String format:new String[]{"yyyyMMddHHmmss Z","yyyyMMddHHmm Z","yyyyMMddHHmmss"}){SimpleDateFormat f=new SimpleDateFormat(format,Locale.US);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("UTC"));try{return f.parse(raw).getTime();}catch(Exception ignored){}}return 0;}
 static String line(Programme p,String label){if(p==null)return label+": غير متاح";SimpleDateFormat f=new SimpleDateFormat("h:mm a",new Locale("ar"));f.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));return label+": "+p.title.trim()+"\n"+f.format(new Date(p.start))+" – "+f.format(new Date(p.end));}
}
