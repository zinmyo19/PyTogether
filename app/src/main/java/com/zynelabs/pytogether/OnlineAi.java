package com.zynelabs.pytogether;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.concurrent.*;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Optional, explicitly user-triggered AI help. Works with any OpenAI-compatible
 * "chat completions" HTTPS endpoint. The owner chooses a provider (OpenCode Zen,
 * OpenAI, OpenRouter, Gemini, DeepSeek, Groq or a custom URL) and supplies their own key.
 */
public final class OnlineAi {
    private OnlineAi() {}
    private static final String PREFS="online_ai", ENDPOINT="endpoint", MODEL="model", KEY="key", IV="iv", KEY_ORIGIN="key_origin", PROVIDER="provider";
    private static final String KS="AndroidKeyStore", ALIAS="PyTogetherOnlineAiKey";
    static final int MAX_CODE=20*1024, MAX_RESPONSE=256*1024;
    private static final String RESPONSE_PROMPT="Return ONLY a JSON object with exactly these fields: {\"explanation\":\"Burmese explanation\",\"fixed_code\":\"full replacement code string\" or null}. The explanation must be in Burmese. fixed_code must be null when no safe complete fix is available.";
    private static final ExecutorService NET=Executors.newSingleThreadExecutor();
    public static AlertDialog configurationDialog, reviewDialog;

    /** id, label, endpoint, suggested model, short Burmese note. Model names are suggestions only. */
    static final String[][] PROVIDERS={
        {"custom","Custom · ကိုယ်ပိုင် URL","","","OpenAI-compatible chat/completions HTTPS URL ထည့်ပါ။"},
        {"opencode","OpenCode Zen","https://opencode.ai/inference/openai/v1/chat/completions","deepseek-v4.1-flash","OpenCode console (opencode.ai) က API key လိုတယ်။ Model စာရင်း: opencode.ai/docs/zen"},
        {"openai","OpenAI","https://api.openai.com/v1/chat/completions","gpt-4o-mini","platform.openai.com က API key လိုတယ်။"},
        {"openrouter","OpenRouter","https://openrouter.ai/api/v1/chat/completions","openai/gpt-4o-mini","openrouter.ai က API key လိုတယ်။ Model အများကြီး ရွေးလို့ရတယ်။"},
        {"gemini","Google Gemini","https://generativelanguage.googleapis.com/v1beta/openai/chat/completions","gemini-2.5-flash","aistudio.google.com က API key လိုတယ်။"},
        {"deepseek","DeepSeek","https://api.deepseek.com/chat/completions","deepseek-chat","platform.deepseek.com က API key လိုတယ်။"},
        {"groq","Groq","https://api.groq.com/openai/v1/chat/completions","llama-3.3-70b-versatile","console.groq.com က API key လိုတယ်။"}
    };
    static String[] provider(String id){for(String[] p:PROVIDERS)if(p[0].equals(id))return p;return PROVIDERS[0];}
    /** Per-provider preference names. "custom" keeps the original names so older saved settings still work. */
    private static String k(String base,String pid){return "custom".equals(pid)?base:base+"_"+pid;}
    static String activeProvider(MainActivity a){String id=prefs(a).getString(PROVIDER,"custom");return provider(id)[0];}

