package com.techgenius.tvoslauncher;

import java.io.IOException;
import java.net.Socket;

/** Cancellation must close blocked ADB reads and prevent connecting to a fallback host. */
public final class AdbGrantRequestTest {
    public static void main(String[] args) throws Exception {
        LocalAdb.GrantRequest request = new LocalAdb.GrantRequest();
        try (Socket socket = new Socket()) {
            request.attach(socket);
            request.cancel();
            if (!socket.isClosed()) throw new AssertionError("Active ADB socket was not closed");
            if (!request.isCancelled()) throw new AssertionError("Cancellation was not retained");
            request.cancel(); // Repeated Back / timeout / destruction must be harmless.
        }
        try (Socket fallback = new Socket()) {
            try {
                request.attach(fallback);
                throw new AssertionError("Cancelled request accepted another connection");
            } catch (IOException expected) {
                // A retry host must not continue granting permissions after cancellation.
            }
        }
        System.out.println("ADB cancellation checks passed");
    }
}
