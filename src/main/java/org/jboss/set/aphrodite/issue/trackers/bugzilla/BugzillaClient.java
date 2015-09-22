/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.set.aphrodite.issue.trackers.bugzilla;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfig;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.jboss.pull.shared.Util;
import org.jboss.set.aphrodite.domain.Comment;
import org.jboss.set.aphrodite.domain.Flag;
import org.jboss.set.aphrodite.domain.FlagStatus;
import org.jboss.set.aphrodite.domain.Issue;
import org.jboss.set.aphrodite.domain.IssueStatus;
import org.jboss.set.aphrodite.domain.IssueTracking;
import org.jboss.set.aphrodite.domain.IssueType;
import org.jboss.set.aphrodite.domain.Release;
import org.jboss.set.aphrodite.domain.Stage;
import org.jboss.set.aphrodite.domain.Stream;
import org.jboss.set.aphrodite.spi.SearchCriteria;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.jboss.set.aphrodite.issue.trackers.bugzilla.BugzillaFields.*;

/**
 * @author Ryan Emerson
 */
public class BugzillaClient {

    private static final Log LOG = LogFactory.getLog(BugzillaClient.class);

    private final URL baseURL;
    private final Map<String, Object> requestMap;


    public BugzillaClient(URL baseURL, String login, String password) {
        this.baseURL = baseURL;

        Map<String, String> params = new HashMap<>();
        if (login != null)
            params.put(LOGIN, login);
        if (password != null)
            params.put(PASSWORD, password);
        requestMap = Collections.unmodifiableMap(params);
    }

    public Issue getIssue(String trackerId) throws MalformedURLException {
        Map<String, Object> params = new HashMap<>(requestMap);
        params.put(RESULT_INCLUDE_FIELDS, RESULT_FIELDS);
        params.put(ISSUE_IDS, trackerId);
        params.put(RESULT_PERMISSIVE_SEARCH, true);

        Map<String, ?> resultMap = executeRequest(XMLRPC.RPC_STRUCT, METHOD_GET_BUG, params);
        Object[] bugs = (Object[]) resultMap.get(RESULT_BUGS);
        if (bugs.length == 1) {
            @SuppressWarnings("unchecked")
            Map<String, Object> results = (Map<String, Object>) bugs[0];
            return getIssueObject(results);
        } else {
            Util.logWarnMessage(LOG, "Zero or more than one bug found with id: " + trackerId);
            return null;
        }
    }

    public Issue getIssueWithComments(String trackerId) throws MalformedURLException {
        Issue issue = getIssue(trackerId);
        issue.setComments(getCommentsForIssue(trackerId));
        return issue;
    }


    public List<Comment> getCommentsForIssue(Issue issue) {
        if (issue == null)
            throw new IllegalArgumentException("The provided issue cannot be null.");
        return getCommentsForIssue(issue.getTrackerId());
    }

    public List<Comment> getCommentsForIssue(String trackerId) {
        Map<String, Object> params = new HashMap<>(requestMap);
        params.put(ISSUE_IDS, trackerId);
        params.put(RESULT_INCLUDE_FIELDS, COMMENT_FIELDS);
        Map<String, ?> results = executeRequest(XMLRPC.RPC_STRUCT, METHOD_GET_COMMENT, params);

        if (results != null && !results.isEmpty() && results.containsKey(RESULT_BUGS)) {
            Map<String, Object> issues = XMLRPC.cast(XMLRPC.RPC_STRUCT, results.get(RESULT_BUGS));
            return getCommentList(XMLRPC.cast(XMLRPC.RPC_STRUCT, issues.get(trackerId)));
        }
        return new ArrayList<>();
    }

    public List<Issue> searchForIssues(SearchCriteria criteria) {
        List<Issue> issues = new ArrayList<>();
        return issues;
    }

    public boolean updateTargetRelease(int id, final String... targetRelease) {
        return updateField(id, TARGET_RELEASE, targetRelease);
    }

