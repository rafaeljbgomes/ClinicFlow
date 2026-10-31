import "server-only";

const DEFAULT_CLIENT_ID = "clinicflow-bff";
const DEFAULT_USER_LOOKUP_CLIENT_ID = "clinicflow-user-lookup";

export type OidcConfig = {
  issuer: string;
  clientId: string;
  clientSecret: string;
  publicUrl: string;
  redirectUri: string;
  postLogoutRedirectUri: string;
  authorizationEndpoint: string;
  registrationEndpoint: string;
  endSessionEndpoint: string;
  tokenEndpoint: string;
  jwksEndpoint: string;
  backchannelLogoutEndpoint: string;
  userLookup: { clientId: string; clientSecret: string; usersEndpoint: string | null };
};

// Endpoints are configured, not discovered (ADR-027). The browser uses the public issuer; the code
// exchange, refresh, keys and back-channel logout use Keycloak's private-network address, which still
// issues tokens with the public iss. Discovery fetched from the private address would report the public
// issuer, which a conforming client must reject.
export function oidcConfig(): OidcConfig {
  const issuer = trimSlash(required("CLINICFLOW_OIDC_ISSUER"));
  const backchannel = trimSlash(process.env.CLINICFLOW_OIDC_INTERNAL_ISSUER?.trim() || issuer);
  const publicUrl = trimSlash(required("CLINICFLOW_PUBLIC_URL"));
  const protocol = (base: string) => `${base}/protocol/openid-connect`;
  return {
    issuer,
    clientId: process.env.CLINICFLOW_OIDC_CLIENT_ID?.trim() || DEFAULT_CLIENT_ID,
    clientSecret: process.env.CLINICFLOW_BFF_CLIENT_SECRET ?? "",
    publicUrl,
    redirectUri: `${publicUrl}/api/auth/callback`,
    postLogoutRedirectUri: `${publicUrl}/login`,
    authorizationEndpoint: `${protocol(issuer)}/auth`,
    registrationEndpoint: `${protocol(issuer)}/registrations`,
    endSessionEndpoint: `${protocol(issuer)}/logout`,
    tokenEndpoint: `${protocol(backchannel)}/token`,
    jwksEndpoint: `${protocol(backchannel)}/certs`,
    backchannelLogoutEndpoint: `${protocol(backchannel)}/logout`,
    // The administrator's user lookup reads the Admin API with its own read-only client (ADR-031).
    userLookup: {
      clientId: process.env.CLINICFLOW_USER_LOOKUP_CLIENT_ID?.trim() || DEFAULT_USER_LOOKUP_CLIENT_ID,
      clientSecret: process.env.CLINICFLOW_USER_LOOKUP_CLIENT_SECRET ?? "",
      usersEndpoint: adminUsersEndpoint(backchannel),
    },
  };
}

// http://keycloak:8080/realms/clinicflow -> http://keycloak:8080/admin/realms/clinicflow/users. Null for an
// issuer of another shape, which leaves the lookup unavailable rather than breaking sign-in.
function adminUsersEndpoint(issuer: string) {
  const match = /^(.*)\/realms\/([^/]+)$/.exec(issuer);
  return match ? `${match[1]}/admin/realms/${match[2]}/users` : null;
}

function required(name: string) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is not configured`);
  return value;
}

function trimSlash(value: string) {
  return value.replace(/\/+$/, "");
}
