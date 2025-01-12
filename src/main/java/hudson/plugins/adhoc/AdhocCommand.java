package hudson.plugins.adhoc;

import hudson.Extension;
import hudson.Functions;
import hudson.cli.CLICommand;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.IOUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.io.FileUtils;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.apache.tools.tar.TarOutputStream;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.args4j.Option;

@Extension
public class AdhocCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return "Run an adhoc pipeline on Jenkins";
    }

    @Option(name = "-jenkinsfile", usage = "The alternative Jenkinsfile name/path")
    private String jenkinsfile = "Jenkinsfile";

    @Option(name = "-pre", usage = "Prepend the output tar file with job name")
    private boolean prepend = false;

    @Option(name = "-uid", usage = "UID for artifact files")
    private int tar_uid;

    @Option(name = "-gid", usage = "GID for artifact files")
    private int tar_gid;

    @Override
    protected int run() throws Exception, SecurityException {
        // TODO jenkins.get().checkpermission?

        Jenkins jenkins = Jenkins.get();

        // prepare temporary directory
        File tmpdir_root = new File(jenkins.getRootDir(), "temp");
        if (!tmpdir_root.isDirectory()) {
            if (!tmpdir_root.mkdirs()) {
                // fuck you `RV_RETURN_VALUE_IGNORED_BAD_PRACTICE`
                ;
            }
        }
        File tmpdir_job =
                Files.createTempDirectory(tmpdir_root.toPath(), "adhoc-").toFile();

        int ret = 0;

        try {
            ret = inner_with_tempdir(tmpdir_job);
        } finally {
            FileUtils.deleteDirectory(tmpdir_job);
        }

        return ret;
    }

    private int inner_with_tempdir(File tmpdir_job) throws Exception {
        // 1. read tar workspace from stdin (and 1.1)
        AdhocCommand.readFromTar(tmpdir_job, stdin);

        // 2. create random job
        WorkflowJob job = new WorkflowJob(Jenkins.get(), tmpdir_job.getName());
        // 2.1
        AdhocSCM scm = new AdhocSCM(tmpdir_job.getName());
        job.setDefinition(new CpsScmFlowDefinition(scm, jenkinsfile));

        int ret = 0;
        try {
            ret = inner_with_job(job);
        } finally {
            job.delete();
        }

        // PersistedList

        return ret;
    }

    private int inner_with_job(WorkflowJob job) throws Exception {
        // schedule/start run
        QueueTaskFuture<WorkflowRun> qtf = job.scheduleBuild2(0);
        if (qtf == null) {
            this.stderr.println("Failed to schedule");
            return 1;
        }
        // 3. stream build logs to `stderr`
        WorkflowRun run = qtf.waitForStart();

        // sometimes the log file hasn't been created at time of reading
        while (true) {
            try {
                run.writeWholeLogTo(stderr);
                stderr.flush();
                break;
            } catch (FileNotFoundException e) {
                Thread.yield();
            }
        }

        Result result = run.getResult();
        if (result == null) {
            this.stderr.println("Result is null (build ongoing?)");
            return 1;
        }
        int ret = result == Result.SUCCESS ? 0 : 1;

        // 4. tar build artifacts to stdout
        writeArtifacts(run);

        // clean up
        run.deleteArtifacts();
        run.delete();

        return ret;
    }

    private void writeArtifacts(WorkflowRun run) throws IOException {
        VirtualFile root = run.getArtifactManager().root();
        String prefix = prepend ? (run.getParent().getName() + "/") : "";
        try (TarOutputStream t = new TarOutputStream(stdout)) {
            for (Run<WorkflowJob, WorkflowRun>.Artifact artifact : run.getArtifacts()) {
                VirtualFile artifact_file = root.child(artifact.relativePath);
                TarEntry te = new TarEntry(prefix + artifact.relativePath);
                String link_target = artifact_file.readLink();
                te.setModTime(artifact_file.lastModified());
                te.setIds(tar_uid, tar_gid);
                if (link_target != null) {
                    te.setLinkFlag(TarConstants.LF_SYMLINK);
                    te.setLinkName(link_target);
                } else {
                    te.setMode(artifact_file.mode());
                    te.setSize(artifact.getFileSize());
                }
                t.putNextEntry(te);
                if (link_target == null) {
                    try (InputStream is = artifact_file.open()) {
                        is.transferTo(t);
                    }
                }
                t.closeEntry();
            }
        }
    }

    private static void readFromTar(File base_dir, InputStream in) throws IOException {
        try (TarInputStream t = new TarInputStream(in)) {
            TarEntry te;
            while ((te = t.getNextEntry()) != null) {
                File f = new File(base_dir, te.getName());
                if (!f.toPath().normalize().startsWith(base_dir.toPath())) {
                    throw new IOException("illegal file name in workspace tar");
                }
                if (te.isDirectory()) {
                    if (!f.mkdirs())
                        ; // what?
                } else {
                    File parent = f.getParentFile();
                    if (parent != null)
                        if (!parent.mkdirs())
                            ; // again?

                    if (te.isSymbolicLink()) {
                        Files.createSymbolicLink(f.toPath(), new File(te.getLinkName()).toPath());
                    } else {
                        IOUtils.copy(t, f);

                        Files.setLastModifiedTime(
                                f.toPath(), FileTime.from(te.getModTime().toInstant()));
                        int mode = te.getMode() & 0777;
                        if (mode != 0 && !Functions.isWindows()) {
                            // copied from Util.modeToPermissions
                            Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
                            PosixFilePermission[] available = PosixFilePermission.values();
                            for (int i = 0; i < available.length; i++) {
                                if ((mode & 1) == 1) permissions.add(available[available.length - i - 1]);
                                mode >>= 1;
                            }
                            Files.setPosixFilePermissions(f.toPath(), permissions);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IOException("Failed to extract", e);
        }
    }
}
