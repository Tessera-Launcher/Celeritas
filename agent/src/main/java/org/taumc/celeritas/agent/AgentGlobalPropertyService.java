package org.taumc.celeritas.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

/** Mixin expects a global property store; a map is enough for one agent. */
public final class AgentGlobalPropertyService implements IGlobalPropertyService {
    private record BasicProperty(String name) implements IPropertyKey {
        @Override
        public boolean equals(Object other) {
            return other instanceof BasicProperty property && name.equals(property.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return "BasicProperty{" + name + "}";
        }
    }

    private final Map<String, IPropertyKey> keys = new HashMap<>();
    private final Map<IPropertyKey, Object> values = new HashMap<>();

    @Override
    public IPropertyKey resolveKey(String name) {
        return keys.computeIfAbsent(name, BasicProperty::new);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        return (T) values.get(key);
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        values.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) values.getOrDefault(key, defaultValue);
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object value = values.get(key);
        return value instanceof String string ? string : defaultValue;
    }
}
