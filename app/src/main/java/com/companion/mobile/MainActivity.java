package com.companion.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;
    private TextView title, empty;
    private ProgressBar progress;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;
    private final android.os.Handler networkHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final ArrayList<Host> hosts = new ArrayList<>();
    private android.content.SharedPreferences prefs;
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int IMPORT_HOSTS_CSV=4101,EXPORT_HOSTS_CSV=4102,IMPORT_CONTROL_PNG=4103;
    private int pendingIconSlot=-1;

    static class Host {
        String name, url, lan, satellite;
        Host(String name, String url) { this(name,url,""); }
        Host(String name, String url,String lan) { this.name = name; this.url = url; this.lan=lan; this.satellite=defaultSatellite(url); }
        static String defaultSatellite(String url){Uri u=Uri.parse(url);String authority=u.getEncodedAuthority();if(authority==null)return "";return "wss://"+authority+"/satellite";}
        String route(){return lan.isEmpty()?satellite:NetworkRoute.auto(lan,satellite);}
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("companion_hosts", MODE_PRIVATE);
        loadHosts();
        WidgetRestorer.restoreAll(this);
        ControlSatelliteService.startAll(this);
        FloatingControlService.restore(this);
        FloatingLauncherService.restore(this);
        buildUi();
        String active = prefs.getString("active", "");
        if (!active.isEmpty()) open(active); else showWelcome();
        android.net.ConnectivityManager cm=getSystemService(android.net.ConnectivityManager.class);
        networkCallback=new android.net.ConnectivityManager.NetworkCallback(){@Override public void onAvailable(android.net.Network network){scheduleWebRouteRefresh();}@Override public void onLost(android.net.Network network){scheduleWebRouteRefresh();}};
        cm.registerDefaultNetworkCallback(networkCallback);
    }

    private void scheduleWebRouteRefresh(){networkHandler.removeCallbacksAndMessages(null);networkHandler.postDelayed(()->{String active=prefs.getString("active","");if(active.isEmpty())return;android.net.ConnectivityManager cm=getSystemService(android.net.ConnectivityManager.class);android.net.Network network=cm.getActiveNetwork();android.net.NetworkCapabilities caps=network==null?null:cm.getNetworkCapabilities(network);boolean wifi=caps!=null&&caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);if(wifi)open(active);else{empty.setVisibility(View.GONE);web.setVisibility(View.VISIBLE);web.loadUrl(active);Toast.makeText(this,"使用行動數據，切換至網際網路主機",Toast.LENGTH_SHORT).show();}},700);}

    private TextView button(String text) {
        TextView b = new TextView(this);
        b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(15); b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), 0, dp(16), 0); b.setMinWidth(dp(64));
        b.setClickable(true); b.setFocusable(true);
        android.util.TypedValue out = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        b.setBackgroundResource(out.resourceId);
        return b;
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(8), 0, dp(8), 0); bar.setBackgroundColor(Color.rgb(17,24,39));
        TextView menu = button("主機");
        title = new TextView(this); title.setText("Companion"); title.setTextColor(Color.WHITE); title.setTextSize(18); title.setSingleLine(true); title.setPadding(18,0,8,0);
        TextView controls = button("控制");
        TextView refresh = button("↻");
        bar.addView(menu, new LinearLayout.LayoutParams(-2, dp(56)));
        bar.addView(controls, new LinearLayout.LayoutParams(-2, dp(56)));
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        bar.addView(refresh, new LinearLayout.LayoutParams(-2, dp(56)));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
        web = new WebView(this); configureWebView();
        empty = new TextView(this); empty.setGravity(Gravity.CENTER); empty.setTextSize(18); empty.setTextColor(Color.DKGRAY); empty.setPadding(48,48,48,48);
        FrameLayout content = new FrameLayout(this); content.addView(web, new FrameLayout.LayoutParams(-1,-1)); content.addView(empty, new FrameLayout.LayoutParams(-1,-1));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56))); root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3))); root.addView(content, new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        menu.setOnClickListener(v -> showHosts()); controls.setOnClickListener(v -> showControls()); refresh.setOnClickListener(v -> web.reload());
    }

    private void configureWebView() {
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setMediaPlaybackRequiresUserGesture(false); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false); s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl(); String scheme = u.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {} return true;
            }
            @Override public void onPageStarted(WebView v, String u, android.graphics.Bitmap f) { progress.setVisibility(View.VISIBLE); }
            @Override public void onPageFinished(WebView v, String u) { progress.setVisibility(View.GONE);String active=prefs.getString("active","");for(Host h:hosts)if(h.url.equals(active)){title.setText(h.name);return;}title.setText("Companion"); }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) { if(r.isForMainFrame()){String failed=r.getUrl().toString();for(Host h:hosts)if(!h.lan.isEmpty()&&failed.startsWith(h.lan)){Toast.makeText(MainActivity.this,"區網連線失敗，改用網際網路",Toast.LENGTH_SHORT).show();v.loadUrl(h.url);return;}Toast.makeText(MainActivity.this,"無法連線到主機",Toast.LENGTH_LONG).show();} }
        });
        web.setWebChromeClient(new WebChromeClient() { @Override public void onProgressChanged(WebView v, int p) { progress.setProgress(p); } });
        web.setDownloadListener((url, ua, cd, mt, len) -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { Toast.makeText(this,"無法下載此檔案",Toast.LENGTH_SHORT).show(); } });
    }

    private void showWelcome() { web.setVisibility(View.GONE); empty.setVisibility(View.VISIBLE); empty.setText("尚未設定 Companion 主機\n\n點選上方「主機」，再按「新增主機」"); }

    private void showHosts() {
        ArrayList<String> labels = new ArrayList<>();
        for (Host h : hosts) labels.add("⏳  "+h.name+"　檢查連線中");
        labels.add("＋ 新增主機");
        labels.add("↓ 匯入主機 CSV");
        labels.add("↑ 匯出主機 CSV");
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,labels);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("切換 Companion 主機").setAdapter(adapter, (d, which) -> {
            if(which<hosts.size())hostActions(which);else if(which==hosts.size())editHost(null,-1);else if(which==hosts.size()+1)chooseImportCSV();else chooseExportCSV();
        }).setNegativeButton("取消", null).create();dialog.show();
        android.net.ConnectivityManager cm=getSystemService(android.net.ConnectivityManager.class);android.net.Network network=cm.getActiveNetwork();android.net.NetworkCapabilities caps=network==null?null:cm.getNetworkCapabilities(network);boolean wifi=caps!=null&&caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
        for(int i=0;i<hosts.size();i++){final int index=i;final Host h=hosts.get(i);new Thread(()->{boolean local=wifi&&!h.lan.isEmpty()&&NetworkRoute.lanAvailable(h.lan);boolean online=local||NetworkRoute.lanAvailable(h.url);String label=(online?"🟢  ":"🔴  ")+h.name+"　"+(online?(local?"區網":"網際網路"):"無法連線");runOnUiThread(()->{labels.set(index,label);adapter.notifyDataSetChanged();});},"Host-Status-"+i).start();}
    }

    private void showControls() {
        ArrayList<Integer> slots=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
        for(int i=1;i<=12;i++){ControlStore.Control c=ControlStore.get(this,i);if(c.ready()){slots.add(i);labels.add(c.name);}}
        labels.add("◉ 懸浮 Satellite 旋鈕");labels.add("＋ 新增控制項");labels.add("＋ 加入通知欄下拉面板");labels.add("Satellite 連線診斷");
        new AlertDialog.Builder(this).setTitle("Companion 控制項").setItems(labels.toArray(new String[0]),(d,w)->{if(w<slots.size())editControl(slots.get(w));else if(w==slots.size())showFloatingControls();else if(w==slots.size()+1){int slot=ControlStore.firstAvailable(this);if(slot<0)Toast.makeText(this,"最多可建立 12 個控制項",Toast.LENGTH_LONG).show();else editControl(slot);}else if(w==slots.size()+2)chooseTile();else showSatelliteDiagnostics();}).setNegativeButton("取消",null).show();
    }

    private void showFloatingControls(){
        List<FloatingControlStore.Item> items=FloatingControlStore.all(this);ArrayList<String> labels=new ArrayList<>();for(FloatingControlStore.Item item:items)labels.add("●  "+item.name);labels.add("＋ 新增懸浮旋鈕");labels.add(FloatingLauncherService.enabled(this)?"關閉 Companion App 啟動球":"開啟 Companion App 啟動球");if(!items.isEmpty())labels.add(FloatingControlStore.enabled(this)?"暫停所有懸浮旋鈕":"啟動所有懸浮旋鈕");
        new AlertDialog.Builder(this).setTitle("懸浮工具 · 長按可移動").setItems(labels.toArray(new String[0]),(d,w)->{if(w<items.size())floatingActions(items.get(w));else if(w==items.size())editFloating(null);else if(w==items.size()+1)toggleLauncherBall();else{boolean enable=!FloatingControlStore.enabled(this);FloatingControlStore.setEnabled(this,enable);if(enable)ensureOverlayAndStart();else FloatingControlService.stopAll(this);}}).setNegativeButton("取消",null).show();
    }

    private void floatingActions(FloatingControlStore.Item item){new AlertDialog.Builder(this).setTitle(item.name).setItems(new String[]{"編輯","移除"},(d,w)->{if(w==0)editFloating(item);else new AlertDialog.Builder(this).setTitle("移除懸浮旋鈕？").setPositiveButton("移除",(x,y)->{FloatingControlStore.remove(this,item.id);FloatingControlService.refresh(this);}).setNegativeButton("取消",null).show();}).setNegativeButton("取消",null).show();}

    private void editFloating(FloatingControlStore.Item old){if(hosts.isEmpty()){Toast.makeText(this,"請先新增 Companion 主機",Toast.LENGTH_LONG).show();return;}LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),0);EditText name=new EditText(this);name.setHint("懸浮旋鈕名稱");name.setSingleLine();name.setText(old==null?"Companion 旋鈕":old.name);Spinner host=new Spinner(this);ArrayList<String> names=new ArrayList<>();int selected=0;for(int i=0;i<hosts.size();i++){names.add(hosts.get(i).name+" — Satellite");if(old!=null&&hosts.get(i).route().equals(old.host))selected=i;}host.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));host.setSelection(selected);TextView opacityLabel=new TextView(this);opacityLabel.setPadding(0,dp(12),0,0);SeekBar opacity=new SeekBar(this);opacity.setMin(5);opacity.setMax(100);opacity.setProgress(old==null?100:old.opacity);opacityLabel.setText("透明度："+opacity.getProgress()+"%");opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){opacityLabel.setText("透明度："+value+"%");}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});CheckBox interaction=new CheckBox(this);interaction.setText("允許按下與旋轉");interaction.setChecked(old==null||old.interactive);TextView hint=new TextView(this);hint.setText("取消勾選時只顯示即時狀態（Tally），不會送出按下或旋轉操作。長按拖曳位置仍可使用。\n\n儲存後會在 Companion Surfaces 出現獨立的 1×1 Satellite Surface，按鈕位置由 Companion 主機指派。");hint.setTextColor(Color.GRAY);hint.setPadding(0,dp(10),0,0);box.addView(name);box.addView(host);box.addView(opacityLabel);box.addView(opacity);box.addView(interaction);box.addView(hint);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(old==null?"新增懸浮旋鈕":"編輯懸浮旋鈕").setView(box).setPositiveButton("儲存",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{String n=name.getText().toString().trim();if(n.isEmpty())n="Companion 旋鈕";FloatingControlStore.Item value=old==null?FloatingControlStore.create(n,hosts.get(host.getSelectedItemPosition()).route(),opacity.getProgress(),interaction.isChecked()):new FloatingControlStore.Item(old.id,n,hosts.get(host.getSelectedItemPosition()).route(),old.x,old.y,opacity.getProgress(),interaction.isChecked());FloatingControlStore.save(this,value);dialog.dismiss();ensureOverlayAndStart();}));dialog.show();}

    private void ensureOverlayAndStart(){FloatingControlStore.setEnabled(this,true);if(android.provider.Settings.canDrawOverlays(this)){FloatingControlService.refresh(this);Toast.makeText(this,"懸浮旋鈕已啟動：輕點按下、左右滑旋轉、長按拖曳移動",Toast.LENGTH_LONG).show();}else{new AlertDialog.Builder(this).setTitle("需要懸浮視窗權限").setMessage("下一個畫面請開啟「允許顯示在其他 App 上層」，返回後懸浮旋鈕會自動出現。").setPositiveButton("前往開啟",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))).setNegativeButton("稍後",null).show();}}

    private void toggleLauncherBall(){boolean enable=!FloatingLauncherService.enabled(this);if(enable&&!android.provider.Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle("需要懸浮視窗權限").setMessage("下一個畫面請開啟「允許顯示在其他 App 上層」，返回後再開啟 Companion App 啟動球。").setPositiveButton("前往開啟",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))).setNegativeButton("稍後",null).show();return;}FloatingLauncherService.setEnabled(this,enable);Toast.makeText(this,enable?"Companion App 啟動球已開啟":"Companion App 啟動球已關閉",Toast.LENGTH_SHORT).show();}

    private void showSatelliteDiagnostics(){android.content.SharedPreferences d=getSharedPreferences("satellite_diagnostics",MODE_PRIVATE);StringBuilder out=new StringBuilder();for(int i=1;i<=12;i++){ControlStore.Control control=ControlStore.get(this,i);String value=d.getString("control_"+i,"");if(control.ready()&&!value.isEmpty())out.append("控制 ").append(i).append("：").append(value).append("\n");}android.appwidget.AppWidgetManager manager=android.appwidget.AppWidgetManager.getInstance(this);Class<?>[] providers={CompanionWidget1x1.class,CompanionWidget2x2.class,CompanionWidget3x2.class,CompanionWidget4x1.class,CompanionWidget1x4.class,CompanionWidget4x2.class,CompanionWidget4x3.class,CompanionWidget4x4.class,CompanionWidgetRotary.class,CompanionWidgetMixed4x4.class,CompanionWidgetRotary4x1.class};for(Class<?> provider:providers){int[] ids=manager.getAppWidgetIds(new ComponentName(this,provider));for(int id:ids){String value=d.getString("widget_"+id,"");if(!value.isEmpty())out.append("Widget ").append(id).append("：").append(value).append("\n");}}if(out.length()==0)out.append("尚無連線紀錄");new AlertDialog.Builder(this).setTitle("Satellite 連線診斷").setMessage(out.toString().trim()).setPositiveButton("確定",null).show();}

    private void editControl(int slot) {
        if(hosts.isEmpty()){ Toast.makeText(this,"請先新增 Companion 主機",Toast.LENGTH_LONG).show(); showHosts(); return; }
        ControlStore.Control old=ControlStore.get(this,slot);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(8),dp(20),0);
        EditText name=new EditText(this); name.setHint("控制項名稱（可留空）"); name.setText(old.name); name.setSingleLine();
        TextView hostLabel=new TextView(this); hostLabel.setText("主機"); hostLabel.setPadding(0,dp(12),0,0);
        Spinner host=new Spinner(this); ArrayList<String> hostNames=new ArrayList<>(); int selected=0;
        for(int i=0;i<hosts.size();i++){String route=hosts.get(i).route();hostNames.add(hosts.get(i).name+" — 自動選擇區網／網際網路"); if(route.equals(old.host)||hosts.get(i).satellite.equals(old.host))selected=i; }
        host.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,hostNames)); host.setSelection(selected);
        TextView hint=new TextView(this); hint.setText("儲存後，此控制會以獨立 Satellite Surface 出現在所選 Companion。請在 Companion 的 Surfaces 頁面指定它所對應的按鈕位置。控制項名稱完全由您輸入，不會被 Companion 後台按鈕文字覆蓋。"); hint.setTextColor(Color.GRAY); hint.setPadding(0,dp(10),0,0);
        TextView iconLabel=new TextView(this);iconLabel.setText("圖示");iconLabel.setPadding(0,dp(12),0,0);
        String[] iconNames={"Companion","燈光","風扇","門","喇叭","播放","停止","電源","閃電","按鈕矩陣"};String[] iconKeys={"companion","light","fan","door","speaker","play","stop","power","bolt","grid"};
        Spinner icon=new Spinner(this);icon.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,iconNames));int selectedIcon=0;for(int i=0;i<iconKeys.length;i++)if(iconKeys[i].equals(old.icon))selectedIcon=i;icon.setSelection(selectedIcon);
        final boolean[] useBuiltin={false};Button applyBuiltin=new Button(this);applyBuiltin.setText("使用選取的內建圖示");applyBuiltin.setOnClickListener(v->{useBuiltin[0]=true;Toast.makeText(this,"儲存後改用內建圖示",Toast.LENGTH_SHORT).show();});
        Button importPng=new Button(this);importPng.setText(old.customIcon.isEmpty()?"自訂匯入 PNG 檔":"更換自訂 PNG（目前已設定）");importPng.setOnClickListener(v->{pendingIconSlot=slot;Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/png").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,IMPORT_CONTROL_PNG);});
        Button removePng=new Button(this);removePng.setText("移除自訂 PNG");removePng.setVisibility(old.customIcon.isEmpty()?View.GONE:View.VISIBLE);removePng.setOnClickListener(v->{useBuiltin[0]=true;ControlStore.saveIcon(this,slot,iconKeys[icon.getSelectedItemPosition()],"");removePng.setVisibility(View.GONE);importPng.setText("自訂匯入 PNG 檔");Toast.makeText(this,"已移除自訂 PNG",Toast.LENGTH_SHORT).show();});
        box.addView(name); box.addView(hostLabel); box.addView(host);box.addView(iconLabel);box.addView(icon);box.addView(applyBuiltin);box.addView(importPng);box.addView(removePng);box.addView(hint);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle("設定控制 "+slot).setView(box).setPositiveButton("儲存",null).setNegativeButton("取消",null);
        if(old.ready())builder.setNeutralButton("移除控制項",null);
        AlertDialog dialog=builder.create();
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try { String n=name.getText().toString().trim();ControlStore.save(this,slot,n,hosts.get(host.getSelectedItemPosition()).route(),1,0,0);if(useBuiltin[0]||!new File(ControlStore.get(this,slot).customIcon).isFile())ControlStore.saveIcon(this,slot,iconKeys[icon.getSelectedItemPosition()],"");Toast.makeText(this,"Satellite 控制項已儲存",Toast.LENGTH_SHORT).show();dialog.dismiss(); } catch(Exception e){Toast.makeText(this,"控制項儲存失敗",Toast.LENGTH_LONG).show();}
            });
            if(old.ready())dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("移除此控制項？").setMessage("會移除 App 內的設定與自訂 PNG。若已加入下拉面板，Android 不允許 App 自動刪除系統按鈕，請在下拉面板編輯畫面手動移除。").setPositiveButton("移除",(confirm,which)->{ControlStore.remove(this,slot);Toast.makeText(this,"控制項已移除",Toast.LENGTH_SHORT).show();dialog.dismiss();}).setNegativeButton("返回",null).show());
        }); dialog.show();
    }

    private EditText numberField(String hint,int value){ EditText e=new EditText(this); e.setHint(hint); e.setText(String.valueOf(value)); e.setGravity(Gravity.CENTER); e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); return e; }

    private void chooseTile(){
        ArrayList<Integer> slots=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();for(int i=1;i<=12;i++)if(ControlStore.get(this,i).ready()){slots.add(i);labels.add(ControlStore.get(this,i).name);}
        if(slots.isEmpty()){Toast.makeText(this,"請先新增控制項",Toast.LENGTH_LONG).show();return;}new AlertDialog.Builder(this).setTitle("選擇要加入的快捷鍵").setItems(labels.toArray(new String[0]),(d,w)->requestTile(slots.get(w))).setNegativeButton("取消",null).show();
    }

    private void requestTile(int slot){
        if(android.os.Build.VERSION.SDK_INT<33){ Toast.makeText(this,"請展開通知欄，點編輯，再加入 Companion "+slot,Toast.LENGTH_LONG).show(); return; }
        Class<?>[] types={CompanionTile1.class,CompanionTile2.class,CompanionTile3.class,CompanionTile4.class,CompanionTile5.class,CompanionTile6.class,CompanionTile7.class,CompanionTile8.class,CompanionTile9.class,CompanionTile10.class,CompanionTile11.class,CompanionTile12.class}; ControlStore.Control c=ControlStore.get(this,slot);
        getSystemService(android.app.StatusBarManager.class).requestAddTileService(new ComponentName(this,types[slot-1]),c.name,CompanionTileBase.iconFor(this,c),getMainExecutor(),result->Toast.makeText(this,result==android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED?"已加入下拉面板":"未加入；也可從下拉面板的編輯功能手動加入",Toast.LENGTH_LONG).show());
    }

    private void hostActions(int index) {
        Host h = hosts.get(index);
        new AlertDialog.Builder(this).setTitle(h.name).setItems(new String[]{"連線", "編輯", "刪除"}, (d,w) -> {
            if (w == 0) open(h.url); else if (w == 1) editHost(h,index); else confirmDelete(index);
        }).setNegativeButton("取消", null).show();
    }

    private void editHost(Host old, int index) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int p=(int)(20*getResources().getDisplayMetrics().density); box.setPadding(p,8,p,0);
        EditText name = new EditText(this); name.setHint("名稱，例如：家裡"); name.setSingleLine();
        EditText url = new EditText(this); url.setHint("網頁網址，例如：https://companion.example.com"); url.setSingleLine(); url.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        EditText lan = new EditText(this); lan.setHint("區網網址，例如：http://192.168.1.100:8000"); lan.setSingleLine(); lan.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        TextView satelliteHint=new TextView(this);satelliteHint.setText("Satellite 網址會由網際網路網址自動產生（/satellite）");satelliteHint.setTextColor(Color.GRAY);satelliteHint.setPadding(0,dp(8),0,0);
        if (old != null) { name.setText(old.name); url.setText(old.url); lan.setText(old.lan); }
        box.addView(name); box.addView(url); box.addView(lan); box.addView(satelliteHint);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(old == null ? "新增主機" : "編輯主機").setView(box).setPositiveButton("儲存", null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(x -> dialog.getButton(-1).setOnClickListener(v -> {
            String n=name.getText().toString().trim(), u=normalize(url.getText().toString()), local=normalize(lan.getText().toString());
            if (n.isEmpty() || u == null || local==null) { Toast.makeText(this,"請輸入名稱、網際網路網址與區網網址",Toast.LENGTH_LONG).show(); return; }
            if (index < 0) hosts.add(new Host(n,u,local)); else { Host changed=hosts.get(index);String oldEndpoint=changed.satellite;changed.name=n;changed.url=u;changed.lan=local;changed.satellite=Host.defaultSatellite(u);NetworkRoute.migrate(this,oldEndpoint,changed.route()); }
            saveHosts(); dialog.dismiss(); open(u);
        })); dialog.show();
    }

    private String normalize(String value) {
        String u=value.trim(); if (!u.contains("://")) u="http://"+u;
        Uri parsed=Uri.parse(u); String s=parsed.getScheme(); return (("http".equals(s)||"https".equals(s)) && parsed.getHost()!=null) ? u : null;
    }

    private void confirmDelete(int i) { new AlertDialog.Builder(this).setTitle("刪除主機？").setMessage(hosts.get(i).name).setPositiveButton("刪除",(d,w)->{ String removed=hosts.remove(i).url; saveHosts(); if (removed.equals(prefs.getString("active",""))) { prefs.edit().remove("active").apply(); web.loadUrl("about:blank"); showWelcome(); } }).setNegativeButton("取消",null).show(); }

    private void open(String url) { empty.setVisibility(View.GONE); web.setVisibility(View.VISIBLE); prefs.edit().putString("active",url).apply();Host found=null;for(Host h:hosts)if(h.url.equals(url)){found=h;title.setText(h.name);break;}if(found==null||found.lan.isEmpty()){web.loadUrl(url);return;}Host target=found;new Thread(()->{boolean local=NetworkRoute.lanAvailable(target.lan);runOnUiThread(()->{Toast.makeText(this,local?"使用區網連線":"區網不可用，使用網際網路連線",Toast.LENGTH_SHORT).show();web.loadUrl(local?target.lan:target.url);});}).start(); }

    private void loadHosts() { try { JSONArray a=new JSONArray(prefs.getString("hosts","[]")); for(int i=0;i<a.length();i++){ JSONObject o=a.getJSONObject(i);String url=o.getString("url");hosts.add(new Host(o.getString("name"),url,o.optString("lan",""))); } } catch(Exception ignored) {} }
    private void saveHosts() { JSONArray a=new JSONArray(); try { for(Host h:hosts){ JSONObject o=new JSONObject(); o.put("name",h.name);o.put("url",h.url);o.put("lan",h.lan);o.put("satellite",h.satellite);a.put(o); } } catch(Exception ignored) {} prefs.edit().putString("hosts",a.toString()).apply(); }

    private void chooseImportCSV(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT_HOSTS_CSV);}
    private void chooseExportCSV(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").putExtra(Intent.EXTRA_TITLE,"companion-hosts.csv").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,EXPORT_HOSTS_CSV);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{Uri uri=data.getData();if(request==IMPORT_CONTROL_PNG&&pendingIconSlot>0){File dir=new File(getFilesDir(),"control-icons");if(!dir.exists()&&!dir.mkdirs())throw new IOException("無法建立圖示資料夾");File target=new File(dir,"control-"+pendingIconSlot+".png");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(target)){if(in==null)throw new IOException("無法開啟 PNG");byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}if(android.graphics.BitmapFactory.decodeFile(target.getAbsolutePath())==null)throw new IOException("不是有效的 PNG");ControlStore.saveIcon(this,pendingIconSlot,"custom",target.getAbsolutePath());Toast.makeText(this,"控制 "+pendingIconSlot+" 已套用自訂 PNG",Toast.LENGTH_LONG).show();pendingIconSlot=-1;}else if(request==IMPORT_HOSTS_CSV){String text=readText(uri);int[] report=importHostsCSV(text);Toast.makeText(this,"已匯入 "+report[0]+" 台；略過重複 "+report[1]+" 台；無效 "+report[2]+" 筆",Toast.LENGTH_LONG).show();}else if(request==EXPORT_HOSTS_CSV){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("無法開啟檔案");out.write(exportHostsCSV().getBytes(StandardCharsets.UTF_8));}Toast.makeText(this,"主機 CSV 已匯出",Toast.LENGTH_LONG).show();}}catch(Exception e){Toast.makeText(this,(request==IMPORT_CONTROL_PNG?"PNG 匯入失敗：":"CSV 處理失敗：")+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private String readText(Uri uri)throws IOException{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("無法開啟檔案");byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toString("UTF-8");}}
    private int[] importHostsCSV(String text){List<List<String>> rows=parseCSV(text);int added=0,duplicates=0,invalid=0;for(int i=0;i<rows.size();i++){List<String> row=rows.get(i);if(i==0&&!row.isEmpty()&&"name".equalsIgnoreCase(row.get(0).trim()))continue;if(row.size()<2){invalid++;continue;}String name=row.get(0).trim(),url=normalize(row.get(1)),lan=row.size()>2&& !row.get(2).trim().isEmpty()?normalize(row.get(2)):"";if(name.isEmpty()||url==null||lan==null){invalid++;continue;}boolean duplicate=false;String key=hostKey(url);for(Host h:hosts)if(hostKey(h.url).equals(key)){duplicate=true;break;}if(duplicate){duplicates++;continue;}hosts.add(new Host(name,url,lan));added++;}if(added>0)saveHosts();return new int[]{added,duplicates,invalid};}
    private String exportHostsCSV(){StringBuilder b=new StringBuilder("\"name\",\"internet_url\",\"local_url\"\r\n");for(Host h:hosts)b.append(csv(h.name)).append(',').append(csv(h.url)).append(',').append(csv(h.lan)).append("\r\n");return b.toString();}
    private String csv(String value){return "\""+value.replace("\"","\"\"")+"\"";}
    private String hostKey(String value){String v=value.trim().toLowerCase(Locale.ROOT);while(v.endsWith("/"))v=v.substring(0,v.length()-1);return v;}
    private List<List<String>> parseCSV(String text){List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false;for(int i=0;i<text.length();i++){char ch=text.charAt(i);if(quoted){if(ch=='\"'){if(i+1<text.length()&&text.charAt(i+1)=='\"'){field.append('\"');i++;}else quoted=false;}else field.append(ch);}else if(ch=='\"')quoted=true;else if(ch==','){row.add(field.toString());field.setLength(0);}else if(ch=='\n'){row.add(field.toString().replace("\r",""));field.setLength(0);if(!row.isEmpty())rows.add(row);row=new ArrayList<>();}else field.append(ch);}if(field.length()>0||!row.isEmpty()){row.add(field.toString().replace("\r",""));rows.add(row);}return rows;}

    @Override public void onBackPressed() { if (web.getVisibility()==View.VISIBLE && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onResume(){super.onResume();FloatingControlService.restore(this);FloatingLauncherService.restore(this);}
    @Override protected void onDestroy(){if(networkCallback!=null)getSystemService(android.net.ConnectivityManager.class).unregisterNetworkCallback(networkCallback);networkHandler.removeCallbacksAndMessages(null);super.onDestroy();}
}
