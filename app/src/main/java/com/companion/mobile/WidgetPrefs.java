package com.companion.mobile;
import android.content.*;
public final class WidgetPrefs {
 static android.content.SharedPreferences p(Context c){return c.getSharedPreferences("satellite_widgets",Context.MODE_PRIVATE);}
 public static void save(Context c,int id,String host,int rows,int cols){save(c,id,host,rows,cols,false);}
 public static void save(Context c,int id,String host,int rows,int cols,boolean rotary){save(c,id,host,rows,cols,rotary?"rotary1":"grid");}
 public static void save(Context c,int id,String host,int rows,int cols,String mode){p(c).edit().putString("h"+id,host).putInt("r"+id,rows).putInt("c"+id,cols).putBoolean("k"+id,!"grid".equals(mode)).putString("m"+id,mode).apply();}
 public static String host(Context c,int id){return p(c).getString("h"+id,"");} public static int rows(Context c,int id){return p(c).getInt("r"+id,1);} public static int cols(Context c,int id){return p(c).getInt("c"+id,1);}
 public static boolean rotary(Context c,int id){return p(c).getBoolean("k"+id,false);}
 public static String mode(Context c,int id){return p(c).getString("m"+id,rotary(c,id)?"rotary1":"grid");}
 public static boolean interactive(Context c,int id){return p(c).getBoolean("i"+id,true);}
 public static void setInteractive(Context c,int id,boolean enabled){p(c).edit().putBoolean("i"+id,enabled).apply();}
 public static int opacity(Context c,int id){return p(c).getInt("o"+id,100);}
 public static void setOpacity(Context c,int id,int opacity){p(c).edit().putInt("o"+id,Math.max(5,Math.min(100,opacity))).apply();}
 public static void remove(Context c,int id){p(c).edit().remove("h"+id).remove("r"+id).remove("c"+id).remove("k"+id).remove("m"+id).remove("i"+id).remove("o"+id).apply();}
}
