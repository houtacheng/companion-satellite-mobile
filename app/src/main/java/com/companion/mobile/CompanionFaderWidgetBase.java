package com.companion.mobile;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public abstract class CompanionFaderWidgetBase extends AppWidgetProvider {
    protected abstract int columns();
    protected abstract String mode();

    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
        for(int id:ids)if(!WidgetPrefs.host(context,id).isEmpty())WidgetSatelliteService.start(context,id);
    }
    @Override public void onDeleted(Context context,int[] ids){for(int id:ids){WidgetSatelliteService.stopWidget(id);WidgetPrefs.remove(context,id);}}
    @Override public void onAppWidgetOptionsChanged(Context context,AppWidgetManager manager,int id,Bundle options){super.onAppWidgetOptionsChanged(context,manager,id,options);if(columns()==1){int width=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);WidgetPrefs.setFaderVertical(context,id,height>=width);WidgetSatelliteService.stopWidget(id);WidgetSatelliteService.start(context,id);}}
    @Override public void onReceive(Context context,Intent intent){
        super.onReceive(context,intent);
        int id=intent.getIntExtra("widget",-1),column=intent.getIntExtra("column",0);
        if("com.companion.mobile.FADER_ROTATE".equals(intent.getAction()))WidgetSatelliteService.rotate(context,id,0,column,intent.getIntExtra("direction",1));
        else if("com.companion.mobile.FADER_PRESS".equals(intent.getAction()))WidgetSatelliteService.press(context,id,0,column);
    }
}
