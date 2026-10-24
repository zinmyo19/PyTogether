package com.zynelabs.pytogether;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.json.JSONObject;
import java.io.File;

/** Runtime installer UI. It intentionally installs names only, never arbitrary pip arguments. */
public final class PackageScreen {
    private PackageScreen() {}

    public static void show(MainActivity a) {
        final AlertDialog[] box = new AlertDialog[1];
        LinearLayout page = a.col();
        page.setPadding(a.d(18), a.d(12), a.d(18), a.d(18));
        TextView title = a.text("Packages · Python packages", 20, a.TEXT);
        title.setTypeface(null, Typeface.BOLD);
        a.add(page, title, 46);
        a.add(page, a.text("Pip package ထည့်ရန် · pip installs trusted code ကိုသာသုံးပါ။\n"
                + "Pure-Python wheels only · Native wheels မရပါ · Internet setting ON လိုသည်။", 12, a.MUTED), -2);
        a.gap(page, 12);

        final TextView state = a.text("", 12, a.MUTED);
        state.setTextIsSelectable(true);
        final TextView list = a.text("Installed · ထည့်ပြီးသား\n(loading…)", 13, a.TEXT);
        list.setTextIsSelectable(true);
        final EditText name = new EditText(a);
        name.setHint("Package name, e.g. requests");
        name.setSingleLine(true);
        name.setTextColor(a.TEXT); name.setHintTextColor(a.MUTED);
        a.add(page, name, 52);
        final TextView install = a.action("Install · ထည့်မယ်", a.TEAL);
        install.setContentDescription("Install Python package");
        a.add(page, install, 48);
        a.gap(page, 10);
        a.add(page, state, -2);
        a.gap(page, 10);
        a.add(page, list, -2);
        a.gap(page, 14);
        a.add(page, a.text("သတိပေးချက်: package install လုပ်တာက package ရဲ့ code/metadata ကို run နိုင်ပါတယ်။\n"
                + "မသိတဲ့ package မထည့်ပါနဲ့။ Source distribution မတည်ဆောက်ပါ။ Native extension မထောက်ပံ့ပါ။\n"
                + "Install ပြီးနောက် engine ကို ပြန် run/ဖွင့်ရန် လိုနိုင်ပါတယ်။", 12, a.ORANGE), -2);

        ScrollView scroll = new ScrollView(a); scroll.addView(page);
        box[0] = new AlertDialog.Builder(a).setTitle("Package manager · ပက်ကေ့ချ်များ")
                .setView(scroll).setNegativeButton("Close · ပိတ်မယ်", null).create();
        box[0].setOnShowListener(d -> refresh(a, list, state));
        install.setOnClickListener(v -> {
            String raw = name.getText().toString().trim();
            if (!a.settings.getBoolean("online", false)) {
                state.setText("Offline mode · Settings > Online ကို ON လုပ်ပြီးမှ Install လုပ်ပါ။\nအင်တာနက်မရှိဘဲ installed list ကိုတော့ ကြည့်နိုင်ပါတယ်။");
                return;
            }
            if (raw.length() == 0) { state.setText("Package name ထည့်ပါ။ ဥပမာ requests"); return; }
            new AlertDialog.Builder(a).setTitle("Run trusted package code?")
                    .setMessage("Install လုပ်ရာမှာ package metadata/code ကို run နိုင်ပါတယ်။ Pure-Python wheel သာ လက်ခံမယ်။ ဆက်လုပ်မလား?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Install", (d, w) -> {
                        if (!a.settings.getBoolean("online", false)) { state.setText("Offline mode · Install မလုပ်ပါ။"); return; }
                        install.setEnabled(false); state.setText("Installing… · pip အလုပ်လုပ်နေသည်။\nမပိတ်ပါနဲ့။");
                        final String pkg = raw;
                        a.worker.execute(() -> {
                            String result;
                            if (!a.settings.getBoolean("online", false)) {
                                result = "{\"ok\":false,\"error\":\"Offline mode was enabled before pip started; install cancelled.\"}";
                            } else try {
                                Python py = python(a);
                                PyObject module = py.getModule("package_manager");
                                result = module.callAttr("install_json", pkg, packagePath(a)).toString();
                            } catch (Throwable t) {
                                result = "{\"ok\":false,\"error\":\"pip runtime unavailable: " + safe(t.toString()) + "\"}";
                            }
                            final String done = result;
                            a.handler.post(() -> {
                                install.setEnabled(true);
                                try {
                                    JSONObject json = new JSONObject(done);
                                    if (json.optBoolean("ok", false)) state.setText("Installed · အောင်မြင်ပါပြီ: " + json.optString("name") + "\nRestart/run engine after this change if an import is cached.");
                                    else state.setText("Not installed · မအောင်မြင်ပါ\n" + json.optString("error", "Unknown pip error"));
                                } catch (Exception e) { state.setText("Not installed · pip result မဖတ်နိုင်ပါ\n" + done); }
                                refresh(a, list, state);
                            });
                        });
                    }).show();
        });
        box[0].show();
    }

    private static String packagePath(MainActivity a) {
        return new File(a.getFilesDir(), "site-packages").getAbsolutePath();
    }

    private static Python python(MainActivity a) {
        if (!Python.isStarted()) Python.start(new AndroidPlatform(a.getApplicationContext()));
        return Python.getInstance();
    }

    private static void refresh(MainActivity a, TextView list, TextView state) {
        a.worker.execute(() -> {
            String value;
            try { value = python(a).getModule("package_manager").callAttr("installed_json", packagePath(a)).toString(); }
            catch (Throwable t) { value = "{\"ok\":false,\"error\":\"pip module unavailable\"}"; }
            final String result = value;
            a.handler.post(() -> {
                try {
                    JSONObject json = new JSONObject(result);
                    if (json.optBoolean("ok", false)) list.setText("Installed · ထည့်ပြီးသား\n" + json.optJSONArray("installed"));
                    else list.setText("Installed list unavailable\n" + json.optString("error"));
                } catch (Exception e) { list.setText("Installed list unavailable"); }
            });
        });
    }

    private static String safe(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }
}
