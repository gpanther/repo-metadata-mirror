package net.greypanther.repomirror;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.common.io.ByteStreams;
import com.googlecode.objectify.ObjectifyService;

@SuppressWarnings("serial")
public final class Mirror extends HttpServlet {
    private final static Logger LOG = Logger.getLogger(Mirror.class.getName());
    private final static Charset CHARSET = Charset.forName("UTF-8");

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        checkAuthorization(req);

        int retryCount = getRetryCount(req);
        String namespace = req.getParameter("namespace");
        String url = req.getParameter("url");
        LOG.info(String.format("Mirroring %s (ns: %s)", url, namespace));

        if (retryCount == 11) {
            save(namespace, new MirroredEntity(url));
            return;
        }

        byte[] content = download(url);
        if (shouldBeStored(url)) {
            save(namespace, new MirroredEntity(url, content));
            return;
        }

        Queue queue = QueueFactory.getDefaultQueue();
        Document doc = Jsoup.parse(new String(content, CHARSET), url);
        for (Element link : doc.select("a")) {
            String href = link.attr("abs:href");

            if (!href.startsWith(url)) {
                // never go up to avoid cycles
                continue;
            }

            if (href.endsWith("./")) {
                // skip going up
                continue;
            }

            boolean shouldBeDownloaded = shouldBeStored(href) || href.endsWith("/");
            if (!shouldBeDownloaded) {
                continue;
            }

            Kickoff.queueStartTask(queue, namespace, href);
        }
    }

    private void checkAuthorization(HttpServletRequest req) {
        if (req.getHeader("X-AppEngine-QueueName") != null) {
            return;
        }
        throw new SecurityException();
    }

    private void save(String namespace, MirroredEntity entity) {
        String currentNamespace = NamespaceManager.get();
        try {
            NamespaceManager.set(namespace);
            ObjectifyService.ofy().save().entity(entity).now();
        } finally {
            NamespaceManager.set(currentNamespace);
        }
    }

    private boolean shouldBeStored(String url) {
        String lowerCaseUrl = url.toLowerCase();
        return lowerCaseUrl.endsWith(".xml") || lowerCaseUrl.endsWith(".pom") || lowerCaseUrl.endsWith(".md5")
                || lowerCaseUrl.endsWith(".sha1");
    }

    private byte[] download(String url) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        URL urlUrl = new URL(url);
        try (InputStream urlStream = urlUrl.openStream()) {
            ByteStreams.copy(urlStream, out);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, String.format("Exception while downloading %s: %s", url, ex.getMessage()), ex);
            throw ex;
        }
        return out.toByteArray();
    }

    private int getRetryCount(HttpServletRequest req) {
        String s = req.getHeader("X-AppEngine-TaskExecutionCount");
        return s == null ? 0 : Integer.parseInt(s);
    }
}
