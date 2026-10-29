// Live issuer acceptance for #89 (ADR-027): through the gateway, the resource servers accept a token the
// clinicflow realm issued and refuse a correctly signed token from another realm and a forged signature.
// Run by Test-KeycloakTokens.ps1, which holds the admin port-forward open for the temporary realm.
const fs = require('node:fs');
const path = require('node:path');
const https = require('node:https');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');

const root = path.resolve(__dirname, '../..');
const address = process.env.GATEWAY_ADDRESS ?? '127.0.0.1';
const authHost = process.env.KEYCLOAK_PUBLIC_HOST ?? 'auth.clinicflow.localhost';
const apiHost = process.env.GATEWAY_API_HOST ?? 'api.clinicflow.localhost';
const publicUrl = process.env.CLINICFLOW_PUBLIC_URL ?? 'https://clinicflow.localhost';
const adminUrl = process.env.KEYCLOAK_ADMIN_URL ?? 'http://localhost:8180';
const issuer = `https://${authHost}/realms/clinicflow`;
const ca = fs.readFileSync(path.join(root, 'secrets/edge/ca.crt'));
const envFile = name => Object.fromEntries(fs.readFileSync(path.join(root, 'secrets/keycloak', name), 'utf8').split(/\r?\n/).filter(l => l.includes('=')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));

// HTTPS to the gateway's address with the real host name for SNI and certificate checks. Docker Desktop's
// LoadBalancer resets a few percent of new connections, so a reset is retried; any HTTP status is final.
async function edge(host, pathAndQuery, options = {}) {
  for (let attempt = 1; ; attempt++) {
    try { return await edgeOnce(host, pathAndQuery, options); }
    catch (error) { if (error.code !== 'ECONNRESET' || attempt === 3) throw error; }
  }
}
function edgeOnce(host, pathAndQuery, { method = 'GET', headers = {}, body } = {}) {
  return new Promise((resolve, reject) => {
    // A fresh connection each time: through the shared agent, Traefik reset these SNI-routed requests.
    const request = https.request({ host: address, port: 443, servername: host, ca, agent: false, method, path: pathAndQuery, headers: { host, ...headers }, timeout: 20000 }, response => {
      let data = '';
      response.on('data', chunk => { data += chunk; });
      response.on('end', () => resolve({ status: response.statusCode, headers: response.headers, body: data }));
    });
    request.on('error', reject);
    request.on('timeout', () => request.destroy(new Error(`${method} ${host}${pathAndQuery} timed out`)));
    if (body) request.write(body);
    request.end();
  });
}
const form = values => new URLSearchParams(values).toString();
const claims = token => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString());
const cookieHeader = setCookies => (setCookies ?? []).map(c => c.split(';')[0]).join('; ');

