# Dependency Injection & Bean Wiring

### Q: Constructor injection, field injection (`@Autowired` on a field), or setter injection — which does
this repo use, and why does it matter?

**A:** Constructor injection, exclusively — a repo-wide search for `@Autowired` on a field in any main-source
file turns up zero real occurrences (the only hit is inside a Javadoc *example comment* in
`ApplicationMetrics.java`, not actual code). Every service class either writes an explicit constructor or uses
Lombok's `@RequiredArgsConstructor` to generate one from its `final` fields.

Why it matters, concretely: constructor injection makes a class's dependencies **visible and immutable** —
every field is `final`, so it's impossible to construct the object in a half-wired state, and a unit test can
instantiate the real class with `new CustomerService(mockRepo, mockMetrics, mockRecorder)` without needing a
Spring context at all (every unit test in this repo that uses plain `@ExtendWith(MockitoExtension.class)`,
e.g. `PaymentServiceTest.java`, relies on exactly this). Field injection hides the same dependencies behind
reflection magic that only works inside a real Spring context, which makes plain unit testing without the
container much harder, and lets a class silently grow more dependencies without the pain of a constructor
getting longer each time — a signal that's lost when it's just another `@Autowired` field appended quietly.

### Q: What does Lombok's `@RequiredArgsConstructor` actually do, and why use it instead of writing the
constructor by hand?

**A:** It's a compile-time code generator — it inspects a class's `final` (and `@NonNull` non-final) fields and
generates exactly the constructor you'd have written by hand, with one parameter per field in declaration
order. Used throughout this repo (`CustomerService`, `ReservationService`, `OutboxRelay`, etc.) purely to cut
boilerplate — it produces identical bytecode behavior to a hand-written constructor, Spring autowires it
exactly the same way (a class with a single constructor needs no `@Autowired` annotation at all — Spring uses
it implicitly).

Worth knowing the limitation this repo actually hit: Lombok does **not** reliably propagate a field-level
`@Qualifier` onto the constructor parameter it generates. Every place in this repo that needs a `@Qualifier`
(see below) writes the constructor explicitly instead of relying on `@RequiredArgsConstructor`, specifically to
avoid depending on that uncertain behavior.

### Q: When do you need `@Qualifier`, and where is it actually used here?

**A:** When more than one bean of the same type could satisfy a single dependency, Spring can't pick one by
type alone — `@Qualifier("beanName")` disambiguates by name. This repo's only real use of it is on the
executor beans added for `CompletableFuture`/`@Async` work this session, since each service defines *multiple*
`Executor`-typed beans on purpose (kept separate so one fan-out point can't starve another — see
[microservices/08](../microservices/08-async-processing-and-thread-safety.md)):

```java
public OrderPlacementService(OrderService orderService, CatalogClient catalog, CustomerDirectoryClient customers,
                             OrderProperties properties, @Qualifier("remoteCallExecutor") Executor remoteCallExecutor) {
```
(`OrderPlacementService.java:59`; the same pattern repeats in `OrderSagaDeadline.java:48` for
`sagaReaperExecutor` and `NotificationDispatcher.java:51` for `notificationSendExecutor`.) Each is an explicit
constructor, not a Lombok-generated one, for exactly the reason above.

### Q: Is there anywhere in this repo where you'd *expect* an ambiguous-bean error but don't get one?

**A:** Yes, worth being able to explain why: every service also has Spring Boot's own auto-configured
`applicationTaskExecutor` bean (from `TaskExecutionAutoConfiguration`, present by default in any Spring Boot
app) *in addition to* the custom-named executors this repo defines. That's potentially two-or-more
`Executor`-typed beans in the same context — but it never causes an ambiguity error, because every injection
point that needs one of the custom pools names it explicitly via `@Qualifier`. Ambiguity only becomes a runtime
error when something tries to inject `Executor` **by type alone** with more than one candidate present — which
nothing in this repo does.
