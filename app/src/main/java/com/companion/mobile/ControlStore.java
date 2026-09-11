package com.companion.mobile;

import android.content.*;

public final class ControlStore {
    public static class Control {
        public final String name, host, icon, customIcon; public final int page, row, column;
        Control(String n,String h,int p,int r,int c,String i,String custom) { name=n;host=h;page=p;row=r;column=c;icon=i;customIcon=custom; }
        public boolean ready() { return !host.isEmpty(); }
    }
    private static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("companion_controls", Context.MODE_PRIVATE); }
    public static Control get(Context c, int slot) {
        android.content.SharedPreferences p=prefs(c); String k="s"+slot+"_";
        return new Control(p.getString(k+"name","控制 "+slot),p.getString(k+"host",""),p.getInt(k+"page",1),p.getInt(k+"row",0),p.getInt(k+"column",0),p.getString(k+"icon","companion"),p.getString(k+"custom_icon",""));
    }
    public static void save(Context c,int slot,String n,String h,int page,int row,int column) {
        String k="s"+slot+"_"; prefs(c).edit().putString(k+"name",n).putString(k+"host",h).putInt(k+"page",page).putInt(k+"row",row).putInt(k+"column",column).apply();
        CompanionTileBase.refresh(c,slot); ControlSatelliteService.startSlot(c,slot);
    }
    public static void saveHost(Context c,int slot,String h){Control old=get(c,slot);save(c,slot,old.name,h,1,0,0);}
    public static void saveIcon(Context c,int slot,String icon,String custom){String k="s"+slot+"_";prefs(c).edit().putString(k+"icon",icon).putString(k+"custom_icon",custom==null?"":custom).apply();CompanionTileBase.refresh(c,slot);}
    public static void unlink(Context c,int slot) {
        String k="s"+slot+"_";
        ControlSatelliteService.stopSlot(slot);
        prefs(c).edit().putString(k+"host","").apply();
        c.getSharedPreferences("satellite_diagnostics",Context.MODE_PRIVATE).edit().remove("control_"+slot).apply();
        CompanionTileBase.refresh(c,slot);
    }
    public static void remove(Context c,int slot){
        Control old=get(c,slot);ControlSatelliteService.stopSlot(slot);
        if(old.customIcon!=null&&!old.customIcon.isEmpty())new java.io.File(old.customIcon).delete();
        String k="s"+slot+"_";android.content.SharedPreferences.Editor e=prefs(c).edit();
        for(String suffix:new String[]{"name","host","page","row","column","icon","custom_icon"})e.remove(k+suffix);
        e.apply();c.getSharedPreferences("satellite_diagnostics",Context.MODE_PRIVATE).edit().remove("control_"+slot).apply();CompanionTileBase.refresh(c,slot);
    }
    public static int firstAvailable(Context c){for(int i=1;i<=12;i++)if(!get(c,i).ready())return i;return -1;}
}
