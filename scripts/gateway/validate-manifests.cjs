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
for (const artifact of provenance.artifacts) {
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
console.log(`Validated ${count} gateway custom resources against pinned schemas and the shared route/network contract.`);

const frontend=docs.find(d=>d.kind==='Deployment'&&d.metadata.name==='frontend');
if(frontend) {
  const config=docs.find(d=>d.kind==='ConfigMap'&&d.data?.GATEWAY_URL);
  assert.equal(config.data.GATEWAY_URL,'http://clinicflow-gateway-api.ingress-system.svc.cluster.local:8080');
  assert.equal(config.data.CLINICFLOW_AUTH_COOKIE_SECURE,'true');
  assert(!Object.keys(config.data).some(k=>k.endsWith('_SERVICE_URL')),'Deployed BFF must not have direct business URLs');
  assert(frontend.spec.template.metadata.annotations['checksum/config']);
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
