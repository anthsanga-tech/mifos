/**
 * Training web-app environment — mirrors web-app oauth flags.
 * Default matches Apache Fineract / web-app: HTTP Basic (oauth.enabled = false).
 */
export const environment = {
  production: false,
  /**
   * When true: Keycloak password grant + Bearer JWT (Fineract OAuth mode).
   * When false: POST /authentication + HTTP Basic (Fineract default).
   */
  oauth: {
    enabled: false,
    /** Keycloak token endpoint base (no trailing slash). */
    serverUrl: 'http://localhost:9000/realms/training/protocol/openid-connect',
    appId: 'training-web'
  }
};
