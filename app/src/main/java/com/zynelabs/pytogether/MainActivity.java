package com.zynelabs.pytogether;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import com.chaquo.python.*;
import com.chaquo.python.android.AndroidPlatform;
import org.json.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    final int BG=0xff0b141c, PANEL=0xff12232e, EDGE=0xff28404c, TEXT=0xffe5eff3, MUTED=0xff8ca8b7, TEAL=0xff56dfc4, ORANGE=0xffffbb78, RED=0xffff9e9e;
    LinearLayout root, tabs, inputBox, traceBox, helperCard, traceHead;
    TextView runButton, foot, networkBadge, homeMode;
    ViewFlipper screens;
    CodeGenerator generator;
    boolean onHome=true;
    int offlineAskRevision=-1;
    View[] lessonViews;
    AlertDialog offlineHelperDialog;
    boolean keyboardVisible=false;
    AlertDialog settingsDialog, resetSettingsDialog;
    android.content.SharedPreferences settings;
    EditText editor, input;
    TextView output, status, lessonTitle, concept, lines, stepInfo, variables, stepOutput;
    TextView backStep, nextStep;
    TextView helperTitle, helperBody, helperPreview, helperUndo;
    JSONObject helperFix;
    AlertDialog fixDialog;
    int helperRevision=-1, undoLesson=-1;
    String undoBefore, undoAfter;
    Handler handler=new Handler();
    ExecutorService worker=Executors.newSingleThreadExecutor();
    JSONArray lessons, steps=new JSONArray();
    android.content.SharedPreferences prefs;
    int selected=0, stepIndex=0;
    volatile int revision=0;
    boolean loading=false;
    volatile boolean destroyed=false;
    Runnable pending;
    int d(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("practice",MODE_PRIVATE);
        settings=getSharedPreferences("settings",MODE_PRIVATE);
        try (java.io.InputStream stream=getAssets().open("lessons.json")) { java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=stream.read(buffer))!=-1)bytes.write(buffer,0,n);lessons=new JSONArray(bytes.toString("UTF-8")); }
        catch(Exception e){throw new RuntimeException(e);}
        selected=prefs.getInt("selected",0);
        if(selected<0||selected>lessons.length())selected=0;
        buildUI(); loadLesson(selected);showHome();
    }
    GradientDrawable shape(int color,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(d(radius));if(stroke!=0)g.setStroke(d(1),stroke);return g;}
    LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
    TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);v.setFontFeatureSettings("kern");return v;}
    LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:d(w),h<0?h:d(h));}
    void add(LinearLayout parent,View view,int height){parent.addView(view,lp(-1,height));}
    TextView action(String label, int color){TextView t=text(label,12,color);t.setGravity(Gravity.CENTER);t.setPadding(d(12),d(8),d(12),d(8));t.setBackground(shape(PANEL,EDGE,10));t.setMinHeight(d(40));return t;}
    LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    void gap(LinearLayout p,int n){p.addView(new View(this),lp(1,n));}
    void buildUI(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);scroll.setClipToPadding(false);
        root=col();root.setPadding(d(18),d(8),d(18),d(20));scroll.addView(root);screens=new ViewFlipper(this);screens.addView(buildHome());screens.addView(scroll);screens.addView(CodeGenerator.build(this));setContentView(screens);
        LinearLayout header=row();
        TextView home=action("Home",TEAL);home.setContentDescription("Go Home");home.setOnClickListener(v->showHome());header.addView(home);
        TextView logo=text("py / together",18,TEXT);logo.setTypeface(null,Typeface.BOLD);header.addView(logo,new LinearLayout.LayoutParams(0,d(40),1));
        networkBadge=text("●  OFFLINE",10,TEAL);networkBadge.setContentDescription("Network mode");header.addView(networkBadge);
        TextView settingsButton=action("Settings",TEAL);settingsButton.setContentDescription("Open Settings");settingsButton.setOnClickListener(v->showSettings());TextView filesButton=action("Files",TEAL);filesButton.setContentDescription("Open file actions");filesButton.setOnClickListener(v->PracticeFiles.show(this));header.addView(filesButton);add(root,header,48);
        TextView tagline=text("ရိုက်ကြည့်ပါ။ ရလဒ်ကို ချက်ချင်းမြင်ပါ။",13,MUTED);add(root,tagline,30);
        HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);tabs=row();hs.addView(tabs);
        for(int i=0;i<=lessons.length();i++){
            final int id=i;String label=i==lessons.length()?"Free practice":(i+1)+"  "+lessons.optJSONObject(i).optString("title");
            TextView tab=action(label,MUTED);LinearLayout.LayoutParams tp=lp(-2,40);tp.setMargins(0,0,d(8),0);tabs.addView(tab,tp);tab.setOnClickListener(v->loadLesson(id));
        }
        gap(root,8);add(root,hs,44);gap(root,12);
        LinearLayout learn=row(); lessonTitle=text("",16,TEXT);lessonTitle.setTypeface(null,Typeface.BOLD);learn.addView(lessonTitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView guide=action("သင်ခန်းစာ",TEAL);guide.setOnClickListener(v->showGuide());learn.addView(guide);add(root,learn,44);
        concept=text("",12,MUTED);concept.setPadding(0,d(6),0,d(6));add(root,concept,-2);
        TextView flow=text("VALUE   →   VARIABLE   →   CHANGE   →   OUTPUT",10,TEAL);flow.setGravity(Gravity.CENTER);flow.setBackground(shape(0xff14332f,0,8));add(root,flow,32);gap(root,12);
        LinearLayout editCard=col();editCard.setBackground(shape(PANEL,EDGE,14));editCard.setPadding(d(12),d(4),d(12),d(8));
        LinearLayout editHead=row();TextView codeLabel=text("PYTHON  /  main.py",11,MUTED);codeLabel.setTypeface(null,Typeface.BOLD);editHead.addView(codeLabel,new LinearLayout.LayoutParams(0,d(40),1));
        runButton=action("Run",TEAL);runButton.setContentDescription("Run Python now");runButton.setOnClickListener(v->runNow());editHead.addView(runButton);
        TextView reset=action("Reset",MUTED);reset.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Reset code?").setMessage("ဒီသင်ခန်းစာရဲ့ မူလ code ကို ပြန်ယူမယ်။").setNegativeButton("Cancel",null).setPositiveButton("Reset",(a,b)->{prefs.edit().remove("code_"+selected).remove("input_"+selected).apply();loadLesson(selected);}).show());editHead.addView(reset);add(editCard,editHead,40);
        LinearLayout codeRow=new LinearLayout(this);codeRow.setGravity(Gravity.TOP);
        lines=text("1",14,0xff4f6c7b);lines.setGravity(Gravity.TOP|Gravity.RIGHT);lines.setTypeface(Typeface.MONOSPACE);lines.setLineSpacing(d(5),1);lines.setPadding(0,d(8),d(10),0);codeRow.addView(lines,lp(24,-1));
        editor=new EditText(this);editor.setTextColor(TEXT);editor.setTextSize(14);editor.setTypeface(Typeface.MONOSPACE);editor.setGravity(Gravity.TOP|Gravity.LEFT);editor.setBackgroundColor(Color.TRANSPARENT);editor.setPadding(0,d(8),0,0);editor.setLineSpacing(d(5),1);editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);editor.setHorizontallyScrolling(true);editor.setContentDescription("Python code editor");editor.setSelectAllOnFocus(false);
        codeRow.addView(editor,new LinearLayout.LayoutParams(0,d(168),1));add(editCard,codeRow,168);add(root,editCard,-2);
        inputBox=col();TextView il=text("INPUT  ·  one line per input()",11,MUTED);add(inputBox,il,30);input=new EditText(this);input.setTextColor(TEXT);input.setTextSize(14);input.setBackground(shape(PANEL,EDGE,8));input.setPadding(d(12),d(8),d(12),d(8));input.setMinLines(1);input.setMaxLines(3);input.setHint("Type input values here");input.setHintTextColor(MUTED);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setContentDescription("Python standard input");add(inputBox,input,-2);add(root,inputBox,-2);
        gap(root,10);LinearLayout outHead=row();TextView outLabel=text("OUTPUT",11,MUTED);outLabel.setTypeface(null,Typeface.BOLD);outHead.addView(outLabel,new LinearLayout.LayoutParams(0,d(28),1));status=text("●  Starting Python…",11,TEAL);outHead.addView(status);add(root,outHead,30);
        ScrollView outputScroll=new ScrollView(this);outputScroll.setBackground(shape(0xff102b28,0,12));output=new TextView(this);output.setTextSize(14);output.setTextColor(TEAL);output.setTypeface(Typeface.MONOSPACE);output.setTextIsSelectable(true);output.setPadding(d(14),d(12),d(14),d(12));output.setContentDescription("Live Python output");outputScroll.addView(output);add(root,outputScroll,110);
        gap(root,10);helperCard=col();helperCard.setPadding(d(12),d(10),d(12),d(10));helperCard.setBackground(shape(PANEL,EDGE,12));
        TextView helperLabel=text("PYTHON HELPER  ·  OFFLINE",10,TEAL);add(helperCard,helperLabel,26);
        helperTitle=text("Code ကို စစ်ပေးနေပါတယ်…",14,TEXT);helperTitle.setTypeface(null,Typeface.BOLD);helperTitle.setContentDescription("Python helper title");add(helperCard,helperTitle,-2);
        helperBody=text("",13,MUTED);helperBody.setPadding(0,d(6),0,d(6));helperBody.setContentDescription("Burmese Python explanation");add(helperCard,helperBody,-2);
        LinearLayout helperActions=row();helperPreview=action("ပြင်နည်း ကြည့်မယ်",TEAL);helperPreview.setContentDescription("Preview suggested fix");helperPreview.setOnClickListener(v->previewFix());helperActions.addView(helperPreview);
        helperUndo=action("နောက်ပြန်ယူမယ်",ORANGE);helperUndo.setContentDescription("Undo helper fix");helperUndo.setVisibility(View.GONE);helperUndo.setOnClickListener(v->undoFix());LinearLayout.LayoutParams undoParams=lp(-2,-2);undoParams.setMargins(d(8),0,0,0);helperActions.addView(helperUndo,undoParams);add(helperCard,helperActions,-2);add(root,helperCard,-2);
        gap(root,8);TextView aiCheck=action("Helper ကိုမေးမယ် · Ask Helper",TEAL);aiCheck.setContentDescription("Ask code Helper");aiCheck.setOnClickListener(v->askHelper());add(root,aiCheck,44);
        gap(root,12);traceHead=row();TextView traceTitle=text("DATA FLOW",11,MUTED);traceTitle.setTypeface(null,Typeface.BOLD);traceHead.addView(traceTitle,new LinearLayout.LayoutParams(0,d(38),1));
        backStep=action("‹",TEAL);backStep.setContentDescription("Previous execution step");nextStep=action("›",TEAL);nextStep.setContentDescription("Next execution step");backStep.setOnClickListener(v->{if(stepIndex>0){stepIndex--;renderStep();}});nextStep.setOnClickListener(v->{if(stepIndex<steps.length()-1){stepIndex++;renderStep();}});traceHead.addView(backStep);traceHead.addView(nextStep);add(root,traceHead,40);
        traceBox=col();traceBox.setPadding(d(12),d(10),d(12),d(10));traceBox.setBackground(shape(PANEL,EDGE,12));stepInfo=text("",11,ORANGE);variables=text("",13,TEXT);variables.setTypeface(Typeface.MONOSPACE);variables.setTextIsSelectable(true);stepOutput=text("",11,MUTED);add(traceBox,stepInfo,-2);gap(traceBox,5);add(traceBox,variables,-2);gap(traceBox,5);add(traceBox,stepOutput,-2);add(root,traceBox,-2);
        foot=text("",10,MUTED);foot.setGravity(Gravity.CENTER);add(root,foot,36);
        TextWatcher changes=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){if(!loading)onEdit();}public void afterTextChanged(Editable e){}};
        editor.addTextChangedListener(changes);input.addTextChangedListener(changes);
        final boolean[] compact={false};
        root.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            android.graphics.Rect visible=new android.graphics.Rect();root.getWindowVisibleDisplayFrame(visible);
            android.util.DisplayMetrics real=new android.util.DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(real);
            boolean keyboard=real.heightPixels-visible.bottom>d(150);
            if(keyboard!=compact[0]){
                compact[0]=keyboard;keyboardVisible=keyboard;
                View[] learning={tagline,hs,learn,concept,flow,foot};
                for(View view:learning)view.setVisibility(keyboard?View.GONE:View.VISIBLE);
                updateSettingViews();
                codeRow.getLayoutParams().height=d(keyboard?140:(selected==lessons.length()?240:168));editor.getLayoutParams().height=d(keyboard?140:(selected==lessons.length()?240:168));
                outputScroll.getLayoutParams().height=d(keyboard?90:(selected==lessons.length()?140:110));root.requestLayout();
                if(keyboard)scroll.post(()->scroll.smoothScrollTo(0,0));
            }
        });
        lessonViews=new View[]{tagline,hs,learn,concept,flow};
        updateSettingViews();
    }
    void loadLesson(int id){
        loading=true;selected=id;JSONObject lesson=lessons.optJSONObject(id);
        String original=lesson==null?"# Try your own Python here\nname = \"Zyne\"\nprint(\"Hello, \" + name)":lesson.optString("code");
        editor.setText(prefs.getString("code_"+id,original));input.setText(prefs.getString("input_"+id,id==3?"မေမေ":""));
        lessonTitle.setText(lesson==null?"Free practice":lesson.optString("burmeseTitle"));concept.setText(lesson==null?"ကိုယ်တိုင် ပြောင်းရေးပါ။ အောက်က output နဲ့ တန်ဖိုးတွေကို ကြည့်ပါ။":lesson.optString("concept"));
        for(int i=0;i<tabs.getChildCount();i++){TextView tab=(TextView)tabs.getChildAt(i);tab.setTextColor(i==id?BG:MUTED);tab.setBackground(shape(i==id?TEAL:PANEL,i==id?0:EDGE,10));}
        prefs.edit().putInt("selected",selected).apply();loading=false;updateSettingViews();onEdit();
    }
    void onEdit(){
        revision++;int current=revision;
        prefs.edit().putString("code_"+selected,editor.getText().toString()).putString("input_"+selected,input.getText().toString()).apply();
        String code=editor.getText().toString();inputBox.setVisibility(code.contains("input(")||code.contains("input (")?View.VISIBLE:View.GONE);
        StringBuilder numbers=new StringBuilder();for(int i=1;i<=code.split("\n",-1).length;i++){if(i>1)numbers.append('\n');numbers.append(i);}lines.setText(numbers.toString());
        colorCode();status.setText(autoRun()?"●  Typing…":"●  Not run · tap Run");status.setTextColor(ORANGE);output.setAlpha(.45f);steps=new JSONArray();renderStep();
        helperFix=null;helperRevision=-1;helperPreview.setVisibility(View.GONE);helperTitle.setText("Code ကို စစ်ပေးနေပါတယ်…");helperBody.setText(autoRun()?"စာရိုက်ပြီး ခဏစောင့်ပါ။ အမှားရှိရင် တစ်ခုချင်း ရှင်းပြမယ်။":"Run နှိပ်ပြီး အမှားရှင်းပြချက် ကြည့်ပါ။");
        if(selected!=undoLesson||!code.equals(undoAfter)){undoBefore=null;undoAfter=null;helperUndo.setVisibility(View.GONE);}
        if(pending!=null)handler.removeCallbacks(pending);
        pending=()->execute(code,input.getText().toString(),current);if(autoRun()&&!onHome)handler.postDelayed(pending,runDelay());
    }
    void colorCode(){
        Editable e=editor.getText();for(ForegroundColorSpan span:e.getSpans(0,e.length(),ForegroundColorSpan.class))e.removeSpan(span);
        String s=e.toString();String[] regex={"\\b(print|input|if|else|for|in|range|int|str|len|True|False|def|return|while|import|from)\\b","\\b[0-9]+\\b","\"[^\"\\n]*\"|'[^'\\n]*'","#[^\\n]*"};int[] colors={TEAL,ORANGE,0xffc8d998,MUTED};
        for(int i=0;i<regex.length;i++){Matcher m=Pattern.compile(regex[i]).matcher(s);while(m.find())e.setSpan(new ForegroundColorSpan(colors[i]),m.start(),m.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
    }
    void execute(String code,String stdin,int version){
        if(destroyed||version!=revision)return;status.setText("●  Running…");
        worker.submit(()->{
            if(version!=revision||destroyed)return;
            try{
                if(!Python.isStarted())Python.start(new AndroidPlatform(this));
                Python.getInstance().getModule("package_manager").callAttr("activate",new java.io.File(getFilesDir(),"site-packages").getAbsolutePath());
                String json=Python.getInstance().getModule("practice_engine").callAttr("run",code,stdin).toString();
                handler.post(()->{if(!destroyed&&version==revision)showResult(json);});
            }catch(Throwable ex){handler.post(()->{if(!destroyed&&version==revision){output.setAlpha(1);output.setTextColor(RED);output.setText("Python engine could not start: "+ex.getMessage());status.setText("●  Engine error");}});}
        });
    }
    void showResult(String json){
        try{JSONObject r=new JSONObject(json);steps=r.optJSONArray("steps");if(steps==null)steps=new JSONArray();stepIndex=0;JSONObject err=r.optJSONObject("error");output.setAlpha(1);
            String out=r.optString("output");
            if(err!=null){String line=err.isNull("line")?"":" · line "+err.optInt("line");output.setTextColor(RED);output.setText(out+(out.isEmpty()?"":"\n")+err.optString("type")+line+"\n"+err.optString("message")+"\n\n"+err.optString("hint"));status.setText("●  Check code");status.setTextColor(RED);}
            else{output.setTextColor(TEAL);output.setText(out.isEmpty()?"No printed output yet. Try print(value).":out);status.setText("●  Live · "+r.optInt("elapsed_ms")+" ms");status.setTextColor(TEAL);}
            showHelper(r.optJSONObject("helper"));
            if(offlineAskRevision==revision){offlineAskRevision=-1;showOfflineExplanation(r.optJSONObject("helper"));}
            renderStep();
        }catch(Exception e){output.setText("Unable to read Python result.");}
    }
    void showHelper(JSONObject h){
        if(!settings.getBoolean("helper",true)){helperFix=null;helperPreview.setVisibility(View.GONE);return;}
        if(h==null){helperTitle.setText("ရှင်းပြချက် မရသေးပါ။");helperBody.setText("Code ပြောင်းရေးပြီး ထပ်စမ်းပါ။");return;}
        String line=h.isNull("line")?"":" · လိုင်း "+h.optInt("line");helperTitle.setText(h.optString("title")+line);
        helperBody.setText(h.optString("explanation")+"\n\n"+h.optString("next_step"));
        helperFix=h.optJSONObject("fix");helperRevision=revision;
        helperPreview.setVisibility(helperFix==null?View.GONE:View.VISIBLE);
    }
    void previewFix(){
        final JSONObject fix=helperFix;final int version=helperRevision;
        if(!settings.getBoolean("helper",true)||fix==null||version!=revision)return;
        final String before=fix.optString("before"),after=fix.optString("after");
        if(!editor.getText().toString().equals(before))return;
        LinearLayout preview=col();preview.setPadding(d(18),d(12),d(18),d(12));
        TextView note=text("ဒီပြင်နည်းကို စစ်ကြည့်ပါ။ လက်ခံမှသာ code ပြောင်းမယ်။ ပြောင်းပြီးရင် လက်ရှိ Auto-run setting အတိုင်း run မယ်။ ပိတ်ထားရင် Run နှိပ်ပါ။",14,TEXT);add(preview,note,-2);
        TextView code=text("မူလ code\n"+before+"\n\nပြင်ပြီး code\n"+after,14,TEXT);code.setTypeface(Typeface.MONOSPACE);code.setTextIsSelectable(true);code.setPadding(0,d(12),0,d(12));add(preview,code,-2);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(PANEL);scroll.addView(preview);
        fixDialog=new AlertDialog.Builder(this).setTitle(fix.optString("label","ပြင်နည်း")) .setView(scroll).setNegativeButton("မပြင်သေးဘူး",null).setPositiveButton("လက်ခံပြီး ပြင်မယ်",(a,b)->{
            if(!settings.getBoolean("helper",true)||version!=revision||!editor.getText().toString().equals(before)){Toast.makeText(this,"Code ပြောင်းသွားလို့ ပြင်နည်းအသစ်ကို စောင့်ပါ။",Toast.LENGTH_LONG).show();return;}
            undoBefore=before;undoAfter=after;undoLesson=selected;editor.setText(after);editor.setSelection(after.length());helperUndo.setVisibility(View.VISIBLE);showUndoNotice();
        }).show();
    }
    void undoFix(){
        if(undoBefore==null||selected!=undoLesson||!editor.getText().toString().equals(undoAfter))return;
        String restore=undoBefore;undoBefore=null;undoAfter=null;helperUndo.setVisibility(View.GONE);editor.setText(restore);editor.setSelection(restore.length());
    }
    void renderStep(){
        if(stepInfo==null)return;
        if(steps.length()==0){stepInfo.setText("တစ်ကြောင်းချင်း တန်ဖိုးစီးဆင်းမှု");variables.setText("Code ပြောင်းပြီး ခဏစောင့်ပါ။");stepOutput.setText("‹ › ဖြင့် execution အဆင့်တွေ ကြည့်နိုင်မယ်။");backStep.setAlpha(.4f);nextStep.setAlpha(.4f);return;}
        JSONObject s=steps.optJSONObject(stepIndex);stepInfo.setText("STEP "+(stepIndex+1)+" / "+steps.length()+"  ·  after line "+s.optInt("line"));
        JSONObject vars=s.optJSONObject("variables");StringBuilder b=new StringBuilder();if(vars!=null){java.util.Iterator<String> keys=vars.keys();while(keys.hasNext()){String k=keys.next();if(b.length()>0)b.append('\n');b.append(k).append("  =  ").append(vars.optString(k));}}
        variables.setText(b.length()==0?"No stored variables yet":b.toString());String out=s.optString("output");stepOutput.setText(out.isEmpty()?"Output မထွက်သေးပါ။":"Output so far: "+out.replaceFirst("\\s+$",""));backStep.setAlpha(stepIndex>0?1:.4f);nextStep.setAlpha(stepIndex<steps.length()-1?1:.4f);
    }
    void showGuide(){
        JSONObject l=lessons.optJSONObject(selected);if(l==null){new AlertDialog.Builder(this).setTitle("Free practice").setMessage("ကိုယ်တိုင်ရေးထားတဲ့ Python code ကို offline run ပေးပါတယ်။\n\nCode အပေါ်၊ output အောက်။ Settings မှာ Auto-run နဲ့ စောင့်ချိန် ပြောင်းနိုင်ပါတယ်။ Auto-run ပိတ်ထားရင် Run နှိပ်ပါ။ input() အတွက် Input အကွက်မှာ တစ်ကြောင်းစီ ရေးပါ။\n\nဒီ prototype က beginner exercise အတွက်ပါ။ မယုံကြည်ရတဲ့ code မထည့်ပါနဲ့။ pip၊ terminal နဲ့ file editor မပါပါ။").setPositiveButton("OK",null).show();return;}
        StringBuilder b=new StringBuilder(l.optString("concept"));JSONArray st=l.optJSONArray("steps");for(int i=0;i<st.length();i++)b.append("\n\n").append(i+1).append(". ").append(st.optString(i));b.append("\n\nလေ့ကျင့်ရန်\n").append(l.optString("challenge"));
        new AlertDialog.Builder(this).setTitle(l.optString("burmeseTitle")).setMessage(b.toString()).setPositiveButton("စမ်းရေးမယ်",null).setNeutralButton("Hint",(a,c)->new AlertDialog.Builder(this).setTitle("Hint").setMessage(l.optString("hint")).setPositiveButton("OK",null).show()).show();
    }
    void showUndoNotice(){Toast.makeText(this,"ပြင်ပြီးပါပြီ။ Helper ထဲမှာ Undo ပြန်ယူနိုင်ပါတယ်။",Toast.LENGTH_LONG).show();}
    void useGeneratedCode(String code){importFileCode(code,"Code ကို Code Editor ထဲ ထည့်ပြီးပါပြီ။ Auto-run ပိတ်ထားပါတယ်။ ဖတ်ကြည့်ပြီးမှ Run နှိပ်ပါ။");}
    void importFileCode(String code){importFileCode(code,"File ဖွင့်ပြီးပါပြီ။ Auto-run ပိတ်ထားပါတယ်။ Code စစ်ပြီးမှ Run နှိပ်ပါ။");}
    void importFileCode(String code,String message){
        // Keep a recoverable copy of the previous free draft; never run imported code automatically.
        int id=lessons.length();String previous=prefs.getString("code_"+id,"");prefs.edit().putString("before_import_code",previous).putString("before_import_input",prefs.getString("input_"+id,"")).apply();
        settings.edit().putBoolean("auto_run",false).apply();loadLesson(id);editor.setText(code);input.setText("");showPractice();updateSettingViews();
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);PracticeFiles.onResult(this,request,result,data);}
    void askHelper(){
        if(undoBefore!=null&&undoLesson==selected&&editor.getText().toString().equals(undoAfter)){
            new AlertDialog.Builder(this).setTitle("Helper").setItems(new String[]{"Helper ကို ထပ်မေးမယ်","နောက်ပြန်ယူမယ် · Undo last fix"},(dialog,which)->{if(which==1)undoFix();else askCurrentHelper();}).setNegativeButton("Cancel",null).show();return;
        }
        askCurrentHelper();
    }
    void askCurrentHelper(){
        if(settings.getBoolean("online",false)){OnlineAi.check(this);return;}
        if(helperRevision==revision){JSONObject h=new JSONObject();try{h.put("title",helperTitle.getText().toString()).put("explanation",helperBody.getText().toString()).put("next_step","");}catch(Exception ignored){}showOfflineExplanation(h);return;}
        settings.edit().putBoolean("helper",true).apply();updateSettingViews();runNow();offlineAskRevision=revision;
    }
    void showOfflineExplanation(JSONObject h){
        if(h==null)return;
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Offline Helper · "+h.optString("title")).setMessage(h.optString("explanation")+"\n\n"+h.optString("next_step")).setPositiveButton("OK",null);
        if(helperFix!=null)dialog.setNeutralButton("ပြင်နည်း ကြည့်မယ်",(a,b)->previewFix());
        if(undoBefore!=null&&undoLesson==selected&&editor.getText().toString().equals(undoAfter))dialog.setNegativeButton("နောက်ပြန်ယူမယ်",(a,b)->undoFix());
        offlineHelperDialog=dialog.show();
    }
    void previewOnlineFix(String before,String after,int expectedRevision,int expectedLesson){
        if(destroyed||expectedRevision!=revision||expectedLesson!=selected||!editor.getText().toString().equals(before)){Toast.makeText(this,"Code ပြောင်းသွားပါပြီ။ Helper ကို အသစ်မေးပါ။",Toast.LENGTH_LONG).show();return;}
        worker.execute(()->{
            boolean valid=true;
            try{if(!Python.isStarted())Python.start(new AndroidPlatform(this));Python.getInstance().getBuiltins().callAttr("compile",after,"<AI suggestion>","exec");}catch(Throwable e){valid=false;}
            final boolean compiles=valid;
            handler.post(()->{
                if(destroyed)return;
                if(!compiles){new AlertDialog.Builder(this).setTitle("Fix မသုံးနိုင်သေးပါ").setMessage("AI ပြန်ပေးတဲ့ code မှာ syntax အမှားရှိနေပါတယ်။ မူလ code ကို မပြောင်းပါ။").setPositiveButton("OK",null).show();return;}
                if(expectedRevision!=revision||expectedLesson!=selected||!editor.getText().toString().equals(before))return;
                TextView preview=text("မူလ code\n"+before+"\n\nAI ပြင်ထားသော code\n"+after+"\n\nAI မှားနိုင်ပါတယ်။ လက်ခံမှသာ ပြောင်းပါမယ်။",14,TEXT);preview.setTypeface(Typeface.MONOSPACE);preview.setTextIsSelectable(true);preview.setPadding(d(18),d(12),d(18),d(12));ScrollView scroll=new ScrollView(this);scroll.addView(preview);
                fixDialog=new AlertDialog.Builder(this).setTitle("Review AI fix · ပြင်နည်းစစ်မယ်").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("လက်ခံပြီး ပြင်မယ်",(a,b)->{
                    if(expectedRevision!=revision||expectedLesson!=selected||!editor.getText().toString().equals(before)){Toast.makeText(this,"Code ပြောင်းသွားလို့ မပြင်ပါ။",Toast.LENGTH_LONG).show();return;}
                    undoBefore=before;undoAfter=after;undoLesson=selected;editor.setText(after);editor.setSelection(after.length());settings.edit().putBoolean("helper",true).apply();updateSettingViews();helperUndo.setVisibility(View.VISIBLE);showUndoNotice();
                }).show();
            });
        });
    }
    View buildHome(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);LinearLayout home=col();home.setPadding(d(24),d(32),d(24),d(32));scroll.addView(home);
        TextView title=text("py / together",30,TEXT);title.setTypeface(null,Typeface.BOLD);add(home,title,60);
        add(home,text("ဒီနေ့ ဘာလုပ်မလဲ?",22,TEXT),56);
        add(home,text("Python ကို တစ်ဆင့်ချင်း နားလည်မယ်။ ရေးထားတာတွေကို ဖုန်းထဲမှာ ဆက်သိမ်းပေးထားမယ်။",14,MUTED),-2);gap(home,20);
        homeMode=text("",12,TEAL);add(home,homeMode,-2);gap(home,20);
        TextView lessonsAction=action("သင်ကြားရေး · Learn Python",TEAL);lessonsAction.setTextSize(17);lessonsAction.setContentDescription("Home Learning");lessonsAction.setOnClickListener(v->chooseLesson());add(home,lessonsAction,64);gap(home,10);
        TextView practiceAction=action("Code Editor · ကိုယ်တိုင်ရေးမယ်",TEAL);practiceAction.setTextSize(16);practiceAction.setContentDescription("Home Code Editor");practiceAction.setOnClickListener(v->{loadLesson(lessons.length());showPractice();});add(home,practiceAction,64);gap(home,10);
        TextView generatorAction=action("AI Code Generator · Code ရေးခိုင်းမယ်",TEAL);generatorAction.setTextSize(16);generatorAction.setContentDescription("Home Code Generator");generatorAction.setOnClickListener(v->showGenerator());add(home,generatorAction,64);gap(home,10);
        TextView settingsAction=action("Settings · ဆက်တင်များ",MUTED);settingsAction.setTextSize(17);settingsAction.setContentDescription("Home Settings");settingsAction.setOnClickListener(v->showSettings());add(home,settingsAction,64);gap(home,16);
        TextView resume=action("ဆက်လေ့ကျင့်မယ် · Resume last draft",TEAL);resume.setContentDescription("Resume last draft");resume.setOnClickListener(v->showPractice());add(home,resume,52);
        gap(home,24);add(home,text("Core practice က offline ပါ။ မယုံကြည်ရတဲ့ code / library ကို မ run ပါနဲ့။",12,MUTED),-2);return scroll;
    }
    void chooseLesson(){
        String[] titles=new String[lessons.length()];for(int i=0;i<titles.length;i++)titles[i]=(i+1)+" · "+lessons.optJSONObject(i).optString("burmeseTitle");
        new AlertDialog.Builder(this).setTitle("သင်ခန်းစာ ရွေးမယ်").setItems(titles,(dialog,which)->{loadLesson(which);showPractice();}).setNegativeButton("Cancel",null).show();
    }
    void showHome(){
        onHome=true;if(pending!=null)handler.removeCallbacks(pending);revision++;
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);
        screens.setDisplayedChild(0);updateSettingViews();
    }
    void showPractice(){onHome=false;screens.setDisplayedChild(1);onEdit();}
    void showGenerator(){
        onHome=true;if(pending!=null)handler.removeCallbacks(pending);revision++;
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);
        screens.setDisplayedChild(2);updateSettingViews();
    }
    @Override public void onBackPressed(){if(!onHome||screens.getDisplayedChild()!=0){showHome();return;}super.onBackPressed();}
    boolean autoRun(){return settings.getBoolean("auto_run",true);}
    int runDelay(){int value=settings.getInt("delay",650);return value==350||value==650||value==1000||value==1500?value:650;}
    int fontSize(){int value=settings.getInt("font",14);return value>=12&&value<=20&&value%2==0?value:14;}
    void runNow(){
        if(pending!=null)handler.removeCallbacks(pending);
        revision++;steps=new JSONArray();renderStep();helperFix=null;helperRevision=-1;helperPreview.setVisibility(View.GONE);
        execute(editor.getText().toString(),input.getText().toString(),revision);
    }
    void updateSettingViews(){
        networkBadge.setText(settings.getBoolean("online",false)?"●  ONLINE":"●  OFFLINE");if(generator!=null)generator.refresh();
        if(homeMode!=null)homeMode.setText(settings.getBoolean("online",false)?"ONLINE TOOLS ENABLED · AI ကို နှိပ်မှ code ပို့မယ်":"OFFLINE · Python နဲ့ မြန်မာ helper အသင့်ရှိပါတယ်");
        int height=d(keyboardVisible?140:(selected==lessons.length()?240:168));editor.getLayoutParams().height=height;((View)editor.getParent()).getLayoutParams().height=height;
        int size=fontSize();editor.setTextSize(size);lines.setTextSize(size);output.setTextSize(size);input.setTextSize(size);variables.setTextSize(size);
        helperCard.setVisibility(View.GONE); // Helper is opened explicitly, not crowded into the editor.
        if(lessonViews!=null)for(View view:lessonViews)view.setVisibility(selected<lessons.length()&&!keyboardVisible?View.VISIBLE:View.GONE);
        if(!settings.getBoolean("helper",true)){helperFix=null;helperPreview.setVisibility(View.GONE);if(fixDialog!=null)fixDialog.dismiss();}
        boolean flow=selected<lessons.length()&&settings.getBoolean("flow",true)&&!keyboardVisible;
        traceHead.setVisibility(flow?View.VISIBLE:View.GONE);traceBox.setVisibility(flow?View.VISIBLE:View.GONE);
        runButton.setVisibility(autoRun()?View.GONE:View.VISIBLE);
        foot.setText((autoRun()?"AUTO-RUN  ·  "+runDelay()+" ms after typing":"MANUAL RUN  ·  tap Run")+"  ·  Python 3.11");
    }
    void settingChanged(boolean executionChanged){
        updateSettingViews();
        if(executionChanged)onEdit();
    }
    void showSettings(){
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);
        LinearLayout panel=col();panel.setPadding(d(20),d(8),d(20),d(16));panel.setBackgroundColor(BG);
        TextView intro=text("Settings နဲ့ draft တွေ ဖုန်းထဲမှာ သိမ်းပါတယ်။ Online AI ကို ကိုယ်တိုင်နှိပ်မှ code ပို့ပါတယ်။",13,MUTED);add(panel,intro,-2);gap(panel,12);
        add(panel,text("CONNECTION · ချိတ်ဆက်မှု",12,TEAL),32);
        RadioGroup mode=new RadioGroup(this);mode.setOrientation(RadioGroup.VERTICAL);
        RadioButton offline=new RadioButton(this);offline.setId(View.generateViewId());offline.setText("Offline · Python နဲ့ မြန်မာ helper သာ");offline.setTextColor(TEXT);offline.setMinHeight(d(48));offline.setContentDescription("Offline mode");mode.addView(offline);
        RadioButton online=new RadioButton(this);online.setId(View.generateViewId());online.setText("Online · Library download / AI ရနိုင်မယ်");online.setTextColor(TEXT);online.setMinHeight(d(48));online.setContentDescription("Online mode");mode.addView(online);
        mode.check(settings.getBoolean("online",false)?online.getId():offline.getId());add(panel,mode,-2);
        mode.setOnCheckedChangeListener((group,id)->{settings.edit().putBoolean("online",id==online.getId()).apply();updateSettingViews();});
        add(panel,text("Offline က network လုပ်ဆောင်ချက်အသစ်တွေကို ပိတ်ပါတယ်။ Online ရွေးထားလည်း AI စစ်မယ် / Install နှိပ်မှ ချိတ်မယ်။ စတင်ပြီးသား request ကိုတော့ ပြန်မရုပ်သိမ်းနိုင်ပါ။ ဒီ setting က AI / installer အတွက်သာပါ။ Python code နဲ့ library တွေရဲ့ network ကို firewall လို မပိတ်နိုင်ပါ။",12,MUTED),-2);
        TextView libraries=action("Python libraries · pip",TEAL);libraries.setContentDescription("Manage Python libraries");libraries.setOnClickListener(v->PackageScreen.show(this));add(panel,libraries,48);gap(panel,6);
        TextView provider=action("AI ရွေးမယ် · AI provider / API key",TEAL);provider.setContentDescription("Configure AI provider");provider.setOnClickListener(v->OnlineAi.configure(this));add(panel,provider,48);gap(panel,12);
        add(panel,text("RUNNING · အလုပ်လုပ်ပုံ",12,TEAL),32);
        addSettingSwitch(panel,"Auto-run","စာရိုက်ပြီး ခဏရပ်တာနဲ့ run မယ်။ ပိတ်ထားရင် Run နှိပ်ပါ။","auto_run",true,true);
        add(panel,text("Auto-run delay · စောင့်ချိန်",14,TEXT),36);
        Spinner delay=settingSpinner(new String[]{"350 ms","650 ms (default)","1000 ms","1500 ms"},new int[]{350,650,1000,1500},"delay",runDelay(),true);delay.setContentDescription("Auto-run delay");add(panel,delay,48);
        gap(panel,12);add(panel,text("READING · ဖတ်ရှုရန်",12,TEAL),32);
        add(panel,text("Code & output စာလုံးအရွယ်",14,TEXT),36);
        Spinner font=settingSpinner(new String[]{"12 sp","14 sp (default)","16 sp","18 sp","20 sp"},new int[]{12,14,16,18,20},"font",fontSize(),false);font.setContentDescription("Code and output font size");add(panel,font,48);
        addSettingSwitch(panel,"Offline Burmese helper","အမှားရှင်းပြချက်နဲ့ လက်ခံမှပြင်ပေးတဲ့ အကြံပြုချက်","helper",true,false);
        addSettingSwitch(panel,"Data flow","Run ပြီးသား variables နဲ့ output အဆင့်တွေကို ပြမယ်။","flow",true,false);
        gap(panel,12);TextView reset=action("Reset settings only",ORANGE);reset.setContentDescription("Reset settings only");reset.setOnClickListener(v->confirmResetSettings());add(panel,reset,48);
        gap(panel,16);TextView about=text("ABOUT PYTOGETHER\nVersion "+BuildConfig.VERSION_NAME+"\n"+getPackageName()+"\nPython 3.11 · Offline core · Optional online tools · AI Code Generator\n\nDZ Terminal / FlowMint တို့နဲ့ သီးခြား app ပါ။\nTrusted beginner code အတွက်သာ ဖြစ်ပါတယ်။ မသိတဲ့ code ကို မ run ပါနဲ့။",12,MUTED);about.setTextIsSelectable(true);add(panel,about,-2);
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);
        settingsDialog=new AlertDialog.Builder(this).setTitle("Settings · ဆက်တင်များ").setView(scroll).setPositiveButton("Done · ပြီးပြီ",null).create();
        settingsDialog.show();
    }
    void addSettingSwitch(LinearLayout panel,String label,String description,String key,boolean fallback,boolean execution){
        Switch toggle=new Switch(this);toggle.setText(label);toggle.setTextColor(TEXT);toggle.setTextSize(15);toggle.setPadding(0,d(8),0,d(8));toggle.setMinHeight(d(48));toggle.setChecked(settings.getBoolean(key,fallback));toggle.setContentDescription(label);add(panel,toggle,-2);
        TextView detail=text(description,12,MUTED);add(panel,detail,-2);
        toggle.setOnCheckedChangeListener((button,checked)->{settings.edit().putBoolean(key,checked).apply();settingChanged(execution);});
    }
    Spinner settingSpinner(String[] labels,int[] values,String key,int selectedValue,boolean execution){
        Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);spinner.setAdapter(adapter);
        for(int i=0;i<values.length;i++)if(values[i]==selectedValue)spinner.setSelection(i);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){if(settings.getInt(key,selectedValue)!=values[pos]){settings.edit().putInt(key,values[pos]).apply();settingChanged(execution);}}});return spinner;
    }
    void confirmResetSettings(){
        resetSettingsDialog=new AlertDialog.Builder(this).setTitle("Reset settings only?").setMessage("Offline mode, Auto-run, delay, စာလုံးအရွယ်၊ helper နဲ့ data flow ကို မူလအတိုင်း ပြန်ထားမယ်။ AI provider/key နဲ့ installed library တွေကို မဖျက်ပါ။ သင်ခန်းစာ၊ ရေးထားတဲ့ code နဲ့ input တွေ မဖျက်ပါ။").setNegativeButton("Cancel",null).setPositiveButton("Reset settings",(dialog,which)->{
            settings.edit().clear().apply();settingChanged(true);settingsDialog.dismiss();showSettings();
        }).show();
    }
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);if(fixDialog!=null)fixDialog.dismiss();if(settingsDialog!=null)settingsDialog.dismiss();if(resetSettingsDialog!=null)resetSettingsDialog.dismiss();worker.shutdownNow();super.onDestroy();}
}
