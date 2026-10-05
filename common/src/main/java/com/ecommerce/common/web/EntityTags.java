package com.ecommerce.common.web;

import com.ecommerce.common.exception.PreconditionFailedException;

/**
 * Optimistic concurrency over HTTP: a resource's {@code version} is its strong ETag, returned on every read, and
 * a client that sends {@code If-Match} on an update is only applied if nobody changed the resource since.
 * That closes the lost-update hole of "read, edit, write back whatever was there when I started".
 */
public final class EntityTags {

    private EntityTags() {
    }

    /** The ETag header value for a resource version, e.g. {@code "3"}. */
    public static String of(Long version) {
        return "\"" + (version == null ? 0 : version) + "\"";
    }

    /**
     * Rejects the update (412) if the caller sent an {@code If-Match} that does not match the current version.
     * No header means the caller did not ask for the check, so the update proceeds (last write wins).
     */
    public static void verifyIfMatch(String ifMatch, Long currentVersion) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return;
        }
        String current = String.valueOf(currentVersion == null ? 0 : currentVersion);
        for (String candidate : ifMatch.split(",")) {
            String tag = candidate.trim();
            if (tag.equals("*")) {
                return;
            }
            if (tag.startsWith("W/")) {
                tag = tag.substring(2);
            }
            if (tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")) {
                tag = tag.substring(1, tag.length() - 1);
            }
            if (tag.equals(current)) {
                return;
            }
        }
        throw new PreconditionFailedException(
                "The resource was changed since you read it (current version " + current + "). Re-read it and retry.",
                "PRECONDITION_FAILED");
    }
}
