// Validate every gateway custom resource against committed, checksum-verified CRDs.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {createHash} = require('node:crypto');
const root = path.resolve(__dirname, '../..');
const YAML = require(path.join(root, 'frontend/node_modules/yaml'));
const Ajv = require(path.join(root, 'frontend/node_modules/ajv'));
const read = name => fs.readFileSync(path.isAbsolute(name) ? name : path.join(root, name), 'utf8');
const documents = name => YAML.parseAllDocuments(read(name)).map(d => { if (d.errors.length) throw d.errors[0]; return d.toJSON(); }).filter(Boolean);
const provenance = JSON.parse(read('deploy/gateway/provenance.json'));
const keycloakProvenance = JSON.parse(read('deploy/helm/keycloak/provenance.json'));
for (const artifact of [...provenance.artifacts, ...keycloakProvenance.artifacts]) {
  assert.equal(createHash('sha256').update(fs.readFileSync(path.join(root,artifact.path))).digest('hex'), artifact.sha256, `Checksum: ${artifact.path}`);
}
const ajv = new Ajv({strict:false, allErrors:true, validateFormats:false});
const schemas = new Map();
// Kubernetes runs CEL and controller semantic validation live; this check covers structural schemas.
function closeObjects(schema) {
  if (!schema || typeof schema !== 'object') return;
  if (schema.type === 'object' && schema.properties && schema.additionalProperties === undefined && !schema['x-kubernetes-preserve-unknown-fields']) schema.additionalProperties = false;
  for (const [key,value] of Object.entries(schema)) if (key !== 'default' && value && typeof value === 'object') {
    if (Array.isArray(value)) value.forEach(closeObjects); else closeObjects(value);
  }
}
for (const file of ['deploy/gateway/crds/gateway-api-1.6.2.yaml','deploy/gateway/crds/traefik-41.6.1.yaml']) {
  for (const crd of documents(file).filter(d => d.kind === 'CustomResourceDefinition')) {
    for (const version of crd.spec.versions.filter(v => v.served)) {
      const schema = structuredClone(version.schema.openAPIV3Schema);
      closeObjects(schema);
      schemas.set(`${crd.spec.group}/${version.name}/${crd.spec.names.kind}`, ajv.compile(schema));
    }
  }
}
const inputs = process.argv.slice(2);
if (!inputs.length) throw Error('Pass rendered gateway and controller manifests; missing schemas are fatal.');
const docs = inputs.flatMap(documents);
let count = 0;
for (const doc of docs) {
  if (!/^(gateway.networking.k8s.io|traefik.io)\//.test(doc.apiVersion)) continue;
  const key = `${doc.apiVersion}/${doc.kind}`;
  const validate = schemas.get(key);
  assert(validate, `Missing pinned schema: ${key}`);
  assert(validate(doc), `${doc.kind}/${doc.metadata.name}: ${ajv.errorsText(validate.errors)}`);
  count++;
}
assert(count > 25, 'Missing gateway custom resources.');
const routes = docs.filter(d => d.kind === 'HTTPRoute');
const contract = JSON.parse(read('deploy/gateway/routes.json'));
for (const c of contract) for (const visibility of ['public','internal']) {
  const route = routes.find(r => r.metadata.name === `${visibility}-${c.id}`);
  assert(route, `Missing ${visibility}-${c.id}`);
  const rule=route.spec.rules[0], rewrite=rule.filters.find(f=>f.type==='URLRewrite').urlRewrite.path;
  assert.deepEqual(rule.matches[0].path,{type:c.match,value:c.path});
  assert.equal(rule.backendRefs[0].name,c.backend);
  assert.equal(rule.backendRefs[0].port,c.port);
  assert.equal(rewrite[c.match === 'Exact' ? 'replaceFullPath' : 'replacePrefixMatch'],c.upstream);
  if (visibility === 'public') assert.deepEqual(route.spec.hostnames,['api.clinicflow.localhost']);
  else assert.equal(route.spec.parentRefs[0].sectionName,'internal-api');
}
const gateway = docs.find(d=>d.kind==='Gateway');
assert(gateway);
for (const listener of gateway.spec.listeners) assert.equal(listener.allowedRoutes.namespaces.from,'Same');
assert.equal(gateway.spec.listeners.find(l=>l.name==='internal-api').port,8080);
const services=docs.filter(d=>d.kind==='Service');
const privateService=services.find(s=>s.metadata.name==='clinicflow-gateway-api');
assert.equal(privateService.spec.type,'ClusterIP');
assert.deepEqual(privateService.spec.ports.map(p=>p.port),[8080]);
const publicService=services.find(s=>s.metadata.name==='clinicflow-gateway-controller');
assert.deepEqual(publicService.spec.ports.map(p=>p.port).sort((a,b)=>a-b),[80,443]);
const deployment=docs.find(d=>d.kind==='Deployment'&&d.metadata.name==='clinicflow-gateway-controller');
assert(deployment.spec.template.spec.containers[0].image.endsWith(provenance.image.split('@')[1]),'Controller image must use the verified proxy digest');
assert.equal(deployment.spec.replicas,1);
assert.equal(deployment.spec.strategy.type,'Recreate');
const policy=docs.find(d=>d.kind==='NetworkPolicy'&&d.metadata.name==='clinicflow-gateway-controller');
for (const [k,v] of Object.entries(policy.spec.podSelector.matchLabels)) assert.equal(deployment.spec.template.metadata.labels[k],v,'Controller policy must select the actual pods');
const args=deployment.spec.template.spec.containers[0].args;
assert(!args.some(a=>a==='--api.insecure=true'||a==='--providers.kubernetesingress'));
assert(args.some(a=>a==='--providers.kubernetesgateway'));
assert(args.some(a=>a==='--accesslog.fields.defaultmode=drop'));
assert(args.some(a=>a==='--accesslog.fields.queryparameters.defaultmode=drop'));
assert(!routes.filter(r=>r.spec.hostnames?.includes('api.clinicflow.localhost')).some(r=>r.spec.rules.some(rule=>rule.backendRefs?.some(b=>b.name==='frontend'))));

// ADR-027 (#89): the auth host routes four path prefixes of the clinicflow realm to Keycloak, and nothing else.
// /admin, /realms/master and the account console stay unrouted; only login-actions carries the login limit.
const authListener=gateway.spec.listeners.find(l=>l.name==='auth-https');
assert(authListener,'Missing the auth host HTTPS listener');
const authHost=authListener.hostname;
const identityRoutes={'identity-oidc':['/realms/clinicflow/protocol/openid-connect','public-limit'],'identity-login':['/realms/clinicflow/login-actions','auth-limit'],'identity-discovery':['/realms/clinicflow/.well-known','public-limit'],'identity-resources':['/resources','public-limit']};
const authRoutes=routes.filter(r=>r.spec.hostnames?.includes(authHost));
assert.deepEqual(authRoutes.map(r=>r.metadata.name).sort(),['auth-redirect',...Object.keys(identityRoutes)].sort(),'The auth host must carry exactly the identity routes and its redirect');
for(const [name,[path,limit]] of Object.entries(identityRoutes)) {
  const route=authRoutes.find(r=>r.metadata.name===name);
  assert.equal(route.spec.parentRefs[0].sectionName,'auth-https');
  assert.equal(route.spec.rules.length,1);
  const rule=route.spec.rules[0];
  assert.deepEqual(rule.matches,[{path:{type:'PathPrefix',value:path}}]);
  assert.deepEqual(rule.backendRefs,[{name:'keycloak',port:8080}]);
  assert.deepEqual(rule.filters.map(f=>f.extensionRef?.name),['secure-headers',limit]);
}
const redirect=authRoutes.find(r=>r.metadata.name==='auth-redirect');
assert.equal(redirect.spec.parentRefs[0].sectionName,'auth-http');
assert.deepEqual(redirect.spec.rules,[{filters:[{type:'RequestRedirect',requestRedirect:{scheme:'https',port:443,statusCode:308}}]}]);
for(const route of routes) for(const rule of route.spec.rules) {
  const toKeycloak=(rule.backendRefs||[]).some(b=>b.name==='keycloak');
  assert.equal(toKeycloak,route.spec.hostnames?.includes(authHost)===true&&route.metadata.name!=='auth-redirect',`${route.metadata.name}: only identity routes may reach Keycloak`);
  if(toKeycloak) for(const match of rule.matches) assert(match.path.value.startsWith('/realms/clinicflow/')||match.path.value==='/resources',`${route.metadata.name}: only the clinicflow realm and theme resources reach Keycloak`);
}
const keycloakEgress=policy.spec.egress.filter(r=>r.to.some(peer=>peer.podSelector?.matchLabels?.['app.kubernetes.io/component']==='keycloak'));
assert.equal(keycloakEgress.length,1,'The controller reaches Keycloak through one dedicated rule');
assert.deepEqual(keycloakEgress[0].ports,[{port:8080,protocol:'TCP'}]);
console.log(`Validated ${count} gateway custom resources against pinned schemas and the shared route/network contract.`);

const frontend=docs.find(d=>d.kind==='Deployment'&&d.metadata.name==='frontend');
if(frontend) {
  const config=docs.find(d=>d.kind==='ConfigMap'&&d.data?.GATEWAY_URL);
  assert.equal(config.data.GATEWAY_URL,'http://clinicflow-gateway-api.ingress-system.svc.cluster.local:8080');
  assert.equal(config.data.CLINICFLOW_AUTH_COOKIE_SECURE,'true');
  assert(!Object.keys(config.data).some(k=>k.endsWith('_SERVICE_URL')),'Deployed BFF must not have direct business URLs');
  assert(frontend.spec.template.metadata.annotations['checksum/config']);
  assert('checksum/client-secret' in frontend.spec.template.metadata.annotations,'A rotated BFF client secret must roll the BFF');
  const policies=docs.filter(d=>d.kind==='NetworkPolicy');
  const selects=(policy,pod)=>Object.keys(policy.spec.podSelector.matchLabels||{}).length>0 && Object.entries(policy.spec.podSelector.matchLabels||{}).every(([k,v])=>pod.spec.template.metadata.labels[k]===v);
  const policy=policies.find(p=>selects(p,frontend));
  assert(policy);
  assert.deepEqual(policy.spec.egress.flatMap(r=>r.ports.map(p=>p.port)).sort((a,b)=>a-b),[53,53,8080,8080,9000]);
  // ADR-027: the BFF reaches Keycloak only, on its HTTP port, for code exchange, refresh, keys and logout.
  const realmPeers=policy.spec.egress.filter(r=>r.to.some(peer=>peer.podSelector?.matchLabels?.['app.kubernetes.io/component']==='keycloak'));
  assert.equal(realmPeers.length,1);
  assert.deepEqual(realmPeers[0].ports,[{port:8080,protocol:'TCP'}]);
  for(const peer of policy.spec.ingress.flatMap(r=>r.from)) { assert(peer.namespaceSelector);assert.equal(peer.podSelector.matchLabels['clinicflow.io/edge'],'gateway'); }
  // ADR-021: ownership checks are the only service-to-service application flows (callee -> callers).
  const ownershipCallers={'patient-service':['appointment-service','clinical-service'],'appointment-service':['clinical-service']};
  const backendPort=name=>contract.find(r=>r.backend===name)?.port;
  for(const backend of docs.filter(d=>d.kind==='Deployment'&&contract.some(r=>r.backend===d.metadata.name))) {
    const backendPolicy=policies.find(p=>selects(p,backend));assert(backendPolicy);
    const port=backendPort(backend.metadata.name);
    for(const rule of backendPolicy.spec.ingress) {
      assert(rule.from?.length,'Backend ingress must have explicit peers');
      for(const peer of rule.from) {assert(peer.namespaceSelector);assert(peer.podSelector);}
      if(rule.ports.some(p=>p.port===port)) for(const peer of rule.from) {
        if(peer.podSelector.matchLabels['clinicflow.io/edge']==='gateway') continue;
        const caller=peer.podSelector.matchLabels['app.kubernetes.io/component'];
        assert((ownershipCallers[backend.metadata.name]||[]).includes(caller),`${backend.metadata.name} must not accept application traffic from ${caller}`);
        assert.equal(peer.namespaceSelector.matchLabels['kubernetes.io/metadata.name'],'clinicflow');
      }
    }
    for(const rule of backendPolicy.spec.egress) for(const peer of rule.to||[]) {
      const callee=peer.podSelector?.matchLabels?.['app.kubernetes.io/component'];
      if(!backendPort(callee)) continue;
      assert((ownershipCallers[callee]||[]).includes(backend.metadata.name),`${backend.metadata.name} must not call ${callee}`);
      assert.deepEqual(rule.ports.map(p=>p.port),[backendPort(callee)]);
    }
  }
  console.log('Validated BFF gateway-only configuration/checksum and frontend/backend application-management policy boundaries.');
}

const keycloak=docs.find(d=>d.kind==='StatefulSet'&&d.metadata.name==='keycloak');
if(keycloak) {
  // ADR-027 (#89): one Keycloak replica, configured for the gateway, behind the keycloak:8080 contract.
  const pod=keycloak.spec.template, container=pod.spec.containers[0];
  const env=Object.fromEntries(container.env.map(e=>[e.name,e]));
  const value=name=>{assert(env[name],`Keycloak must set ${name}`);assert.equal(typeof env[name].value,'string',`${name} must be a literal`);return env[name].value;};
  assert.equal(keycloak.spec.replicas,1,'KC_CACHE=local is single-node');
  assert.equal(value('KC_CACHE'),'local');
  assert(!env.KC_CACHE_STACK,'No JGroups discovery stack on a single node');
  assert.equal(value('KC_PROXY_HEADERS'),'xforwarded');
  assert.equal(value('KC_HTTP_RELATIVE_PATH'),'/');
  assert.equal(value('KC_HOSTNAME_BACKCHANNEL_DYNAMIC'),'true');
  assert(Number(value('KC_DB_POOL_MAX_SIZE'))>=1&&Number(value('KC_DB_POOL_MAX_SIZE'))<=10,'Keycloak shares PostgreSQL\'s 100 connections');
  assert.match(value('KC_HOSTNAME_ADMIN'),/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/,'The admin console is reached through a port-forward only');
  const hostname=new URL(value('KC_HOSTNAME'));
  assert.equal(hostname.protocol,'https:');
  assert.equal(hostname.host,authHost,'Keycloak\'s public host is the gateway\'s auth host');
  const certificateHosts=[...read('scripts/gateway/New-DevelopmentCertificate.ps1').match(/\$Hostnames\s*=\s*@\(([^)]*)\)/)[1].matchAll(/'([^']+)'/g)].map(m=>m[1]);
  assert(certificateHosts.includes(authHost),'The development certificate must cover the auth host');
  // Credentials come from Secrets only; the demo password is synthetic and documented.
  for(const e of container.env) if(/PASSWORD|SECRET/.test(e.name)&&e.name!=='CLINICFLOW_DEMO_PASSWORD') assert(!('value' in e),`${e.name} must come from a Secret`);
  assert.deepEqual(container.envFrom.map(s=>s.secretRef?.name).sort(),['clinicflow-bff-oidc','clinicflow-keycloak-admin','clinicflow-keycloak-database']);
  assert.deepEqual(container.resources,{requests:{cpu:'250m',memory:'512Mi'},limits:{cpu:'1',memory:'768Mi'}});
  const startup=container.startupProbe;
  assert((startup.initialDelaySeconds||0)+startup.periodSeconds*startup.failureThreshold>=90,'The first start needs at least 90 s');
  for(const probe of ['startupProbe','livenessProbe','readinessProbe']) assert.equal(container[probe].httpGet.port,'http-internal',`${probe} uses the management port`);
  assert.equal(container.securityContext.readOnlyRootFilesystem,true);
  assert.equal(container.securityContext.allowPrivilegeEscalation,false);
  assert.deepEqual(container.securityContext.capabilities.drop,['ALL']);
  assert.equal(pod.spec.securityContext.runAsNonRoot,true);
  assert.equal(pod.spec.securityContext.seccompProfile.type,'RuntimeDefault');
  const account=docs.find(d=>d.kind==='ServiceAccount'&&d.metadata.name===pod.spec.serviceAccountName);
  assert.equal(account?.automountServiceAccountToken,false,'Keycloak needs no Kubernetes API token');
  const emptyDirs=new Set(pod.spec.volumes.filter(v=>v.emptyDir).map(v=>v.name));
  assert.deepEqual(container.volumeMounts.map(m=>m.mountPath).sort(),['/opt/keycloak/data/tmp','/opt/keycloak/data/transaction-logs','/tmp']);
  assert(container.volumeMounts.every(m=>emptyDirs.has(m.name)),'Writable paths are scratch volumes; the realm import directory stays the image\'s');
  // The private-network name every client uses selects these pods on the HTTP port only.
  const keycloakService=docs.find(d=>d.kind==='Service'&&d.metadata.name==='keycloak');
  assert(keycloakService,'Missing the keycloak Service');
  assert.deepEqual(keycloakService.spec.ports.map(p=>[p.port,p.targetPort]),[[8080,'http']]);
  for(const [k,v] of Object.entries(keycloakService.spec.selector)) assert.equal(pod.metadata.labels[k],v,'The keycloak Service must select the Keycloak pods');
  assert.equal(pod.metadata.labels['app.kubernetes.io/component'],'keycloak');
  // One issuer everywhere: Keycloak's public origin, the BFF and every resource server agree on iss.
  const issuer=`${hostname.origin}/realms/clinicflow`;
  const configs=docs.filter(d=>d.kind==='ConfigMap'&&d.data?.CLINICFLOW_OIDC_ISSUER);
  assert(configs.length>=5,'The BFF and the four resource servers must trust Keycloak');
  for(const config of configs) assert.equal(config.data.CLINICFLOW_OIDC_ISSUER,issuer,`${config.metadata.name} must trust ${issuer}`);
  const privateUrls=configs.flatMap(c=>[c.data.CLINICFLOW_OIDC_INTERNAL_ISSUER,c.data.CLINICFLOW_OIDC_JWKS_URI]).filter(Boolean);
  assert(privateUrls.length>=5);
  for(const url of privateUrls) assert.equal(new URL(url).host,`${keycloakService.metadata.name}:${keycloakService.spec.ports[0].port}`,`${url} must use the keycloak Service`);
  const bffConfig=configs.find(c=>c.data.CLINICFLOW_OIDC_INTERNAL_ISSUER);
  assert.equal(value('CLINICFLOW_PUBLIC_URL'),bffConfig.data.CLINICFLOW_PUBLIC_URL,'The realm\'s redirect URIs follow the BFF\'s public URL');
  // Ingress: the gateway and the private-network clients on 8080, Prometheus on 9000. Egress: DNS and PostgreSQL.
  const policies=docs.filter(d=>d.kind==='NetworkPolicy');
  const keycloakPolicy=policies.find(p=>p.metadata.name==='keycloak');
  assert(keycloakPolicy);
  for(const [k,v] of Object.entries(keycloakPolicy.spec.podSelector.matchLabels)) assert.equal(pod.metadata.labels[k],v,'The Keycloak policy must select the actual pods');
  assert.deepEqual(keycloakPolicy.spec.policyTypes,['Ingress','Egress']);
  const peers=port=>keycloakPolicy.spec.ingress.filter(r=>r.ports.every(p=>p.port===port)).flatMap(r=>r.from);
  assert.equal(keycloakPolicy.spec.ingress.flatMap(r=>r.from).length,peers(8080).length+peers(9000).length,'Keycloak accepts 8080 and 9000 only');
  for(const peer of [...peers(8080),...peers(9000)]) {assert(peer.namespaceSelector,'Every peer names its namespace');assert(peer.podSelector,'Every peer names its pods');}
  const gatewayPeers=peers(8080).filter(p=>p.podSelector.matchLabels?.['clinicflow.io/edge']==='gateway');
  assert.equal(gatewayPeers.length,1);
  assert.equal(gatewayPeers[0].namespaceSelector.matchLabels['kubernetes.io/metadata.name'],'ingress-system');
  const clientPeers=peers(8080).filter(p=>p!==gatewayPeers[0]);
  assert.equal(clientPeers.length,1);
  assert.equal(clientPeers[0].namespaceSelector.matchLabels['kubernetes.io/metadata.name'],keycloak.metadata.namespace||'clinicflow');
  assert.deepEqual(clientPeers[0].podSelector.matchExpressions,[{key:'app.kubernetes.io/component',operator:'In',values:['frontend','patient-service','appointment-service','clinical-service','notification-service']}]);
  assert.deepEqual(peers(9000).map(p=>[p.namespaceSelector.matchLabels['kubernetes.io/metadata.name'],p.podSelector.matchLabels]),[['monitoring',{'app.kubernetes.io/name':'prometheus'}]]);
  assert.deepEqual(keycloakPolicy.spec.egress.flatMap(r=>r.ports.map(p=>p.port)).sort((a,b)=>a-b),[53,53,5432]);
  assert.deepEqual(keycloakPolicy.spec.egress.flatMap(r=>r.to.map(peer=>peer.podSelector.matchLabels['k8s-app']||peer.podSelector.matchLabels['app.kubernetes.io/component'])).sort(),['kube-dns','postgres']);
  const postgresPolicy=policies.find(p=>p.metadata.name==='clinicflow-postgres');
  assert(postgresPolicy.spec.ingress[0].from[0].podSelector.matchExpressions[0].values.includes('keycloak'),'PostgreSQL must admit Keycloak');
  // No other workload accepts traffic from Keycloak: it calls nothing but PostgreSQL.
  for(const other of policies.filter(p=>p!==keycloakPolicy&&p!==postgresPolicy)) for(const rule of other.spec.ingress||[]) for(const peer of rule.from||[]) {
    const selector=peer.podSelector||{};
    assert(selector.matchLabels?.['app.kubernetes.io/component']!=='keycloak'&&!(selector.matchExpressions||[]).some(e=>e.values?.includes('keycloak')),`${other.metadata.name} must not admit Keycloak`);
  }
  console.log('Validated the Keycloak release: one issuer across charts, the keycloak:8080 contract, hardening and network policy.');
}