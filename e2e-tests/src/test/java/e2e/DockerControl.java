package e2e;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Minimal Docker CLI control for fault-injection scenarios (e.g. proving the
 * gateway's circuit breaker actually opens when a backend is unreachable).
 * Requires `docker` on PATH wherever the test runs - that's an intentional,
 * explicit trade-off for this one scenario, not the norm for this module
 * (every other feature here is pure HTTP against the already-running stack).
 */
public class DockerControl {

    public static int stop(String containerName) {
        return run("docker", "stop", containerName);
    }

    public static int start(String containerName) {
        return run("docker", "start", containerName);
    }

    /**
     * Deletes every rate_limit:* key in the gateway's Redis. The rate-limit
     * scenario in resilience.feature deliberately exhausts its own
     * client-IP counter (that's the point of the test), and that counter
     * is shared across every route for 60s - without this cleanup, any
     * other feature run within that window that happens to hit the same
     * route gets a false 429 that has nothing to do with whatever it's
     * actually testing. Call this at the end of that scenario (via
     * configure afterScenario, so it runs even if an assertion fails).
     */
    public static void clearRateLimitKeys() {
        // Redis requires a password: it comes from the environment (source the git-ignored .env).
        String password = System.getenv("REDIS_PASSWORD");
        if (password == null || password.isBlank()) {
            return; // best effort: without the password the limiter simply is not reset
        }
        run("docker", "exec", "redis", "redis-cli", "-a", password, "--no-auth-warning", "eval",
            "for _,k in ipairs(redis.call('keys','rate_limit:*')) do redis.call('del',k) end", "0");
    }

    public static boolean waitUntilHealthy(String containerName, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if ("healthy".equals(inspectHealth(containerName))) {
                return true;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static String inspectHealth(String containerName) {
        try {
            Process proc = new ProcessBuilder(
                "docker", "inspect", "--format", "{{.State.Health.Status}}", containerName)
                .redirectErrorStream(true)
                .start();
            String output = new String(proc.getInputStream().readAllBytes()).trim();
            proc.waitFor(5, TimeUnit.SECONDS);
            return output;
        } catch (IOException | InterruptedException e) {
            return "unknown";
        }
    }

    private static int run(String... command) {
        try {
            Process proc = new ProcessBuilder(command).redirectErrorStream(true).start();
            proc.getInputStream().readAllBytes();
            return proc.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to run: " + String.join(" ", command), e);
        }
    }
}
