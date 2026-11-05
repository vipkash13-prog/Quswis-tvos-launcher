package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.net.URL;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores Xtream credentials encrypted with an Android Keystore AES-GCM key. */
final class XtreamAccount {
    static final String DEFAULT_SERVER = "http://cli2345.live";
    private static final String PREFS = "xtream_live_account";
    private static final String ALIAS = "QuswisXtreamAccountKey";
    private XtreamAccount() {}

    static final class Credentials {
        final String server, username, password;
        Credentials(String server, String username, String password) {
            this.server=server; this.username=username; this.password=password;
        }
    }

    static boolean has(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("ciphertext");
    }

    static Credentials load(Context context) throws Exception {
        SharedPreferences prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String encoded=prefs.getString("ciphertext",null), ivText=prefs.getString("iv",null);
        if(encoded==null||ivText==null)return null;
        byte[] iv=Base64.decode(ivText,Base64.NO_WRAP);
        byte[] encrypted=Base64.decode(encoded,Base64.NO_WRAP);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        JSONObject json=new JSONObject(new String(cipher.doFinal(encrypted),"UTF-8"));
        return new Credentials(validateServer(json.optString("server")),json.optString("username"),json.optString("password"));
    }

    static void save(Context context,String server,String username,String password) throws Exception {
        String cleanServer=validateServer(server);
        if(username==null||username.trim().isEmpty()||password==null||password.isEmpty())
            throw new IllegalArgumentException("أدخل اسم المستخدم وكلمة المرور");
        JSONObject json=new JSONObject().put("server",cleanServer)
                .put("username",username.trim()).put("password",password);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(json.toString().getBytes("UTF-8"));
        context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
                .putString("ciphertext",Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply();
    }

    static void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();
    }

    static String validateServer(String raw) throws Exception {
        if(raw==null||raw.trim().isEmpty())throw new IllegalArgumentException("أدخل رابط السيرفر");
        String value=raw.trim();
        while(value.endsWith("/"))value=value.substring(0,value.length()-1);
        URL url=new URL(value);
        String protocol=url.getProtocol();
        if((!protocol.equalsIgnoreCase("http")&&!protocol.equalsIgnoreCase("https"))
                ||url.getHost()==null||url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null)
            throw new IllegalArgumentException("رابط السيرفر غير صالح");
        return value;
    }

    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing=store.getKey(ALIAS,null);
        if(existing instanceof SecretKey)return (SecretKey)existing;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setKeySize(256).build());
        return generator.generateKey();
    }
}
