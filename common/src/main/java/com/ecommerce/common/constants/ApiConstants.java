package com.ecommerce.common.constants;

public class ApiConstants {

    // API Endpoints
    public static final String API_PREFIX = "/api";
    public static final String CUSTOMERS_ENDPOINT = "/customers";
    public static final String ORDERS_ENDPOINT = "/orders";
    public static final String INVENTORY_ENDPOINT = "/inventory";
    public static final String PAYMENTS_ENDPOINT = "/payments";

    // Kafka Topics
    public static final String KAFKA_TOPIC_ORDER_CREATED = "order-created";
    public static final String KAFKA_TOPIC_INVENTORY_RESERVED = "inventory-reserved";
    public static final String KAFKA_TOPIC_INVENTORY_FAILED = "inventory-failed";
    public static final String KAFKA_TOPIC_PAYMENT_PROCESSED = "payment-processed";
    public static final String KAFKA_TOPIC_PAYMENT_FAILED = "payment-failed";

    // Kafka Consumer Groups
    public static final String KAFKA_GROUP_ORDER = "order-group";
    public static final String KAFKA_GROUP_INVENTORY = "inventory-group";
    public static final String KAFKA_GROUP_PAYMENT = "payment-group";

    // Error Codes
    public static final String ERROR_CODE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String ERROR_CODE_VALIDATION = "VALIDATION_FAILED";
    public static final String ERROR_CODE_INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    public static final String ERROR_CODE_EVENT_PUBLISHING = "EVENT_PUBLISHING_ERROR";
    public static final String ERROR_CODE_BUSINESS = "BUSINESS_ERROR";
    public static final String ERROR_CODE_INTERNAL = "INTERNAL_ERROR";
    public static final String ERROR_CODE_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_CODE_FORBIDDEN = "FORBIDDEN";
    public static final String ERROR_CODE_AUTHENTICATION = "AUTHENTICATION_FAILED";

    // Pagination Defaults
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_NUMBER = 0;

    // API Versions
    public static final String API_VERSION = "v1";

    // Response Messages
    public static final String SUCCESS_MESSAGE = "Operation completed successfully";
    public static final String ERROR_MESSAGE = "An error occurred while processing the request";
    public static final String RESOURCE_CREATED = "Resource created successfully";
    public static final String RESOURCE_UPDATED = "Resource updated successfully";
    public static final String RESOURCE_DELETED = "Resource deleted successfully";

    private ApiConstants() {
        // Prevent instantiation
        throw new AssertionError("Cannot instantiate constants class");
    }
}
