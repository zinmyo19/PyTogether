package com.zynelabs.pytogether;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** AI Code Generator: offline recipes/check, fake-provider online flow, review gates, provider keys, real endpoint errors. */
public class CodeGeneratorInstrumentation extends SettingsInstrumentation {
    MainActivity main(){return (MainActivity)activity;}
    CodeGenerator gen(){return main().generator;}
    void ui(Runnable r)throws Exception{final Throwable[] t={null};runOnMainSync(()->{try{r.run();}catch(Throwable e){t[0]=e;}});if(t[0]!=null)throw new RuntimeException(t[0]);}
    <T> T uiGet(Callable<T> c)throws Exception{final Object[] v={null};final Throwable[] t={null};runOnMainSync(()->{try{v[0]=c.call();}catch(Throwable e){t[0]=e;}});if(t[0]!=null)throw new RuntimeException(t[0]);@SuppressWarnings("unchecked")T r=(T)v[0];return r;}
    void waitFor(String name,Callable<Boolean> cond)throws Exception{long until=System.currentTimeMillis()+90000;while(System.currentTimeMillis()<until){if(cond.call()){passed++;log.append("PASS ").append(name).append('\n');return;}Thread.sleep(250);}throw new AssertionError("timeout: "+name+" | status="+uiGet(()->gen().status.getText().toString()));}
    static void collect(View v,StringBuilder b){if(v instanceof TextView)b.append(((TextView)v).getText()).append('\n');if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),b);}}
    String resultText()throws Exception{return uiGet(()->{StringBuilder b=new StringBuilder();collect(gen().resultBox,b);return b.toString();});}
    String statusText()throws Exception{return uiGet(()->gen().status.getText().toString());}
    void setText(EditText e,String s)throws Exception{ui(()->e.setText(s));}
    void click(String description)throws Exception{ui(()->{View v=find(gen().scroll,description);if(v==null)throw new AssertionError("Missing "+description);v.performClick();});waitForIdleSync();}
    void dialog(AlertDialog d,int button)throws Exception{ui(()->d.getButton(button).performClick());waitForIdleSync();}
    int shownChild()throws Exception{return uiGet(()->main().screens.getDisplayedChild());}
    void setOnline(boolean on)throws Exception{getTargetContext().getSharedPreferences("settings",0).edit().putBoolean("online",on).commit();ui(()->main().updateSettingViews());}
    void pickProvider(String id)throws Exception{ui(()->{Spinner s=(Spinner)find(OnlineAi.configurationDialog.getWindow().getDecorView(),"AI provider");for(int i=0;i<OnlineAi.PROVIDERS.length;i++)if(OnlineAi.PROVIDERS[i][0].equals(id))s.setSelection(i);});waitForIdleSync();Thread.sleep(200);}
    String read(String endpoint)throws Exception{Method m=OnlineAi.class.getDeclaredMethod("readSecret",MainActivity.class,String.class);m.setAccessible(true);return (String)m.invoke(null,activity,endpoint);}
    void saveProvider(String id,String key)throws Exception{ui(()->OnlineAi.configure(main()));waitForIdleSync();pickProvider(id);ui(()->{if(!key.isEmpty())aiField("AI API key").setText(key);OnlineAi.configurationDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});waitForIdleSync();}
    EditText aiField(String description){return (EditText)find(OnlineAi.configurationDialog.getWindow().getDecorView(),description);}

    @Override public void onStart(){
        Bundle results=new Bundle();
        try{
            Context c=getTargetContext();
            c.getSharedPreferences("settings",0).edit().clear().commit();c.getSharedPreferences("online_ai",0).edit().clear().commit();
            c.getSharedPreferences("practice",0).edit().clear().commit();
            OnlineAi.testTransport=null;
            activity=startActivitySync(intent());
            c.getSharedPreferences("practice",0).edit().putString("code_"+main().lessons.length(),"print('my old draft')").commit();
            check("launch opens Home",(Boolean)field("onHome"));
            check("Home has AI Code Generator choice",find(activity.getWindow().getDecorView(),"Home Code Generator")!=null);
            click0("Home Code Generator");
            check("Generator screen opens (third screen) without autorun",shownChild()==2&&(Boolean)field("onHome"));
            check("Generator shows Offline when Online is off",uiGet(()->gen().modeBadge.getText().toString()).contains("OFFLINE"));
            waitFor("Ready examples listed offline",()->gen().recipeBox.getChildCount()>=10);

            // ---- Offline: recipe match, review gate, accept ----
            setText(gen().ask,"နံပါတ်နှစ်ခု ပေါင်းတဲ့ program");click("Generator go");
            waitFor("Offline generator returns recipe with steps",()->{try{return resultText().contains("a + b")&&resultText().contains("1.");}catch(Exception e){return false;}});
            check("Offline result does not touch editor",!textField("editor").contains("a + b"));
            click("Review and use code");
            check("Review dialog appears and shows old draft + new code",uiGet(()->gen().useDialog!=null&&gen().useDialog.isShowing()));
            dialog(gen().useDialog,AlertDialog.BUTTON_NEGATIVE);
            check("Declining review leaves editor untouched",!textField("editor").contains("a + b"));
            click("Review and use code");dialog(gen().useDialog,AlertDialog.BUTTON_POSITIVE);
            check("Accepting inserts code in Code Editor, auto-run off, editor screen",textField("editor").contains("a + b")&&shownChild()==1&&!settings().getBoolean("auto_run",true));
            check("Previous draft kept for restore",c.getSharedPreferences("practice",0).getString("before_import_code","").equals("print('my old draft')"));
            ui(()->main().input.setText("2\n3"));ui(()->main().runNow());
            expect("Accepted recipe runs on device","ပေါင်းလဒ် = 5");
            ui(()->main().showGenerator());

            // ---- Offline: no match does not invent code ----
            setText(gen().ask,"weather forecast app");click("Generator go");
            waitFor("No-match offline request gives honest message",()->{try{return statusText().contains("မတွေ့ပါ");}catch(Exception e){return false;}});
            check("No-match shows no code box",!resultText().contains("Generated code"));

            // ---- Offline check mode ----
            ui(()->gen().setMode(true));
            setText(gen().codeIn,"print(\"hi\"");setText(gen().ask,"");click("Generator go");
            waitFor("Offline check lists the mistake with line",()->{try{String t=resultText();return t.contains("တွေ့တဲ့ အမှားများ")&&t.contains("လိုင်း 1");}catch(Exception e){return false;}});
            ui(()->gen().setMode(false));

            // ---- Providers: presets, endpoint prefill and per-provider keys ----
            ui(()->OnlineAi.configure(main()));waitForIdleSync();pickProvider("opencode");
            check("OpenCode preset fills endpoint",aiField("AI HTTPS endpoint").getText().toString().equals("https://opencode.ai/inference/openai/v1/chat/completions"));
            check("OpenCode preset fills model",aiField("AI model").getText().toString().equals("deepseek-v4.1-flash"));
            ui(()->OnlineAi.configurationDialog.dismiss());
            saveProvider("opencode","opencode-test-key-1");
            saveProvider("openai","openai-test-key-2");
            check("OpenAI active with its own key",OnlineAi.activeProvider(main()).equals("openai")&&read("https://api.openai.com/v1/chat/completions").equals("openai-test-key-2"));
            SharedPreferences ai=c.getSharedPreferences("online_ai",0);
            check("Keys not plaintext in prefs",!ai.getAll().toString().contains("test-key"));
            saveProvider("opencode","");
            check("Switching back keeps OpenCode key",OnlineAi.activeProvider(main()).equals("opencode")&&read("https://opencode.ai/inference/openai/v1/chat/completions").equals("opencode-test-key-1"));

            // ---- Online gate: Offline never calls provider ----
            final AtomicInteger calls=new AtomicInteger();final String[] seen=new String[3];
            OnlineAi.testTransport=(endpoint,model,key,system,user)->{calls.incrementAndGet();seen[0]=key;seen[1]=system;seen[2]=user;
                return "```json\n{\"summary\":\"နံပါတ်နှစ်ခုကို ပေါင်းပါတယ်။\",\"steps\":[\"input နဲ့ ယူတယ်\",\"print နဲ့ ပြတယ်\"],\"libraries\":[\"numpy\"],\"code\":\"a = int(input())\\nb = int(input())\\nprint(a + b)\\n\",\"notes\":\"စမ်းကြည့်ပါ\"}\n```";};
            setOnline(false);setText(gen().ask,"နံပါတ်နှစ်ခု ပေါင်း");click("Generator go");Thread.sleep(1500);
            check("Offline mode never calls the AI provider",calls.get()==0);

            // ---- Online generate with fake provider ----
            setOnline(true);
            check("Online note names provider",uiGet(()->gen().modeNote.getText().toString()).contains("OpenCode Zen"));
            setText(gen().ask,"နံပါတ်နှစ်ခု ပေါင်းတဲ့ program ရေးပေးပါ");click("Generator go");
            waitFor("Online result shows AI code, steps, library note",()->{try{String t=resultText();return t.contains("print(a + b)")&&t.contains("input နဲ့ ယူတယ်")&&t.contains("numpy");}catch(Exception e){return false;}});
            check("Provider got saved key, Burmese-teacher prompt, user request",calls.get()==1&&"opencode-test-key-1".equals(seen[0])&&seen[1].contains("Burmese")&&seen[2].contains("နံပါတ်နှစ်ခု"));
            check("Missing library numpy is flagged",resultText().contains("numpy"));
            check("Online result did not change the editor",!textField("editor").contains("print(a + b)\n\n"));
            ui(()->main().showGenerator());

            // ---- Online check: confirm gate then result ----
            OnlineAi.testTransport=(endpoint,model,key,system,user)->{calls.incrementAndGet();seen[2]=user;
                return "{\"summary\":\"အမှား ၁ ခု တွေ့တယ်\",\"mistakes\":[{\"line\":2,\"problem\":\"colon မပါဘူး\",\"why\":\"for ရဲ့ နောက်မှာ : လိုတယ်\"}],\"steps\":[],\"libraries\":[],\"code\":\"for i in range(3):\\n    print(i)\\n\",\"notes\":\"\"}";};
            calls.set(0);ui(()->gen().setMode(true));setText(gen().codeIn,"for i in range(3)\n    print(i)\n");setText(gen().ask,"");click("Generator go");
            check("Check mode asks before sending code",uiGet(()->gen().sendDialog!=null&&gen().sendDialog.isShowing())&&calls.get()==0);
            dialog(gen().sendDialog,AlertDialog.BUTTON_POSITIVE);
            waitFor("Online check lists mistake and fixed code",()->{try{String t=resultText();return t.contains("လိုင်း 2")&&t.contains("colon မပါဘူး")&&t.contains("for i in range(3):");}catch(Exception e){return false;}});
            check("Fix was NOT applied automatically",!textField("editor").contains("for i in range(3):"));
            click("Check this code again");
            check("Check again moves fixed code into the checker",uiGet(()->gen().codeIn.getText().toString()).contains("range(3):")&&uiGet(()->gen().checkMode));
            ui(()->gen().setMode(false));

            // ---- Errors are readable and don't leave stuck state ----
            OnlineAi.testTransport=(endpoint,model,key,system,user)->{throw new OnlineAi.ProviderException("Provider returned HTTP 401 · API key မှန်လား စစ်ပါ။");};
            setText(gen().ask,"hello");click("Generator go");
            waitFor("Provider error shown in Burmese status",()->{try{return statusText().contains("HTTP 401");}catch(Exception e){return false;}});
            check("After error the screen is usable again",!uiGet(()->gen().busy));
            OnlineAi.testTransport=(endpoint,model,key,system,user)->"ဒီမှာ code ပါ\n```python\nprint('hi')\n```";
            setText(gen().ask,"hello");click("Generator go");
            waitFor("Loose (non-JSON) answer still shows code",()->{try{return resultText().contains("print('hi')");}catch(Exception e){return false;}});

            // ---- Parser edge cases ----
            check("parseResult: null code stays null",!CodeGenerator.parseResult("{\"summary\":\"x\",\"code\":null,\"steps\":[],\"mistakes\":[]}").has("code"));
            check("parseResult: fixed_code alias accepted",CodeGenerator.parseResult("{\"explanation\":\"x\",\"fixed_code\":\"print(1)\"}").optString("code").equals("print(1)"));
            check("parseResult: fenced code string is unwrapped",CodeGenerator.parseResult("{\"summary\":\"x\",\"code\":\"```python\\nprint(2)\\n```\"}").optString("code").trim().equals("print(2)"));
            check("parseResult: garbage does not crash",CodeGenerator.parseResult("").optString("summary").length()>=0);

            // ---- Back button and Home ----
            OnlineAi.testTransport=null;
            ui(()->main().onBackPressed());
            check("Back from Generator returns Home",shownChild()==0);

            // ---- Real HTTPS error handling against live providers (no key, expect auth error) ----
            StringBuilder net=new StringBuilder();
            String[][] live={{"opencode","https://opencode.ai/inference/openai/v1/chat/completions","deepseek-v4.1-flash"},{"openai","https://api.openai.com/v1/chat/completions","gpt-4o-mini"},{"openrouter","https://openrouter.ai/api/v1/chat/completions","openai/gpt-4o-mini"},{"deepseek","https://api.deepseek.com/chat/completions","deepseek-chat"},{"groq","https://api.groq.com/openai/v1/chat/completions","llama-3.3-70b-versatile"}};
            Method post=OnlineAi.class.getDeclaredMethod("post",String.class,String.class,String.class,String.class,String.class);post.setAccessible(true);
            for(String[] p:live){
                String outcome;
                try{post.invoke(null,p[1],p[2],"",  "s","u");outcome="UNEXPECTED SUCCESS";}
                catch(InvocationTargetException e){Throwable cause=e.getCause();outcome=cause instanceof OnlineAi.ProviderException?cause.getMessage().replace('\n',' '):"NETWORK "+cause;}
                net.append("LIVE ").append(p[0]).append(": ").append(outcome).append('\n');
            }
            log.append(net);
            results.putString("stream","\n"+log+"\n"+passed+" checks passed\nNo real AI answer tested: owner provider/key not supplied.\n");
            finish(Activity.RESULT_OK,results);
        }catch(Throwable ex){results.putString("stream","\n"+log+"FAIL "+ex+"\n");finish(Activity.RESULT_CANCELED,results);}
    }
    void click0(String description)throws Exception{ui(()->{View v=find(activity.getWindow().getDecorView(),description);if(v==null)throw new AssertionError("Missing "+description);v.performClick();});waitForIdleSync();}
}
