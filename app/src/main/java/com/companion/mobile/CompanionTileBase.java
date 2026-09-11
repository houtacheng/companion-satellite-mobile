package com.companion.mobile;

import android.content.*;
import android.service.quicksettings.*;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import java.io.File;

public abstract class CompanionTileBase extends TileService {
    protected abstract int slot();
    @Override public void onStartListening(){ super.onStartListening(); ControlSatelliteService.startSlot(this,slot()); update(); }
    @Override public void onClick(){ super.onClick(); ControlSatelliteService.press(this,slot()); }
    private void update(){ Tile t=getQsTile(); if(t==null)return; ControlStore.Control c=ControlStore.get(this,slot()); t.setLabel(c.name);t.setIcon(iconFor(this,c));t.setState(!c.ready()?Tile.STATE_UNAVAILABLE:(ControlSatelliteService.isPressed(slot())?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE));t.updateTile(); }
    public static Icon iconFor(Context c,ControlStore.Control value){if(value.customIcon!=null&&!value.customIcon.isEmpty()){File f=new File(value.customIcon);if(f.isFile()){android.graphics.Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath());if(b!=null)return Icon.createWithBitmap(b);}}return Icon.createWithResource(c,resourceFor(value.icon));}
    private static int resourceFor(String key){if("power".equals(key))return android.R.drawable.ic_lock_power_off;if("play".equals(key))return android.R.drawable.ic_media_play;if("stop".equals(key))return android.R.drawable.ic_menu_close_clear_cancel;if("light".equals(key))return android.R.drawable.btn_star_big_on;if("fan".equals(key))return android.R.drawable.ic_popup_sync;if("door".equals(key))return android.R.drawable.ic_menu_directions;if("bolt".equals(key))return android.R.drawable.ic_dialog_alert;if("grid".equals(key))return android.R.drawable.ic_dialog_dialer;if("speaker".equals(key))return android.R.drawable.ic_lock_silent_mode_off;return R.drawable.companion_control;}
    public static void refresh(Context c,int slot){ Class<?>[] types={CompanionTile1.class,CompanionTile2.class,CompanionTile3.class,CompanionTile4.class,CompanionTile5.class,CompanionTile6.class,CompanionTile7.class,CompanionTile8.class,CompanionTile9.class,CompanionTile10.class,CompanionTile11.class,CompanionTile12.class}; TileService.requestListeningState(c,new ComponentName(c,types[slot-1])); }
}
