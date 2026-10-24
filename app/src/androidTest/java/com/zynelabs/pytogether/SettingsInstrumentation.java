package com.zynelabs.pytogether;

import android.app.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Real UI controls, restart persistence and settings-only reset coverage. */
public class SettingsInstrumentation extends SmokeInstrumentation {
    View find(View view,String description){
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View child=find(group.getChildAt(i),description);if(child!=null)return child;}}
        return null;
    }
    View control(String description)throws Exception{return find(((AlertDialog)field("settingsDialog")).getWindow().getDecorView(),description);}
    void toggle(String description,boolean enabled)throws Exception{runOnMainSync(()->{try{((Switch)control(description)).setChecked(enabled);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();}
    void choose(String description,int position)throws Exception{runOnMainSync(()->{try{((Spinner)control(description)).setSelection(position);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();Thread.sleep(150);}
    SharedPreferences settings(){return activity.getSharedPreferences("settings",0);}
    boolean visible(String name)throws Exception{final boolean[] yes={false};runOnMainSync(()->{try{yes[0]=((View)field(name)).getVisibility()==View.VISIBLE;}catch(Exception e){throw new RuntimeException(e);}});return yes[0];}
    void dismissSettings()throws Exception{runOnMainSync(()->{try{((AlertDialog)field("settingsDialog")).dismiss();}catch(Exception e){throw new RuntimeException(e);}});}
    void resetButton(int button)throws Exception{runOnMainSync(()->{try{((AlertDialog)field("resetSettingsDialog")).getButton(button).performClick();}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();}
    Intent intent(){Intent i=new Intent();i.setClassName("com.zynelabs.pytogether","com.zynelabs.pytogether.MainActivity");i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);return i;}
    @Override public void onStart(){
        Bundle results=new Bundle();
        try{
            getTargetContext().getSharedPreferences("settings",0).edit().clear().commit();
            getTargetContext().getSharedPreferences("practice",0).edit().putInt("selected",2).putString("code_2","price = 1200\ncount = 3\ntotal = price * count\nprint(total)").putString("input_2","").commit();
            activity=startActivitySync(intent());invoke("showPractice");
            check("new version retains selected lesson after update",((Integer)field("selected"))==2);
            check("old draft still loaded",textField("editor").contains("price = 1200"));
            check("settings default auto-run on",settings().getBoolean("auto_run",true));
            expect("upgraded existing draft runs","3600");
            invoke("showSettings");
            check("settings opens from native screen",((AlertDialog)field("settingsDialog")).isShowing());
            String code=textField("editor");
            runOnMainSync(()->{try{((EditText)field("editor")).setSelection(5);}catch(Exception e){throw new RuntimeException(e);}});
            choose("Code and output font size",4);
            check("font setting persisted",settings().getInt("font",0)==20);
            final float[] sizes={0,0};final int[] cursor={0};
            runOnMainSync(()->{try{sizes[0]=((TextView)field("editor")).getTextSize();sizes[1]=((TextView)field("output")).getTextSize();cursor[0]=((EditText)field("editor")).getSelectionStart();}catch(Exception e){throw new RuntimeException(e);}});
            check("font applied to editor and output",Math.abs(sizes[0]-20*activity.getResources().getDisplayMetrics().scaledDensity)<1&&sizes[0]==sizes[1]);
            check("font keeps code and cursor",code.equals(textField("editor"))&&cursor[0]==5);
            for(int i=0;i<5;i++){choose("Code and output font size",i);check("font option "+i,settings().getInt("font",14)==12+i*2);}
            toggle("Offline Burmese helper",false);toggle("Data flow",false);
            check("helper panel hidden",!visible("helperCard"));check("trace content and controls hidden",!visible("traceBox")&&!visible("traceHead"));
            choose("Auto-run delay",3);toggle("Auto-run",false);
            check("manual Run action visible",visible("runButton"));
            dismissSettings();
            String previous=output();set("name = input()\nprint('manual:' + name)","kept-input");Thread.sleep(2000);
            check("auto-run off does not execute edits",output().equals(previous));
            check("manual edit status honest",textField("status").contains("Not run"));
            invoke("runNow");expect("manual Run uses latest code and stdin","manual:kept-input");
            invoke("showSettings");toggle("Auto-run",true);dismissSettings();expect("reenabled auto-run executes","manual:kept-input");
            set("print('delayed-new')","");Thread.sleep(500);check("1500ms delay does not run at 500ms",!output().contains("delayed-new"));expect("selected delay eventually runs","delayed-new");
            invoke("showSettings");toggle("Auto-run",false);dismissSettings();
            set("name = input()\nprint('persist:' + name)","persisted-input");
            int selected=(Integer)field("selected");
            runOnMainSync(()->activity.finish());waitForIdleSync();Thread.sleep(200);
            activity=startActivitySync(intent());invoke("showPractice");
            check("restart retains selected lesson",((Integer)field("selected"))==selected);
            check("restart retains code and stdin",textField("editor").contains("persist:")&&textField("input").equals("persisted-input"));
            check("restart retains all settings",!settings().getBoolean("auto_run",true)&&!settings().getBoolean("helper",true)&&!settings().getBoolean("flow",true)&&settings().getInt("delay",0)==1500&&settings().getInt("font",0)==20);
            check("restart reapplies visibility",visible("runButton")&&!visible("helperCard")&&!visible("traceBox"));
            invoke("runNow");expect("manual works after restart","persist:persisted-input");
            Map<String,?> drafts=new HashMap<>(activity.getSharedPreferences("practice",0).getAll());
            invoke("showSettings");invoke("confirmResetSettings");resetButton(AlertDialog.BUTTON_NEGATIVE);
            check("reset Cancel leaves settings",!settings().getBoolean("auto_run",true)&&settings().getInt("font",0)==20);
            invoke("confirmResetSettings");resetButton(AlertDialog.BUTTON_POSITIVE);
            check("reset restores settings defaults",settings().getAll().isEmpty());
            check("reset retains every draft and selected lesson",drafts.equals(activity.getSharedPreferences("practice",0).getAll()));
            check("reset restores panels and auto mode",!visible("runButton")&&!visible("helperCard")&&visible("traceHead")&&visible("traceBox"));
            dismissSettings();expect("reset defaults auto-run without deleting input","persist:persisted-input");
            invoke("showSettings");choose("Auto-run delay",0);toggle("Auto-run",false);dismissSettings();
            set("print('cancel-pending')","");invoke("showSettings");toggle("Auto-run",true);toggle("Auto-run",false);dismissSettings();Thread.sleep(1000);
            check("disabling auto-run cancels queued debounce",!output().contains("cancel-pending"));
            invoke("showSettings");invoke("confirmResetSettings");resetButton(AlertDialog.BUTTON_POSITIVE);dismissSettings();
            lesson(2);set("price = 1200\ncount = 3\ntotal = price * count\nprint(total)","");expect("settings final clean example","3600");
            results.putString("stream","\n"+log+"\n"+passed+" checks passed\n");finish(Activity.RESULT_OK,results);
        }catch(Throwable ex){results.putString("stream","\n"+log+"FAIL "+ex+"\n");finish(Activity.RESULT_CANCELED,results);}
    }
}
