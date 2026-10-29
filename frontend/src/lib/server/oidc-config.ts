import "server-only";

const DEFAULT_CLIENT_ID = "clinicflow-bff";

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
  };
}

function required(name: string) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is not configured`);
  return value;
}

function trimSlash(value: string) {
  return value.replace(/\/+$/, "");
}
