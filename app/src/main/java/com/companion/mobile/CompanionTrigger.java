package com.companion.mobile;

import android.content.*;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.net.*;

public final class CompanionTrigger {
    public static void press(Context context, int slot) {
        ControlStore.Control c=ControlStore.get(context,slot);
        if(!c.ready()){ Toast.makeText(context,"請先在 Companion App 設定控制 "+slot,Toast.LENGTH_LONG).show(); return; }
        Context app=context.getApplicationContext();
        new Thread(() -> {
            try {
                String base=c.host.endsWith("/")?c.host.substring(0,c.host.length()-1):c.host;
                URLConnection raw=new URL(base+"/api/location/"+c.page+"/"+c.row+"/"+c.column+"/press").openConnection();
                HttpURLConnection conn=(HttpURLConnection)raw; conn.setRequestMethod("POST"); conn.setConnectTimeout(3500); conn.setReadTimeout(3500); conn.setDoOutput(true); conn.getOutputStream().close();
                int code=conn.getResponseCode(); conn.disconnect(); if(code<200||code>=300) throw new Exception("HTTP "+code);
            } catch(Exception e) { new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app,"Companion 控制失敗："+e.getMessage(),Toast.LENGTH_LONG).show()); }
        }).start();
    }
}
