package com.example.mednlp.rules;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge between UIMA and the rest of the application. UIMA instantiates annotators itself from
 * descriptors, so they cannot receive Spring beans through constructors. Each annotator instead gets
 * a plain-string configuration parameter (the provider id) and looks the provider up here.
 * Unit tests register their own provider under a unique id, so no Spring context is needed.
 */
public final class RuleProviderRegistry {

    private static final Map<String, RuleProvider> PROVIDERS = new ConcurrentHashMap<>();

    private RuleProviderRegistry() {
    }

    public static void register(String id, RuleProvider provider) {
        PROVIDERS.put(id, provider);
    }

    public static void unregister(String id) {
        PROVIDERS.remove(id);
    }

    public static RuleProvider get(String id) {
        RuleProvider provider = PROVIDERS.get(id);
        if (provider == null) {
            throw new IllegalStateException("No RuleProvider registered under id '" + id + "'");
        }
        return provider;
    }
}
