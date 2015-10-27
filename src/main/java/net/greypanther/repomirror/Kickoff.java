package net.greypanther.repomirror;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.io.ByteStreams;

@SuppressWarnings("serial")
public final class Kickoff extends HttpServlet {
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        checkUser();

        resp.setContentType("text/html");
        try (InputStream ins = getClass().getResourceAsStream("/kickoff.html")) {
            ByteStreams.copy(ins, resp.getOutputStream());
        }
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        checkUser();

        MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
        for (Counter.Key key : Counter.Key.values()) {
            Counter.reset(key, memcache);
        }

        Queue queue = QueueFactory.getDefaultQueue();
        queueStartTask(queue, "repo1.maven.org", "https://repo1.maven.org/maven2/");
        queueStartTask(queue, "repository.apache.org", "https://repository.apache.org/content/repositories/");
        queueStartTask(queue, "oss.sonatype.org", "https://oss.sonatype.org/content/repositories/");
        queueStartTask(queue, "repository.jboss.org", "https://repository.jboss.org/");
        queueStartTask(queue, "mirrors.ibiblio.org", "http://mirrors.ibiblio.org/maven2/");

        resp.setContentType("text/plain");
        resp.getWriter().println("Done.");
    }

    static void queueStartTask(Queue queue, String namespace, String url) {
        long minBackoffSeconds = TimeUnit.MINUTES.toSeconds(10);
        int retryLimits = 11;
        long ageLimitSeconds = TimeUnit.DAYS.toSeconds(7);
        long eta = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ThreadLocalRandom.current().nextInt(2, 7));
        queue.add(TaskOptions.Builder.withUrl("/admin/mirror").param("namespace", namespace).param("url", url)
                .retryOptions(RetryOptions.Builder.withDefaults().minBackoffSeconds(minBackoffSeconds)
                        .taskRetryLimit(retryLimits).taskAgeLimitSeconds(ageLimitSeconds))
                .method(Method.POST).etaMillis(eta));
    }

    private void checkUser() {
        if (!UserServiceFactory.getUserService().isUserAdmin()) {
            throw new SecurityException();
        }
    }
}