    public boolean updateStatus(int id, IssueStatus status) {
        return updateField(id, STATUS, status);
    }

    public boolean updateTargetMilestone(int id, String targetMilestone) {
        return updateField(id, TARGET_RELEASE, targetMilestone);
    }

    public boolean updateEstimate(int id, double worktime) {
        return updateField(id, ESTIMATED_TIME, worktime);
    }

    public boolean postComment(Integer id, String comment, boolean isPrivate) {
        Map<String, Object> params = new HashMap<>(requestMap);
        params.put(ID, id);
        params.put(COMMENT, comment);
        params.put(PRIVATE_COMMENT, isPrivate);
        return runCommand(METHOD_ADD_COMMENT, params);
    }

    public boolean updateFlags(Integer[] ids, String name, FlagStatus status) {
        String flagStatus = status.getSymbol();
        Map<String, String> updates = new HashMap<>();
        updates.put(NAME, name);
        updates.put(STATUS, flagStatus);
        Object[] updateArray = {updates};

        Map<String, Object> params = new HashMap<>(requestMap);
        params.put(ISSUE_IDS, ids);
        params.put(UPDATE_FIELDS, updateArray);
        params.put(RESULT_PERMISSIVE_SEARCH, true);

        return runCommand(METHOD_UPDATE_BUG, params);
    }

    // Bit of a mess, is there a better way of doing this?
    @SuppressWarnings("unchecked")
    private Issue getIssueObject(Map<String, Object> issueFields) throws MalformedURLException {
        Integer id = (Integer) issueFields.get(ID);
        URL url = new URL(baseURL + ID_QUERY + id);
        Issue issue = new Issue(url);
        issue.setTrackerId(id.toString());
        issue.setAssignee((String) issueFields.get(ASSIGNEE));
        issue.setDescription((String) issueFields.get(DESCRIPTION));
        issue.setType(IssueType.valueOf(((String) issueFields.get(ISSUE_TYPE)).toUpperCase()));
        issue.setStatus(IssueStatus.valueOf((String) issueFields.get(STATUS)));
        issue.setComponent((String) ((Object[]) issueFields.get(COMPONENT))[0]);
        issue.setProduct((String) issueFields.get(PRODUCT));
        issue.setStatus(IssueStatus.valueOf(((String) issueFields.get(STATUS)).toUpperCase()));
        issue.setType(IssueType.valueOf(((String) issueFields.get(ISSUE_TYPE)).toUpperCase()));

        String version = (String) ((Object[]) issueFields.get(VERSION))[0];
        Release release = new Release(version, (String) issueFields.get(TARGET_MILESTONE));
        issue.setRelease(release);

        List<URL> dependsOn = new ArrayList<>();
        Object[] dependencies = (Object[]) issueFields.get(DEPENDS_ON);
        for (Object dependencyId : dependencies)
            dependsOn.add(new URL(baseURL + BugzillaFields.ID_QUERY + dependencyId));
        issue.setDependsOn(dependsOn);

        List<URL> blocks = new ArrayList<>();
        Object[] blockers = (Object[]) issueFields.get(BLOCKS);
        for (Object blockingId : blockers)
            blocks.add(new URL(baseURL + BugzillaFields.ID_QUERY + blockingId));
        issue.setBlocks(blocks);

        Double estimatedTime = (Double) issueFields.get(ESTIMATED_TIME);
        Double hoursWorked = (Double) issueFields.get(HOURS_WORKED);
        issue.setTracking(new IssueTracking(estimatedTime, hoursWorked));

        Stage issueStage = new Stage();
        List<Stream> streams = new ArrayList<>();
        for (Object object : (Object[]) issueFields.get(FLAGS)) {
            Map<String, Object> flagMap = (Map<String, Object>) object;
            String name = (String) flagMap.get(FLAG_NAME);

            if (name.contains("_ack")) { // If Flag
                Optional<Flag> flag = getFlag(name);
                if (!flag.isPresent())
                    continue;

                FlagStatus status = FlagStatus.getMatchingFlag((String) flagMap.get(FLAG_STATUS));
                issueStage.setStatus(flag.get(), status);
            } else { // Else Stream
                FlagStatus status = FlagStatus.getMatchingFlag((String) flagMap.get(FLAG_STATUS));
                streams.add(new Stream(name, status));
            }
        }
        issue.setStage(issueStage);
        issue.setStreams(streams);

        return issue;
    }

