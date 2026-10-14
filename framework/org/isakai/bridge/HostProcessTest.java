// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.bridge;

import java.io.IOException;

/**
 * Behavioral test for the supervised host-process runner. No ffmpeg
 * needed: /bin/sh stands in for a decoder.
 *
 * The regression this locks: the previous shape drained stdout to EOF
 * before waiting, so a child that never wrote could not be timed out.
 */
public final class HostProcessTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        // 1. A process that never writes must be killed at the deadline.
        long start = System.nanoTime();
        try {
            IsakaiMedia.run(new ProcessBuilder("/bin/sh", "-c", "sleep 30"), 4096, 4096, 300);
            throw new AssertionError("a process that never writes was not timed out");
        } catch (IOException expected) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            check(ms < 10_000, "timeout took " + ms + " ms; the deadline is not enforced");
            check(expected.getMessage().contains("timed out"), "unexpected message: " + expected.getMessage());
        }
        // 2. Normal capture: stdout and stderr are separated.
        IsakaiMedia.Proc ok = IsakaiMedia.run(
                new ProcessBuilder("/bin/sh", "-c", "printf out; printf err 1>&2"), 4096, 4096, 5000);
        check(ok.exit == 0, "exit " + ok.exit);
        check(new String(ok.out).equals("out"), "stdout: " + new String(ok.out));
        check(new String(ok.err).equals("err"), "stderr: " + new String(ok.err));
        // 3. stderr keeps the tail: the useful ffmpeg text is last.
        IsakaiMedia.Proc tail = IsakaiMedia.run(
                new ProcessBuilder("/bin/sh", "-c", "for i in 1 2 3 4 5; do echo line-$i 1>&2; done"), 4096, 12, 5000);
        check(new String(tail.err).endsWith("line-5\n"), "stderr tail: " + new String(tail.err));
        check(tail.err.length <= 12, "stderr cap: " + tail.err.length);
        // 4. Nonlinear exit codes surface with their stderr text.
        IsakaiMedia.Proc bad = IsakaiMedia.run(
                new ProcessBuilder("/bin/sh", "-c", "printf boom 1>&2; exit 3"), 4096, 4096, 5000);
        check(bad.exit == 3, "exit " + bad.exit);
        check(bad.errorText("gone").equals("boom"), "error text: " + bad.errorText("gone"));
        // 5. stdout overflow is an error, not silent truncation.
        try {
            IsakaiMedia.run(new ProcessBuilder("/bin/sh", "-c", "yes isakai | head -c 200000"), 4096, 4096, 10_000);
            throw new AssertionError("stdout overflow was accepted");
        } catch (IOException expected) {
            check(expected.getMessage().contains("exceeded"), "unexpected message: " + expected.getMessage());
        }
        // 6. A child waiting on stdin gets EOF immediately through our own
        //    stdin close, so it cannot hang holding the deadline.
        IsakaiMedia.Proc eof = IsakaiMedia.run(
                new ProcessBuilder("/bin/sh", "-c", "cat; printf done"), 4096, 4096, 5000);
        check(eof.exit == 0 && new String(eof.out).equals("done"), "stdin close: " + new String(eof.out));
        System.out.println("PASS: host process supervision (deadline under no output, separated pipes,"
                + " stderr tail cap, exit text, stdout overflow kill, stdin EOF)");
    }
}
