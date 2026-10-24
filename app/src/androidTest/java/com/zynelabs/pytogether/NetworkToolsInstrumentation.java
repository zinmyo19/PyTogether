package com.zynelabs.pytogether;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.*;
import com.chaquo.python.*;
import com.chaquo.python.android.AndroidPlatform;
import java.lang.reflect.*;
import java.util.concurrent.*;
import org.json.*;

public class NetworkToolsInstrumentation extends SettingsInstrumentation {
    void clickHome(String description)throws Exception{runOnMainSync(()->{View v=find(activity.getWindow().getDecorView(),description);if(v==null)throw new AssertionError("Missing "+description);v.performClick();});waitForIdleSync();}
    void mode(String description)throws Exception{runOnMainSync(()->{try{((RadioButton)control(description)).setChecked(true);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();}
    EditText aiField(String description){return (EditText)find(OnlineAi.configurationDialog.getWindow().getDecorView(),description);}
    static boolean hasPref(SharedPreferences p,String base){for(String n:p.getAll().keySet())if(n.equals(base)||(n.startsWith(base+"_")&&!n.equals("key_origin")&&!n.startsWith("key_origin_")))return true;return false;}
    void saveAi(String endpoint,String model,String key){runOnMainSync(()->{aiField("AI HTTPS endpoint").setText(endpoint);aiField("AI model").setText(model);aiField("AI API key").setText(key);OnlineAi.configurationDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});waitForIdleSync();}
    @Override public void onStart(){Bundle results=new Bundle();try{
        activity=startActivitySync(intent());
        check("launch opens Home",(Boolean)field("onHome"));
        check("Home has Code Editor and Learning choices",find(activity.getWindow().getDecorView(),"Home Code Editor")!=null&&find(activity.getWindow().getDecorView(),"Home Learning")!=null);
        check("Error Helper stays out of Home choices",find(activity.getWindow().getDecorView(),"Home Error helper")==null);
        clickHome("Home Code Editor");check("Code Editor opens free practice",!(Boolean)field("onHome")&&(Integer)field("selected")==6);
        set("print('home-draft')","home-input");expect("editor works after Home","home-draft");invoke("showHome");clickHome("Resume last draft");check("Home round trip retains draft",textField("editor").equals("print('home-draft')")&&textField("input").equals("home-input"));
        invoke("showSettings");mode("Offline mode");check("Offline gate blocks AI",!OnlineAi.isEnabled((MainActivity)activity));mode("Online mode");check("Online mode explicitly enabled",OnlineAi.isEnabled((MainActivity)activity));dismissSettings();
        check("HTTPS endpoint validation rejects unsafe inputs",!OnlineAi.validEndpoint("http://example.com")&&!OnlineAi.validEndpoint("https://u:p@example.com/v1")&&!OnlineAi.validEndpoint("https://example.com/v1?api_key=x")&&!OnlineAi.validEndpoint("https://example.com/v1#x")&&OnlineAi.validEndpoint("https://example.com/v1/chat/completions"));
        runOnMainSync(()->OnlineAi.configure((MainActivity)activity));waitForIdleSync();
        saveAi("https://example.com/v1/chat/completions","test-model","test-only-not-a-real-api-key");
        check("AI configuration saved",OnlineAi.configured((MainActivity)activity));
        SharedPreferences p=activity.getSharedPreferences("online_ai",0);
        check("API key is not plaintext in app preferences",!p.getAll().toString().contains("test-only-not-a-real-api-key")&&hasPref(p,"key")&&hasPref(p,"iv"));
        Method read=OnlineAi.class.getDeclaredMethod("readSecret",MainActivity.class,String.class);read.setAccessible(true);
        check("Keystore can decrypt saved key",read.invoke(null,activity,"https://example.com/v1/chat/completions").equals("test-only-not-a-real-api-key"));
        runOnMainSync(()->OnlineAi.check((MainActivity)activity));waitForIdleSync();check("AI requires explicit code-send confirmation",OnlineAi.reviewDialog!=null&&OnlineAi.reviewDialog.isShowing());
        check("AI review does not modify code",textField("editor").equals("print('home-draft')"));
        runOnMainSync(()->OnlineAi.reviewDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        runOnMainSync(()->OnlineAi.configure((MainActivity)activity));waitForIdleSync();saveAi("https://other.example.com/v1/chat/completions","test-model","");check("provider change clears saved key",!p.contains("key"));
        p.edit().clear().commit();
        MainActivity a=(MainActivity)activity;final String[] packageResult={""};CountDownLatch done=new CountDownLatch(1);
        a.worker.execute(()->{try{if(!Python.isStarted())Python.start(new AndroidPlatform(a));Python py=Python.getInstance();String path=new java.io.File(a.getFilesDir(),"site-packages").getAbsolutePath();PyObject pm=py.getModule("package_manager");
            String invalid=pm.callAttr("install_json","--index-url https://evil.example",path).toString();if(new JSONObject(invalid).optBoolean("ok"))throw new AssertionError("invalid name accepted");
            String installed=pm.callAttr("install_json","colorama",path).toString();if(!new JSONObject(installed).optBoolean("ok"))throw new AssertionError(installed);
            String result=py.getModule("practice_engine").callAttr("run","import colorama\nprint(colorama.__version__)","").toString();if(new JSONObject(result).optJSONObject("error")!=null)throw new AssertionError(result);
            packageResult[0]="PASS Android runtime pip installs colorama pure-Python wheel\nPASS installed colorama imports in practice engine\nPASS pip rejects URL/options package names\n";
        }catch(Throwable e){packageResult[0]="FAIL package test "+e;}finally{done.countDown();}});
        if(!done.await(240,TimeUnit.SECONDS))throw new AssertionError("pip test timeout");if(packageResult[0].startsWith("FAIL"))throw new AssertionError(packageResult[0]);log.append(packageResult[0]);passed+=3;
        invoke("showSettings");mode("Offline mode");dismissSettings();set("import colorama\nprint('offline-package:' + colorama.__version__)","");expect("installed package works in Offline mode","offline-package:");
        invoke("showHome");check("final Home remains available",(Boolean)field("onHome"));
        results.putString("stream","\n"+log+"\n"+passed+" checks passed\nNo live AI provider request sent: owner provider/key not supplied.\n");finish(Activity.RESULT_OK,results);
    }catch(Throwable ex){results.putString("stream","\n"+log+"FAIL "+ex+"\n");finish(Activity.RESULT_CANCELED,results);}}
}
