package hudson.plugins.adhoc;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import jenkins.model.Jenkins;

import java.io.InputStream;
import java.nio.charset.Charset;
import org.apache.commons.io.IOUtils;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import org.kohsuke.args4j.Argument;

@Extension
public class AdhocCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return "Run an adhoc pipeline on Jenkins";
    }

    // TODO arguments
    @Argument(metaVar = "NAME", usage = "Name of person to greet")
    public String name;

    @Override
    protected int run() throws Exception {
        // TODO jenkins.get().checkpermission?

        String script = IOUtils.toString(stdin, Charset.defaultCharset());

        WorkflowJob job = new WorkflowJob(Jenkins.get(), "temp-job");
        job.setDefinition(new CpsFlowDefinition(script, true));

        QueueTaskFuture<WorkflowRun> qtf = job.scheduleBuild2(0);
        if (qtf == null) {
            this.stderr.println("Failed to schedule");
            return 1;
        }
        WorkflowRun run = qtf.waitForStart();
        run.writeWholeLogTo(stdout);
        stdout.flush();

        Result result = run.getResult();
        if (result == null) {
            this.stderr.println("Result is null (build ongoing?)");
            return 1;
        }
        if (result.isCompleteBuild()) {
            this.stdout.println("Success");
        } else {
            this.stdout.println("error: " + result);
        }

        job.delete();

        return 0;
    }
}
