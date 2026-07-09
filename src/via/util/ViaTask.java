/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package via.util;

import com.viaversion.viaversion.api.platform.PlatformTask;
import com.viaversion.viaversion.api.scheduler.Task;
import com.viaversion.viaversion.api.scheduler.TaskStatus;

@SuppressWarnings({"unused", "RedundantSuppression"})
public class ViaTask implements PlatformTask<Task> {
    private final Task object;

    public ViaTask(Task object) {
        this.object = object;
    }

    public Task getObject() {
        return this.object;
    }

    public void cancel() {
        this.object.cancel();
    }

    public TaskStatus getStatus() {
        return this.getObject().status();
    }
}

