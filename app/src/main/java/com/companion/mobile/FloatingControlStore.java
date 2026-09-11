package com.companion.mobile;

import android.content.*;
import org.json.*;
import java.util.*;

public final class FloatingControlStore {
    private static final String PREFS="floating_satellite_controls", KEY="items", ENABLED="enabled";
    public static final class Item {
        public final String id,name,host,variable,web,lan; public final int x,y,opacity,size; public final boolean interactive,circular,fader,vertical;public final float min,max;
        Item(String id,String name,String host,int x,int y,int opacity,boolean interactive,int size,boolean circular){this(id,name,host,x,y,opacity,interactive,size,circular,false,true,0,100,"","","");}
        Item(String id,String name,String host,int x,int y,int opacity,boolean interactive,int size,boolean circular,boolean fader,boolean vertical,float min,float max,String variable,String web,String lan){this.id=id;this.name=name;this.host=host;this.x=x;this.y=y;this.opacity=Math.max(5,Math.min(100,opacity));this.interactive=interactive;this.size=Math.max(48,Math.min(220,size));this.circular=circular;this.fader=fader;this.vertical=vertical;this.min=min;this.max=max;this.variable=variable;this.web=web;this.lan=lan;}
    }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static List<Item> all(Context c){ArrayList<Item> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs(c).getString(KEY,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);out.add(new Item(o.getString("id"),o.optString("name","Companion 旋鈕"),o.getString("host"),o.optInt("x",24+i*18),o.optInt("y",180+i*18),o.optInt("opacity",100),o.optBoolean("interactive",true),o.optInt("size",76),o.optBoolean("circular",true),o.optBoolean("fader",false),o.optBoolean("vertical",true),(float)o.optDouble("min",0),(float)o.optDouble("max",100),o.optString("variable",""),o.optString("web",""),o.optString("lan","")));}}catch(Exception ignored){}return out;}
    public static Item get(Context c,String id){for(Item item:all(c))if(item.id.equals(id))return item;return null;}
    public static Item create(String name,String host,int opacity,boolean interactive,int size,boolean circular){return new Item(UUID.randomUUID().toString(),name,host,24,180,opacity,interactive,size,circular);}
    public static Item createFader(String name,String host,int opacity,boolean interactive,int size,boolean vertical,float min,float max,String variable,String web,String lan){return new Item(UUID.randomUUID().toString(),name,host,24,180,opacity,interactive,size,false,true,vertical,min,max,variable,web,lan);}
    public static void save(Context c,Item value){List<Item> items=all(c);boolean found=false;for(int i=0;i<items.size();i++)if(items.get(i).id.equals(value.id)){items.set(i,value);found=true;break;}if(!found)items.add(value);write(c,items);prefs(c).edit().putBoolean(ENABLED,true).apply();}
    public static void position(Context c,String id,int x,int y){Item old=get(c,id);if(old!=null)save(c,new Item(old.id,old.name,old.host,x,y,old.opacity,old.interactive,old.size,old.circular,old.fader,old.vertical,old.min,old.max,old.variable,old.web,old.lan));}
    public static void remove(Context c,String id){List<Item> items=all(c);items.removeIf(v->v.id.equals(id));write(c,items);}
    public static boolean enabled(Context c){return prefs(c).getBoolean(ENABLED,true);}
    public static void setEnabled(Context c,boolean value){prefs(c).edit().putBoolean(ENABLED,value).apply();}
    private static void write(Context c,List<Item> items){JSONArray a=new JSONArray();try{for(Item item:items){JSONObject o=new JSONObject();o.put("id",item.id);o.put("name",item.name);o.put("host",item.host);o.put("x",item.x);o.put("y",item.y);o.put("opacity",item.opacity);o.put("interactive",item.interactive);o.put("size",item.size);o.put("circular",item.circular);o.put("fader",item.fader);o.put("vertical",item.vertical);o.put("min",item.min);o.put("max",item.max);o.put("variable",item.variable);o.put("web",item.web);o.put("lan",item.lan);a.put(o);}}catch(Exception ignored){}prefs(c).edit().putString(KEY,a.toString()).apply();}
    private FloatingControlStore(){}
}