    @SuppressWarnings("unchecked")
    private List<Comment> getCommentList(Map<String, Object> issue) {
        List<Comment> issueComments = new ArrayList<>();
        // Get all comments associated with issue
        for (Object[] comments : XMLRPC.iterable(XMLRPC.RPC_ARRAY, issue.values())) {
            // Iterate each comment and retrieve relevant fields
            for (Map<String, Object> comment : XMLRPC.iterable(XMLRPC.RPC_STRUCT, comments)) {
                String id = String.valueOf(comment.get(COMMENT_ID));
                String body = (String) comment.get(COMMENT_BODY);
                boolean isPrivate = (Boolean) comment.get(COMMENT_IS_PRIVATE);
                issueComments.add(new Comment(id, body, isPrivate));
            }
        }
        return issueComments;
    }

    private boolean updateField(int bugzillaId, String field, Object content) {
        Map<String, Object> params = new HashMap<>(requestMap);
        params.put(ID, bugzillaId);
        params.put(field, content);
        return runCommand(METHOD_UPDATE_BUG, params);
    }

    private <T> T executeRequest(final XMLRPC<T> type, String method, Object... params) {
        try {
            return type.cast(getRpcClient().execute(method, params));
        } catch (XmlRpcException e) {
            Util.logException(LOG, e);
            throw new RuntimeException(e); // TODO improve exception handling
        }
    }

    private XmlRpcClient getRpcClient() {
        String apiURL = baseURL + API_URL;
        XmlRpcClient rpcClient;
        rpcClient = new XmlRpcClient();

        try {
            URL url = new URL(apiURL);
            rpcClient.setConfig(getClientConfig(url));
        } catch (MalformedURLException e) {
            Util.logException(LOG, e);
            throw new RuntimeException(e);
        }
        return rpcClient;
    }

    private XmlRpcClientConfig getClientConfig(URL apiURL) {
        XmlRpcClientConfigImpl config;
        config = new XmlRpcClientConfigImpl();
        config.setServerURL(apiURL);
        return config;
    }

    private boolean runCommand(String method, Object... params) {
        try {
            getRpcClient().execute(method, params);
            return true;
        } catch (XmlRpcException e) {
            throw new IllegalStateException(e);
        }
    }

    // TODO is there a cleaner way to do this?
    private static class XMLRPC<T> {
        static final XMLRPC<Object[]> RPC_ARRAY = new XMLRPC<>(Object[].class);
        static final XMLRPC<Map<String, Object>> RPC_STRUCT = new XMLRPC<>(Map.class);

        final Class<T> cls;

        @SuppressWarnings("unchecked")
        XMLRPC(final Class<?> cls) {
            this.cls = (Class<T>) cls;
        }

        T cast(final Object obj) {
            return cls.cast(obj);
        }

        static <T> T cast(final XMLRPC<T> type, Object obj) {
            return type.cast(obj);
        }

        static <T> Iterable<T> iterable(final XMLRPC<T> type, final Collection<Object> c) {
            final Iterator<Object> it = c.iterator();
            return () -> new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public T next() {
                    return type.cast(it.next());
                }

                @Override
                public void remove() {
                    it.remove();
                }
            };
        }

        static <T> Iterable<T> iterable(final XMLRPC<T> type, final Object[] array) {
            final Iterator<Object> it = Arrays.asList(array).iterator();
            return () -> new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public T next() {
                    return type.cast(it.next());
                }

                @Override
                public void remove() {
                    it.remove();
                }
            };
        }
    }
}
