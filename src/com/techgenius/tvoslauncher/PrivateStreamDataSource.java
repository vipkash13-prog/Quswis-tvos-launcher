package com.techgenius.tvoslauncher;

import android.net.Uri;import androidx.media3.datasource.*;import java.io.*;import java.util.*;

/** Keep authenticated HTTP errors out of ExoPlayer's exception log. */
@androidx.media3.common.util.UnstableApi
final class PrivateStreamDataSource implements DataSource {
    static final class Factory implements DataSource.Factory {
        private final DataSource.Factory base;Factory(DataSource.Factory base){this.base=base;}
        public DataSource createDataSource(){return new PrivateStreamDataSource(base.createDataSource());}
    }
    private final DataSource delegate;PrivateStreamDataSource(DataSource delegate){this.delegate=delegate;}
    public void addTransferListener(TransferListener listener){delegate.addTransferListener(listener);}
    public long open(DataSpec spec)throws IOException{try{return delegate.open(spec);}catch(IOException e){throw new IOException("Subscription source connection failed");}}
    public int read(byte[] data,int offset,int size)throws IOException{try{return delegate.read(data,offset,size);}catch(IOException e){throw new IOException("Subscription stream interrupted");}}
    public Uri getUri(){return delegate.getUri();}
    public Map<String,List<String>> getResponseHeaders(){return delegate.getResponseHeaders();}
    public void close()throws IOException{try{delegate.close();}catch(IOException ignored){}}
}
