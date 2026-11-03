package com.techgenius.tvoslauncher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AdbAuthTest {
    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
    static final class FakeAdbd implements AdbAuth.Transport {
        final ArrayDeque<AdbAuth.Message> incoming = new ArrayDeque<>();
        final List<Integer> authTypes = new ArrayList<>();
        byte[] publicKey;
        final boolean rejectSignature;
        FakeAdbd(boolean rejectSignature) { this.rejectSignature = rejectSignature; }
        public void send(int command, int arg0, int arg1, byte[] data) {
            if (command == AdbAuth.CNXN) {
                incoming.add(new AdbAuth.Message(AdbAuth.AUTH, AdbAuth.TOKEN, new byte[20]));
            } else if (command == AdbAuth.AUTH) {
                authTypes.add(arg0);
                if (arg0 == AdbAuth.SIGNATURE && rejectSignature) {
                    incoming.add(new AdbAuth.Message(AdbAuth.AUTH, AdbAuth.TOKEN, new byte[20]));
                } else {
                    if (arg0 == AdbAuth.PUBLIC_KEY) publicKey = data;
                    incoming.add(new AdbAuth.Message(AdbAuth.CNXN, 0, new byte[0]));
                }
            }
        }
        public AdbAuth.Message receive() {
            check(!incoming.isEmpty(), "Handshake blocked waiting for another token");
            return incoming.remove();
        }
    }
    static BigInteger little(byte[] data) {
        byte[] big = new byte[data.length];
        for (int i = 0; i < data.length; i++) big[i] = data[data.length - 1 - i];
        return new BigInteger(1, big);
    }
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair key = generator.generateKeyPair();
        FakeAdbd fresh = new FakeAdbd(true);
        List<String> status = new ArrayList<>();
        AdbAuth.connect(fresh, key, false, status::add);
        check(fresh.authTypes.equals(Arrays.asList(AdbAuth.PUBLIC_KEY)), "New client must send public key on first token");
        check(status.contains("Waiting for Android USB debugging authorization"), "Authorization UI stage missing");
        FakeAdbd trusted = new FakeAdbd(false);
        AdbAuth.connect(trusted, key, true, s -> {});
        check(trusted.authTypes.equals(Arrays.asList(AdbAuth.SIGNATURE)), "Trusted client must reuse signed authentication");
        FakeAdbd revoked = new FakeAdbd(true);
        AdbAuth.connect(revoked, key, true, s -> {});
        check(revoked.authTypes.equals(Arrays.asList(AdbAuth.SIGNATURE, AdbAuth.PUBLIC_KEY)), "Revoked trust must request confirmation again");
        String text = new String(fresh.publicKey, StandardCharsets.US_ASCII);
        check(fresh.publicKey[fresh.publicKey.length - 1] == 0, "ADB public key must end in NUL");
        byte[] decoded = java.util.Base64.getDecoder().decode(text.substring(0, text.indexOf(' ')));
        check(decoded.length == 524, "Android RSA structure length invalid");
        ByteBuffer buffer = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN);
        check(buffer.getInt() == 64, "RSA word length invalid");
        long inverse = buffer.getInt() & 0xffffffffL;
        byte[] modulus = new byte[256], rr = new byte[256];
        buffer.get(modulus); buffer.get(rr);
        RSAPublicKey pub = (RSAPublicKey) key.getPublic();
        check(little(modulus).equals(pub.getModulus()), "RSA modulus changed");
        check(little(rr).equals(BigInteger.ONE.shiftLeft(4096).mod(pub.getModulus())), "Montgomery RR invalid");
        check(pub.getModulus().multiply(BigInteger.valueOf(inverse)).add(BigInteger.ONE).mod(BigInteger.ONE.shiftLeft(32)).signum() == 0, "Montgomery inverse invalid");
        check(buffer.getInt() == 65537, "RSA exponent invalid");
        check(text.substring(0, text.indexOf(' ')).equals(java.util.Base64.getEncoder().encodeToString(decoded)), "Base64 encoding not canonical");
        System.out.println("ADB new-key, trusted-key, revoked-key and public-key structure checks passed");
    }
}