// The BFF's authorization-code flow, performed by hand through the gateway: login page, credential post,
// redirect with the code, then the confidential token exchange on the public token endpoint.
async function codeFlowToken(email, password) {
  const verifier = crypto.randomBytes(32).toString('base64url');
  const state = crypto.randomBytes(12).toString('base64url');
  const redirectUri = `${publicUrl}/api/auth/callback`;
  const authorize = await edge(authHost, `/realms/clinicflow/protocol/openid-connect/auth?${form({ client_id: 'clinicflow-bff', response_type: 'code', scope: 'openid', state, redirect_uri: redirectUri, code_challenge: crypto.createHash('sha256').update(verifier).digest('base64url'), code_challenge_method: 'S256' })}`);
  assert.equal(authorize.status, 200, 'The login page loads through the gateway');
  const action = new URL(authorize.body.match(/id="kc-form-login"[^>]*action="([^"]+)"/)[1].replaceAll('&amp;', '&'));
  assert.equal(action.host, authHost, 'The login form posts to the public auth host');
  const login = await edge(authHost, action.pathname + action.search, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded', cookie: cookieHeader(authorize.headers['set-cookie']) }, body: form({ username: email, password, credentialId: '' }) });
  assert.equal(login.status, 302, `The credential post redirects to the BFF callback (got ${login.status})`);
  const callback = new URL(login.headers.location);
  assert.equal(callback.searchParams.get('state'), state);
  const secret = envFile('clinicflow-bff.env').CLINICFLOW_BFF_CLIENT_SECRET;
  const exchange = await edge(authHost, '/realms/clinicflow/protocol/openid-connect/token', { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded', authorization: `Basic ${Buffer.from(`clinicflow-bff:${secret}`).toString('base64')}` }, body: form({ grant_type: 'authorization_code', code: callback.searchParams.get('code'), redirect_uri: redirectUri, code_verifier: verifier }) });
  assert.equal(exchange.status, 200, 'The code exchange succeeds on the public token endpoint');
  return JSON.parse(exchange.body).access_token;
}

async function admin(method, pathName, token, body) {
  const response = await fetch(`${adminUrl}${pathName}`, { method, headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, body: body && JSON.stringify(body) });
  if (!response.ok) throw new Error(`${method} ${pathName} returned ${response.status}`);
  return response;
}
const callApi = token => edge(apiHost, '/api/v1/patients', { headers: { authorization: `Bearer ${token}` } });

// The resilience check reuses the gateway sign-in and API call while it restarts Keycloak.
module.exports = { codeFlowToken, callApi };
if (require.main === module) (async () => {
  // 1. Control: a token from the clinicflow realm, issued through the gateway, is accepted.
  const valid = await codeFlowToken('sofia.almeida@demo.clinicflow.local', process.env.CLINICFLOW_DEMO_PASSWORD ?? 'ClinicFlowDemo!2026');
  assert.equal(claims(valid).iss, issuer);
  assert.equal((await callApi(valid)).status, 200, 'The clinicflow realm token is accepted');
  console.log(`PASS clinicflow realm token accepted (iss ${issuer})`);

  // 2. A correctly signed Keycloak token from another realm, with the same audience and roles, is refused.
  const credentials = envFile('admin.env');
  const masterLogin = await fetch(`${adminUrl}/realms/master/protocol/openid-connect/token`, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: form({ grant_type: 'password', client_id: 'admin-cli', username: credentials.KC_BOOTSTRAP_ADMIN_USERNAME, password: credentials.KC_BOOTSTRAP_ADMIN_PASSWORD }) });
  assert.equal(masterLogin.status, 200, 'The administrator signs in through the port-forward');
  const adminToken = (await masterLogin.json()).access_token;
  const realm = `clinicflow-probe-${crypto.randomBytes(4).toString('hex')}`;
  const password = crypto.randomBytes(18).toString('base64url');
  await admin('POST', '/admin/realms', adminToken, {
    realm, enabled: true,
    roles: { realm: [{ name: 'PSYCHOLOGIST' }] },
    clients: [{ clientId: 'probe', publicClient: true, directAccessGrantsEnabled: true, standardFlowEnabled: false, protocolMappers: [
      { name: 'audience', protocol: 'openid-connect', protocolMapper: 'oidc-audience-mapper', config: { 'included.custom.audience': 'clinicflow-api', 'access.token.claim': 'true' } },
      { name: 'roles', protocol: 'openid-connect', protocolMapper: 'oidc-usermodel-realm-role-mapper', config: { 'claim.name': 'roles', multivalued: 'true', 'jsonType.label': 'String', 'access.token.claim': 'true' } },
    ] }],
    users: [{ username: 'probe@example.test', email: 'probe@example.test', emailVerified: true, enabled: true, firstName: 'Issuer', lastName: 'Probe', realmRoles: ['PSYCHOLOGIST'], credentials: [{ type: 'password', value: password, temporary: false }] }],
  });
  try {
    const probe = await fetch(`${adminUrl}/realms/${realm}/protocol/openid-connect/token`, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: form({ grant_type: 'password', client_id: 'probe', username: 'probe@example.test', password }) });
    assert.equal(probe.status, 200, 'The temporary realm issues a token');
    const other = (await probe.json()).access_token;
    assert.equal(claims(other).iss, `https://${authHost}/realms/${realm}`);
    assert.deepEqual([claims(other).aud].flat().includes('clinicflow-api'), true);
    assert.equal((await callApi(other)).status, 401, 'Another realm\'s token is refused');
    console.log(`PASS token from realm ${realm} refused`);
  } finally {
    await admin('DELETE', `/admin/realms/${realm}`, adminToken);
    console.log(`Removed temporary realm ${realm}`);
  }

  // 3. The clinicflow issuer and claims, signed by a key Keycloak never published under a forged kid.
  const { privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
  const realKid = JSON.parse(Buffer.from(valid.split('.')[0], 'base64url').toString()).kid;
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid: realKid })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({ ...claims(valid), jti: crypto.randomUUID() })).toString('base64url');
  const forged = `${header}.${payload}.${crypto.sign('RSA-SHA256', Buffer.from(`${header}.${payload}`), privateKey).toString('base64url')}`;
  assert.equal((await callApi(forged)).status, 401, 'A forged signature is refused');
  console.log('PASS forged signature refused');

  // 4. The genuine token with one changed claim no longer verifies.
  const [h, , s] = valid.split('.');
  const tampered = `${h}.${Buffer.from(JSON.stringify({ ...claims(valid), roles: ['ADMIN'] })).toString('base64url')}.${s}`;
  assert.equal((await callApi(tampered)).status, 401, 'A tampered token is refused');
  console.log('PASS tampered token refused');
  console.log('Keycloak issuer acceptance passed.');
})().catch(error => { console.error(error.message); process.exit(1); });
