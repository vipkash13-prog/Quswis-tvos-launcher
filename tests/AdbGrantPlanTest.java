package com.techgenius.tvoslauncher;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class AdbGrantPlanTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static String execute(String command) throws Exception {
        Process process = new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return output;
    }
    public static void main(String[] args) throws Exception {
        List<AdbGrantPlan.Step> older = AdbGrantPlan.create("com.example.tv", "com.example.tv/.PowerService", 28, false, false);
        List<AdbGrantPlan.Step> modern = AdbGrantPlan.create("com.example.tv", "com.example.tv/.PowerService", 34, true, true);
        check(older.get(0).command.contains("ACCESS_COARSE_LOCATION"), "Coarse must precede fine on Android 12+");
        check(older.get(1).command.contains("ACCESS_FINE_LOCATION"), "Fine location missing");
        check(older.stream().noneMatch(s -> s.command.contains("READ_MEDIA_IMAGES") || s.command.contains("MANAGE_EXTERNAL_STORAGE")), "Unsupported permissions sent to old TV");
        check(modern.stream().noneMatch(s -> s.command.contains("READ_EXTERNAL_STORAGE") || s.command.contains("WRITE_EXTERNAL_STORAGE")), "Removed permissions sent to Android 13+");
        check(older.stream().noneMatch(s -> s.label.equals("Device admin") || s.label.equals("Continue Watching")), "Unsupported TV services not skipped");
        check(modern.stream().anyMatch(s -> s.command.contains("'.SleepReceiver$Admin'"))
                || modern.stream().anyMatch(s -> s.command.contains("/.SleepReceiver$Admin'")), "Admin component must be shell quoted");
        check(AdbGrantPlan.succeeded(execute(new AdbGrantPlan.Step("success", "true", true).command)), "Successful shell command was rejected");
        check(!AdbGrantPlan.succeeded(execute(new AdbGrantPlan.Step("failure", "false", true).command)), "Shell failure was accepted as success");
        check(!AdbGrantPlan.succeeded("Permission denied"), "Unchecked shell output was accepted");
        check(!AdbGrantPlan.succeeded("__TVOS_EXIT__:0\n__TVOS_EXIT__:1\n"), "Last exit status was ignored");
        check("a'b".equals(execute("printf %s " + AdbGrantPlan.quote("a'b"))), "Shell quoting changed a value");
        System.out.println("ADB grant plan and shell exit checks passed");
    }
}
