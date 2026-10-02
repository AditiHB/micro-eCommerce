package com.ecommerce.orderservice;

import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * JUnit 5 Test Suite for Order Service Integration Tests.
 * Run this suite directly in your IDE to execute all order service integration tests.
 */
@Suite
@SuiteDisplayName("Order Service Integration Tests Suite")
@SelectPackages("com.ecommerce.orderservice")
@IncludeTags("integration")
public class OrderIntegrationTestSuite {
}
