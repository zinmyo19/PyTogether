package com.zynelabs.pytogether;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Focused instrumentation for file import/export helpers and review-only fixes. */
public class HelperFilesInstrumentation extends SettingsInstrumentation {
    void dismissFix() throws Exception {
        runOnMainSync(() -> { try { AlertDialog d=(AlertDialog)field("fixDialog"); if(d!=null&&d.isShowing()) d.dismiss(); } catch(Exception e){throw new RuntimeException(e);} });
        waitForIdleSync();
    }
    AlertDialog waitForFix(final int expectedVersion) throws Exception {
        long until=System.currentTimeMillis()+20000;
        while(System.currentTimeMillis()<until){
            final AlertDialog[] found={null};
            runOnMainSync(() -> { try { AlertDialog d=(AlertDialog)field("fixDialog"); if(d!=null&&d.isShowing()&&((MainActivity)activity).revision==expectedVersion) found[0]=d; } catch(Exception e){throw new RuntimeException(e);} });
            if(found[0]!=null)return found[0];
            Thread.sleep(100);
        }
        throw new AssertionError("fixDialog did not appear for current version "+expectedVersion);
    }
    void clickFix(AlertDialog d,int which) throws Exception {
        runOnMainSync(() -> d.getButton(which).performClick());
        waitForIdleSync();
    }
    String readFile(Uri uri) throws Exception {
        Method m=PracticeFiles.class.getDeclaredMethod("readUtf8Bounded",MainActivity.class,Uri.class);
        m.setAccessible(true);
        try{return (String)m.invoke(null,activity,uri);}
        catch(InvocationTargetException e){Throwable c=e.getCause();if(c instanceof Exception)throw (Exception)c;throw e;}
    }
    void restoreState() throws Exception {
        dismissFix();
        runOnMainSync(() -> { try { AlertDialog d=(AlertDialog)field("offlineHelperDialog"); if(d!=null&&d.isShowing())d.dismiss(); } catch(Exception e){throw new RuntimeException(e);} });
        waitForIdleSync();
        activity.getSharedPreferences("settings",0).edit().clear().commit();
        invoke("updateSettingViews");
        lesson(2); set("price = 1200\ncount = 3\ntotal = price * count\nprint(total)","");
        invoke("showHome");
    }
    @Override public void onStart(){
        Bundle results=new Bundle();
        try{
            activity=startActivitySync(intent()); invoke("showPractice");
            activity.getSharedPreferences("settings",0).edit().clear().commit();
            invoke("updateSettingViews");

            String old="print('old draft')";
            activity.getSharedPreferences("practice",0).edit().putString("code_6",old).putString("input_6","old-input").commit();
            lesson(6); set(old,"old-input");
            String beforeOutput=output();
            runOnMainSync(() -> ((MainActivity)activity).importFileCode("print('imported')"));
            waitForIdleSync();
            check("import disables auto-run",!activity.getSharedPreferences("settings",0).getBoolean("auto_run",true));
            check("import keeps prior code backup",activity.getSharedPreferences("practice",0).getString("before_import_code","").equals(old));
            check("import keeps prior input backup",activity.getSharedPreferences("practice",0).getString("before_import_input","").equals("old-input"));
            check("import loads code",textField("editor").equals("print('imported')"));
            Thread.sleep(1100); check("import does not execute automatically",output().equals(beforeOutput));
            invoke("runNow"); expect("manual run after import","imported");

            File good=File.createTempFile("pytogether-test-",".py",activity.getCacheDir());
            try(FileOutputStream out=new FileOutputStream(good)){out.write("print('utf8')".getBytes(StandardCharsets.UTF_8));}
            check("bounded UTF-8 file read",readFile(Uri.fromFile(good)).equals("print('utf8')"));
            File bad=File.createTempFile("pytogether-bad-",".py",activity.getCacheDir());
            try(FileOutputStream out=new FileOutputStream(bad)){out.write(new byte[]{(byte)0xc3,(byte)0x28});}
            boolean rejected=false;try{readFile(Uri.fromFile(bad));}catch(IOException expected){rejected=true;}
            check("invalid UTF-8 file rejected",rejected);
            File exported=File.createTempFile("pytogether-export-",".py",activity.getCacheDir());
            Field pending=PracticeFiles.class.getDeclaredField("pendingSave");pending.setAccessible(true);
            @SuppressWarnings("unchecked") java.util.Map<MainActivity,String> saves=(java.util.Map<MainActivity,String>)pending.get(null);
            saves.put((MainActivity)activity,"print('saved snapshot')");
            Intent saved=new Intent().setData(Uri.fromFile(exported));
            runOnMainSync(()->PracticeFiles.onResult((MainActivity)activity,0x5053,Activity.RESULT_OK,saved));
            check("file export writes captured UTF-8 snapshot",readFile(Uri.fromFile(exported)).equals("print('saved snapshot')"));
            check("file export does not change editor",textField("editor").equals("print('imported')"));
            exported.delete();
            good.delete(); bad.delete();

            JSONObject plain=OnlineAi.parseAdvice("{\"explanation\":\"မြန်မာ\",\"fixed_code\":\"print(1)\"}");
            JSONObject fenced=OnlineAi.parseAdvice("```json\n{\"explanation\":\"မြန်မာ\",\"fixed_code\":null}\n```");
            check("parse plain advice JSON",plain!=null&&plain.optString("fixed_code").equals("print(1)"));
            check("parse fenced advice JSON",fenced!=null&&fenced.isNull("fixed_code"));
            check("reject prose as advice",OnlineAi.parseAdvice("Here is some advice: use a colon.")==null);

            set("print('before')",""); waitForIdleSync(); final int version=((MainActivity)activity).revision, selected=((MainActivity)activity).selected; final String original=textField("editor");
            dismissFix();
            runOnMainSync(() -> ((MainActivity)activity).previewOnlineFix(original,"print('after')",version,selected));
            AlertDialog fix=waitForFix(version);
            check("async fix shows review dialog",fix.isShowing());
            check("review preview leaves editor unchanged",textField("editor").equals(original));
            clickFix(fix,AlertDialog.BUTTON_POSITIVE); check("accepted fix changes editor",textField("editor").equals("print('after')"));
            invoke("undoFix"); check("undo restores exact source",textField("editor").equals(original));

            final int staleVersion=((MainActivity)activity).revision; final int staleLesson=((MainActivity)activity).selected; final String staleOriginal=textField("editor"); dismissFix();
            runOnMainSync(() -> { ((MainActivity)activity).previewOnlineFix(staleOriginal,"print('stale')",staleVersion,staleLesson); ((MainActivity)activity).editor.setText("print('newer')"); });
            Thread.sleep(1200); waitForIdleSync();
            check("stale fix cannot apply",textField("editor").equals("print('newer')")); dismissFix();

            final int invalidVersion=((MainActivity)activity).revision; final int invalidLesson=((MainActivity)activity).selected; final String invalidOriginal=textField("editor");
            runOnMainSync(() -> ((MainActivity)activity).previewOnlineFix(invalidOriginal,"print(",invalidVersion,invalidLesson));
            Thread.sleep(1500); waitForIdleSync();
            check("invalid async fix does not apply",textField("editor").equals(invalidOriginal)); dismissFix();

            set("if True\n    print(1)",""); invoke("runNow"); expect("offline helper syntax error","SyntaxError");
            String helperBefore=textField("editor"); invoke("askHelper"); waitForIdleSync();
            final boolean[] helperShown={false}; final String[] helperMessage={""};
            runOnMainSync(() -> { try { AlertDialog d=(AlertDialog)field("offlineHelperDialog"); helperShown[0]=d!=null&&d.isShowing(); if(helperShown[0])helperMessage[0]=((TextView)d.findViewById(android.R.id.message)).getText().toString(); } catch(Exception e){throw new RuntimeException(e);} });
            check("offline helper dialog shows",helperShown[0]); check("offline helper explains in Burmese",helperMessage[0].matches("(?s).*[\\u1000-\\u109f].*")); check("offline helper leaves editor unchanged",textField("editor").equals(helperBefore));

            restoreState();
            results.putString("stream","\n"+log+"\n"+passed+" checks passed\n"); finish(Activity.RESULT_OK,results);
        }catch(Throwable ex){
            try{restoreState();}catch(Throwable ignored){}
            results.putString("stream","\n"+log+"FAIL "+ex+"\n"+passed+" checks passed\n"); finish(Activity.RESULT_CANCELED,results);
        }
    }
}
