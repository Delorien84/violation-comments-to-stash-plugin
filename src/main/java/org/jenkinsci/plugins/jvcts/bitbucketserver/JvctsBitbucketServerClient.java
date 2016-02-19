package org.jenkinsci.plugins.jvcts.bitbucketserver;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;
import static org.jenkinsci.plugins.jvcts.JvctsLogger.doLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.google.common.annotations.VisibleForTesting;
import com.jayway.jsonpath.JsonPath;
import hudson.model.BuildListener;
import net.minidev.json.JSONArray;
import org.jenkinsci.plugins.jvcts.config.ViolationsToBitbucketServerConfig;


public class JvctsBitbucketServerClient {
    private static final String VERSION = "version";
    private static final String ID = "id";
    private static final long PAGE_SIZE = 100;
    private static BitbucketServerInvoker bitbucketServerInvoker = new BitbucketServerInvoker();
    private final ViolationsToBitbucketServerConfig config;
    private final BuildListener listener;

    public JvctsBitbucketServerClient(ViolationsToBitbucketServerConfig config, BuildListener listener) {
        this.config = config;
        this.listener = listener;
    }

    @VisibleForTesting
    public static void setBitbucketServerInvoker(BitbucketServerInvoker bitbucketServerInvoker) {
        JvctsBitbucketServerClient.bitbucketServerInvoker = bitbucketServerInvoker;
    }

    public List<String> getChangedFileInPullRequest() {
        String diff = bitbucketServerInvoker.invokeUrl(config, getBitbucketServerPullRequestDiffGetBase(),
                BitbucketServerInvoker.Method.GET, null, listener);
        List<String> res = new ArrayList<String>();
        Scanner scanner = null;
        try {
            scanner = new Scanner(diff);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("diff --git a/")) {
                    int idx = line.indexOf("b/");
                    if (idx >= "diff --git a/".length()) {
                        res.add(line.substring("diff --git a/".length(), idx).trim());
                    }
                }
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return res;
        // return invokeAndParse(getBitbucketServerPulLRequestBase() + "/changes?limit=999999", "$..path.toString");
    }

    public void commentPullRequest(String changedFile, int line, String message) {
        String postContent = "{"
                + "\"filename\": \"" + changedFile + "\","
                + "\"content\": \"" + message + "\","
                + "\"line_to\": " + line
                + "}";

        // String postContent = "{ \"text\": \"" + message.replaceAll("\"", "") + "\", \"anchor\": { \"lineFrom\": \"" + line
        // + "\", \"lineType\": \"ADDED\", \"fileType\": \"TO\", \"path\": \"" + changedFile + "\" }}";
        bitbucketServerInvoker.invokeUrl(config, getBitbucketServerPullRequestCommentsPostBase(),
                BitbucketServerInvoker.Method.POST, postContent, listener);
    }

    public void removeCommentsFromPullRequest() {
        List<Number> commentIds = new ArrayList<Number>();
        String nextUrl = getBitbucketServerPullRequestCommentsGetBase();
        do {
            String json = bitbucketServerInvoker.invokeUrl(config, nextUrl, BitbucketServerInvoker.Method.GET, null, listener);
            nextUrl = null; // clean if json is null
            if (json != null && json.length() > 0) {
                List<Number> moreCommentsIds = parse(json, "$.values[?(@.user.username=='" + config.getBitbucketServerUser() + "')].id");
                if (moreCommentsIds != null) {
                    commentIds.addAll(moreCommentsIds);
                }
                try {
                    nextUrl = JsonPath.read(json, "$.next");
                } catch (Exception e) {
                    doLog(listener, FINE, "No next page.");
                }
            }
        } while (nextUrl != null && nextUrl.length() > 0);

        // remove the comments
        for (Number commentId : commentIds) {
            bitbucketServerInvoker.invokeUrl(config, getBitbucketServerPullRequestCommentsDeleteBase(commentId.toString()),
                    BitbucketServerInvoker.Method.DELETE, null, listener);
        }
    }

