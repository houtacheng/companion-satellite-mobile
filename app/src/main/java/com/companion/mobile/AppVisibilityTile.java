package com.companion.mobile;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public final class AppVisibilityTile extends TileService {
    @Override public void onStartListening(){super.onStartListening();update();}
    @Override public void onClick(){
        super.onClick();
        try{
            if(!MainActivity.hideIfVisible()){
                Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                if(Build.VERSION.SDK_INT>=34){
                    PendingIntent pending=PendingIntent.getActivity(this,16626,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                    startActivityAndCollapse(pending);
                }else startActivityAndCollapse(open);
            }
        }catch(Exception error){
            Toast.makeText(this,"無法切換 Companion App，請再試一次",Toast.LENGTH_SHORT).show();
        }
        refresh(this);
    }
    private void update(){
        Tile tile=getQsTile();if(tile==null)return;
        boolean visible=MainActivity.isVisible();
        tile.setLabel(visible?"隱藏 Companion App":"開啟 Companion App");
        tile.setIcon(Icon.createWithResource(this,R.drawable.companion_control));
        tile.setState(visible?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);
        tile.updateTile();
    }
    public static void refresh(Context context){try{TileService.requestListeningState(context,new ComponentName(context,AppVisibilityTile.class));}catch(Exception ignored){}}
}
