package org.jenkinsci.plugins.jvcts.perform;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;
import static org.jenkinsci.plugins.jvcts.JvctsLogger.doLog;
import static org.jenkinsci.plugins.jvcts.config.ViolationsToBitbucketServerConfigHelper.FIELD_BITBUCKET_SERVER_BASE_URL;
import static org.jenkinsci.plugins.jvcts.config.ViolationsToBitbucketServerConfigHelper.FIELD_BITBUCKET_SERVER_PROJECT;
import static org.jenkinsci.plugins.jvcts.config.ViolationsToBitbucketServerConfigHelper.FIELD_BITBUCKET_SERVER_PULL_REQUEST_ID;
import static org.jenkinsci.plugins.jvcts.config.ViolationsToBitbucketServerConfigHelper.FIELD_BITBUCKET_SERVER_REPO;
import static org.jenkinsci.plugins.jvcts.config.ViolationsToBitbucketServerConfigHelper.FIELD_BITBUCKET_SERVER_USER;
import static org.jenkinsci.plugins.jvcts.config.ViolationsToBitbucketServerConfigHelper.FIELD_COMMIT_HASH;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import hudson.EnvVars;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.plugins.jvcts.JvctsLogger;
import org.jenkinsci.plugins.jvcts.bitbucketserver.JvctsBitbucketServerClient;
import org.jenkinsci.plugins.jvcts.config.ParserConfig;
import org.jenkinsci.plugins.jvcts.config.ViolationsToBitbucketServerConfig;


public class JvctsPerformer {

    public static void jvctsPerform(ViolationsToBitbucketServerConfig config, AbstractBuild<?, ?> build,
            BuildListener listener) {
        try {
            EnvVars env = build.getEnvironment(listener);
            config = expand(config, env);
            listener.getLogger().println("---");
            listener.getLogger().println("--- Jenkins Violation Comments to Bitbucket Server ---");
            listener.getLogger().println("---");
            logConfiguration(config, build, listener);

            listener.getLogger().println("Running Jenkins Violation Comments To Bitbucket Server");
            listener.getLogger().println("Will comment " + config.getBitbucketServerPullRequestId());

            File workspace = new File(build.getExecutor().getCurrentWorkspace().toURI());
            doLog(FINE, "Workspace: " + workspace.getAbsolutePath());
            doPerform(config, build, listener);
        } catch (Exception e) {
            doLog(SEVERE, "", e);
            return;
        }
    }

    @VisibleForTesting
    public static void doPerform(final ViolationsToBitbucketServerConfig config, AbstractBuild<?, ?> build, final BuildListener listener)
            throws IOException, InterruptedException {

        Map<String, List<ViolationSerial>> violations = build.getWorkspace().act(new FileCallable<Map<String, List<ViolationSerial>>>() {

            private static final long serialVersionUID = 1L;

            @Override
            public Map<String, List<ViolationSerial>> invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                return new FullBuildModelWrapper(config, f, listener).getViolationsPerFile();
            }
        });

