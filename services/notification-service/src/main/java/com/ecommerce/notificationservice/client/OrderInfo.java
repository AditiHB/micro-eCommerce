// OBSOLETE - safe to delete this file.
//
// Notification-service no longer calls order-service: the saga events carry the customer id, so there is no
// extra REST hop (and no failure mode) between an event and its notification. This file is intentionally an empty
// compilation unit because it could not be removed in the session that retired it.
package com.ecommerce.notificationservice.client;
