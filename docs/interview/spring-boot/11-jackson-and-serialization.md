# Jackson & Serialization

### Q: How is `ObjectMapper` configured in this repo, and why is it important that it's one shared instance
rather than Jackson's defaults used ad hoc?

**A:** `JacksonConfig` (`common/.../config/`) defines a single `@Bean public ObjectMapper objectMapper()`,
plus a `public static ObjectMapper newObjectMapper()` factory that builds an *identically configured* instance
for use outside the web layer (Kafka event serialization reuses this same factory, not a second, differently
configured mapper). Customizations: `JavaTimeModule` registered (so `Instant`/`LocalDateTime` fields serialize
correctly instead of failing or falling back to reflection-based serialization) and
`SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` disabled — dates always serialize as readable ISO-8601
strings, never raw epoch-millis numbers.

Why one shared, deliberately configured mapper matters: Jackson's *default* `ObjectMapper` (what you get from
`new ObjectMapper()` with no configuration) serializes dates as epoch-millisecond numbers by default and has no
`JavaTimeModule` registered at all — using an unconfigured mapper anywhere in this codebase (say, inside a
one-off utility method) would silently produce a different wire format than every other part of the system,
exactly the kind of inconsistency Kafka event consumers and HTTP clients would be fragile against. This is the
same discipline discussed in the microservices guide's performance notes — a heavyweight, configuration-bearing
object like this is built once and reused, never recreated ad hoc (see
[microservices performance notes](../microservices/README.md)).

### Q: What's the `ProblemDetail` Jackson mixin for, and what would break without it?

**A:** Spring's `ProblemDetail` (the RFC 9457 error-response type this repo's exception handling is built on —
see [06](06-validation-and-exception-handling.md)) stores its extension members (this repo's `errorCode`,
`errors`, `timestamp`, `traceId`) internally in a generic `properties` map, and Jackson's default serialization
of that would nest them *inside* a `"properties": {...}` object in the JSON response rather than placing them
as top-level siblings of `type`/`title`/`status`/`detail`, as RFC 9457 actually specifies. `JacksonConfig`
registers a mixin (`mapper.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)`) specifically to flatten
those extension members to the top level — without it, every error response in this entire system would be
correctly *structured* per Spring's internal model, but non-compliant with the actual spec its `type`/`title`
fields claim to follow, and inconsistent with what any RFC-9457-aware client library would expect to parse.

### Q: Give an example of `@JsonIgnore` and `@JsonProperty` solving a *real* problem in this repo, not just
"renaming a field."

**A:** `@JsonIgnore` — `CreateOrderRequest`'s three `@AssertTrue`-annotated cross-field validation methods
(see [06](06-validation-and-exception-handling.md)) are also marked `@JsonIgnore`. Without it, Jackson's
default bean-property introspection would see a boolean-returning `isXxxValid()`-shaped method and treat it as
a serializable JavaBean property — these validation methods would silently show up as extra fields in the JSON
representation of the request object, which is specifically wrong for something that exists purely to drive
validation, never to be part of the actual data shape.

`@JsonProperty` — `PagedResponse`'s `isLast`/`isFirst` boolean fields are explicitly annotated
`@JsonProperty("isLast")`/`@JsonProperty("isFirst")`. The reason this is needed at all: Jackson's default bean
introspection for a boolean getter named `isLast()` can, depending on the exact getter/field naming
combination, produce an unexpectedly different JSON property name than you'd naively expect (Jackson's
"is"-prefix stripping rules for booleans are a frequent source of this exact surprise) — the explicit
`@JsonProperty` pins down precisely what should appear on the wire, rather than leaving it to introspection
rules that are easy to get subtly wrong without noticing until a client's deserialization quietly fails to
populate a field. `DomainEvent.getEventType()` uses `@JsonProperty(access = JsonProperty.Access.READ_ONLY)` for
a related but distinct reason: it's a *computed* field (derived from the event's actual type), fine to include
when serializing an event out, but meaningless — and potentially dangerous — to accept back in on
deserialization, where it should never be settable by whatever's doing the deserializing.