        commentBitbucketServer(violations, config, listener);
    }

    private static void commentBitbucketServer(Map<String, List<ViolationSerial>> violationsPerFile,
            ViolationsToBitbucketServerConfig config, BuildListener listener) throws MalformedURLException {
        JvctsBitbucketServerClient jvctsBitbucketServerClient = new JvctsBitbucketServerClient(config, listener);
        if (!isNullOrEmpty(config.getBitbucketServerPullRequestId())) {
            doLog(FINE, "Removing Jenkins comments for pull request ' " + config.getBitbucketServerPullRequestId() + "'");
            jvctsBitbucketServerClient.removeCommentsFromPullRequest();

            doLog(FINE, "Commenting pull request \"" + config.getBitbucketServerPullRequestId() + "\"");
            for (String changedFileInBitbucketServer : jvctsBitbucketServerClient.getChangedFileInPullRequest()) {
                doLog(FINE, "Changed file in pull request: \"" + changedFileInBitbucketServer + "\"");
                // jvctsBitbucketServerClient.removeCommentsFromPullRequest(changedFileInBitbucketServer);
                for (ViolationSerial violation : getViolationsForFile(violationsPerFile, changedFileInBitbucketServer, listener)) {
                    jvctsBitbucketServerClient.commentPullRequest(changedFileInBitbucketServer, violation.getLine(),
                            constructCommentMessage(violation));
                }
            }
        }
        if (!isNullOrEmpty(config.getCommitHash())) {
            doLog(FINE, "Commenting commit \"" + config.getCommitHash() + "\"");
            for (String changedFileInBitbucketServer : jvctsBitbucketServerClient.getChangedFileInCommit()) {
                doLog(FINE, "Changed file in commit: \"" + changedFileInBitbucketServer + "\"");
                jvctsBitbucketServerClient.removeCommentsCommit(changedFileInBitbucketServer);
                for (ViolationSerial violation : getViolationsForFile(violationsPerFile, changedFileInBitbucketServer, listener)) {
                    jvctsBitbucketServerClient.commentCommit(changedFileInBitbucketServer, violation.getLine(),
                            constructCommentMessage(violation));
                }
            }
        }
    }

    /**
     * Get list of violations that has files ending with changed file in Bitbucket Server. Violation instances may have absolute or relative
     * paths, we can not trust that.
     */
    private static List<ViolationSerial> getViolationsForFile(Map<String, List<ViolationSerial>> violationsPerFile,
            String changedFileInBitbucketServer, BuildListener listener) {
        List<ViolationSerial> found = newArrayList();
        for (String reportedFile : violationsPerFile.keySet()) {
            if (reportedFile.endsWith(changedFileInBitbucketServer) || changedFileInBitbucketServer.endsWith(reportedFile)) {
                JvctsLogger.doLog(listener, FINE, "Found comments for \"" + changedFileInBitbucketServer + "\" (reported as \""
                        + reportedFile + "\")");
                found.addAll(violationsPerFile.get(reportedFile));
            } else {
                doLog(FINE, "Changed file and reported file not matching. Bitbucket Server: \"" + changedFileInBitbucketServer
                        + "\" Reported: \"" + reportedFile + "\"");
            }
        }
        return found;
    }

    private static String constructCommentMessage(ViolationSerial v) {
        String message = firstNonNull(emptyToNull(v.getMessage()), v.getPopupMessage());
        String severity = v.getSeverity();
        if (!isNullOrEmpty(v.getSeverityLevel() + "")) {
            severity += " (" + v.getSeverityLevel() + ") ";
        }
        return (v.getType() + " L" + v.getLine() + " " + severity + message).trim().replaceAll("\n", " ")
                .replaceAll("  ", " ");
    }

    /**
     * Makes sure any Jenkins variable, used in the configuration fields, are evaluated.
     */
    private static ViolationsToBitbucketServerConfig expand(ViolationsToBitbucketServerConfig config, EnvVars environment) {
        ViolationsToBitbucketServerConfig expanded = new ViolationsToBitbucketServerConfig();
        expanded.setBitbucketServerBaseUrl(environment.expand(config.getBitbucketServerBaseUrl()));
        expanded.setBitbucketServerUser(environment.expand(config.getBitbucketServerUser()));
        expanded.setBitbucketServerPassword(environment.expand(config.getBitbucketServerPassword()));
        expanded.setBitbucketServerProject(environment.expand(config.getBitbucketServerProject()));
        expanded.setBitbucketServerPullRequestId(environment.expand(config.getBitbucketServerPullRequestId()));
        expanded.setCommitHash(environment.expand(config.getCommitHash()));
        expanded.setBitbucketServerRepo(environment.expand(config.getBitbucketServerRepo()));
        for (ParserConfig parserConfig : config.getParserConfigs()) {
            ParserConfig p = new ParserConfig();
            p.setParserTypeDescriptor(parserConfig.getParserTypeDescriptor());
            p.setPattern(environment.expand(parserConfig.getPattern()));
            p.setPathPrefix(environment.expand(parserConfig.getPathPrefixOpt().or("")));
            expanded.getParserConfigs().add(p);
        }
        return expanded;
    }

    /**
     * Enables testing of configuration GUI.
     */
    private static void logConfiguration(ViolationsToBitbucketServerConfig config, AbstractBuild<?, ?> build,
            BuildListener listener) {
        listener.getLogger().println(FIELD_BITBUCKET_SERVER_USER + ": " + config.getBitbucketServerUser());
        listener.getLogger().println(FIELD_BITBUCKET_SERVER_BASE_URL + ": " + config.getBitbucketServerBaseUrl());
        listener.getLogger().println(FIELD_BITBUCKET_SERVER_PROJECT + ": " + config.getBitbucketServerProject());
        listener.getLogger()
                .println(FIELD_BITBUCKET_SERVER_PULL_REQUEST_ID + ": " + config.getBitbucketServerPullRequestId());
        listener.getLogger().println(FIELD_COMMIT_HASH + ": " + config.getCommitHash());
        listener.getLogger().println(FIELD_BITBUCKET_SERVER_REPO + ": " + config.getBitbucketServerRepo());
        for (ParserConfig parserConfig : config.getParserConfigs()) {
            listener.getLogger().println(parserConfig.getParserTypeDescriptor() + ": " + parserConfig.getPattern());
            if (parserConfig.getPathPrefixOpt().isPresent()) {
                listener.getLogger().println(
                        parserConfig.getParserTypeDescriptor() + " pathPrefix: " + parserConfig.getPathPrefix());
            }
        }
    }
}
