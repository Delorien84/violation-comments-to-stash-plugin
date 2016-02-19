package org.jenkinsci.plugins.jvcts.perform;

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Maps.newHashMap;
import static hudson.plugins.violations.TypeDescriptor.TYPES;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;
import static org.jenkinsci.plugins.jvcts.JvctsLogger.doLog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.common.annotations.VisibleForTesting;
import hudson.model.BuildListener;
import hudson.plugins.violations.TypeDescriptor;
import hudson.plugins.violations.model.FullBuildModel;
import hudson.plugins.violations.model.FullFileModel;
import hudson.plugins.violations.model.Violation;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.jenkinsci.plugins.jvcts.config.ParserConfig;
import org.jenkinsci.plugins.jvcts.config.ViolationsToBitbucketServerConfig;


public class FullBuildModelWrapper {

    private final Map<String, FullBuildModel> models = newHashMap();
    private final ViolationsToBitbucketServerConfig config;
    private final BuildListener listener;

    public FullBuildModelWrapper(ViolationsToBitbucketServerConfig config, File workspace, BuildListener listener) {
        this.config = config;
        this.listener = listener;
        for (ParserConfig parserConfig : config.getParserConfigs()) {
            for (String pattern : parserConfig.getPattern().split(",")) {
                for (String fileName : findFilesFromPattern(workspace, pattern)) {
                    String[] sourcePaths = {};
                    TypeDescriptor type = TYPES.get(parserConfig.getParserTypeDescriptor().getName());
                    try {
                        if (!models.containsKey(parserConfig.getParserTypeDescriptor().getName())) {
                            models.put(parserConfig.getParserTypeDescriptor().getName(), new FullBuildModel());
                        }
                        type.createParser().parse(models.get(parserConfig.getParserTypeDescriptor().getName()), workspace, fileName,
                                sourcePaths);
                    } catch (IOException e) {
                        doLog(SEVERE, "Error while parsing \"" + fileName + "\" for type "
                                + parserConfig.getParserTypeDescriptor().getName(), e);
                    }
                }
            }
        }
    }

    public Map<String, List<ViolationSerial>> getViolationsPerFile() {
        Map<String, List<ViolationSerial>> violationsPerFile = newHashMap();
        for (ParserConfig parserConfig : config.getParserConfigs()) {
            String typeDescriptorName = parserConfig.getParserTypeDescriptor().getName();
            if (models.containsKey(typeDescriptorName)) {
                for (String fileModel : models.get(typeDescriptorName).getFileModelMap().keySet()) {
                    String sourceFile = usingForwardSlashes(determineSourcePath(fileModel, parserConfig));
                    if (sourceFile == null) {
                        doLog(listener, SEVERE, "Could not determine source file from: " + fileModel);
                        continue;
                    }
                    TreeMap<String, TreeSet<Violation>> typeMap = models.get(typeDescriptorName).getFileModel(fileModel).getTypeMap();
                    for (String type : typeMap.keySet()) {
                        for (Violation violation : typeMap.get(type)) {
                            if (!violationsPerFile.containsKey(sourceFile)) {
                                violationsPerFile.put(sourceFile, new ArrayList<ViolationSerial>());
                            }
                            violationsPerFile.get(sourceFile).add(new ViolationSerial(violation));
                        }
                    }
                }
            }
        }
        doLog(FINE, "Found " + violationsPerFile.size() + " violations");
        return violationsPerFile;
    }

    @VisibleForTesting
    static String usingForwardSlashes(String unknownSlashed) {
        return unknownSlashed.replaceAll("\\\\", "/");
    }

    private String determineSourcePath(String fileModel, ParserConfig parserConfig) {
        doLog(FINE, "Determining source path for " + fileModel);
        FullBuildModel model = models.get(parserConfig.getParserTypeDescriptor().getName());
        FullFileModel fullFileModel = model.getFileModel(fileModel);
        if (fullFileModel.getSourceFile() != null) {
            if (fullFileModel.getSourceFile().exists()) {
                return fullFileModel.getSourceFile().getAbsolutePath();
            } else {
                doLog(FINE, "Not found: " + fullFileModel.getSourceFile().getAbsolutePath());
            }
        }
        File withDisplayName = new File(fullFileModel.getDisplayName());
        if (withDisplayName.exists()) {
            return withDisplayName.getAbsolutePath();
        } else {
            doLog(FINE, "Not found: " + withDisplayName.getAbsolutePath());
        }
        if (parserConfig.getPathPrefixOpt().isPresent()) {
            File withPrefix = new File(parserConfig.getPathPrefixOpt().get() + fullFileModel.getDisplayName());
            if (withPrefix.exists()) {
                return withPrefix.getAbsolutePath();
            } else {
                doLog(FINE, "Not found: " + withPrefix.getAbsolutePath());
            }
        }
        doLog(FINE, "Using: " + fullFileModel.getDisplayName());
        return fullFileModel.getDisplayName();
    }

    /**
     * Include matching pattern within workspace.
     *
     * @see FileSet#setIncludes(String)
     */
    private String[] findFilesFromPattern(final File workspace, String pattern) {
        if (nullToEmpty(pattern).trim().isEmpty()) {
            return new String[] {};
        }
        FileSet fileSet = new FileSet();
        Project project = new Project();
        fileSet.setProject(project);
        fileSet.setDir(workspace);
        fileSet.setIncludes(pattern);
        return fileSet.getDirectoryScanner(project).getIncludedFiles();
    }
}
