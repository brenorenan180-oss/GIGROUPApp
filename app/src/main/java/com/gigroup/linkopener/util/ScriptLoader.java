package com.gigroup.linkopener.util;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * ScriptLoader — carrega arquivos JS dos assets como String.
 * Retorna null em caso de erro (o chamador verifica antes de injetar).
 */
public class ScriptLoader {

    private static final String TAG = "GIGROUP-ScriptLoader";

    public static String loadAsset(Context ctx, String assetPath) {
        try {
            InputStream is = ctx.getAssets().open(assetPath);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "Erro ao carregar asset: " + assetPath + " — " + e.getMessage());
            return null;
        }
    }
}
