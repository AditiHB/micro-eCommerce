# Testing Spring Boot Applications

### Q: What's the full spectrum of test types this repo uses, from fastest/narrowest to
slowest/broadest, and what's real vs. mocked at each level?

**A:** Four distinct levels, each a deliberate choice of how much of the real stack to boot:

1. **Plain Mockito unit test, no Spring context at all** — `@ExtendWith(MockitoExtension.class)`, dependencies
   as `@Mock`, the class under test constructed by hand (`new PaymentService(mockRepo, mockGateway,
   mockPublisher)`, `PaymentServiceTest.java`). Fastest — no container, no classpath scanning, nothing Spring
   at all — and the right default for testing business logic in isolation.
2. **`@WebMvcTest`** — slices in only the web layer: `@WebMvcTest(CustomerController.class)`
   (`CustomerControllerUnitTest.java`) boots `MockMvc` and the real controller, but the service layer
   (`@MockBean CustomerService`) and even the current-user resolver (`@MockBean CurrentUser`) are mocked out.
   Paired here with `@AutoConfigureMockMvc(addFilters = false)` — security filters are explicitly disabled in
   this slice, so a request reaches the controller without needing a real JWT; the mocked `CurrentUser` just
   lets it through. Good for verifying request/response mapping, status codes, and validation wiring, without
   paying for a database or real auth.
3. **`@DataJpaTest`** — slices in only the JPA/repository layer: `CustomerRepositoryTest.java` combines it with
   `@AutoConfigureTestDatabase(replace = Replace.NONE)` specifically to **stop** Spring from swapping in an
   embedded database, keeping the real Testcontainers Postgres instead (see
   [microservices/11](../microservices/11-observability-and-testing.md) for why H2 would hide real bugs here).
   No web layer, no service layer — just entities, repositories, and the real schema.
4. **`@SpringBootTest`** — the full application context. Sometimes the *real* one
   (`PaymentControllerIntegrationTest.java`: `@AutoConfigureMockMvc(addFilters = false)` +
   `@WithMockUser(roles = "ADMIN")`, nothing mocked, hitting real Testcontainers Postgres end to end); sometimes
   a deliberately **narrower** one — `OutboxIntegrationTest` uses
   `@SpringBootTest(classes = TestMessagingApplication.class)` to boot a minimal test-only application instead
   of the real service's full `@SpringBootApplication`, when the test only cares about one shared mechanism
   (the outbox) and doesn't want every other concern in that service's actual app wired in too.

### Q: `@MockBean`/`@SpyBean` vs. plain `@Mock` — what's the actual difference, and when do you need the
Spring-aware versions?

**A:** `@Mock` (plain Mockito) creates a mock object with no Spring involvement at all — fine, and preferred,
when you're constructing the class under test by hand and never booting a context. `@MockBean`/`@SpyBean` are
Spring-Boot-test-specific: they create the mock/spy **and register it in the application context**, replacing
whatever real bean would otherwise have been autowired there — necessary the moment you're using
`@WebMvcTest`/`@DataJpaTest`/`@SpringBootTest` and need *one specific bean* inside that real context swapped
out (e.g. `CustomerControllerUnitTest`'s `@MockBean CustomerService` — the controller is autowired by Spring, so
the only way to give it a mock service is to register the mock as a bean Spring itself will inject).

### Q: Why does this repo use `@MockBean`/`@SpyBean` rather than Spring Boot 3.4's newer
`@MockitoBean`/`@MockitoSpyBean`?

**A:** Because it can't do otherwise yet — the root `pom.xml` pins `spring-boot.version` to `3.2.5`, and
`@MockitoBean`/`@MockitoSpyBean` were introduced in Spring Boot 3.4 (as a replacement addressing some known
`@MockBean` limitations around context caching). This is a good example of a question where the honest,
precise answer is a version constraint, not a design preference — worth being able to say plainly rather than
inventing a stylistic justification for what's actually just the Boot version this repo is pinned to.

### Q: Why bother with `@DataJpaTest`/narrow slices at all, instead of just always using full
`@SpringBootTest`?

**A:** Context startup cost and blast radius of a failure. A `@SpringBootTest` boots every bean in the
application — including ones that have nothing to do with what you're actually testing — which is both slower
(more beans to construct, more auto-configuration to evaluate) and noisier when something fails (a repository
test failing because an unrelated Kafka listener bean couldn't start is a confusing failure mode). A slice test
boots only what that layer needs, so a `@DataJpaTest` failure can only mean the JPA/schema layer is actually
broken, not that something unrelated elsewhere in the app failed to wire up. The tradeoff is coverage: a slice
test, by definition, never proves the *whole* app actually boots and wires together — which is exactly the gap
this repo's handful of genuine `@SpringBootTest`s (and the Karate e2e suite, one level up again) exist to
close.
