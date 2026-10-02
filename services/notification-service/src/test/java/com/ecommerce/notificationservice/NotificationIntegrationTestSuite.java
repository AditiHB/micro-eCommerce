package com.ecommerce.notificationservice;

import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * JUnit 5 Test Suite for Notification Service Integration Tests.
 * Run this suite directly in your IDE to execute all notification service integration tests.
 */
@Suite
@SuiteDisplayName("Notification Service Integration Tests Suite")
@SelectPackages("com.ecommerce.notificationservice")
@IncludeTags("integration")
public class NotificationIntegrationTestSuite {
}
