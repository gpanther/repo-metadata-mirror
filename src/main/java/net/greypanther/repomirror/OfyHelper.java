package net.greypanther.repomirror;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.googlecode.objectify.ObjectifyService;

public final class OfyHelper implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ObjectifyService.register(MirroredEntity.class);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // do nothing
    }
}
