function fn() {
  var config = {
    // Through the API Gateway (customers/orders/payments/inventory/auth).
    // Override with -Dgateway.url=... if the gateway isn't on localhost:8080.
    gatewayUrl: karate.properties['gateway.url'] || 'http://localhost:8080',
    // notification-service is not routed through the gateway - it's hit
    // directly on its own port, same as the Postman/Insomnia environments.
    notificationUrl: karate.properties['notification.url'] || 'http://localhost:8086',
    // Seeded by services/customer-service's V5/V6 "Seed_Test_Users" Flyway
    // migration (ADMIN role - satisfies every endpoint used by these
    // scenarios). See db/README.md.
    testUsername: 'karate_admin',
    testPassword: 'KarateTest123!'
  };

  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 10000);

  return config;
}
