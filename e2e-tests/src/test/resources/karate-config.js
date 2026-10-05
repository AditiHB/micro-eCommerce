function fn() {
  // Credentials are never written down in this repository. They come from the environment - source
  // the git-ignored .env that scripts/gen-env.sh generates before running the suite:
  //     set -a; . ./.env; set +a
  function required(name) {
    var value = java.lang.System.getenv(name);
    if (!value) {
      karate.fail('Environment variable ' + name + ' is not set. Run scripts/gen-env.sh, then: set -a; . ./.env; set +a');
    }
    return value;
  }

  var config = {
    // Through the API Gateway (customers/orders/payments/inventory).
    // Override with -Dgateway.url=... if the gateway isn't on localhost:8080.
    gatewayUrl: karate.properties['gateway.url'] || 'http://localhost:8080',
    // notification-service is not routed through the gateway - it's hit
    // directly on its own port, same as the Postman/Insomnia environments.
    notificationUrl: karate.properties['notification.url'] || 'http://localhost:8086',
    // Keycloak issues every token. Override with -Dkeycloak.url=... when it is elsewhere.
    keycloakTokenUrl: (karate.properties['keycloak.url'] || 'http://localhost:8180')
        + '/realms/ecommerce/protocol/openid-connect/token',
    e2eClientId: 'ecommerce-e2e',
    e2eClientSecret: required('E2E_CLIENT_SECRET'),
    // Development-only identities created by infrastructure/keycloak/seed-dev.sh.
    // karate_admin (ADMIN) satisfies every role check used by the scenarios.
    testUsername: 'karate_admin',
    testPassword: required('E2E_ADMIN_PASSWORD'),
    managerUsername: 'karate_manager',
    managerPassword: required('E2E_MANAGER_PASSWORD'),
    // karate_user is a plain customer bound to customer id 1 (the customer_id claim).
    customerUsername: 'karate_user',
    customerPassword: required('E2E_USER_PASSWORD')
  };

  // Against the smallstep overlay (docker-compose.pki.yml) Keycloak and nginx present certificates issued by the
  // platform's private CA. DEVELOPMENT ONLY: trust whatever certificate is presented rather than importing the
  // CA root into the JVM. (Run with -Dkeycloak.url=https://localhost:8443 -Dgateway.url=https://localhost.)
  if (config.keycloakTokenUrl.indexOf('https') === 0 || config.gatewayUrl.indexOf('https') === 0) {
    karate.configure('ssl', true);
  }

  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 10000);

  return config;
}
