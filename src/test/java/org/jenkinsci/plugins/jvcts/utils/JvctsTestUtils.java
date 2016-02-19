package org.jenkinsci.plugins.jvcts.utils;

import static com.google.common.base.Joiner.on;
import static org.jenkinsci.plugins.jvcts.utils.BitbucketServerClientFaker.fakeBitbucketServerClient;
import static org.jenkinsci.plugins.jvcts.utils.BitbucketServerClientFaker.getRequestsSentToBitbucketServer;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;

import com.google.common.io.Resources;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.Node;
import org.jenkinsci.plugins.jvcts.config.ParserConfig;
import org.jenkinsci.plugins.jvcts.config.ViolationsToBitbucketServerConfig;
import org.mockito.Mockito;


public class JvctsTestUtils {
    private JvctsTestUtils() {
    }

    private static void disableLogging() {
        Logger logger = Logger.getLogger("");
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }
    }

    public static ViolationsToBitbucketServerConfig preConfigure(List<ParserConfig> pconfigs) throws IOException {
        ViolationsToBitbucketServerConfig config = new ViolationsToBitbucketServerConfig();
        for (ParserConfig pconfig : pconfigs) {
            config.getParserConfigs().add(pconfig);
        }
        config.setBitbucketServerBaseUrl("http://stash.server/");
        config.setBitbucketServerUser("stashUser");
        config.setBitbucketServerPassword("stashPassword");
        config.setBitbucketServerProject("stashProject");
        config.setBitbucketServerRepo("stashRepo");

        disableLogging();

        fakeBitbucketServerClient();
        return config;
    }

    public static AbstractBuild<?, ?> getWorkspace() throws IOException {
        AbstractBuild<?, ?> build = Mockito.mock(Build.class);

        Node mockNode = Mockito.mock(Node.class);
        Mockito.when(mockNode.createPath(Mockito.anyString()))
                .thenReturn(new FilePath(new File(Resources.getResource("test-resources-placeholder.txt").getFile()).getParentFile()));

        Mockito.when(build.getBuiltOn()).thenReturn(mockNode);
        // Mockito.when(build.getRootDir())
        // .thenReturn(new File(Resources.getResource("test-resources-placeholder.txt").getFile()).getParentFile()));
        return build;
    }

    public static void assertRequested(String request) {
        for (String requested : getRequestsSentToBitbucketServer()) {
            if (requested.equals(request)) {
                return;
            }
        }
        fail("Did not capture:\n" + request + "\nCaptured:\n" + on("\n").join(getRequestsSentToBitbucketServer()));
    }
}
