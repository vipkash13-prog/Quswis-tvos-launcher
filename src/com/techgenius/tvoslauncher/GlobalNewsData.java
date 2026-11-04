package com.techgenius.tvoslauncher;

import org.json.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.text.*;
import java.util.*;
import java.util.regex.*;

/** RSS/Atom normalization, independent of Android UI. No example headlines are app assets. */
final class GlobalNewsData {
    static final int WORLD=0,SAUDI=1,TECH=2,ECONOMY=3,SPORT=4;
    static final String[] SECTIONS={"العالمية","السعودية","التقنية","الاقتصاد","الرياضة"};
    static final TimeZone RIYADH=TimeZone.getTimeZone("Asia/Riyadh");
    static final class Story {
        String title="",source="",url="",image="",summary="";long published;int sections;boolean urgent,cached;
        JSONObject json()throws Exception{return new JSONObject().put("title",title).put("source",source).put("url",url).put("image",image).put("summary",summary).put("published",published).put("sections",sections).put("urgent",urgent);}
        static Story from(JSONObject o){Story s=new Story();s.title=o.optString("title");s.source=o.optString("source");s.url=o.optString("url");s.image=o.optString("image");s.summary=o.optString("summary");s.published=o.optLong("published");s.sections=o.optInt("sections");s.urgent=o.optBoolean("urgent");return s;}
    }
    private static String limited(String value,int max){return value.length()>max?value.substring(0,max):value;}
    static String clean(String html){
        if(html==null)return "";
        String out=html.replaceAll("(?is)<(script|style|iframe)[^>]*>.*?</\\1>"," ").replaceAll("(?is)<[^>]+>"," ")
                .replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'").replace("&#39;","'").replace("&nbsp;"," ");
        Matcher matcher=Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(out);StringBuffer b=new StringBuffer();
        while(matcher.find()){String n=matcher.group(1),replacement="";try{int code=Integer.parseInt(n.startsWith("x")?n.substring(1):n,n.startsWith("x")?16:10);if(Character.isValidCodePoint(code)&&code>=32)replacement=new String(Character.toChars(code));}catch(Exception ignored){}matcher.appendReplacement(b,Matcher.quoteReplacement(replacement));}matcher.appendTail(b);
        return b.toString().replaceAll("\\s+"," ").trim();
    }
    static String webUrl(String raw){
        if(raw==null)return "";String value=raw.trim().replace("&amp;","&");if(value.startsWith("//"))value="https:"+value;
        if(value.startsWith("http://"))value="https://"+value.substring(7);
        try{URI uri=new URI(value).normalize();if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)return "";return limited(uri.toASCIIString(),3000);}catch(Exception error){return "";}
    }
    /** The source's own image resize route, retaining the original asset ID and publisher. */
    static String photoUrl(String raw){
        String safe=webUrl(raw);
        Matcher sky=Pattern.compile("^(https://images\\.skynewsarabia\\.com/images/v1/\\d{4}/\\d{2}/\\d{2}/\\d+/)\\d+/\\d+(/[^?]+(?:\\?.*)?)$").matcher(safe);
        return sky.matches()?sky.group(1)+"1600/900"+sky.group(2):safe;
    }
    static String canonical(String url){
        String safe=webUrl(url);if(safe.isEmpty())return "";
        try{URI u=new URI(safe);StringBuilder result=new StringBuilder("https://").append(u.getHost().toLowerCase(Locale.US));if(u.getPort()!=-1&&u.getPort()!=443)result.append(':').append(u.getPort());String path=u.getRawPath();result.append(path==null||path.isEmpty()?"/":path);List<String> query=new ArrayList<>();
            if(u.getRawQuery()!=null)for(String part:u.getRawQuery().split("&")){String key=part.split("=",2)[0].toLowerCase(Locale.US);if(!key.startsWith("utm_")&&!key.equals("fbclid")&&!key.equals("gclid"))query.add(part);}
            if(!query.isEmpty())result.append('?').append(String.join("&",query));return result.toString();
        }catch(Exception e){return safe;}
    }
    static long date(String raw){
        if(raw==null||raw.trim().isEmpty())return 0;
        for(String value:new String[]{raw.trim(),raw.contains(",")?raw.substring(raw.indexOf(',')+1).trim():raw.trim()})
            for(String pattern:new String[]{"EEE, dd MMM yyyy HH:mm:ss Z","EEE, dd MMM yyyy HH:mm:ss z","dd MMM yyyy HH:mm:ss Z","dd MMM yyyy HH:mm:ss z","yyyy-MM-dd'T'HH:mm:ss.SSSXXX","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mmXXX"}){
                SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.US);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("UTC"));ParsePosition p=new ParsePosition(0);Date date=f.parse(value,p);if(date!=null&&p.getIndex()==value.length())return date.getTime();
            }
        return 0;
    }
    static String time(long at,String pattern){SimpleDateFormat f=new SimpleDateFormat(pattern,new Locale("ar"));f.setTimeZone(RIYADH);return f.format(new Date(at));}
    static String age(long published,long now){if(published<=0)return "وقت النشر غير متاح";if(published>now+300000)return "موعد نشر لاحق";long minutes=Math.max(0,(now-published)/60000);if(minutes<1)return "الآن";if(minutes<60)return "قبل "+minutes+" د";if(minutes<1440)return "قبل "+minutes/60+" س";if(minutes<10080)return "قبل "+minutes/1440+" يوم";return time(published,"d/MM/yyyy");}
    private static String name(Node n){String local=n.getLocalName();return local==null?n.getNodeName().replaceFirst("^.*:",""):local;}
    private static String child(Element e,String... tags){for(String tag:tags)for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&tag.equalsIgnoreCase(name(n)))return n.getTextContent();return "";}
    static List<Story> parse(String xml,String source,int declaredSection)throws Exception{
        if(xml==null||xml.length()>1800000)throw new IllegalArgumentException("Feed too large");
        if(Pattern.compile("<!\\s*(DOCTYPE|ENTITY)",Pattern.CASE_INSENSITIVE).matcher(xml).find())throw new IllegalArgumentException("DTD/entity declarations are not accepted");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setExpandEntityReferences(false);
        for(String feature:new String[]{"http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities","http://apache.org/xml/features/nonvalidating/load-external-dtd"})try{factory.setFeature(feature,false);}catch(Exception ignored){}
        javax.xml.parsers.DocumentBuilder builder=factory.newDocumentBuilder();builder.setEntityResolver((publicId,systemId)->new InputSource(new StringReader("")));
        Document document=builder.parse(new InputSource(new StringReader(xml)));NodeList nodes=document.getElementsByTagNameNS("*","item");if(nodes.getLength()==0)nodes=document.getElementsByTagNameNS("*","entry");
        if(nodes.getLength()==0&&!"rss".equalsIgnoreCase(name(document.getDocumentElement()))&&!"feed".equalsIgnoreCase(name(document.getDocumentElement())))throw new IllegalArgumentException("Response is not RSS/Atom");
        List<Story> out=new ArrayList<>();
        for(int i=0;i<nodes.getLength()&&out.size()<100;i++){
            Element entry=(Element)nodes.item(i);Story s=new Story();s.title=limited(clean(child(entry,"title")),500);s.source=source;
            String link=child(entry,"link");if(link.trim().isEmpty())for(Node n=entry.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&name(n).equals("link")){Element l=(Element)n;if(!l.hasAttribute("rel")||l.getAttribute("rel").equals("alternate")){link=l.getAttribute("href");break;}}
            s.url=canonical(link);if(s.url.isEmpty()||s.title.isEmpty())continue;
            String summary=child(entry,"encoded","description","summary","content");s.summary=limited(clean(summary),12000);
            s.published=date(child(entry,"pubDate","published","date","updated"));
            StringBuilder categories=new StringBuilder();NodeList descendants=entry.getElementsByTagName("*");int best=-1;
            for(int j=0;j<descendants.getLength();j++){
                Element element=(Element)descendants.item(j);String tag=name(element);
                if(tag.equals("category"))categories.append(' ').append(element.hasAttribute("term")?element.getAttribute("term"):element.getTextContent());
                boolean enclosure=tag.equals("enclosure")&&element.getAttribute("type").startsWith("image/");
                boolean media=(tag.equals("thumbnail")||tag.equals("content"))&&element.hasAttribute("url")&&(element.getNamespaceURI()!=null&&element.getNamespaceURI().contains("mrss")||element.getAttribute("type").startsWith("image/"));
                if(enclosure||media){String image=webUrl(element.getAttribute("url"));int width=0;try{width=Integer.parseInt(element.getAttribute("width"));}catch(Exception ignored){}int rank=width+(enclosure||tag.equals("content")?10000:0);if(!image.isEmpty()&&rank>best){s.image=image;best=rank;}}
            }
            if(s.image.isEmpty()){Matcher image=Pattern.compile("(?is)<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(summary);if(image.find())s.image=webUrl(image.group(1));}
            s.sections=classify(s.title+" "+s.summary+" "+categories);if(declaredSection>0)s.sections|=1<<declaredSection;
            s.urgent=s.title.matches("^عاجل(?:\\s|[|:：،-]).*")||categories.toString().toLowerCase(Locale.US).contains("breaking");out.add(s);
        }
        return out;
    }
    private static boolean word(String text,String... values){for(String value:values)if(text.contains(" "+value+" "))return true;return false;}
    static int classify(String raw){String text=" "+clean(raw).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]"," ").replaceAll("\\s+"," ")+" ";int bits=0;
        if(text.contains("السعود")||text.contains("محمد بن سلمان")||text.contains("رؤية 2030")||word(text,"الرياض","saudi"))bits|=1<<SAUDI;
        if(text.contains("الذكاء الاصطناعي")||text.contains("تكنولوج")||word(text,"التقنية","تقنية","تقنيات","هواتف","هاتف","آبل","أبل","سامسونغ","سامسونج","واتساب","مايكروسوفت","أندرويد","رقمي","رقمية","سيبراني","سيبرانية","technology","tech"))bits|=1<<TECH;
        if(text.contains("اقتصاد")||word(text,"النفط","نفط","الأسهم","أسهم","البورصة","بورصة","الدولار","التضخم","الفائدة","المصارف","اقتصادية","الاستثمار","الأسواق","اقتصاد","business","economy"))bits|=1<<ECONOMY;
        if(text.contains("كرة القدم")||text.contains("كرة السلة")||word(text,"الدوري","دوري","مباراة","مباريات","المنتخب","بطولة","البطولات","الرياضة","رياضي","رياضية","أولمبياد","sports","sport"))bits|=1<<SPORT;
        return bits;
    }
    static List<Story> merge(Collection<Story> input){
        List<Story> sorted=new ArrayList<>(input);sorted.sort((a,b)->Long.compare(b.published,a.published));Map<String,Story> urls=new LinkedHashMap<>();Set<String> titles=new HashSet<>();
        for(Story s:sorted){String key=canonical(s.url);if(key.isEmpty()||s.title.isEmpty())continue;Story prior=urls.get(key);if(prior!=null){prior.sections|=s.sections;if(prior.image.isEmpty())prior.image=s.image;continue;}
            String title=clean(s.title).replaceAll("[\\p{Punct}\\s]+"," ").trim();if(!titles.add(title))continue;urls.put(key,s);}
        return new ArrayList<>(urls.values());
    }
    static List<Story> select(List<Story> items,int section,long now){List<Story> out=new ArrayList<>();for(Story s:merge(items))if(s.published<=now+300000&&(section==WORLD||(s.sections&(1<<section))!=0))out.add(s);return out;}
}
