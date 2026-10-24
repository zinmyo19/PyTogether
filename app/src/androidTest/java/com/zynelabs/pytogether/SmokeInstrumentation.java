package com.zynelabs.pytogether;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.widget.*;
import java.lang.reflect.*;

/** Dependency-free on-device smoke test. Run with adb shell am instrument -w. */
public class SmokeInstrumentation extends Instrumentation {
    Activity activity;
    int passed=0;
    StringBuilder log=new StringBuilder();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    Object field(String key)throws Exception{Field f=activity.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(activity);}
    String output()throws Exception{final String[] s={""};runOnMainSync(()->{try{s[0]=((TextView)field("output")).getText().toString();}catch(Exception e){throw new RuntimeException(e);}});return s[0];}
    void set(String code,String stdin)throws Exception{runOnMainSync(()->{try{((EditText)field("input")).setText(stdin);((EditText)field("editor")).setText(code);}catch(Exception e){throw new RuntimeException(e);}});}
    void expect(String name,String wanted)throws Exception{
        long until=System.currentTimeMillis()+90000;
        while(System.currentTimeMillis()<until){if(output().contains(wanted)){passed++;log.append("PASS ").append(name).append('\n');return;}Thread.sleep(300);}
        throw new AssertionError(name+" expected "+wanted+" actual "+output());
    }
    void lesson(int index)throws Exception{runOnMainSync(()->{try{Method m=activity.getClass().getDeclaredMethod("loadLesson",int.class);m.setAccessible(true);m.invoke(activity,index);}catch(Exception e){throw new RuntimeException(e);}});}
    void invoke(String name)throws Exception{runOnMainSync(()->{try{Method m=activity.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});}
    void check(String name,boolean ok){if(!ok)throw new AssertionError(name);passed++;log.append("PASS ").append(name).append('\n');}
    String textField(String name)throws Exception{final String[] value={""};runOnMainSync(()->{try{value[0]=((TextView)field(name)).getText().toString();}catch(Exception e){throw new RuntimeException(e);}});return value[0];}
    void helperReady()throws Exception{
        long until=System.currentTimeMillis()+20000;final boolean[] ready={false};
        while(System.currentTimeMillis()<until){runOnMainSync(()->{try{ready[0]=field("helperFix")!=null;}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;Thread.sleep(200);}
        throw new AssertionError("No helper fix: "+textField("helperBody"));
    }
    void dialogButton(int which)throws Exception{runOnMainSync(()->{try{((AlertDialog)field("fixDialog")).getButton(which).performClick();}catch(Exception e){throw new RuntimeException(e);}});}
    @Override public void onStart(){
        Bundle results=new Bundle();
        try{
            Intent intent=new Intent();intent.setClassName("com.zynelabs.pytogether","com.zynelabs.pytogether.MainActivity");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent);invoke("showPractice");
            runOnMainSync(()->activity.getSharedPreferences("settings",0).edit().clear().commit());
            invoke("updateSettingViews");lesson(0);set("print('မင်္ဂလာပါ Python')","");
            expect("initial offline Python startup","မင်္ဂလာပါ Python");
            if(activity.getResources().getConfiguration().orientation!=Configuration.ORIENTATION_PORTRAIT)throw new AssertionError("Not portrait");passed++;log.append("PASS portrait\n");
            set("price = 1200\ncount = 3\ntotal = price * count\nprint(total)","");expect("auto-run arithmetic without Run button","3600");
            set("print(7 * 8)","");expect("edited output replaces previous output","56");
            set("print(unknown_name)","");expect("beginner NameError feedback","NameError");
            set("print(\"oops)","");expect("unfinished typing syntax feedback","SyntaxError");
            set("name = input()\nprint(\"Hello, \" + name)","Zyne");expect("stdin auto-run","Hello, Zyne");
            set("while True:\n    pass","");expect("infinite Python loop guard","PracticeLimit");
            set("print(42)","");expect("engine recovers after limit","42");
            lesson(2);expect("lesson switch","3600");
            Object s=field("steps");if(((org.json.JSONArray)s).length()<4)throw new AssertionError("Missing trace");passed++;log.append("PASS actual data-flow snapshots\n");
            set("price = 2500\ncount = 4\ntotal = price * count\nprint(total)","");expect("custom exercise","10000");
            lesson(0);expect("return to saved first lesson","42");
            lesson(2);expect("per-lesson draft persisted","10000");
            String broken="if True\n    print(123)";
            set(broken,"");expect("helper receives syntax error","SyntaxError");helperReady();
            check("Burmese one-concept explanation",textField("helperBody").matches("(?s).*[\\u1000-\\u109f].*"));
            check("diagnostics never silently overwrite",textField("editor").equals(broken));
            invoke("previewFix");check("preview does not change source",textField("editor").equals(broken));dialogButton(AlertDialog.BUTTON_NEGATIVE);
            check("cancel keeps original source",textField("editor").equals(broken));
            invoke("previewFix");dialogButton(AlertDialog.BUTTON_POSITIVE);expect("explicit apply auto-runs fixed source","123");
            check("only missing colon added",textField("editor").equals("if True:\n    print(123)"));
            invoke("undoFix");expect("undo restores error","SyntaxError");check("undo restores exact original",textField("editor").equals(broken));helperReady();
            invoke("previewFix");set("print(456)","");dialogButton(AlertDialog.BUTTON_POSITIVE);expect("new code runs despite stale preview","456");
            check("stale preview cannot overwrite new edits",textField("editor").equals("print(456)"));
            set("print(true)","");expect("lowercase boolean diagnostic","NameError");helperReady();invoke("previewFix");dialogButton(AlertDialog.BUTTON_POSITIVE);expect("boolean fix works on bundled Python","True");
            set("print(789)","");expect("manual edits after helper fix","789");invoke("undoFix");check("undo cannot overwrite later typing",textField("editor").equals("print(789)"));
            set(broken,"");expect("lesson stale test error","SyntaxError");helperReady();invoke("previewFix");lesson(0);dialogButton(AlertDialog.BUTTON_POSITIVE);expect("lesson remains intact after stale apply","42");
            check("stale preview cannot replace another lesson",textField("editor").equals("print(42)"));
            lesson(2);
            // Leave a clean, useful arithmetic example for the screenshot.
            set("price = 1200\ncount = 3\ntotal = price * count\nprint(total)","");expect("final screenshot state","3600");
            results.putString("stream","\n"+log+"\n"+passed+" checks passed\n");finish(Activity.RESULT_OK,results);
        }catch(Throwable ex){results.putString("stream","\n"+log+"FAIL "+ex+"\n");finish(Activity.RESULT_CANCELED,results);}
    }
}
