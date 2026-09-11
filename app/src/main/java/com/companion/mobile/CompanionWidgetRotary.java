package com.companion.mobile;
import android.appwidget.*;import android.content.*;import android.widget.RemoteViews;
public class CompanionWidgetRotary extends AppWidgetProvider {
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids){if(WidgetPrefs.host(c,id).isEmpty())continue;WidgetPrefs.save(c,id,WidgetPrefs.host(c,id),1,1,true);renderPlaceholder(c,m,id);WidgetSatelliteService.start(c,id);}}
 @Override public void onDeleted(Context c,int[] ids){for(int id:ids){WidgetSatelliteService.stopWidget(id);WidgetPrefs.remove(c,id);c.getSharedPreferences("satellite_diagnostics",Context.MODE_PRIVATE).edit().remove("widget_"+id).apply();}}
 @Override public void onReceive(Context c,Intent i){super.onReceive(c,i);int id=i.getIntExtra("widget",-1);if("com.companion.mobile.ROTARY_LEFT".equals(i.getAction()))WidgetSatelliteService.rotate(c,id,-1);else if("com.companion.mobile.ROTARY_RIGHT".equals(i.getAction()))WidgetSatelliteService.rotate(c,id,1);else if("com.companion.mobile.ROTARY_PRESS".equals(i.getAction()))WidgetSatelliteService.press(c,id,0,0);}
 static void renderPlaceholder(Context c,AppWidgetManager m,int id){m.updateAppWidget(id,new RemoteViews(c.getPackageName(),R.layout.widget_rotary));}
}
