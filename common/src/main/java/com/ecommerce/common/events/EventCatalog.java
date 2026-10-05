package com.ecommerce.common.events;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The registry of every event contract on the classpath: each class annotated with {@link EventSchema}.
 *
 * <p>It drives three things so they can never disagree: the type header written on every message (the
 * logical name, not a Java class name), the type mapping consumers use to turn that name back into a class
 * (an allow-list - nothing outside it is ever deserialized), and the list of topics (and their dead-letter
 * topics) the broker is asked to create.
 */
public final class EventCatalog {

    private static final String BASE_PACKAGE = "com.ecommerce";

    private static volatile Map<String, Class<? extends DomainEvent>> byType;

    private EventCatalog() {
    }

    /** Logical type name of an event class; falls back to the simple class name for undeclared classes. */
    public static String typeOf(Class<?> eventClass) {
        EventSchema schema = eventClass.getAnnotation(EventSchema.class);
        return schema != null ? schema.type() : eventClass.getSimpleName();
    }

    public static int versionOf(Class<?> eventClass) {
        EventSchema schema = eventClass.getAnnotation(EventSchema.class);
        return schema != null ? schema.version() : 1;
    }

    /** Topic an event class is published to. */
    public static String topicOf(Class<?> eventClass) {
        EventSchema schema = eventClass.getAnnotation(EventSchema.class);
        if (schema == null) {
            throw new IllegalArgumentException(eventClass.getName() + " has no @EventSchema, so it has no topic");
        }
        return schema.topic();
    }

    public static Optional<Class<? extends DomainEvent>> classFor(String type) {
        return Optional.ofNullable(registry().get(type));
    }

    public static Collection<Class<? extends DomainEvent>> all() {
        return registry().values();
    }

    /** Every topic that carries at least one event type. */
    public static Set<String> topics() {
        return registry().values().stream()
                .map(EventCatalog::topicOf)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * {@code type:class,type:class} in the format of Spring Kafka's {@code spring.json.type.mapping}, for the
     * (de)serializers: only these logical names map to a class, and only these classes are trusted.
     */
    public static String typeMapping() {
        return registry().entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue().getName())
                .collect(Collectors.joining(","));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Class<? extends DomainEvent>> registry() {
        Map<String, Class<? extends DomainEvent>> local = byType;
        if (local == null) {
            synchronized (EventCatalog.class) {
                local = byType;
                if (local == null) {
                    Map<String, Class<? extends DomainEvent>> found = new TreeMap<>();
                    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
                    scanner.addIncludeFilter(new AnnotationTypeFilter(EventSchema.class));
                    for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
                        try {
                            Class<?> type = Class.forName(candidate.getBeanClassName());
                            if (DomainEvent.class.isAssignableFrom(type)) {
                                Class<? extends DomainEvent> eventClass = (Class<? extends DomainEvent>) type;
                                Class<? extends DomainEvent> clash = found.put(typeOf(eventClass), eventClass);
                                if (clash != null && clash != eventClass) {
                                    throw new IllegalStateException("Event type '" + typeOf(eventClass)
                                            + "' is declared by both " + clash.getName() + " and " + eventClass.getName());
                                }
                            }
                        } catch (ClassNotFoundException e) {
                            throw new IllegalStateException("Cannot load event class " + candidate.getBeanClassName(), e);
                        }
                    }
                    local = byType = Collections.unmodifiableMap(found);
                }
            }
        }
        return local;
    }
}
