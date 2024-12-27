package hudson.plugins.adhoc;

import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.queue.SubTask;

public class AdhocTask implements Queue.Task {

    private final Label label;
    private final String displayName;
    private final Runnable runnable;

    public AdhocTask(Label label, Runnable runnable, String displayName) {
        this.label = label;
        this.displayName = displayName;
        this.runnable = runnable;
    }

    public String getFullDisplayName() {
        return this.displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getName() {
        return this.displayName;
    }

    public String getUrl() {
        return null;
    }

    public Queue.Executable createExecutable() {
        return new Queue.Executable() {
            public SubTask getParent() {
                return AdhocTask.this;
            }

            public void run() {
                runnable.run();
            }

            public long getEstimatedDuration() {
                // arbitrary value
                return 1000;
            }

            @Override
            public String toString() {
                return displayName;
            }
        };
    }
}
