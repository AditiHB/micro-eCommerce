package com.ecommerce.inventoryservice;

import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * JUnit 5 Test Suite for Inventory Service Integration Tests.
 * Run this suite directly in your IDE to execute all inventory service integration tests.
 */
@Suite
@SuiteDisplayName("Inventory Service Integration Tests Suite")
@SelectPackages("com.ecommerce.inventoryservice")
@IncludeTags("integration")
public class InventoryIntegrationTestSuite {
}
