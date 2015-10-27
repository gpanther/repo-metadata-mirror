package net.greypanther.repomirror;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.memcache.MemcacheService;

public final class Counter {
    public enum Key {
        ATTEMPTS, STORED, ENQUEUED
    }

    public static void reset(Key key, MemcacheService memcache) {
        String currentNamespace = NamespaceManager.get();
        try {
            NamespaceManager.set("");
            memcache.put(key.name(), 0L);
        } finally {
            NamespaceManager.set(currentNamespace);
        }
    }

    public static void increment(Key key, MemcacheService memcache) {
        String currentNamespace = NamespaceManager.get();
        try {
            NamespaceManager.set("");
            memcache.increment(key.name(), 1);
        } finally {
            NamespaceManager.set(currentNamespace);
        }
    }

    public static long get(Key key, MemcacheService memcache) {
        String currentNamespace = NamespaceManager.get();
        try {
            NamespaceManager.set("");
            Object o = memcache.get(key.name());
            return o == null ? Long.MIN_VALUE : (Long) o;
        } finally {
            NamespaceManager.set(currentNamespace);
        }
    }
}
