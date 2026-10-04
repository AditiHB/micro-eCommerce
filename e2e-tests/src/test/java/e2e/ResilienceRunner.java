package e2e;

import com.intuit.karate.junit5.Karate;

/**
 * Runs resilience.feature against an already-running stack. Named *Runner,
 * not *Test - deliberately outside Surefire's default inclusion patterns,
 * so it never executes as a side effect of a plain `mvn test`/`mvn install`.
 * Needs `docker` on PATH (see DockerControl) - see this module's README.md.
 */
class ResilienceRunner {

    @Karate.Test
    Karate testResilience() {
        return Karate.run("classpath:e2e/resilience.feature");
    }
}
