package com.zynelabs.pytogether;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Map;
import java.util.WeakHashMap;

/** Native Android Storage Access Framework flows for the free-practice Python draft. */
public final class PracticeFiles {
    private static final int REQUEST_OPEN = 0x504f;
    private static final int REQUEST_SAVE = 0x5053;
    private static final int MAX_BYTES = 64 * 1024;
    private static final Map<MainActivity, String> pendingSave = new WeakHashMap<>();

    private PracticeFiles() { }

    /** Shows the file actions; no filesystem directories or storage permission are used. */
    public static void show(final MainActivity a) {
        if (a == null || a.isFinishing()) return;
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * a.getResources().getDisplayMetrics().density);
        panel.setPadding(pad, 0, pad, pad);

        TextView note = new TextView(a);
        note.setText("Files are opened and saved using Android's document picker.\n\nImported code is shown for review and is never run automatically.");
        note.setTextColor(a.TEXT);
        note.setTextSize(14);
        note.setPadding(0, 0, 0, pad / 2);
        panel.addView(note, new LinearLayout.LayoutParams(-1, -2));

        Button open = new Button(a);
        open.setText("Open Python file");
        open.setContentDescription("Open Python file");
        open.setOnClickListener(v -> {
            ((AlertDialog) v.getTag()).dismiss();
            openPicker(a);
        });
        panel.addView(open, new LinearLayout.LayoutParams(-1, -2));

        Button save = new Button(a);
        save.setText("Save practice draft as .py");
        save.setContentDescription("Save practice draft as Python file");
        save.setOnClickListener(v -> {
            ((AlertDialog) v.getTag()).dismiss();
            // Capture now, not after the document picker returns: edits while it is open
            // must not change the snapshot being exported.
            pendingSave.put(a, a.editor == null ? "" : a.editor.getText().toString());
            savePicker(a);
        });
        panel.addView(save, new LinearLayout.LayoutParams(-1, -2));

        if(a.prefs.contains("before_import_code")){
            Button restore=new Button(a);restore.setText("Restore draft from before last import");restore.setOnClickListener(v->new AlertDialog.Builder(a).setTitle("Restore previous free draft?").setMessage("This replaces the current free-practice draft. Auto-run will stay off.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->{String code=a.prefs.getString("before_import_code","");String input=a.prefs.getString("before_import_input","");a.importFileCode(code);a.input.setText(input);}).show());panel.addView(restore,new LinearLayout.LayoutParams(-1,-2));
        }
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Practice files")
                .setView(panel)
                .setNegativeButton("Cancel", null)
                .create();
        open.setTag(dialog);
        save.setTag(dialog);
        dialog.show();
    }

    private static void openPicker(MainActivity a) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/x-python", "text/plain", "*/*"});
        try {
            a.startActivityForResult(intent, REQUEST_OPEN);
        } catch (ActivityNotFoundException e) {
            // Some older document providers do not accept text/*; broaden only the picker.
            intent.setType("*/*");
            intent.removeExtra(Intent.EXTRA_MIME_TYPES);
            try {
                a.startActivityForResult(intent, REQUEST_OPEN);
            } catch (ActivityNotFoundException unavailable) {
                Toast.makeText(a, "No document picker is available on this device.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void savePicker(MainActivity a) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/x-python");
        intent.putExtra(Intent.EXTRA_TITLE, "main.py");
        try {
            a.startActivityForResult(intent, REQUEST_SAVE);
        } catch (ActivityNotFoundException e) {
            intent.setType("text/plain");
            try {
                a.startActivityForResult(intent, REQUEST_SAVE);
            } catch (ActivityNotFoundException unavailable) {
                pendingSave.remove(a);
                Toast.makeText(a, "No document picker is available on this device.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Called by MainActivity.onActivityResult; returns true when requestCode belongs to this class. */
    public static boolean onResult(MainActivity a, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SAVE) {
            String snapshot = pendingSave.remove(a);
            if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
                if (a != null && resultCode != android.app.Activity.RESULT_CANCELED)
                    Toast.makeText(a, "Save was not completed: no destination was selected.", Toast.LENGTH_LONG).show();
                return true;
            }
            if (a == null || snapshot == null) return true;
            try (OutputStream out = a.getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new IOException("The selected document cannot be written.");
                out.write(snapshot.getBytes(Charset.forName("UTF-8")));
                out.flush();
                Toast.makeText(a, "Practice draft exported as main.py.", Toast.LENGTH_SHORT).show();
            } catch (SecurityException | IOException e) {
                Toast.makeText(a, "Could not save the Python file: " + safeMessage(e), Toast.LENGTH_LONG).show();
            }
            return true;
        }
        if (requestCode != REQUEST_OPEN) return false;
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            if (a != null && resultCode != android.app.Activity.RESULT_CANCELED)
                Toast.makeText(a, "Open was not completed: no file was selected.", Toast.LENGTH_LONG).show();
            return true;
        }
        if (a == null) return true;
        try {
            String code = readUtf8Bounded(a, data.getData());
            if (!isPythonName(data.getData()) && !looksLikePython(code)) {
                Toast.makeText(a, "That file is not recognized as a Python file (.py).", Toast.LENGTH_LONG).show();
                return true;
            }
            showImportPreview(a, code);
        } catch (SecurityException | IOException e) {
            Toast.makeText(a, "Could not read the selected file: " + safeMessage(e), Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private static String readUtf8Bounded(MainActivity a, Uri uri) throws IOException {
        try (InputStream in = a.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("The selected document cannot be read.");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (total + n > MAX_BYTES) throw new IOException("The file is larger than the 64 KB practice-file limit.");
                bytes.write(buffer, 0, n);
                total += n;
            }
            CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                return decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            } catch (CharacterCodingException badUtf8) {
                throw new IOException("The file is not valid UTF-8 text.");
            }
        }
    }

    private static void showImportPreview(final MainActivity a, final String code) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);
        TextView warning = new TextView(a);
        warning.setText("TRUST WARNING\nThis code came from a file and may be unsafe. Review it carefully. It will not be run automatically. Importing replaces the current free-practice draft only after you confirm.");
        warning.setTextColor(a.RED);
        warning.setTextSize(14);
        warning.setPadding(0, 0, 0, pad / 2);
        box.addView(warning, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(a);
        TextView preview = new TextView(a);
        preview.setText(code);
        preview.setTextColor(a.TEXT);
        preview.setTextSize(13);
        preview.setTypeface(android.graphics.Typeface.MONOSPACE);
        preview.setTextIsSelectable(true);
        preview.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        preview.setPadding(pad, pad, pad, pad);
        scroll.addView(preview, new ViewGroup.LayoutParams(-1, -2));
        box.addView(scroll, new LinearLayout.LayoutParams(-1, Math.round(260 * a.getResources().getDisplayMetrics().density)));
        new AlertDialog.Builder(a).setTitle("Review Python file")
                .setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Replace current draft", (d, which) -> {
                    a.importFileCode(code);
                }).show();
    }

    private static boolean isPythonName(Uri uri) {
        String path = uri == null ? null : uri.getPath();
        return path != null && path.toLowerCase(java.util.Locale.US).endsWith(".py");
    }

    private static boolean looksLikePython(String code) {
        String trimmed = code.trim();
        return trimmed.startsWith("#!") || trimmed.contains("print(") || trimmed.contains("import ")
                || trimmed.contains("def ") || trimmed.contains("class ") || trimmed.contains("input(");
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? "the provider denied access" : message;
    }
}