    private static android.content.SharedPreferences prefs(MainActivity a) { return a.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static String origin(String endpoint) { try { URL u=new URL(endpoint); return u.getProtocol().toLowerCase()+"://"+u.getAuthority().toLowerCase()+u.getPath(); } catch(Exception e){return "";} }
    public static boolean validEndpoint(String value) {
        if(value==null) return false; String s=value.trim();
        try { URL u=new URL(s); return "https".equalsIgnoreCase(u.getProtocol()) && u.getHost()!=null && !u.getHost().isEmpty() && u.getUserInfo()==null && u.getQuery()==null && u.getRef()==null && u.getPort()<=65535; }
        catch(Exception e){return false;}
    }
    public static boolean isEnabled(MainActivity a) { return a!=null && !a.destroyed && a.settings!=null && a.settings.getBoolean("online",false); }
    private static String endpointOf(MainActivity a){return prefs(a).getString(k(ENDPOINT,activeProvider(a)),"");}
    private static String modelOf(MainActivity a){return prefs(a).getString(k(MODEL,activeProvider(a)),"").trim();}
    private static boolean configuredSaved(MainActivity a) { return validEndpoint(endpointOf(a)) && !modelOf(a).isEmpty(); }
    public static boolean configured(MainActivity a) { return a!=null && configuredSaved(a); }
    static String providerLabel(MainActivity a){return provider(activeProvider(a))[1];}
    static String hostOf(MainActivity a){return host(endpointOf(a));}
    private static SecretKey cryptoKey() throws Exception {
        KeyStore ks=KeyStore.getInstance(KS); ks.load(null); if(!ks.containsAlias(ALIAS)) {
            KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,KS);
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); kg.generateKey();
        } return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
    }
    private static void saveSecret(MainActivity a,String pid,String endpoint,String secret) throws Exception {
        android.content.SharedPreferences.Editor e=prefs(a).edit();
        if(secret==null || secret.isEmpty()) { e.remove(k(KEY,pid)).remove(k(IV,pid)).remove(k(KEY_ORIGIN,pid)).apply(); return; }
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,cryptoKey()); byte[] iv=c.getIV();
        e.putString(k(KEY,pid),android.util.Base64.encodeToString(c.doFinal(secret.getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP)).putString(k(IV,pid),android.util.Base64.encodeToString(iv,android.util.Base64.NO_WRAP)).putString(k(KEY_ORIGIN,pid),origin(endpoint)).apply();
    }
    /** Decrypts the active provider's saved key; returns "" when none or when the endpoint changed. */
    private static String readSecret(MainActivity a,String endpoint) {
        try { android.content.SharedPreferences p=prefs(a); String pid=activeProvider(a); if(!origin(endpoint).equals(p.getString(k(KEY_ORIGIN,pid),""))) return ""; String ct=p.getString(k(KEY,pid),null),ivs=p.getString(k(IV,pid),null); if(ct==null||ivs==null)return ""; Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,cryptoKey(),new GCMParameterSpec(128,android.util.Base64.decode(ivs,0))); return new String(c.doFinal(android.util.Base64.decode(ct,0)),StandardCharsets.UTF_8); } catch(Exception e){ return ""; }
    }
    private static void clearSecret(MainActivity a,String pid) { prefs(a).edit().remove(k(KEY,pid)).remove(k(IV,pid)).remove(k(KEY_ORIGIN,pid)).apply(); }
    private static boolean hasSecret(MainActivity a,String pid){return prefs(a).contains(k(KEY,pid));}

    public static void configure(MainActivity a) {
        if(a==null || a.destroyed)return;
        final android.content.SharedPreferences p=prefs(a); LinearLayout box=a.col(); box.setPadding(a.d(18),a.d(4),a.d(18),a.d(4));
        TextView intro=a.text("AI ရွေးမယ် · Choose your AI\nကိုယ့်ဆီမှာ ရှိတဲ့ AI service တစ်ခုရွေးပါ၊ ကိုယ်ပိုင် API key ထည့်ပါ။ Key ကို encrypted သိမ်းမယ်၊ ပြန်မပြပါ။ Provider တစ်ခုချင်းစီရဲ့ key ကို သီးခြားသိမ်းပေးတယ်။ မယုံကြည်ရတဲ့ Python / library က ဒီ app ဒေတာကို ရယူနိုင်တာကြောင့် မ run ပါနဲ့။",13,a.TEXT); a.add(box,intro,-2);
        Spinner spinner=new Spinner(a); spinner.setContentDescription("AI provider"); String[] labels=new String[PROVIDERS.length]; for(int i=0;i<labels.length;i++)labels[i]=PROVIDERS[i][1];
        spinner.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,labels)); String active=activeProvider(a); for(int i=0;i<PROVIDERS.length;i++)if(PROVIDERS[i][0].equals(active))spinner.setSelection(i); a.add(box,spinner,48);
        TextView note=a.text("",12,a.MUTED); a.add(box,note,-2);
        EditText endpoint=new EditText(a); endpoint.setContentDescription("AI HTTPS endpoint"); endpoint.setHint("https://api.example.com/v1/chat/completions"); endpoint.setSingleLine(true); endpoint.setTextColor(a.TEXT); endpoint.setHintTextColor(a.MUTED); a.add(box,endpoint,52);
        EditText model=new EditText(a); model.setContentDescription("AI model"); model.setHint("Model name · ဥပမာ gpt-4o-mini"); model.setSingleLine(true); model.setTextColor(a.TEXT); model.setHintTextColor(a.MUTED); a.add(box,model,52);
        EditText secret=new EditText(a); secret.setContentDescription("AI API key");secret.setSaveEnabled(false);secret.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); secret.setHint("API key · ရိုက်ထည့်ရန်"); secret.setSingleLine(true); secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); secret.setTextColor(a.TEXT); secret.setHintTextColor(a.MUTED); a.add(box,secret,52);
        TextView state=a.text("",12,a.MUTED); a.add(box,state,-2);
        final String[] shown={active};
        Runnable fill=()->{ String pid=shown[0]; String[] preset=provider(pid); endpoint.setText(p.getString(k(ENDPOINT,pid),preset[2])); model.setText(p.getString(k(MODEL,pid),preset[3])); secret.setText("");
            note.setText(preset[4]+(preset[3].isEmpty()?"":"\nအကြံပြု model ပါပြီးသား။ အလုပ်မလုပ်ရင် provider ရဲ့ model စာရင်းကြည့်ပြီး ပြောင်းပါ။"));
            state.setText((isEnabled(a)?"Online: ON":"Online မဖွင့်ရသေးပါ (Settings မှာ ဖွင့်ပါ)")+"  ·  "+(hasSecret(a,pid)?"Key သိမ်းထားပြီး (ပြန်မပြပါ)":"Key မသိမ်းရသေးပါ")); };
        fill.run();
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){String pid=PROVIDERS[pos][0];if(!pid.equals(shown[0])){shown[0]=pid;fill.run();}}});
        TextView clear=a.action("Clear saved key · သိမ်းထားသော key ဖျက်မယ်",a.ORANGE); clear.setOnClickListener(v->{clearSecret(a,shown[0]);secret.setText("");state.setText("Key ဖျက်ပြီးပါပြီ။");Toast.makeText(a,"Saved key cleared",Toast.LENGTH_SHORT).show();}); a.add(box,clear,44);
        ScrollView scroll=new ScrollView(a); scroll.addView(box);
        configurationDialog=new AlertDialog.Builder(a).setTitle("AI ရွေးမယ် · Online AI").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save · သိမ်းမယ်",null).create();
        configurationDialog.setOnShowListener(x->configurationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String pid=shown[0], ep=endpoint.getText().toString().trim(), mo=model.getText().toString().trim(), entered=secret.getText().toString().trim();
            if(!validEndpoint(ep)){endpoint.setError("HTTPS endpoint only; no query, fragment, or username");return;} if(mo.isEmpty()){model.setError("Model is required");return;}
            String old=p.getString(k(ENDPOINT,pid),""); android.content.SharedPreferences.Editor ed=p.edit().putString(PROVIDER,pid).putString(k(ENDPOINT,pid),ep).putString(k(MODEL,pid),mo); if(!origin(old).equals(origin(ep))) { clearSecret(a,pid); } ed.apply();
            try { if(!entered.isEmpty()) saveSecret(a,pid,ep,entered); } catch(Exception e){ Toast.makeText(a,"Could not securely save key",Toast.LENGTH_LONG).show(); return; }
            configurationDialog.dismiss();
            if(!isEnabled(a))Toast.makeText(a,"သိမ်းပြီးပါပြီ။ AI သုံးဖို့ Settings မှာ Online ကို ဖွင့်ပါ။",Toast.LENGTH_LONG).show();
        })); configurationDialog.show();
    }

    /** Callback delivered on the UI thread. Exactly one of text/error is non-null. */
    public interface Result { void done(String text,String error); }
    /** Test seam: instrumentation can replace the network call with a fake provider. Null in normal use. */
    static volatile Transport testTransport=null;
    interface Transport { String send(String endpoint,String model,String key,String system,String user) throws Exception; }

    /** Sends one chat request to the saved provider. Caller must already have shown the user exactly what is sent. */
    static void request(MainActivity a,String system,String user,Result cb) {
        final String ep=endpointOf(a), mo=modelOf(a), key=readSecret(a,ep);
        NET.execute(()->{
            if(!isEnabled(a))return; String answer=null, error=null;
            try { answer=testTransport!=null?testTransport.send(ep,mo,key,system,user):post(ep,mo,key,system,user); if(answer==null||answer.trim().isEmpty())error="Provider returned no answer"; }
            catch(Exception e){ error=friendly(e); }
            final String r=answer, problem=error; if(a.destroyed)return;
            a.runOnUiThread(()->{if(!a.destroyed)cb.done(problem==null?r:null,problem);});
        });
    }
    private static String friendly(Exception e){
        String m=e.getMessage(); if(e instanceof ProviderException)return m;
        if(e instanceof SocketTimeoutException)return "Provider က အဖြေ နှေးနေပါတယ်။ ခဏနေ ပြန်စမ်းပါ။ (timeout)";
        if(e instanceof UnknownHostException)return "Internet မရပါ။ Network ကို စစ်ပါ။";
        return "Provider ကို ဆက်သွယ်လို့မရပါ။ HTTPS endpoint နဲ့ internet ကို စစ်ပါ။";
    }
    static final class ProviderException extends IOException { ProviderException(String m){super(m);} }
    private static String post(String ep,String model,String key,String system,String user) throws Exception {
        JSONObject body=new JSONObject().put("model",model).put("messages",new JSONArray().put(new JSONObject().put("role","system").put("content",system)).put(new JSONObject().put("role","user").put("content",user)));
        HttpURLConnection c=(HttpURLConnection)new URL(ep).openConnection();
        try {
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(20000); c.setReadTimeout(90000); c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("Accept","application/json");
            if(key!=null&&!key.isEmpty())c.setRequestProperty("Authorization","Bearer "+key);
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(bytes.length); try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int status=c.getResponseCode();
            if(status<200||status>=300){ String detail=""; try(InputStream err=c.getErrorStream()){ if(err!=null)detail=errorDetail(readBounded(err,16*1024)); }catch(Exception ignored){}
                String hint=status==401||status==403?" · API key မှန်လား စစ်ပါ။":status==404||status==400?" · Model အမည် / URL မှန်လား စစ်ပါ။":status==429?" · တောင်းဆိုမှု များနေ/credit ကုန်နေနိုင်ပါတယ်။":"";
                throw new ProviderException("Provider returned HTTP "+status+hint+(detail.isEmpty()?"":"\n"+detail)); }
            String raw; try(InputStream in=c.getInputStream()){raw=readBounded(in,MAX_RESPONSE);}
            JSONObject json=new JSONObject(raw); JSONObject msg=json.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            return msg.isNull("content")?"":msg.optString("content","");
        } finally { c.disconnect(); }
    }
    /** Pulls the provider's own short error message out of a JSON error body (never includes request data). */
    static String errorDetail(String raw){
        try { JSONObject o=new JSONObject(raw.trim().startsWith("[")?new JSONArray(raw).getJSONObject(0).toString():raw); Object e=o.opt("error"); String m=e instanceof JSONObject?((JSONObject)e).optString("message",""):(e instanceof String?(String)e:o.optString("message",""));
            if(m.length()>240)m=m.substring(0,240)+"…"; return m; } catch(Exception ex){ String s=raw==null?"":raw.trim(); return s.length()>160?s.substring(0,160)+"…":s; }
    }

    public static void check(MainActivity a) {
        if(a==null||a.destroyed)return;
        if(!isEnabled(a)){ new AlertDialog.Builder(a).setTitle("Offline · အော့ဖ်လိုင်း").setMessage("Main Settings မှ Online ကို ON မလုပ်ထားသေးပါ။\nAI သို့ code မပို့ပါ။").setPositiveButton("OK",null).show(); return; }
        if(!configuredSaved(a)){ new AlertDialog.Builder(a).setTitle("Setup needed · ပြင်ဆင်ရန်").setMessage("AI ကို အရင်ရွေးပြီး endpoint နဲ့ model ကို သတ်မှတ်ပါ။").setPositiveButton("Configure",(d,w)->configure(a)).setNegativeButton("Cancel",null).show(); return; }
        final int version=a.revision, lesson=a.selected; final String snapshot=a.editor==null?"":a.editor.getText().toString(); if(snapshot.getBytes(StandardCharsets.UTF_8).length>MAX_CODE){ Toast.makeText(a,"Code is limited to 20 KB",Toast.LENGTH_LONG).show(); return; }
        final String ep=endpointOf(a);
        LinearLayout box=a.col(); box.setPadding(a.d(16),0,a.d(16),0); TextView disclosure=a.text("Provider: "+providerLabel(a)+" ("+host(ep)+")\nCode will be sent over the internet. Provider charges or data retention may apply.\nThis is advisory only; the app will never modify your code automatically.",13,a.TEXT); a.add(box,disclosure,-2);
        TextView preview=a.text(snapshot,13,a.MUTED); preview.setTypeface(Typeface.MONOSPACE); preview.setTextIsSelectable(true); preview.setBackground(a.shape(a.PANEL,a.EDGE,8)); preview.setPadding(a.d(10),a.d(8),a.d(10),a.d(8)); a.add(box,preview,180);
        EditText question=new EditText(a); question.setHint("Question (optional)"); question.setText("ဒီ code ကို ရှင်းပြပြီး တိုးတက်အောင် အကြံပေးပါ။"); question.setTextColor(a.TEXT); question.setHintTextColor(a.MUTED); question.setMinLines(2); question.setGravity(Gravity.TOP); a.add(box,question,70);
        ScrollView reviewScroll=new ScrollView(a);reviewScroll.addView(box);
        reviewDialog=new AlertDialog.Builder(a).setTitle("Send code? · code ပို့မလား").setView(reviewScroll).setNegativeButton("Cancel",null).setPositiveButton("Send code",null).create();
        reviewDialog.setOnShowListener(x->reviewDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ if(!isEnabled(a)){reviewDialog.dismiss();return;} String q=question.getText().toString().trim(); if(q.length()>8000){question.setError("Question too long (8000 characters max)");return;} if(q.isEmpty())q="ဒီ code ကို ရှင်းပြပါ။"; reviewDialog.dismiss(); send(a,snapshot,q,version,lesson); }));
        reviewDialog.show();
    }
    static String host(String ep){try{return new URL(ep).getHost();}catch(Exception e){return "(invalid)";}}
    private static void send(MainActivity a,String code,String question,int version,int lesson) {
        request(a,RESPONSE_PROMPT,question+"\n\nCode:\n"+code,(result,problem)->{
            if(problem!=null){ new AlertDialog.Builder(a).setTitle("Online AI unavailable").setMessage(problem).setPositiveButton("OK",null).show(); return; }
            JSONObject advice=parseAdvice(result); String explanation=advice==null?result:advice.optString("explanation","");
            if(explanation==null||explanation.trim().isEmpty()) explanation=result;
            String candidate=null; if(advice!=null&&!advice.isNull("fixed_code")) candidate=advice.optString("fixed_code",null);
            final String fix=candidate; final boolean canReview=fix!=null&&!fix.isEmpty()&&!fix.equals(code)&&fix.getBytes(StandardCharsets.UTF_8).length<=MAX_CODE;
            TextView out=a.text(explanation+(advice==null?"\n\n(Advisory text; no structured fix was provided.)":""),14,a.TEXT); out.setPadding(a.d(18),a.d(12),a.d(18),a.d(12));out.setTextIsSelectable(true);ScrollView resultScroll=new ScrollView(a);resultScroll.addView(out);
            AlertDialog dialog=new AlertDialog.Builder(a).setTitle("AI advice · အကြံပေးချက်").setView(resultScroll).setPositiveButton("Done",null).create();
            if(canReview) dialog.setButton(AlertDialog.BUTTON_NEUTRAL,"Review fix",(d,w)->a.previewOnlineFix(code,fix,version,lesson));
            dialog.setOnShowListener(v->{if(canReview) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{dialog.dismiss();a.previewOnlineFix(code,fix,version,lesson);});});
            dialog.show();
        });
    }
    /** Parse a provider's plain or fenced JSON response without accepting arbitrary prose as a fix. */
    static JSONObject parseAdvice(String raw) {
        if(raw==null)return null; String s=raw.trim();
        if(s.startsWith("```")&&s.endsWith("```")){ int nl=s.indexOf('\n'); if(nl<0)return null; s=s.substring(nl+1,s.length()-3).trim(); }
        if(!s.startsWith("{")||!s.endsWith("}"))return null;
        try { JSONObject o=new JSONObject(s); if(!o.has("explanation")||!o.has("fixed_code"))return null; return o; } catch(Exception e){return null;}
    }
    private static String readBounded(InputStream in,int max)throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n,total=0; while((n=in.read(buf))!=-1){total+=n;if(total>max)throw new IOException("response too large");b.write(buf,0,n);}return b.toString(StandardCharsets.UTF_8.name());}
}
