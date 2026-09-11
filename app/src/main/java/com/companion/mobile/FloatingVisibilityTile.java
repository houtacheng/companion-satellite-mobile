package com.companion.mobile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class FloatingVisibilityTile extends TileService {
    @Override public void onStartListening(){super.onStartListening();update();}
    @Override public void onClick(){
        super.onClick();
        boolean visible=FloatingControlStore.enabled(this)&&FloatingControlStore.hasVisible(this);
        if(visible){
            FloatingControlStore.setAllHidden(this,true);
            FloatingControlService.stopAll(this);
        }else if(FloatingControlStore.all(this).isEmpty()||!Settings.canDrawOverlays(this)){
            Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivityAndCollapse(open);
        }else{
            FloatingControlStore.setAllHidden(this,false);
            FloatingControlStore.setEnabled(this,true);
            FloatingControlService.refresh(this);
        }
        refresh(this);
    }
    private void update(){
        Tile tile=getQsTile();if(tile==null)return;
        boolean visible=FloatingControlStore.enabled(this)&&FloatingControlStore.hasVisible(this);
        tile.setLabel(visible?"隱藏所有懸浮按鈕":"開啟所有懸浮按鈕");
        tile.setIcon(Icon.createWithResource(this,R.drawable.companion_control));
        tile.setState(FloatingControlStore.all(this).isEmpty()?Tile.STATE_UNAVAILABLE:(visible?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE));
        tile.updateTile();
    }
    public static void refresh(Context context){try{TileService.requestListeningState(context,new ComponentName(context,FloatingVisibilityTile.class));}catch(Exception ignored){}}
}