    // @SuppressWarnings("rawtypes")
    // public void removeCommentsFromPullRequest(String changedFile) {
    // for (Object comment : getCommentsOnPullRequest(changedFile)) {
    // if (toMap(toMap(comment).get("author")).get("name").equals(config.getBitbucketServerUser())) {
    // removeCommentFromPullRequest((Map) comment);
    // }
    // }
    // }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object o) {
        return (Map<String, Object>) o;
    }

    // private JSONArray getCommentsOnPullRequest(String changedFile) {
    // return invokeAndParse(getBitbucketServerPulLRequestBase() + "/comments?path=" + changedFile + "&limit=999999",
    // "$.values[*]");
    // }

    private <T> T invokeAndParse(String url, String jsonPath) {
        String json = bitbucketServerInvoker.invokeUrl(config, url, BitbucketServerInvoker.Method.GET, null, listener);
        return parse(json, jsonPath);
    }

    private <T> T parse(String json, String jsonPath) {
        try {
            return JsonPath.read(json, jsonPath);
        } catch (Exception e) {
            doLog(listener, SEVERE, "Unnable to parse json: " + json, e);
            return null;
        }
    }

    // private void removeCommentFromPullRequest(@SuppressWarnings("rawtypes") Map comment) {
    // bitbucketServerInvoker.invokeUrl(config, getBitbucketServerPulLRequestBase() + "/comments/" + comment.get(ID)
    // + "?version=" + comment.get(VERSION), BitbucketServerInvoker.Method.DELETE, "", listener);
    // }
    //
    // private String getBitbucketServerPullRequestBase() {
    // return config.getBitbucketServerBaseUrl() + "/rest/api/1.0/repositories/" + config.getBitbucketServerProject()
    // + "/" + config.getBitbucketServerRepo() + "/pullrequests/" + config.getBitbucketServerPullRequestId();
    // }

    private String getBitbucketServerPullRequestDiffGetBase() {
        return config.getBitbucketServerBaseUrl() + "/2.0/repositories/" + config.getBitbucketServerProject() + "/"
                + config.getBitbucketServerRepo() + "/pullrequests/" + config.getBitbucketServerPullRequestId() + "/diff";
    }

    private String getBitbucketServerPullRequestCommentsGetBase() {
        return config.getBitbucketServerBaseUrl() + "/2.0/repositories/" + config.getBitbucketServerProject() + "/"
                + config.getBitbucketServerRepo() + "/pullrequests/" + config.getBitbucketServerPullRequestId() + "/comments?pagelen="
                + PAGE_SIZE;
    }

    private String getBitbucketServerPullRequestCommentsDeleteBase(String commentId) {
        return config.getBitbucketServerBaseUrl() + "/1.0/repositories/" + config.getBitbucketServerProject() + "/"
                + config.getBitbucketServerRepo() + "/pullrequests/" + config.getBitbucketServerPullRequestId() + "/comments/" + commentId;
    }

    private String getBitbucketServerPullRequestCommentsPostBase() {
        return config.getBitbucketServerBaseUrl() + "/1.0/repositories/" + config.getBitbucketServerProject() + "/"
                + config.getBitbucketServerRepo() + "/pullrequests/" + config.getBitbucketServerPullRequestId() + "/comments";
    }

    private String getBitbucketServerCommitsBase() {
        return config.getBitbucketServerBaseUrl() + "/rest/api/1.0/projects/" + config.getBitbucketServerProject()
                + "/repos/" + config.getBitbucketServerRepo() + "/commits/" + config.getCommitHash();
    }

    public List<String> getChangedFileInCommit() {
        return invokeAndParse(getBitbucketServerCommitsBase() + "/changes?limit=999999", "$..path.toString");
    }

    private JSONArray getCommentsOnCommit(String changedFile) {
        return invokeAndParse(getBitbucketServerCommitsBase() + "/comments?path=" + changedFile + "&limit=999999", "$.values[*]");
    }

    @SuppressWarnings("rawtypes")
    public void removeCommentsCommit(String changedFile) {
        for (Object comment : getCommentsOnCommit(changedFile)) {
            if (toMap(toMap(comment).get("author")).get("name").equals(config.getBitbucketServerUser())) {
                removeCommentFromCommit((Map) comment);
            }
        }
    }

    private void removeCommentFromCommit(@SuppressWarnings("rawtypes") Map comment) {
        bitbucketServerInvoker.invokeUrl(config, getBitbucketServerCommitsBase() + "/comments/" + comment.get(ID) + "?version="
                + comment.get(VERSION), BitbucketServerInvoker.Method.DELETE, "", listener);
    }

    public void commentCommit(String changedFile, int line, String message) {
        String postContent = "{ \"text\": \"" + message.replaceAll("\"", "") + "\", \"anchor\": { \"line\": \"" + line
                + "\", \"lineType\": \"ADDED\", \"fileType\": \"TO\", \"path\": \"" + changedFile + "\" }}";
        bitbucketServerInvoker.invokeUrl(config, getBitbucketServerCommitsBase() + "/comments", BitbucketServerInvoker.Method.POST,
                postContent, listener);
    }
}
