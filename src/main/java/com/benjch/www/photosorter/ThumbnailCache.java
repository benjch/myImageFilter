package com.benjch.www.photosorter;

import java.util.LinkedHashMap;
import java.util.Map;

public class ThumbnailCache {

    private static final int MAX_ENTRIES = 300;

    private final Map<String, byte[]> cache = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public synchronized byte[] get(String key) {
        return cache.get(key);
    }

    public synchronized void put(String key, byte[] value) {
        cache.put(key, value);
    }

    public synchronized void invalidateByPrefix(String prefix) {
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
