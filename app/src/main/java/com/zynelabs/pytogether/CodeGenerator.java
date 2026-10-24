package com.zynelabs.pytogether;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.json.*;
import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * "AI Code Generator" screen: ask for code in plain Burmese, or paste your own code and have
 * the mistakes pointed out. Online mode uses the owner's own AI provider; Offline mode uses
 * reviewed recipes and the rule-based helper.  Nothing ever changes the editor without a
 * review dialog and an explicit "accept" tap, and generated code is never run automatically.
 */
final class CodeGenerator {
    static final String GENERATE_SYSTEM="You are a patient Python teacher for an absolute beginner who reads only Burmese. Write simple, correct Python 3.11 code for the request. Use only the standard library unless the request clearly needs a pure-Python package (then list its pip name in libraries). Use input() for any user input; the app has an Input box with one line per input(). Keep the code short with Burmese comments and plain variable names. Never write code that harms people, steals data, scans networks you do not own, or deletes files; if asked, refuse politely in summary and set code to null. Return ONLY a JSON object: {\"summary\":\"one or two Burmese sentences about what the code does\",\"steps\":[\"3-8 short Burmese steps explaining how the code works, in order, mentioning the line or keyword\"],\"libraries\":[\"pip package names that are not in the standard library, or empty\"],\"code\":\"the complete Python code\" or null,\"notes\":\"one Burmese tip on what to try next or what to watch out for\"}. Everything except the code itself must be in Burmese.";
    static final String CHECK_SYSTEM="You are a kind Python teacher for a beginner who reads only Burmese. The student wrote the code below and may say what it should do. Find every real mistake: syntax errors, runtime errors, wrong logic compared with what they want, and habits that cause bugs. Keep their style and variable names and change as little as possible; do not rewrite parts that are fine. For each mistake give the line number, what is wrong and why, in simple Burmese. Return ONLY a JSON object: {\"summary\":\"one Burmese sentence verdict\",\"mistakes\":[{\"line\":number or null,\"problem\":\"Burmese\",\"why\":\"Burmese reason and how to fix\"}],\"steps\":[\"optional short Burmese tips\"],\"libraries\":[],\"code\":\"the complete corrected code\" or null when nothing needs fixing,\"notes\":\"\"}. If there are no mistakes, mistakes is [] and code is null.";
    static final int MAX_REQUEST=2000;

    final MainActivity a;
    final ScrollView scroll;
    final LinearLayout page;
    TextView modeBadge, modeNote, tabGenerate, tabCheck, primary, status, takeEditor;
    EditText ask, codeIn;
    LinearLayout recipeBox, resultBox, codeBox;
    boolean checkMode=false, busy=false;
    int token=0;
    JSONObject lastResult;
    AlertDialog useDialog, sendDialog;

    interface PyDone { void done(String json,String error); }

    static View build(MainActivity a){ CodeGenerator g=new CodeGenerator(a); a.generator=g; return g.scroll; }

