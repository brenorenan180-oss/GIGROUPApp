package com.gigroup.linkopener.util;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ScriptLoader {
    public static String load(Context ctx, String path) {
        try {
            InputStream is = ctx.getAssets().open(path);
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        } catch (IOException e) {
            Log.e("ScriptLoader", "Erro ao carregar: " + path + " — " + e.getMessage());
            return null;
        }
    }
}
