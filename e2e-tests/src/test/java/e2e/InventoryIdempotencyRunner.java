package e2e;

import com.intuit.karate.junit5.Karate;

/**
 * Runs inventory-idempotency.feature against an already-running stack.
 * Named *Runner, not *Test - deliberately outside Surefire's default
 * inclusion patterns, so it never executes as a side effect of a plain
 * `mvn test`/`mvn install`. See this module's README.md to run it.
 */
class InventoryIdempotencyRunner {

    @Karate.Test
    Karate testInventoryIdempotency() {
        return Karate.run("classpath:e2e/inventory-idempotency.feature");
    }
}
