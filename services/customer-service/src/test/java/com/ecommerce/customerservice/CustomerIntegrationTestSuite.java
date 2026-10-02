package com.ecommerce.customerservice;

import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * JUnit 5 Test Suite for Customer Service Integration Tests.
 * Run this suite directly in your IDE to execute all customer service integration tests.
 */
@Suite
@SuiteDisplayName("Customer Service Integration Tests Suite")
@SelectPackages("com.ecommerce.customerservice")
@IncludeTags("integration")
public class CustomerIntegrationTestSuite {
}
