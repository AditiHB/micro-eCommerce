package com.ecommerce.common.testsupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** A real Redis for the tests that exercise the shared cache. */
public final class SharedRedis {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static boolean paused;

    static {
        REDIS.start();
    }

    private SharedRedis() {
    }

    public static String host() {
        return REDIS.getHost();
    }

    public static int port() {
        return REDIS.getMappedPort(6379);
    }

    /** Stops the Redis server (to prove the service keeps working through a cache outage); restart it after. */
    public static synchronized void pause() {
        if (!paused) {
            REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
            paused = true;
        }
    }

    /** Safe to call whether or not Redis is currently paused. */
    public static synchronized void resume() {
        if (paused) {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
            paused = false;
        }
    }
}
