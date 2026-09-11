package com.companion.mobile;
import android.appwidget.*;import android.content.*;import android.widget.RemoteViews;
public abstract class CompanionRotaryGridWidgetBase extends AppWidgetProvider {
 protected abstract int rows();protected abstract String mode();protected abstract int layout();
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids){if(WidgetPrefs.host(c,id).isEmpty())continue;WidgetPrefs.save(c,id,WidgetPrefs.host(c,id),rows(),4,mode());m.updateAppWidget(id,new RemoteViews(c.getPackageName(),layout()));WidgetSatelliteService.start(c,id);}}
 @Override public void onDeleted(Context c,int[] ids){for(int id:ids){WidgetSatelliteService.stopWidget(id);WidgetPrefs.remove(c,id);c.getSharedPreferences("satellite_diagnostics",Context.MODE_PRIVATE).edit().remove("widget_"+id).apply();}}
 @Override public void onReceive(Context c,Intent i){super.onReceive(c,i);int id=i.getIntExtra("widget",-1),row=i.getIntExtra("row",0),col=i.getIntExtra("column",0);if("com.companion.mobile.ROTARY_ROTATE".equals(i.getAction()))WidgetSatelliteService.rotate(c,id,row,col,i.getIntExtra("direction",1));else if("com.companion.mobile.ROTARY_PRESS".equals(i.getAction())||"com.companion.mobile.WIDGET_PRESS".equals(i.getAction()))WidgetSatelliteService.press(c,id,row,col);}
}
