package com.ecommerce.productservice;

import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * JUnit 5 Test Suite for Product Service Integration Tests.
 * Run this suite directly in your IDE to execute all product service integration tests.
 */
@Suite
@SuiteDisplayName("Product Service Integration Tests Suite")
@SelectPackages("com.ecommerce.productservice")
@IncludeTags("integration")
public class ProductIntegrationTestSuite {
}
