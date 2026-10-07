# Auto-Configuration & Conditional Beans

### Q: In your own words, how does Spring Boot decide what to configure automatically?

**A:** Every starter on the classpath ships one or more `@Configuration` classes registered via an
`AutoConfiguration.imports` file, each guarded by `@Conditional*` annotations — `@ConditionalOnClass` (only
apply if a given class is on the classpath, so e.g. Jackson auto-config only activates if Jackson is actually
present), `@ConditionalOnMissingBean` (only apply if the application hasn't already defined its own bean of
that type — this is the escape hatch that lets you override any default), `@ConditionalOnProperty`, and others.
Boot evaluates all of these at startup and wires in whatever's left after the conditions are checked — which is
why adding a dependency to a `pom.xml` can silently change what beans exist, with no code written at all.

### Q: Give a concrete example from this repo of a bean that exists only because a property says so.

**A:** `NotificationDispatcher` and `OrderSagaDeadline` are both gated with
`@ConditionalOnProperty(prefix = "ecommerce.notifications", name = "dispatcher-enabled", havingValue = "true",
matchIfMissing = true)` / the equivalent `reaper-enabled` flag — present by default (`matchIfMissing = true`),
but a test profile (or an operator) can set the flag `false` to switch the bean off entirely, not just disable
its logic. The sender implementations use the same mechanism to be mutually exclusive:
`LoggingNotificationSender` is `@ConditionalOnProperty(name = "notification.channel", havingValue = "log",
matchIfMissing = true)` while `EmailNotificationSender` requires `havingValue = "email"` — only one of the two
ever becomes a bean, decided purely by a property value, with no `if/else` anywhere in application code.

### Q: How do you override a Spring Boot auto-configured bean with your own?

**A:** Define your own bean of the same type — most auto-configuration is guarded with
`@ConditionalOnMissingBean`, so the moment your `@Configuration` class provides one, Boot's own default quietly
steps aside rather than conflicting. This repo does exactly this for caching:
`RedisConfig implements CachingConfigurer` and defines its own `@Bean public CacheManager cacheManager(...)`
(`RedisConfig.java:52`) — a hand-built `RedisCacheManager` with `setTransactionAware(true)` and a
`FailSafeCache`-wrapping `decorateCache` override, replacing whatever Boot's own Redis auto-configuration would
otherwise have produced, with zero need to disable or exclude the auto-configuration explicitly.

### Q: What's `@EnableConfigurationProperties` for, and why does almost every service have a tiny
`@Configuration` class that does nothing but call it?

**A:** `@ConfigurationProperties`-annotated classes (`OrderProperties`, `NotificationProperties`, etc.) aren't
automatically registered as Spring beans just by existing — something has to tell Spring to bind them and make
them injectable. `@EnableConfigurationProperties(OrderProperties.class)` on `order-service`'s own
`RestClientConfig` class does exactly that (its own Javadoc makes the point explicit: *"Order-service's own
configuration: switches on `OrderProperties`"*). It's a one-line, easy-to-miss piece of wiring — worth knowing
to look for when a `@ConfigurationProperties` class mysteriously isn't getting its values bound.

### Q: What's the difference between `@ConditionalOnProperty` and just checking the property value with an
`if` statement inside a regular `@Component`?

**A:** `@ConditionalOnProperty` decides whether the **bean exists at all** — if the condition fails, Spring
never constructs it, never calls its constructor, never registers its `@Scheduled` methods, and nothing else in
the application can inject it (a dependent bean would fail to start instead of silently getting a half-working
instance). An `if` check inside a bean's method only skips *behavior*, while the bean (and any resources its
constructor acquires — a thread pool, a connection) still exists and consumes memory/startup time regardless.
For something like `NotificationDispatcher`'s `@Scheduled` dispatch loop, the difference is concrete: with the
conditional, disabling it means the scheduled job genuinely never runs at all; with a hand-rolled `if`, you'd
still be paying for a `@Scheduled` method firing every second just to immediately no-op.
