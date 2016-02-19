package org.jenkinsci.plugins.jvcts;

import static hudson.tasks.BuildStepMonitor.NONE;
import static java.lang.Boolean.TRUE;
import static org.jenkinsci.plugins.jvcts.perform.JvctsPerformer.jvctsPerform;

import java.io.IOException;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import org.jenkinsci.plugins.jvcts.config.ViolationsToBitbucketServerConfig;


public class ViolationsToBitbucketServerRecorder extends Recorder {
    @Extension
    public static final BuildStepDescriptor<Publisher> DESCRIPTOR = new ViolationsToBitbucketServerDescriptor();
    private ViolationsToBitbucketServerConfig config;

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        if (build.getResult().isBetterOrEqualTo(Result.UNSTABLE)) {
            // run only on SUCCESS OR UNSTABLE
            jvctsPerform(config, build, listener);
        }

        return TRUE;
    }

    @Override
    public BuildStepDescriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    public ViolationsToBitbucketServerRecorder() {
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return NONE;
    }

    public void setConfig(ViolationsToBitbucketServerConfig config) {
        this.config = config;
    }

    public ViolationsToBitbucketServerConfig getConfig() {
        return config;
    }
}
