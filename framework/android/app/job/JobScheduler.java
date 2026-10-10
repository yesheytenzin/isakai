// SPDX-License-Identifier: GPL-3.0-or-later
package android.app.job;

import java.util.Collections;
import java.util.List;

/** Background job scheduling; no-op shell. */
public class JobScheduler {
    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_FAILURE = 0;
    public int schedule(Object job) { return RESULT_FAILURE; }
    public void cancel(int jobId) { }
    public void cancelAll() { }
    public List getAllPendingJobs() { return Collections.emptyList(); }
    public Object getPendingJob(int jobId) { return null; }
}
