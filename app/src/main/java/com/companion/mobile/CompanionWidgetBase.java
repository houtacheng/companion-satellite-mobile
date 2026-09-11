package com.companion.mobile;
import android.appwidget.*;import android.content.*;import android.os.Build;import android.widget.RemoteViews;import android.view.View;
public abstract class CompanionWidgetBase extends AppWidgetProvider {
 static final int[] KEYS={R.id.key1,R.id.key2,R.id.key3,R.id.key4,R.id.key5,R.id.key6,R.id.key7,R.id.key8,R.id.key9,R.id.key10,R.id.key11,R.id.key12,R.id.key13,R.id.key14,R.id.key15,R.id.key16};
 protected abstract int rows(); protected abstract int cols();
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids){if(WidgetPrefs.host(c,id).isEmpty())continue;WidgetPrefs.save(c,id,WidgetPrefs.host(c,id),rows(),cols());renderPlaceholder(c,m,id,rows(),cols());WidgetSatelliteService.start(c,id);}}
 @Override public void onDeleted(Context c,int[] ids){for(int id:ids){WidgetSatelliteService.stopWidget(id);WidgetPrefs.remove(c,id);c.getSharedPreferences("satellite_diagnostics",Context.MODE_PRIVATE).edit().remove("widget_"+id).apply();}}
 @Override public void onReceive(Context c,Intent i){super.onReceive(c,i);if("com.companion.mobile.WIDGET_PRESS".equals(i.getAction()))WidgetSatelliteService.press(c,i.getIntExtra("widget",-1),i.getIntExtra("row",0),i.getIntExtra("column",0));}
 static int keyId(int rows,int cols,int index){if(cols==1&&rows>1)return new int[]{R.id.key1,R.id.key5,R.id.key9,R.id.key13}[index];if(cols==2)return new int[]{R.id.key1,R.id.key2,R.id.key5,R.id.key6}[index];if(cols==3)return new int[]{R.id.key1,R.id.key2,R.id.key3,R.id.key5,R.id.key6,R.id.key7}[index];return KEYS[index];}
 static RemoteViews baseViews(Context c,int rows,int cols){if(rows==1&&cols==1)return new RemoteViews(c.getPackageName(),R.layout.widget_satellite_1x1);RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_satellite);for(int id:KEYS)v.setViewVisibility(id,View.GONE);for(int i=0;i<rows*cols;i++)v.setViewVisibility(keyId(rows,cols,i),View.VISIBLE);v.setViewVisibility(R.id.widget_row2,rows>1?View.VISIBLE:View.GONE);v.setViewVisibility(R.id.widget_row3,rows>2?View.VISIBLE:View.GONE);v.setViewVisibility(R.id.widget_row4,rows>3?View.VISIBLE:View.GONE);return v;}
 static void renderPlaceholder(Context c,AppWidgetManager m,int id,int rows,int cols){RemoteViews v=baseViews(c,rows,cols);m.updateAppWidget(id,v);}
}
