package com.ecommerce.paymentservice;

import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * JUnit 5 Test Suite for Payment Service Integration Tests.
 * Run this suite directly in your IDE to execute all payment service integration tests.
 */
@Suite
@SuiteDisplayName("Payment Service Integration Tests Suite")
@SelectPackages("com.ecommerce.paymentservice")
@IncludeTags("integration")
public class PaymentIntegrationTestSuite {
}
