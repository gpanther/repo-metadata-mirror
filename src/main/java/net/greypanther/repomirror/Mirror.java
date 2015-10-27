package net.greypanther.repomirror;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
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

        MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();

        Counter.increment(Counter.Key.ATTEMPTS, memcache);
        DownloadedInfo content = download(url);
        if (shouldBeStored(url)) {
            save(namespace, new MirroredEntity(url, content.content, content.lastModified));
            Counter.increment(Counter.Key.STORED, memcache);
            return;
        }

        Queue queue = QueueFactory.getDefaultQueue();
        Document doc = Jsoup.parse(new String(content.content, CHARSET), url);
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

            Counter.increment(Counter.Key.ENQUEUED, memcache);
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

    private DownloadedInfo download(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        try (InputStream urlStream = connection.getInputStream()) {
            ByteStreams.copy(urlStream, out);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, String.format("Exception while downloading %s: %s", url, ex.getMessage()), ex);
            throw ex;
        }

        Date lastModified = connection.getLastModified() == 0 ? null : new Date(connection.getLastModified());
        return new DownloadedInfo(out.toByteArray(), lastModified);
    }

    private int getRetryCount(HttpServletRequest req) {
        String s = req.getHeader("X-AppEngine-TaskExecutionCount");
        return s == null ? 0 : Integer.parseInt(s);
    }

    private static final class DownloadedInfo {
        private final byte[] content;
        private final Date lastModified;

        private DownloadedInfo(byte[] content, Date lastModified) {
            this.content = content;
            this.lastModified = lastModified;
        }
    }
}
