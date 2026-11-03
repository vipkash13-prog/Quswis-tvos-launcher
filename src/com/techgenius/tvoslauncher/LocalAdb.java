package com.techgenius.tvoslauncher;

import android.content.Context;
import android.provider.Settings;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Pure-Java local ADB client (127.0.0.1:5555) for granting tvOS Launcher's system permissions
 * automatically during Easy Setup when ADB / Network Debugging is enabled on the TV.
 */
final class LocalAdb {
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final int A_VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 4096;

    private static final int ADB_AUTH_TOKEN = 1;
    private static final int ADB_AUTH_SIGNATURE = 2;
    private static final int ADB_AUTH_RSAPUBLICKEY = 3;

    interface Callback {
        void onResult(boolean success, String message);
    }

    interface VolumeKeyListener {
        void onVolumeKey(int dir, boolean down);
    }

    interface BooleanSupplier {
        boolean getAsBoolean();
    }

    static volatile boolean portReachable = false;
    private static final android.os.Handler KEEP_ALIVE_HANDLER =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static Context keepAliveAppCtx;
    private static boolean keepAliveWatchdogScheduled = false;
    private static volatile boolean repairInProgress = false;
    private static volatile long lastRepairAttemptMs = 0L;
    private static final long KEEP_ALIVE_INTERVAL_MS = 60_000L;
    private static final long REPAIR_COOLDOWN_MS = 25_000L;

    private static final Runnable KEEP_ALIVE_TICK = new Runnable() {
        @Override
        public void run() {
            KEEP_ALIVE_HANDLER.removeCallbacks(this);
            if (keepAliveAppCtx == null || !isKeepAliveEnabled(keepAliveAppCtx)) {
                keepAliveWatchdogScheduled = false;
                return;
            }
            try {
                android.os.PowerManager pm = keepAliveAppCtx.getSystemService(android.os.PowerManager.class);
                if (pm == null || pm.isInteractive()) {
                    checkAndRepairPermissionsIfNeeded(keepAliveAppCtx, false);
                }
            } catch (Throwable ignored) {
            }
            KEEP_ALIVE_HANDLER.postDelayed(this, KEEP_ALIVE_INTERVAL_MS);
        }
    };

    private LocalAdb() { }

    static boolean isPaired(Context c) {
        return c.getSharedPreferences("adb_state", Context.MODE_PRIVATE).getBoolean("paired", false);
    }

    private static void setPaired(Context c, boolean val) {
        c.getSharedPreferences("adb_state", Context.MODE_PRIVATE).edit().putBoolean("paired", val).apply();
    }

    static boolean isKeepAliveEnabled(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("adb_state", Context.MODE_PRIVATE);
        if (!sp.getBoolean("keep_alive_default_on", false)) {
            sp.edit()
                    .putBoolean("keep_alive_default_on", true)
                    .putBoolean("keep_alive", true)
                    .apply();
        }
        return sp.getBoolean("keep_alive", true);
    }

    static void setKeepAliveEnabled(Context c, boolean enabled) {
        c.getSharedPreferences("adb_state", Context.MODE_PRIVATE).edit().putBoolean("keep_alive", enabled).apply();
        if (enabled) {
            ensureKeepAliveWatchdog(c);
        } else {
            KEEP_ALIVE_HANDLER.removeCallbacks(KEEP_ALIVE_TICK);
            keepAliveWatchdogScheduled = false;
        }
    }

    static void ensureKeepAliveWatchdog(Context c) {
        if (c == null) return;
        keepAliveAppCtx = c.getApplicationContext();
        if (!isKeepAliveEnabled(keepAliveAppCtx)) return;
        if (!keepAliveWatchdogScheduled) {
            keepAliveWatchdogScheduled = true;
            KEEP_ALIVE_HANDLER.removeCallbacks(KEEP_ALIVE_TICK);
            KEEP_ALIVE_HANDLER.postDelayed(KEEP_ALIVE_TICK, 5_000L);
        }
    }

    static void scheduleKeepAliveRepair(Context c, long delayMs) {
        if (c == null) return;
        final Context appCtx = c.getApplicationContext();
        keepAliveAppCtx = appCtx;
        if (!isKeepAliveEnabled(appCtx)) return;
        KEEP_ALIVE_HANDLER.postDelayed(() -> checkAndRepairPermissionsIfNeeded(appCtx, false), Math.max(500L, delayMs));
    }

