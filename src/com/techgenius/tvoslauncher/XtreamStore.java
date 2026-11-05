package com.techgenius.tvoslauncher;

import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import org.json.JSONObject;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

/** Credentials and subscription catalogue stay encrypted in noBackupFilesDir. */
final class XtreamStore {
    private static final String ALIAS="Quswis.Xtream.AES.v1";
    private static javax.crypto.SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            generator.generateKey();
        }
        return (javax.crypto.SecretKey)ks.getKey(ALIAS,null);
    }
    private static File file(Context c,String name){File dir=new File(c.getNoBackupFilesDir(),"xtream-private");dir.mkdirs();return new File(dir,name+".enc");}
    static synchronized void write(Context c,String name,JSONObject data)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted=cipher.doFinal(data.toString().getBytes(StandardCharsets.UTF_8));AtomicFile target=new AtomicFile(file(c,name));FileOutputStream stream=null;
        try{stream=target.startWrite();stream.write(cipher.getIV().length);stream.write(cipher.getIV());stream.write(encrypted);target.finishWrite(stream);}
        catch(Exception e){if(stream!=null)target.failWrite(stream);throw e;}
    }
    static synchronized JSONObject read(Context c,String name)throws Exception{
        File source=file(c,name);if(!source.isFile())return null;if(source.length()>64L*1024*1024)throw new IOException("Private cache too large");
        try(InputStream input=new AtomicFile(source).openRead();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            int length=input.read();if(length!=12)throw new IOException("Invalid encrypted record");byte[] iv=new byte[length];if(input.read(iv)!=length)throw new IOException("Truncated record");
            byte[] chunk=new byte[8192];int n;while((n=input.read(chunk))!=-1)bytes.write(chunk,0,n);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            return new JSONObject(new String(cipher.doFinal(bytes.toByteArray()),StandardCharsets.UTF_8));
        }
    }
    static synchronized void remove(Context c,String name){new AtomicFile(file(c,name)).delete();}
}
