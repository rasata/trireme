package com.apigee.noderunner.shell;

import com.apigee.noderunner.container.netty.NettyHttpContainer;
import com.apigee.noderunner.core.NodeEnvironment;
import com.apigee.noderunner.core.NodeException;
import com.apigee.noderunner.core.NodeScript;
import com.apigee.noderunner.core.ScriptStatus;
import com.apigee.noderunner.core.internal.Utils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.RhinoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * This is the "main," which runs the script.
 */
public class Main
{
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final String RUN_REPL =
        "var repl = require('repl');\n" +
        "var opts = {};\n" +
        "var sr = repl.start(opts);\n" +
        "sr.on('exit', function() {\n" +
        "process.exit();\n" +
        "});";

    public static final String ADAPTER_PROP = "HttpAdapter";
    public static final String OPT_PROP = "OptLevel";
    public static final String SEAL_PROP = "SealRoot";

    private String scriptSource;
    private String scriptFile;
    private boolean runRepl;
    private String[] scriptArgs;
    private String scriptName;

    private static void printUsage()
    {
        System.err.println("Usage: " + Main.class.getName() + " [options] [ -e script | script.js ] [arguments]");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  -e, --eval script    Evaluate script");
        System.err.println("  -i, --interactive    Enter the REPL even if stdin doesn't appear to be a terminal");
    }

    public static void main(String[] args)
    {
        Main m = new Main();
        if (!m.parseArgs(args)) {
            System.exit(1);
        } else {
            int ec = m.run();
            System.exit(ec);
        }
    }

    private boolean argsComplete()
    {
        return (runRepl || (scriptSource != null) || (scriptFile != null));
    }

    private boolean parseArgs(String[] args)
    {
        int i = 0;
        boolean wasEval = false;
        while ((i < args.length) && !argsComplete()) {
            if ("-h".equals(args[i]) || "--help".equals(args[i])) {
                printUsage();
                return false;
            } else if (wasEval) {
                wasEval = false;
                scriptSource = args[i];
                scriptName = "[eval]";
            } else if ("-i".equals(args[i]) || "--interactive".equals(args[i])) {
                runRepl = true;
                scriptName = "[repl]";
            } else if ("-e".equals(args[i]) || "--eval".equals(args[i])) {
                wasEval = true;
            } else {
                scriptFile = args[i];
            }
            i++;
        }

        if (i < args.length) {
            scriptArgs = new String[args.length - i];
            System.arraycopy(args, i, scriptArgs, 0, scriptArgs.length);
        }
        return true;
    }

    private int run()
    {
        String containerName = System.getProperty(ADAPTER_PROP);

        NodeEnvironment env = new NodeEnvironment();
        String opt = System.getProperty(SEAL_PROP);
        if (opt != null) {
            env.setSealRoot(Boolean.valueOf(opt));
        }
        opt = System.getProperty(OPT_PROP);
        if (opt != null) {
            env.setOptLevel(Integer.parseInt(opt));
        }

        try {
            if ((containerName != null) && "netty".equals(containerName)) {
                env.setHttpContainer(new NettyHttpContainer());
            }

            NodeScript ns;
            if (scriptFile != null) {
                File sf = new File(scriptFile);
                scriptName = sf.getName();
                ns = env.createScript(scriptName, sf, scriptArgs);
            } else if (scriptSource != null) {
                ns = env.createScript(scriptName, scriptSource, scriptArgs);
            } else if (runRepl || (System.console() != null)) {
                ns = env.createScript("[repl]", RUN_REPL, null);
                ns.setPinned(true);
            } else {
                scriptSource = Utils.readStream(System.in);
                scriptName = "[stdin]";
                ns = env.createScript(scriptName, scriptSource, null);
            }

            ScriptStatus status;
            try {
                Future<ScriptStatus> future = ns.execute();
                status = future.get();
            } finally {
                ns.close();
            }

            if (status.hasCause()) {
                printException(status.getCause());
            }

            return status.getExitCode();
        } catch (NodeException ne) {
            ne.printStackTrace(System.err);
            return 99;
        } catch (IOException ioe) {
            ioe.printStackTrace(System.err);
            return 99;
        } catch (InterruptedException ie) {
            return 99;
        } catch (ExecutionException ee) {
            printException(ee.getCause());
            return 99;
        } finally {
            env.close();
        }
    }

    private static void printException(Throwable ee)
    {
        if (ee instanceof JavaScriptException) {
            Object value = ((JavaScriptException)ee).getValue();
            Context.enter();
            System.err.println(Context.toString(value));
            Context.exit();
        }
        if (ee instanceof RhinoException) {
            RhinoException re = (RhinoException)ee;
            System.err.println(re.details());
            System.err.println(re.getScriptStackTrace());
        } else {
            System.err.println(ee.getMessage());
            ee.printStackTrace(System.err);
        }
    }
}
