package com.companion.mobile;

import android.app.*; import android.appwidget.*; import android.content.*; import android.widget.RemoteViews;

public class CompanionWidget extends AppWidgetProvider {
    private static final int[] IDS={0,R.id.control1,R.id.control2,R.id.control3,R.id.control4};
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){ for(int id:ids)m.updateAppWidget(id,views(c)); }
    @Override public void onReceive(Context c,Intent i){ super.onReceive(c,i); if("com.companion.mobile.TRIGGER".equals(i.getAction())) CompanionTrigger.press(c,i.getIntExtra("slot",1)); }
    private static RemoteViews views(Context c){ RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_companion); for(int s=1;s<=4;s++){ ControlStore.Control control=ControlStore.get(c,s); v.setTextViewText(IDS[s],control.name); Intent i=new Intent(c,CompanionWidget.class).setAction("com.companion.mobile.TRIGGER").putExtra("slot",s); v.setOnClickPendingIntent(IDS[s],PendingIntent.getBroadcast(c,100+s,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)); } return v; }
    public static void updateAll(Context c){ AppWidgetManager m=AppWidgetManager.getInstance(c); ComponentName n=new ComponentName(c,CompanionWidget.class); int[] ids=m.getAppWidgetIds(n); for(int id:ids)m.updateAppWidget(id,views(c)); }
}
