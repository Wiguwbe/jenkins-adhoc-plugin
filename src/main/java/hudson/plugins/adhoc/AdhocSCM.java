package hudson.plugins.adhoc;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.NullChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.jenkinsci.remoting.RoleChecker;

public class AdhocSCM extends SCM {

    private String dirname;

    public AdhocSCM(String dirname) {
        this.dirname = dirname;
    }

    @Override
    public String getType() {
        return "AdhocSCM";
    }

    @Override
    public void checkout(
            Run<?, ?> build,
            Launcher launcher,
            FilePath workspace,
            TaskListener listener,
            File changelogFile,
            SCMRevisionState baseline)
            throws IOException, InterruptedException {
        CheckoutTask task = new CheckoutTask();
        workspace.act(task);
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(
            Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        return SCMRevisionState.NONE;
    }

    public class CheckoutTask implements FileCallable<Boolean> {
        @Override
        public Boolean invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            FilePath workspace = new FilePath(f);
            FilePath root = Jenkins.get().getRootPath().child("temp").child(dirname);
            root.copyRecursiveTo(workspace);
            return true;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // all good
            ;
        }
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new NullChangeLogParser();
    }
}
