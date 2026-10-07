# Validation & Exception Handling

### Q: Walk through the Bean Validation annotations used on a real request DTO in this repo.

**A:** `CreateProductRequest` (`services/inventory-service/.../dto/`): `@NotBlank` on `name`/`sku`/`category`,
`@Size(max = ...)` bounding string length, `@NotNull` + `@DecimalMin("0.01")` on `price` (not just non-null —
must be a strictly positive amount), and `@Pattern(regexp = "[A-Z]{3}")` on `currency` (an ISO-4217-shaped
3-letter uppercase code, checked by regex rather than trusting free text). Each of these is a declarative
constraint Spring validates automatically the moment the controller method parameter is annotated `@Valid` —
no manual `if (name == null) throw ...` anywhere in the controller or service for these specific checks.

### Q: How does `@Valid` cascade into nested objects and collections — give a concrete multi-level example.

**A:** `CreateOrderRequest.items` is declared `@Valid @Size(max = MAX_LINES) List<Item> items` — the `@Valid`
here is doing two jobs: validating the list itself (via `@Size`) *and* telling Bean Validation to also validate
**every element inside it**. Each nested static `Item` class has its own constraints (`@NotBlank` on the
product ID, `@NotNull @Positive @Max(1000)` on quantity) that only get checked because the outer field is
`@Valid`, not merely present — a plain `List<Item> items` with no `@Valid` would validate the list's own
constraints but silently skip validating each `Item` inside it.

The same class also has three cross-field checks via `@AssertTrue`-annotated boolean methods (each marked
`@JsonIgnore` so Jackson doesn't try to serialize the boolean "property" an `isXxx()`-shaped method would
otherwise look like) — Bean Validation doesn't have a clean declarative way to express "these two fields must
agree with each other," so a boolean method that returns the actual cross-field check, annotated
`@AssertTrue`, is the standard way to express it without dropping to a fully custom `ConstraintValidator`.

### Q: What's the difference between `@Valid` on a `@RequestBody` and `@Validated` on the controller class
for `@RequestParam`/`@PathVariable`?

**A:** `@Valid` on a `@RequestBody` parameter validates the deserialized object's own field-level annotations —
failures throw `MethodArgumentNotValidException`. `@Validated` at the **class** level is what's needed to make
Bean Validation annotations on individual `@RequestParam`/`@PathVariable` method parameters (which aren't full
objects with their own annotated fields) get checked at all — `InventoryController` and `ProductController` are
both class-level `@Validated`, enabling constraints like `@RequestParam @Positive Integer quantity` and
`@RequestParam @PositiveOrZero Integer quantity` on individual primitive-ish parameters. A failure here throws
a **different** exception — `ConstraintViolationException`, not `MethodArgumentNotValidException` — which is
exactly why this repo has a separate `@RestControllerAdvice` (`RequestValidationAdvice`) just to catch that one
specific case, distinct from the body-validation handling.

### Q: Describe this repo's global exception-handling structure — why an abstract base class in `common`
with an empty subclass per service, rather than one shared `@RestControllerAdvice` bean?

**A:** `ProblemDetailsAdvice` (`common/.../exception/`) is an **abstract** class extending Spring's
`ResponseEntityExceptionHandler`, not itself annotated `@RestControllerAdvice` — it can't be a bean on its own.
Each service has its own trivial subclass: `@RestControllerAdvice public class GlobalExceptionHandler extends
ProblemDetailsAdvice {}` — literally empty, all the real mapping logic lives once in the shared parent.

Why not just put `@RestControllerAdvice` directly on the shared class in `common`? Spring's component scanning
is normally rooted at each service's own base package — a `@RestControllerAdvice` bean living in a different
module's package wouldn't be picked up by a given service's scan unless that service explicitly widened its
scan base, which is exactly the kind of implicit cross-module coupling worth avoiding (see
[microservices/01](../microservices/01-architecture-and-service-boundaries.md) on `common`'s scope). The empty
per-service subclass is a small amount of boilerplate that buys a clean, explicit opt-in per service, while
every actual exception-to-HTTP-status mapping decision still lives in exactly one place.

### Q: What does `ProblemDetailsAdvice` actually map, and what does it use for the response body shape?

**A:** Built on Spring's `ProblemDetail` (RFC 9457, formerly 7807) — `application/problem+json`, a
standardized shape (`type`, `title`, `status`, `detail`, plus arbitrary extension members) rather than an
ad hoc custom error JSON per project. Concretely: `BusinessException` maps to whatever HTTP status the
exception itself carries (each business exception subtype knows its own correct status — 409, 422, etc.);
`OptimisticLockingFailureException` → 409 with code `CONCURRENT_MODIFICATION`;
`DataIntegrityViolationException` → 409 with code `DATA_CONFLICT`; a catch-all `Exception` handler → 500 with
code `INTERNAL_ERROR`, logging the full stack server-side while deliberately returning nothing internal to the
caller. It also overrides `handleMethodArgumentNotValid` (body validation failures → a field-error map, code
`VALIDATION_FAILED`) and `handleHttpMessageNotReadable` (malformed JSON → code `MALFORMED_REQUEST`). A shared
`Problems` builder utility adds consistent extension fields — `errorCode`, `timestamp`, and a `traceId` pulled
from MDC — onto every problem response, including ones Spring itself generates without this codebase's
involvement (405, 415, missing required parameter), via an override of `createResponseEntity` that enriches
any `ProblemDetail` that wasn't already enriched.

Two narrower, separately-ordered advices exist alongside it rather than folding everything into one class:
`RequestValidationAdvice` (`ConstraintViolationException` → 400) and `SecurityExceptionAdvice`
(`AccessDeniedException` → 403), both marked `@Order(Ordered.HIGHEST_PRECEDENCE)` so they take priority over
the broader catch-all handling in `ProblemDetailsAdvice` for their specific exception types.