    private CodeGenerator(MainActivity a){
        this.a=a; scroll=new ScrollView(a); scroll.setFillViewport(true); scroll.setBackgroundColor(a.BG); scroll.setClipToPadding(false);
        page=a.col(); page.setPadding(a.d(18),a.d(8),a.d(18),a.d(24)); scroll.addView(page);
        LinearLayout header=a.row();
        TextView home=a.action("Home",a.TEAL); home.setContentDescription("Generator Home"); home.setOnClickListener(v->a.showHome()); header.addView(home);
        TextView title=a.text("AI Code Generator",18,a.TEXT); title.setTypeface(null,Typeface.BOLD); title.setPadding(a.d(10),0,0,0); header.addView(title,new LinearLayout.LayoutParams(0,a.d(40),1));
        modeBadge=a.text("●  OFFLINE",10,a.TEAL); modeBadge.setContentDescription("Generator mode"); modeBadge.setOnClickListener(v->a.showSettings()); header.addView(modeBadge);
        a.add(page,header,48);
        a.add(page,a.text("ရေးချင်တာကို မြန်မာလို ပြောပါ၊ Code ရေးပေးပြီး တစ်ဆင့်ချင်း ရှင်းပြပေးမယ်။ ကိုယ်ရေးထားတာ မှားနေရင်လည်း ထောက်ပြပြီး ပြင်ပေးမယ်။",13,a.MUTED),-2);
        a.gap(page,8);
        modeNote=a.text("",12,a.TEAL); modeNote.setContentDescription("Generator mode note"); modeNote.setOnClickListener(v->{ if(!a.settings.getBoolean("online",false)||!OnlineAi.configured(a)) a.showSettings(); else OnlineAi.configure(a); }); a.add(page,modeNote,-2);
        a.gap(page,10);
        LinearLayout tabRow=a.row();
        tabGenerate=a.action("၁ · Code ရေးခိုင်းမယ်",a.TEAL); tabGenerate.setContentDescription("Generator tab write"); tabGenerate.setOnClickListener(v->setMode(false));
        tabCheck=a.action("၂ · ငါ့ code စစ်ခိုင်းမယ်",a.MUTED); tabCheck.setContentDescription("Generator tab check"); tabCheck.setOnClickListener(v->setMode(true));
        LinearLayout.LayoutParams l1=new LinearLayout.LayoutParams(0,a.d(48),1); l1.setMargins(0,0,a.d(6),0); LinearLayout.LayoutParams l2=new LinearLayout.LayoutParams(0,a.d(48),1);
        tabRow.addView(tabGenerate,l1); tabRow.addView(tabCheck,l2); a.add(page,tabRow,52);
        a.gap(page,10);
        ask=new EditText(a); ask.setContentDescription("Generator request"); ask.setTextColor(a.TEXT); ask.setHintTextColor(a.MUTED); ask.setTextSize(15); ask.setMinLines(3); ask.setGravity(Gravity.TOP); ask.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        ask.setBackground(a.shape(a.PANEL,a.EDGE,10)); ask.setPadding(a.d(12),a.d(10),a.d(12),a.d(10)); a.add(page,ask,110);
        codeBox=a.col(); a.gap(codeBox,8);
        codeIn=new EditText(a); codeIn.setContentDescription("Generator code input"); codeIn.setHint("ကိုယ့် code ကို ဒီမှာ ထည့်ပါ"); codeIn.setTextColor(a.TEXT); codeIn.setHintTextColor(a.MUTED); codeIn.setTypeface(Typeface.MONOSPACE); codeIn.setTextSize(13); codeIn.setMinLines(6); codeIn.setGravity(Gravity.TOP);
        codeIn.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); codeIn.setHorizontallyScrolling(true);
        codeIn.setBackground(a.shape(a.PANEL,a.EDGE,10)); codeIn.setPadding(a.d(12),a.d(10),a.d(12),a.d(10)); a.add(codeBox,codeIn,170);
        a.gap(codeBox,6); takeEditor=a.action("Editor ထဲက code ယူမယ်",a.TEAL); takeEditor.setContentDescription("Take code from editor"); takeEditor.setOnClickListener(v->codeIn.setText(currentEditorCode())); a.add(codeBox,takeEditor,44);
        a.add(page,codeBox,-2);
        a.gap(page,10);
        primary=a.action("Code ရေးပေးပါ",a.BG); primary.setTextSize(15); primary.setTypeface(null,Typeface.BOLD); primary.setBackground(a.shape(a.TEAL,0,12)); primary.setContentDescription("Generator go"); primary.setOnClickListener(v->go()); a.add(page,primary,54);
        a.gap(page,6);
        status=a.text("",12,a.ORANGE); status.setContentDescription("Generator status"); a.add(page,status,-2);
        resultBox=a.col(); a.add(page,resultBox,-2);
        a.gap(page,16);
        TextView rt=a.text("AI မသုံးဘဲ ရတဲ့ ကြိုပါနမူနာများ · Ready examples",12,a.TEAL); a.add(page,rt,32);
        recipeBox=a.col(); a.add(page,recipeBox,-2);
        a.gap(page,18);
        a.add(page,a.text("AI မှားနိုင်ပါတယ်။ Code ကို Editor ထဲ မထည့်ခင် ဖတ်ကြည့်ပြီး ကိုယ်တိုင် Run ပြီး စစ်ပါ။ မသိတဲ့ code ကို မ run ပါနဲ့။ ကိုယ့်ဆီမှာ ခွင့်ပြုထားတဲ့ အလုပ်အတွက်သာ သုံးပါ။",11,a.MUTED),-2);
        setMode(false); loadRecipes(); refresh();
    }

    String currentEditorCode(){ return a.editor==null?"":a.editor.getText().toString(); }
    String sitePath(){ return new File(a.getFilesDir(),"site-packages").getAbsolutePath(); }

    /** Re-reads Online/Offline and provider state. */
    void refresh(){
        boolean online=a.settings.getBoolean("online",false);
        modeBadge.setText(online?"●  ONLINE":"●  OFFLINE");
        if(!online) modeNote.setText("OFFLINE · AI မသုံးပါ။ အောက်က ကြိုပါနမူနာတွေထဲက ရွေးပေးမယ်၊ ကိုယ့် code ကို ရိုးရိုးစည်းမျဉ်းနဲ့ စစ်ပေးမယ်။ ကိုယ်လိုချင်တာအတိအကျကို AI နဲ့ ရေးခိုင်းချင်ရင် ဒီစာကို နှိပ်ပြီး Online ဖွင့်ပါ။");
        else if(!OnlineAi.configured(a)) modeNote.setText("ONLINE ဖွင့်ထားပါပြီ၊ ဒါပေမဲ့ AI ကို မရွေးရသေးပါ။ ဒီစာကို နှိပ်ပြီး AI provider နဲ့ API key ထည့်ပါ။");
        else modeNote.setText("ONLINE · "+OnlineAi.providerLabel(a)+" ("+OnlineAi.hostOf(a)+") နဲ့ ချိတ်ထားတယ်။ ခလုတ်နှိပ်မှ ပို့မယ်။ AI ပြောင်းချင်ရင် ဒီစာကို နှိပ်ပါ။");
        modeNote.setTextColor(online&&OnlineAi.configured(a)?a.TEAL:a.ORANGE);
    }

    void setMode(boolean check){
        checkMode=check;
        tabGenerate.setTextColor(check?a.MUTED:a.BG); tabGenerate.setBackground(a.shape(check?a.PANEL:a.TEAL,check?a.EDGE:0,10));
        tabCheck.setTextColor(check?a.BG:a.MUTED); tabCheck.setBackground(a.shape(check?a.TEAL:a.PANEL,check?0:a.EDGE,10));
        ask.setHint(check?"ဘာလုပ်စေချင်တာလဲ / ဘာမှားနေလဲ (မသိရင် ထားခဲ့လို့ရတယ်)":"ဥပမာ: နံပါတ်နှစ်ခု မေးပြီး ပေါင်းပေးတဲ့ program ရေးပေးပါ");
        ask.setMinLines(check?2:3);
        codeBox.setVisibility(check?View.VISIBLE:View.GONE);
        primary.setText(check?"စစ်ပြီး ပြင်ပေးပါ":"Code ရေးပေးပါ");
        status.setText("");
    }

    void loadRecipes(){
        python((json,err)->{
            if(json==null)return;
            try{ JSONArray list=new JSONArray(json); recipeBox.removeAllViews();
                for(int i=0;i<list.length();i++){ final String id=list.getJSONObject(i).getString("id"); TextView item=a.action(list.getJSONObject(i).getString("title"),a.TEAL); item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); item.setContentDescription("Recipe "+id); item.setOnClickListener(v->showRecipe(id));
                    LinearLayout.LayoutParams p=a.lp(-1,-2); p.setMargins(0,0,0,a.d(6)); recipeBox.addView(item,p); }
            }catch(Exception ignored){}
        },"recipe_list");
    }
    void showRecipe(String id){
        python((json,err)->{ if(json==null){ status.setText("Engine မစတင်နိုင်ပါ: "+err); return; } render(parse(json,"offline"),null,"OFFLINE နမူနာ"); },"offline_recipe",id);
    }

    void go(){
        if(busy)return;
        String request=ask.getText().toString().trim(); String code=codeIn.getText().toString();
        if(request.length()>MAX_REQUEST){ ask.setError("စာရှည်လွန်းတယ် (အများဆုံး "+MAX_REQUEST+" လုံး)"); return; }
        if(checkMode){
            if(code.trim().isEmpty()){ codeIn.setError("စစ်ခိုင်းမယ့် code ကို အရင်ထည့်ပါ"); return; }
            if(code.getBytes(StandardCharsets.UTF_8).length>OnlineAi.MAX_CODE){ codeIn.setError("Code က 20 KB ထက် မကျော်ရပါ"); return; }
        } else if(request.isEmpty()){ ask.setError("ဘာရေးခိုင်းချင်လဲ အရင်ရိုက်ပါ"); return; }
        boolean online=a.settings.getBoolean("online",false);
        if(!online){ offlineGo(request,code); return; }
        if(!OnlineAi.configured(a)){ new AlertDialog.Builder(a).setTitle("AI မရွေးရသေးပါ").setMessage("AI provider နဲ့ API key ကို အရင်ထည့်ပါ။").setPositiveButton("ထည့်မယ်",(d,w)->OnlineAi.configure(a)).setNegativeButton("Cancel",null).show(); return; }
        if(checkMode) confirmThenSend(request,code); else sendOnline(request,code);
    }

    void offlineGo(String request,String code){
        status.setText("Offline နဲ့ ရှာနေပါတယ်…"); busy=true; final int mine=++token;
        if(checkMode){
            python((json,err)->{ busy=false; if(mine!=token)return;
                if(json==null){ status.setText("Engine မစတင်နိုင်ပါ: "+err); return; }
                status.setText(""); JSONObject r=parse(json,"offline"); render(r,code,"OFFLINE စစ်ချက်"); },"offline_check",code);
        } else {
            python((json,err)->{ busy=false; if(mine!=token)return;
                if(json==null){ status.setText("Engine မစတင်နိုင်ပါ: "+err); return; }
                try{ JSONObject o=new JSONObject(json);
                    if(o.isNull("match")){ status.setText("ဒီတောင်းဆိုချက်နဲ့ ကိုက်တဲ့ ကြိုပါနမူနာ မတွေ့ပါ။ အောက်က နမူနာတွေထဲက ရွေးနိုင်တယ်။ ကိုယ်လိုချင်တာအတိအကျကို ရေးခိုင်းချင်ရင် Online ဖွင့်ပြီး AI ချိတ်ပါ။"); status.setTextColor(a.ORANGE); resultBox.removeAllViews(); }
                    else { status.setText(""); render(parse(o.getJSONObject("match").toString(),"offline"),null,"OFFLINE နမူနာ"); }
                }catch(Exception e){ status.setText("ရလဒ်ကို မဖတ်နိုင်ပါ"); }
            },"offline_generate",request);
        }
    }

    void confirmThenSend(String request,String code){
        LinearLayout box=a.col(); box.setPadding(a.d(16),0,a.d(16),0);
        a.add(box,a.text("Provider: "+OnlineAi.providerLabel(a)+" ("+OnlineAi.hostOf(a)+")\nအောက်က code ကို internet ကနေ ပို့မယ်။ Provider ရဲ့ ကုန်ကျစရိတ် / data မူဝါဒ ရှိနိုင်တယ်။ ပို့ပြီးရင် ဘာမှ အလိုအလျောက် မပြောင်းပါ။",13,a.TEXT),-2);
        TextView preview=a.text(code,13,a.MUTED); preview.setTypeface(Typeface.MONOSPACE); preview.setBackground(a.shape(a.PANEL,a.EDGE,8)); preview.setPadding(a.d(10),a.d(8),a.d(10),a.d(8)); a.add(box,preview,180);
        ScrollView sv=new ScrollView(a); sv.addView(box);
        sendDialog=new AlertDialog.Builder(a).setTitle("Code ပို့မလား?").setView(sv).setNegativeButton("Cancel",null).setPositiveButton("ပို့မယ်",(d,w)->sendOnline(request,code)).show();
    }

    void sendOnline(String request,String code){
        busy=true; final int mine=++token; resultBox.removeAllViews(); status.setTextColor(a.ORANGE); status.setText("AI ဆီက အဖြေ စောင့်နေပါတယ်… (စက္ကန့် ၃၀ လောက် ကြာနိုင်ပါတယ်)");
        String system=checkMode?CHECK_SYSTEM:GENERATE_SYSTEM;
        String user=checkMode?((request.isEmpty()?"ဒီ code မှာ ဘာမှားနေလဲ စစ်ပေးပါ။":request)+"\n\nCode:\n"+code):request;
        OnlineAi.request(a,system,user,(text,problem)->{
            if(mine!=token)return; busy=false;
            if(problem!=null){ status.setTextColor(a.RED); status.setText("AI က အဖြေမပေးနိုင်ပါ:\n"+problem); return; }
            status.setText(""); JSONObject r=parseResult(text); r.remove("source"); try{ r.put("source","online"); }catch(JSONException ignored){}
            render(r,checkMode?code:null,"AI · "+OnlineAi.providerLabel(a));
        });
    }

    // ---- result handling -------------------------------------------------------------------

    /** Turns a provider answer (JSON, fenced JSON or loose text with a code block) into the screen's result shape. */
    static JSONObject parseResult(String raw){
        JSONObject out=new JSONObject(); String s=raw==null?"":raw.trim(); JSONObject o=null;
        try{ int i=s.indexOf('{'), j=s.lastIndexOf('}'); if(i>=0&&j>i) o=new JSONObject(s.substring(i,j+1)); }catch(Exception e){ o=null; }
        try{
            if(o==null||(!o.has("code")&&!o.has("fixed_code")&&!o.has("summary")&&!o.has("explanation"))){
                String fence=firstFence(s); String prose=s; if(fence!=null) prose=s.replaceFirst("(?s)```[a-zA-Z0-9]*\\s*\\n.*?```","").trim();
                if(prose.length()>1500) prose=prose.substring(0,1500)+"…";
                out.put("summary",prose.isEmpty()?"AI က code ပဲ ပြန်ပေးပါတယ်။":prose).put("steps",new JSONArray()).put("mistakes",new JSONArray()).put("libraries",new JSONArray()).put("notes","AI က စနစ်တကျ ပုံစံနဲ့ မပြန်ခဲ့ပါ။ ဖတ်ကြည့်ပြီး သုံးပါ။");
                if(fence!=null) out.put("code",fence); return out;
            }
            String summary=str(o,"summary"); if(summary==null) summary=str(o,"explanation"); out.put("summary",summary==null?"":summary);
            out.put("steps",strings(o.optJSONArray("steps"))); out.put("libraries",strings(o.optJSONArray("libraries")));
            JSONArray mistakes=new JSONArray(); JSONArray ms=o.optJSONArray("mistakes");
            if(ms!=null) for(int i=0;i<ms.length();i++){ JSONObject m=ms.optJSONObject(i); if(m==null){ String t=ms.optString(i,""); if(!t.isEmpty()) mistakes.put(new JSONObject().put("line",JSONObject.NULL).put("problem",t).put("why","")); continue; }
                Object line=m.isNull("line")?JSONObject.NULL:m.opt("line"); String problem=str(m,"problem"), why=str(m,"why"); mistakes.put(new JSONObject().put("line",line).put("problem",problem==null?"":problem).put("why",why==null?"":why)); }
            out.put("mistakes",mistakes);
            String code=str(o,"code"); if(code==null) code=str(o,"fixed_code"); if(code!=null){ code=stripFence(code); if(code.trim().isEmpty()) code=null; }
            if(code!=null) out.put("code",code);
            String notes=str(o,"notes"); out.put("notes",notes==null?"":notes);
        }catch(JSONException ignored){}
        return out;
    }
    static String str(JSONObject o,String key){ if(o==null||!o.has(key)||o.isNull(key)) return null; String v=o.optString(key,""); return v; }
    static JSONArray strings(JSONArray in){ JSONArray out=new JSONArray(); if(in==null)return out; for(int i=0;i<in.length();i++){ if(in.isNull(i))continue; String t=in.optString(i,"").trim(); if(!t.isEmpty()) out.put(t); } return out; }
    static String firstFence(String s){ java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?s)```[a-zA-Z0-9]*\\s*\\n(.*?)```").matcher(s); return m.find()?m.group(1).replaceAll("\\s+$","")+"\n":null; }
    static String stripFence(String code){ String t=code.trim(); if(t.startsWith("```")){ String f=firstFence(t.endsWith("```")?t:t+"\n```"); if(f!=null) return f; } return code; }
    JSONObject parse(String json,String source){ try{ JSONObject o=new JSONObject(json); if(!o.has("source")) o.put("source",source); return o; }catch(Exception e){ return new JSONObject(); } }

    /** Shows the result after a quick compile/import check of the proposed code. */
    void render(JSONObject r,String original,String sourceLabel){
        lastResult=r; String code=str(r,"code");
        if(code!=null && code.getBytes(StandardCharsets.UTF_8).length>OnlineAi.MAX_CODE){ code=null; try{ r.remove("code"); r.put("notes","AI ပြန်ပေးတဲ့ code က ကြီးလွန်းလို့ မပြပါ။"); }catch(JSONException ignored){} }
        final String proposed=code;
        if(proposed==null){ draw(r,original,sourceLabel,null); return; }
        python((json,err)->{ JSONObject analysis=null; try{ if(json!=null) analysis=new JSONObject(json); }catch(Exception ignored){} draw(r,original,sourceLabel,analysis); },"analyze",proposed,sitePath());
    }

    void draw(JSONObject r,String original,String sourceLabel,JSONObject analysis){
        resultBox.removeAllViews(); String code=str(r,"code");
        LinearLayout card=a.col(); card.setBackground(a.shape(a.PANEL,a.EDGE,14)); card.setPadding(a.d(14),a.d(12),a.d(14),a.d(14)); card.setContentDescription("Generator result");
        TextView head=a.text("ရလဒ် · "+sourceLabel,11,a.TEAL); a.add(card,head,28);
        String summary=str(r,"summary"); if(summary!=null&&!summary.isEmpty()){ TextView s=a.text(summary,15,a.TEXT); s.setTextIsSelectable(true); a.add(card,s,-2); a.gap(card,8); }
        JSONArray mistakes=r.optJSONArray("mistakes");
        if(mistakes!=null&&mistakes.length()>0){
            a.add(card,a.text("တွေ့တဲ့ အမှားများ",12,a.ORANGE),30);
            for(int i=0;i<mistakes.length();i++){ JSONObject m=mistakes.optJSONObject(i); if(m==null)continue; String line=m.isNull("line")?"":"လိုင်း "+m.optInt("line")+" · ";
                TextView t=a.text((i+1)+". "+line+m.optString("problem")+(m.optString("why").trim().isEmpty()?"":"\n"+m.optString("why").trim()),13,a.TEXT); t.setTextIsSelectable(true); t.setPadding(a.d(10),a.d(8),a.d(10),a.d(8)); t.setBackground(a.shape(0xff1a2a35,0,8)); LinearLayout.LayoutParams p=a.lp(-1,-2); p.setMargins(0,0,0,a.d(6)); card.addView(t,p); }
            a.gap(card,4);
        }
        JSONArray steps=r.optJSONArray("steps");
        if(steps!=null&&steps.length()>0){ a.add(card,a.text(mistakes!=null&&mistakes.length()>0&&code==null?"အကြံပြုချက်များ":"တစ်ဆင့်ချင်း ရှင်းပြချက်",12,a.TEAL),30);
            for(int i=0;i<steps.length();i++){ TextView t=a.text((i+1)+". "+steps.optString(i),13,a.TEXT); t.setTextIsSelectable(true); t.setPadding(0,a.d(2),0,a.d(6)); a.add(card,t,-2); } a.gap(card,4); }
        if(code!=null){
            a.add(card,a.text(original!=null?"ပြင်ပြီး code":"Code",12,a.TEAL),30);
            TextView box=a.text(code,13,a.TEAL); box.setTypeface(Typeface.MONOSPACE); box.setTextIsSelectable(true); box.setHorizontallyScrolling(false); box.setBackground(a.shape(0xff102b28,0,10)); box.setPadding(a.d(12),a.d(10),a.d(12),a.d(10)); box.setContentDescription("Generated code"); a.add(card,box,-2);
            if(analysis!=null) addAnalysis(card,analysis);
            JSONArray libs=r.optJSONArray("libraries");
            if(libs!=null&&libs.length()>0){ StringBuilder b=new StringBuilder(); for(int i=0;i<libs.length();i++){ if(i>0)b.append(", "); b.append(libs.optString(i)); }
                TextView l=a.text("AI က ဒီ library တွေ လိုတယ်လို့ ပြောတယ်: "+b+"\nSettings → Python libraries (pip) ကနေ install လုပ်ပါ (Online လိုတယ်၊ pure-Python ပဲ ရတယ်)။",12,a.ORANGE); a.add(card,l,-2); }
            a.gap(card,10);
            TextView use=a.action("Review လုပ်ပြီး Editor ထဲ ထည့်မယ်",a.BG); use.setBackground(a.shape(a.TEAL,0,10)); use.setTypeface(null,Typeface.BOLD); use.setContentDescription("Review and use code"); final String fcode=code; use.setOnClickListener(v->confirmUse(fcode,original)); a.add(card,use,48); a.gap(card,6);
            LinearLayout row=a.row(); TextView copy=a.action("Copy",a.TEAL); copy.setContentDescription("Copy generated code"); copy.setOnClickListener(v->{ ((ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("code",fcode)); Toast.makeText(a,"Copy လုပ်ပြီးပါပြီ",Toast.LENGTH_SHORT).show(); });
            TextView again=a.action("ဒီ code ကို ထပ်စစ်မယ်",a.TEAL); again.setContentDescription("Check this code again"); again.setOnClickListener(v->{ setMode(true); codeIn.setText(fcode); resultBox.removeAllViews(); scroll.post(()->scroll.fullScroll(View.FOCUS_UP)); });
            LinearLayout.LayoutParams c1=new LinearLayout.LayoutParams(0,a.d(44),1); c1.setMargins(0,0,a.d(6),0); row.addView(copy,c1); row.addView(again,new LinearLayout.LayoutParams(0,a.d(44),2)); a.add(card,row,48);
        } else if(original!=null && (mistakes==null||mistakes.length()==0)){
            a.add(card,a.text("ပြင်စရာ code မရှိပါဘူး။ ကိုယ့် code အတိုင်း ဆက်သုံးနိုင်တယ်။",13,a.TEAL),-2);
        }
        String notes=str(r,"notes"); if(notes!=null&&!notes.trim().isEmpty()){ a.gap(card,8); TextView n=a.text(notes,12,a.MUTED); n.setTextIsSelectable(true); a.add(card,n,-2); }
        a.add(resultBox,card,-2);
        scroll.post(()->scroll.smoothScrollTo(0,resultBox.getTop()-a.d(8)));
    }

    void addAnalysis(LinearLayout card,JSONObject analysis){
        a.gap(card,6);
        if(!analysis.optBoolean("compiles",true)){
            String line=analysis.isNull("line")?"":" (လိုင်း "+analysis.optInt("line")+")";
            a.add(card,a.text("သတိ: ဒီ code မှာ syntax အမှား ရှိနေသေးတယ်"+line+" — "+analysis.optString("message")+"\nEditor ထဲထည့်ပြီး Helper ကို မေးလို့ရတယ်။ ဒါမှမဟုတ် \"ထပ်စစ်မယ်\" နှိပ်ပါ။",12,a.RED),-2);
        }
        JSONArray missing=analysis.optJSONArray("missing"), unavailable=analysis.optJSONArray("unavailable");
        if(missing!=null&&missing.length()>0){ a.add(card,a.text("ဒီ library တွေ ဖုန်းထဲမှာ မရှိသေးဘူး: "+join(missing)+"\nSettings → Python libraries (pip) ကနေ install လုပ်ပါ။ မရတဲ့ library ဆိုရင် အဲဒါမပါဘဲ ရေးခိုင်းပါ။",12,a.ORANGE),-2); }
        if(unavailable!=null&&unavailable.length()>0){ a.add(card,a.text("ဒီ module တွေ Android မှာ မရပါ: "+join(unavailable)+" — screen window ဖွင့်တဲ့ code ဖြစ်လို့ print နဲ့ပဲ ပြခိုင်းပါ။",12,a.ORANGE),-2); }
    }
    static String join(JSONArray a){ StringBuilder b=new StringBuilder(); for(int i=0;i<a.length();i++){ if(i>0)b.append(", "); b.append(a.optString(i)); } return b.toString(); }

    /** Always-review step: shows what will change in the Code Editor and waits for an explicit accept. */
    void confirmUse(String code,String original){
        String current=a.prefs.getString("code_"+a.lessons.length(),"");
        StringBuilder msg=new StringBuilder();
        if(!current.trim().isEmpty()&&!current.equals(code)) msg.append("လက်ရှိ Code Editor (Free practice) ထဲက code ကို အစားထိုးမယ်။\nFiles ထဲက \"Restore draft from before last import\" နဲ့ ပြန်ယူလို့ရတယ်။\n\nလက်ရှိ code\n").append(current).append("\n\n");
        msg.append("ထည့်မယ့် code\n").append(code).append("\n\nAI မှားနိုင်ပါတယ်။ လက်ခံပြီးရင် Auto-run ပိတ်ထားမယ်၊ Run ကို ကိုယ်တိုင်နှိပ်ပါ။");
        TextView t=a.text(msg.toString(),13,a.TEXT); t.setTypeface(Typeface.MONOSPACE); t.setTextIsSelectable(true); t.setPadding(a.d(18),a.d(12),a.d(18),a.d(12)); ScrollView sv=new ScrollView(a); sv.addView(t);
        useDialog=new AlertDialog.Builder(a).setTitle("Review · စစ်ကြည့်ပါ").setView(sv).setNegativeButton("မထည့်သေးဘူး",null).setPositiveButton("လက်ခံပြီး ထည့်မယ်",(d,w)->a.useGeneratedCode(code)).show();
    }

    // ---- Python bridge ---------------------------------------------------------------------

    void python(PyDone done,String function,Object... args){
        if(a.destroyed)return;
        try{ a.worker.execute(()->{
            String out=null, error=null;
            try{ if(!Python.isStarted())Python.start(new AndroidPlatform(a));
                Python py=Python.getInstance(); py.getModule("package_manager").callAttr("activate",sitePath());
                out=py.getModule("code_generator").callAttr(function,args).toString();
            }catch(Throwable t){ error=String.valueOf(t.getMessage()); }
            final String o=out, e=error; a.handler.post(()->{ if(!a.destroyed) done.done(o,e); });
        }); }catch(java.util.concurrent.RejectedExecutionException ignored){}
    }
}
