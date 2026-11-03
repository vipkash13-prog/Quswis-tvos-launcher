package com.techgenius.tvoslauncher;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import javax.crypto.Cipher;

/** Android adbd authentication. Only adbd can ask SystemUI to display its RSA confirmation. */
final class AdbAuth {
    static final int CNXN = 0x4e584e43, AUTH = 0x48545541;
    static final int TOKEN = 1, SIGNATURE = 2, PUBLIC_KEY = 3;
    static final class Message {
        final int command, arg0;
        final byte[] data;
        Message(int command, int arg0, byte[] data) {
            this.command = command; this.arg0 = arg0; this.data = data;
        }
    }
    interface Transport {
        void send(int command, int arg0, int arg1, byte[] data) throws Exception;
        Message receive() throws Exception;
    }
    interface Status { void update(String stage); }
    static void connect(Transport wire, KeyPair key, boolean trusted, Status status) throws Exception {
        status.update("Starting ADB handshake");
        wire.send(CNXN, 0x01000000, 4096, "host::\0".getBytes(StandardCharsets.UTF_8));
        boolean signatureSent = false, publicKeySent = false;
        while (true) {
            Message reply = wire.receive();
            if (reply.command == CNXN) {
                status.update("ADB authorized");
                return;
            }
            if (reply.command != AUTH || reply.arg0 != TOKEN || reply.data == null || reply.data.length != 20) {
                throw new java.io.IOException("Unexpected ADB authorization response");
            }
            if (trusted && !signatureSent) {
                signatureSent = true;
                wire.send(AUTH, SIGNATURE, 0, sign((RSAPrivateKey) key.getPrivate(), reply.data));
                status.update("Checking saved ADB authorization");
            } else if (!publicKeySent) {
                publicKeySent = true;
                // Present an untrusted client's public key on the first challenge so adbd
                // can request confirmation without waiting for a failed-signature exchange.
                wire.send(AUTH, PUBLIC_KEY, 0, publicKey((RSAPublicKey) key.getPublic()));
                status.update("Waiting for Android USB debugging authorization");
            } else {
                throw new java.io.IOException("TV did not authorize this ADB key. Check the USB debugging confirmation on the TV.");
            }
        }
    }
    static byte[] sign(RSAPrivateKey key, byte[] token) throws Exception {
        byte[] prefix = {0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
        byte[] digest = new byte[prefix.length + token.length];
        System.arraycopy(prefix, 0, digest, 0, prefix.length);
        System.arraycopy(token, 0, digest, prefix.length, token.length);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(digest);
    }
    static byte[] publicKey(RSAPublicKey key) {
        BigInteger n = key.getModulus(), r32 = BigInteger.ONE.shiftLeft(32);
        if (n.bitLength() != 2048 || key.getPublicExponent().intValue() != 65537) {
            throw new IllegalArgumentException("ADB requires an RSA-2048 key with exponent 65537");
        }
        ByteBuffer packed = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        packed.putInt(64);
        packed.putInt(r32.subtract(n.mod(r32).modInverse(r32)).mod(r32).intValue());
        packed.put(littleEndian(n));
        packed.put(littleEndian(BigInteger.ONE.shiftLeft(4096).mod(n)));
        packed.putInt(65537);
        return (base64(packed.array()) + " tvoslauncher@tv\0")
                .getBytes(StandardCharsets.US_ASCII);
    }
    private static String base64(byte[] bytes) {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        StringBuilder result = new StringBuilder((bytes.length + 2) / 3 * 4);
        for (int i = 0; i < bytes.length; i += 3) {
            int a = bytes[i] & 255;
            int b = i + 1 < bytes.length ? bytes[i + 1] & 255 : 0;
            int c = i + 2 < bytes.length ? bytes[i + 2] & 255 : 0;
            result.append(chars.charAt(a >>> 2));
            result.append(chars.charAt(((a & 3) << 4) | (b >>> 4)));
            result.append(i + 1 < bytes.length ? chars.charAt(((b & 15) << 2) | (c >>> 6)) : '=');
            result.append(i + 2 < bytes.length ? chars.charAt(c & 63) : '=');
        }
        return result.toString();
    }
    private static byte[] littleEndian(BigInteger value) {
        byte[] be = value.toByteArray(), le = new byte[256];
        for (int i = 0; i < Math.min(256, be.length); i++) le[i] = be[be.length - 1 - i];
        return le;
    }
}
