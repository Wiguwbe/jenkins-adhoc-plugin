package hudson.plugins.adhoc;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.cli.CLICommand;
import jenkins.model.Jenkins;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;
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

        this.stdout.println("Hello " + this.name);

        String script = IOUtils.toString(stdin, Charset.defaultCharset());

        Binding binding = new Binding();
        PrintWriter outPW = new PrintWriter(new OutputStreamWriter(stdout, getClientCharset()), true);
        binding.setProperty("out", outPW);
        binding.setProperty("stdin", stdin);
        binding.setProperty("stdout", stdout);
        binding.setProperty("stderr", stderr);

        GroovyShell shell = new GroovyShell(Jenkins.get().getPluginManager().uberClassLoader, binding);
        GroovySandbox sandbox = new GroovySandbox();
        sandbox.runScript(shell, script);

        outPW.flush();

        return 0;
    }
}