    /**
     * Checks whether a known conflicting firmware AccessibilityService (such as Wanbo's
     * {@code com.sdt.projector.observer/com.sdt.hdmi.MyAccessibilityService}, which swallows all
     * D-Pad, OK, and Back key events in its {@code onKeyEvent}) is currently enabled alongside ours.
     */
    static boolean hasConflictingAccessibilityService(Context c) {
        if (c == null) return false;
        try {
            String existingAcc = Settings.Secure.getString(
                    c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (existingAcc != null && (existingAcc.contains("com.sdt.hdmi.MyAccessibilityService")
                    || existingAcc.contains("com.sdt.projector.observer"))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static String buildCleanAccessibilityServicesList(Context c, String existingAcc) {
        String pkg = c.getPackageName();
        String ourAcc = pkg + "/" + pkg + ".PowerService";
        String ourShort = pkg + "/.PowerService";
        StringBuilder sb = new StringBuilder();
        if (existingAcc != null && !existingAcc.trim().isEmpty()) {
            for (String part : existingAcc.split(":")) {
                String trimmed = part == null ? "" : part.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.equals(ourAcc) || trimmed.equals(ourShort)) continue;
                // Exclude Wanbo HDMI observer accessibility service which intercepts & drops all remote keys
                if (trimmed.contains("com.sdt.hdmi.MyAccessibilityService")
                        || trimmed.contains("com.sdt.projector.observer")) {
                    continue;
                }
                if (sb.length() > 0) sb.append(':');
                sb.append(trimmed);
            }
        }
        if (sb.length() > 0) sb.append(':');
        sb.append(ourAcc);
        return sb.toString();
    }

    /**
     * Lightweight, zero-allocation permission status check.
     * Returns true if any core permission (such as Accessibility for Button Mapper) is missing.
     */
    static boolean needsPermissionRepair(Context c) {
        if (c == null) return false;
        // 1. Accessibility Service (most commonly disabled by TV firmwares, or polluted by conflicting HDMI service)
        if (!ButtonMapper.isServiceEnabled(c) || PowerService.instance == null || hasConflictingAccessibilityService(c)) {
            return true;
        }
        // 2. Default Home Screen
        try {
            android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = c.getPackageManager().resolveActivity(
                    home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null || !c.getPackageName().equals(ri.activityInfo.packageName)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        // 3. Location permission (for Wi-Fi SSID & Weather)
        try {
            if (c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        // 4. Notification Listener (when supported on non-low-RAM devices)
        try {
            android.app.ActivityManager am = c.getSystemService(android.app.ActivityManager.class);
            if (am != null && !am.isLowRamDevice() && !MediaWatchService.granted(c)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static void checkAndRepairPermissionsIfNeeded(Context c, boolean forceNow) {
        if (c == null) return;
        final Context appCtx = c.getApplicationContext();
        if (!forceNow && !isKeepAliveEnabled(appCtx)) return;
        if (!forceNow && !isPaired(appCtx)) return;
        if (repairInProgress) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (!forceNow && lastRepairAttemptMs > 0L && (now - lastRepairAttemptMs) < REPAIR_COOLDOWN_MS) {
            return;
        }
        if (!forceNow && !needsPermissionRepair(appCtx)) {
            return;
        }
        repairInProgress = true;
        lastRepairAttemptMs = now;
        grantAllPermissions(appCtx, (success, msg) -> {
            repairInProgress = false;
        });
    }

    static void probePortAsync(Context c, Runnable onDone) {
        new Thread(() -> {
            String[] hosts = {"127.0.0.1", localLanIp()};
            boolean open = false;
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                if (isPortOpen(h, 5555, 500)) {
                    open = true;
                    break;
                }
            }
            portReachable = open;
            if (onDone != null) onDone.run();
        }, "adb-probe").start();
    }

    static String getStatusLabel(Context c) {
        boolean adbOn = isAdbSettingEnabled(c);
        boolean paired = isPaired(c);
        if (portReachable && paired) {
            return I18n.s(c, "Connected & Paired (Port 5555)", "Bağlı ve Eşleşti (Port 5555)");
        }
        if (portReachable) {
            return I18n.s(c, "Port 5555 Open · Ready to Pair", "Port 5555 Açık · Eşleşmeye Hazır");
        }
        if (adbOn && paired) {
            return I18n.s(c, "Paired · ADB Enabled", "Eşleşti · ADB Açık");
        }
        if (adbOn) {
            return I18n.s(c, "ADB Enabled · Not Paired", "ADB Açık · Eşleşmedi");
        }
        return I18n.s(c, "Disconnected (ADB Off)", "Bağlı Değil (ADB Kapalı)");
    }

    static boolean isAdbSettingEnabled(Context c) {
        try {
            if (Settings.Global.getInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1) {
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            if (Settings.Global.getInt(c.getContentResolver(), "adb_wifi_enabled", 0) == 1) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String localLanIp() {
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                    if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static final class GrantRequest {
        private boolean cancelled;
        private Socket socket;
        synchronized void attach(Socket value) throws java.io.IOException {
            if (cancelled) throw new java.io.IOException("ADB request cancelled");
            socket = value;
        }
        synchronized boolean isCancelled() { return cancelled; }
        synchronized void cancel() {
            cancelled = true;
            if (socket != null) try { socket.close(); } catch (Exception ignored) { }
        }
    }
    private static final ThreadLocal<GrantRequest> activeGrant = new ThreadLocal<>();
    private static final ThreadLocal<BatchProgressListener> grantProgress = new ThreadLocal<>();
    private static void grantStage(String stage) {
        BatchProgressListener listener = grantProgress.get();
        if (listener != null) listener.onStep(0, 0, stage, "");
    }

    static GrantRequest grantAllPermissions(Context c, Callback cb) {
        return grantAllPermissions(c, cb, null);
    }

    static GrantRequest grantAllPermissions(Context c, Callback cb, BatchProgressListener progress) {
        GrantRequest request = new GrantRequest();
        Context app = c.getApplicationContext();
        new Thread(() -> {
            activeGrant.set(request);
            if (progress != null) grantProgress.set(progress);
            java.util.Timer deadline = new java.util.Timer("adb-grant-deadline", true);
            deadline.schedule(new java.util.TimerTask() {
                @Override public void run() { request.cancel(); }
            }, 90000L);
            try {
                String pkg = app.getPackageName();
                android.app.ActivityManager am = app.getSystemService(android.app.ActivityManager.class);
                boolean listener = am != null && !am.isLowRamDevice();
                boolean admin = app.getPackageManager().hasSystemFeature("android.software.device_admin");
                List<AdbGrantPlan.Step> plan = AdbGrantPlan.create(pkg,
                        buildCleanAccessibilityServicesList(app, Settings.Secure.getString(
                                app.getContentResolver(), "enabled_accessibility_services")),
                        android.os.Build.VERSION.SDK_INT, listener, admin);
                List<String> commands = new ArrayList<>();
                for (AdbGrantPlan.Step step : plan) commands.add(step.command);
                String[] hosts = {"127.0.0.1", localLanIp()};
                Exception lastError = new java.io.IOException("Local ADB port 5555 is not available. Enable USB/network debugging on this TV.");
                for (String host : hosts) {
                    if (request.isCancelled()) throw new java.io.IOException("ADB setup timed out after 90 seconds");
                    if (host == null || host.isEmpty()) continue;
                    try {
                        grantStage("Connecting to " + host + ":5555");
                        List<String> outputs = runShellCommandsOnHost(app, host, 5555, commands,
                                (done, total, command, output) -> {
                                    AdbGrantPlan.Step step = plan.get(done - 1);
                                    if (progress != null) progress.onStep(done, total, step.label, output);
                                    if (step.required && !AdbGrantPlan.succeeded(output)) {
                                        throw new IllegalStateException(step.label + " failed: " + output);
                                    }
                                });
                        String missing = verifyCoreGrants(app);
                        if (!missing.isEmpty()) throw new java.io.IOException("Permission verification failed: " + missing);
                        portReachable = true;
                        setPaired(app, true);
                        StringBuilder warnings = new StringBuilder();
                        for (int i = 0; i < outputs.size(); i++) {
                            if (!plan.get(i).required && !AdbGrantPlan.succeeded(outputs.get(i))) {
                                warnings.append(plan.get(i).label).append("; ");
                            }
                        }
                        if (cb != null) cb.onResult(true, warnings.toString());
                        return;
                    } catch (Exception e) {
                        lastError = e;
                    }
                }
                throw lastError;
            } catch (Exception e) {
                if (cb != null) cb.onResult(false, e.getMessage());
            } finally {
                deadline.cancel();
                activeGrant.remove();
                grantProgress.remove();
            }
        }, "local-adb-grants").start();
        return request;
    }

    private static String verifyCoreGrants(Context c) {
        StringBuilder missing = new StringBuilder();
        if (c.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) missing.append("location; ");
        String services = Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
        android.content.ComponentName service = new android.content.ComponentName(c, PowerService.class);
        if (services == null || (!services.contains(service.flattenToString())
                && !services.contains(service.flattenToShortString()))
                || Settings.Secure.getInt(c.getContentResolver(), "accessibility_enabled", 0) != 1) {
            missing.append("accessibility; ");
        }
        android.app.AppOpsManager ops = c.getSystemService(android.app.AppOpsManager.class);
        if (ops == null || ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), c.getPackageName()) != android.app.AppOpsManager.MODE_ALLOWED) {
            missing.append("usage access; ");
        }
        android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo resolved = c.getPackageManager().resolveActivity(home,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null || resolved.activityInfo == null
                || !c.getPackageName().equals(resolved.activityInfo.packageName)) missing.append("default Home; ");
        return missing.toString();
    }

    /**
     * Streams `getevent -l 2>/dev/null` over the already-paired local ADB socket (without prompting)
     * so long-press volume up/down works even on TVs where the system consumes volume keys before
     * AccessibilityService.onKeyEvent. Matches tvOS 2.0.4 (E5.smali + I.smali).
     */
    static boolean watchVolumeKeys(Context c, VolumeKeyListener listener, BooleanSupplier shouldRun) {
        String[] hosts = {"127.0.0.1", localLanIp()};
        for (String host : hosts) {
            if (host == null || host.isEmpty()) continue;
            if (!isPortOpen(host, 5555, 500)) continue;
            try {
                KeyPair kp = getOrCreateKeyPair(c);
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, 5555), 2500);
                    socket.setSoTimeout(3000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    OutputStream out = socket.getOutputStream();

                    byte[] banner = "host::\0".getBytes(StandardCharsets.UTF_8);
                    sendPacket(out, A_CNXN, A_VERSION, MAX_PAYLOAD, banner);

                    Packet p = readPacket(in);
                    if (p.command == A_AUTH && p.arg0 == ADB_AUTH_TOKEN) {
                        byte[] sig = signToken((RSAPrivateKey) kp.getPrivate(), p.data);
                        sendPacket(out, A_AUTH, ADB_AUTH_SIGNATURE, 0, sig);
                        p = readPacket(in);
                    }
                    if (p.command != A_CNXN) {
                        continue;
                    }
                    setPaired(c, true);

                    socket.setSoTimeout(0);
                    int localId = 1;
                    byte[] openData = "shell:getevent -l 2>/dev/null\0".getBytes(StandardCharsets.UTF_8);
                    sendPacket(out, A_OPEN, localId, 0, openData);

                    StringBuilder lineBuf = new StringBuilder();
                    while (shouldRun.getAsBoolean()) {
                        Packet pkt = readPacket(in);
                        if (pkt.command == A_WRTE) {
                            sendPacket(out, A_OKAY, localId, pkt.arg0, null);
                            if (pkt.data != null && pkt.data.length > 0) {
                                lineBuf.append(new String(pkt.data, StandardCharsets.UTF_8));
                                int nl;
                                while ((nl = lineBuf.indexOf("\n")) >= 0) {
                                    String line = lineBuf.substring(0, nl).trim();
                                    lineBuf.delete(0, nl + 1);
                                    if (!line.contains("EV_KEY")) continue;
                                    String[] parts = line.split("\\s+");
                                    for (int i = 0; i + 2 < parts.length; i++) {
                                        if ("EV_KEY".equals(parts[i])) {
                                            String key = parts[i + 1];
                                            String state = parts[i + 2];
                                            int dir = "KEY_VOLUMEUP".equals(key) ? 1
                                                    : ("KEY_VOLUMEDOWN".equals(key) ? -1 : 0);
                                            if (dir != 0 && ("DOWN".equals(state) || "UP".equals(state))) {
                                                listener.onVolumeKey(dir, "DOWN".equals(state));
                                            }
                                            break;
                                        }
                                    }
                                }
                                if (lineBuf.length() > 4096) {
                                    lineBuf.setLength(0);
                                }
                            }
                        } else if (pkt.command == A_CLSE) {
                            sendPacket(out, A_CLSE, localId, pkt.arg0, null);
                            break;
                        }
                    }
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    static void forceStopPackagesAsync(Context c, java.util.List<String> pkgs) {
        if (pkgs == null || pkgs.isEmpty() || !isPaired(c)) return;
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            for (String p : pkgs) {
                if (p == null || p.isEmpty() || p.equals(c.getPackageName())) continue;
                if (sb.length() > 0) sb.append("; ");
                sb.append("am force-stop ").append(p);
            }
            if (sb.length() == 0) return;
            String[] hosts = {"127.0.0.1", localLanIp()};
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                if (!isPortOpen(h, 5555, 400)) continue;
                try {
                    runShellCommand(c, h, 5555, sb.toString());
                    return;
                } catch (Exception ignored) {
                }
            }
        }, "local-adb-forcestop").start();
    }

    static boolean startActivityViaAdbAsync(Context c, String pkg, String cls) {
        if (pkg == null || pkg.isEmpty() || !isPaired(c)) return false;
        new Thread(() -> {
            String cmd = (cls != null && !cls.isEmpty())
                    ? ("am start -n " + pkg + "/" + cls)
                    : ("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
            String[] hosts = {"127.0.0.1", localLanIp()};
            for (String h : hosts) {
                if (h == null || h.isEmpty()) continue;
                if (!isPortOpen(h, 5555, 400)) continue;
                try {
                    runShellCommand(c, h, 5555, cmd);
                    return;
                } catch (Exception ignored) {
                }
            }
        }, "local-adb-start").start();
        return true;
    }

    interface BatchProgressListener {
        void onStep(int index, int total, String cmd, String output);
    }

    static String execShellOutput(Context c, String shellCmd) throws Exception {
        String[] hosts = {"127.0.0.1", localLanIp()};
        Exception lastErr = null;
        for (String h : hosts) {
            if (h == null || h.isEmpty()) continue;
            if (!isPortOpen(h, 5555, 600)) continue;
            try {
                String out = runShellCommandOutput(c, h, 5555, shellCmd);
                portReachable = true;
                setPaired(c, true);
                return out;
            } catch (Exception e) {
                lastErr = e;
            }
        }
        if (lastErr != null) throw lastErr;
        throw new Exception("PORT_CLOSED");
    }

    /**
     * Runs each shell command in its own separate `shell:` stream over a single authenticated ADB socket
     * (matching CleanYourTV's rule: "Run pm commands one per shell call, chained commands may silently stop after the first").
     */
    static boolean execShellSequential(Context c, List<String> commands, BatchProgressListener progress) {
        String[] hosts = {"127.0.0.1", localLanIp()};
        for (String h : hosts) {
            if (h == null || h.isEmpty()) continue;
            if (!isPortOpen(h, 5555, 600)) continue;
            try {
                runShellCommandsOnHost(c, h, 5555, commands, progress);
                portReachable = true;
                setPaired(c, true);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static void runShellCommand(Context c, String host, int port, String shellCmd) throws Exception {
        runShellCommandOutput(c, host, port, shellCmd);
    }

    private static String runShellCommandOutput(Context c, String host, int port, String shellCmd) throws Exception {
        List<String> res = runShellCommandsOnHost(c, host, port, java.util.Collections.singletonList(shellCmd), null);
        return res.isEmpty() ? "" : res.get(0);
    }

    private static List<String> runShellCommandsOnHost(
            Context c, String host, int port, List<String> commands, BatchProgressListener progress) throws Exception {
        grantStage("Loading ADB identity");
        KeyPair kp = getOrCreateKeyPair(c);
        try (Socket socket = new Socket()) {
            GrantRequest request = activeGrant.get();
            if (request != null) request.attach(socket);
            socket.connect(new InetSocketAddress(host, port), 2500);
            socket.setSoTimeout(35000); // Allow up to 35s for user to tap "Allow USB debugging" on TV
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            AdbAuth.connect(new AdbAuth.Transport() {
                @Override public void send(int command, int arg0, int arg1, byte[] data) throws Exception {
                    sendPacket(out, command, arg0, arg1, data);
                }
                @Override public AdbAuth.Message receive() throws Exception {
                    Packet packet = readPacket(in);
                    return new AdbAuth.Message(packet.command, packet.arg0, packet.data);
                }
            }, kp, isPaired(c), LocalAdb::grantStage);

            socket.setSoTimeout(15000);
            List<String> outputs = new java.util.ArrayList<>(commands.size());
            int total = commands.size();
            for (int i = 0; i < total; i++) {
                String shellCmd = commands.get(i);
                int localId = i + 1;
                byte[] openData = ("shell:" + shellCmd + "\0").getBytes(StandardCharsets.UTF_8);
                sendPacket(out, A_OPEN, localId, 0, openData);

                StringBuilder sb = new StringBuilder();
                while (true) {
                    Packet p = readPacket(in);
                    if (p.arg1 != localId) continue;
                    if (p.command == A_WRTE) {
                        sendPacket(out, A_OKAY, localId, p.arg0, null);
                        if (p.data != null && p.data.length > 0) {
                            sb.append(new String(p.data, StandardCharsets.UTF_8));
                        }
                    } else if (p.command == A_CLSE) {
                        sendPacket(out, A_CLSE, localId, p.arg0, null);
                        break;
                    }
                }
                String outStr = sb.toString();
                outputs.add(outStr);
                if (progress != null) {
                    progress.onStep(i + 1, total, shellCmd, outStr);
                }
            }
            return outputs;
        }
    }

    private static final class Packet {
        int command;
        int arg0;
        int arg1;
        byte[] data;
    }

    private static void sendPacket(OutputStream out, int command, int arg0, int arg1, byte[] data) throws Exception {
        int len = data == null ? 0 : data.length;
        int sum = 0;
        if (data != null) {
            for (byte b : data) {
                sum += (b & 0xFF);
            }
        }
        ByteBuffer hdr = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        hdr.putInt(command);
        hdr.putInt(arg0);
        hdr.putInt(arg1);
        hdr.putInt(len);
        hdr.putInt(sum);
        hdr.putInt(~command);
        out.write(hdr.array());
        if (len > 0) {
            out.write(data);
        }
        out.flush();
    }

    private static Packet readPacket(DataInputStream in) throws Exception {
        byte[] hdrBytes = new byte[24];
        in.readFully(hdrBytes);
        ByteBuffer hdr = ByteBuffer.wrap(hdrBytes).order(ByteOrder.LITTLE_ENDIAN);
        Packet p = new Packet();
        p.command = hdr.getInt();
        p.arg0 = hdr.getInt();
        p.arg1 = hdr.getInt();
        int len = hdr.getInt();
        hdr.getInt(); // checksum
        hdr.getInt(); // magic
        if (len < 0 || len > 16 * 1024 * 1024) throw new java.io.IOException("Invalid ADB packet length");
        if (len > 0) {
            p.data = new byte[len];
            in.readFully(p.data);
        } else {
            p.data = new byte[0];
        }
        return p;
    }

    private static byte[] signToken(RSAPrivateKey key, byte[] token) throws Exception {
        return AdbAuth.sign(key, token);
    }

    private static synchronized KeyPair getOrCreateKeyPair(Context c) throws Exception {
        File privFile = new File(c.getFilesDir(), "adb_rsa.priv");
        File pubFile = new File(c.getFilesDir(), "adb_rsa.pub");
        if (privFile.exists() && pubFile.exists()) {
            try {
                byte[] privBytes = readAll(privFile);
                byte[] pubBytes = readAll(pubFile);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                KeyPair saved = new KeyPair(
                        kf.generatePublic(new X509EncodedKeySpec(pubBytes)),
                        kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes)));
                RSAPublicKey pub = (RSAPublicKey) saved.getPublic();
                RSAPrivateKey priv = (RSAPrivateKey) saved.getPrivate();
                if (pub.getModulus().equals(priv.getModulus()) && pub.getModulus().bitLength() == 2048
                        && pub.getPublicExponent().intValue() == 65537) return saved;
                setPaired(c, false);
            } catch (Exception ignored) {
            }
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        try (FileOutputStream fos = new FileOutputStream(privFile)) {
            fos.write(kp.getPrivate().getEncoded());
        }
        try (FileOutputStream fos = new FileOutputStream(pubFile)) {
            fos.write(kp.getPublic().getEncoded());
        }
        return kp;
    }

    private static byte[] readAll(File f) throws Exception {
        byte[] b = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int off = 0;
            while (off < b.length) {
                int r = fis.read(b, off, b.length - off);
                if (r <= 0) break;
                off += r;
            }
        }
        return b;
    }
}
