// Exercise installed Gateway API route specifications against the live controller.
// Temporary, header-selected routes keep normal application traffic on its backends.
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const http = require('node:http'), https = require('node:https');
const {execFileSync} = require('node:child_process'), {randomUUID} = require('node:crypto');
const root = path.resolve(__dirname, '../..'), matrix = require('../../deploy/gateway/routes.json');
const namespace = process.env.GATEWAY_TEST_NAMESPACE || 'clinicflow';
const ingress = process.env.GATEWAY_TEST_INGRESS_NAMESPACE || 'ingress-system';
const address = process.env.GATEWAY_TEST_RUNTIME_HOST || '127.0.0.1';
const run = `edge-contract-${randomUUID().slice(0, 8)}`, labels = {'clinicflow.io/contract': run};
const ca = fs.readFileSync(path.join(root, 'secrets/edge/ca.crt'));
const kubectl = (...args) => execFileSync('kubectl', args, {encoding:'utf8', timeout:180000});
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const apply = object => execFileSync('kubectl', ['apply','-f','-'], {input:JSON.stringify(object), encoding:'utf8'});
function request(target, {host='api.clinicflow.localhost', method='GET', body='', headers={}, plain=false}={}) {
  return new Promise((resolve, reject) => {
    const req = (plain ? http : https).request({hostname:address, port:plain ? 80 : 443, path:target,
      servername:host, ca, method, headers:{host,'x-clinicflow-contract':run,...headers}}, response => {
      const chunks=[]; response.on('data',chunk=>chunks.push(chunk));
      response.on('end',()=>resolve({status:response.statusCode, headers:response.headers,text:Buffer.concat(chunks).toString()}));
    });
    req.setTimeout(20000,()=>req.destroy(new Error('Gateway request exceeded the test deadline')));
    req.on('error',reject); req.end(body);
  });
}
async function main() {
  const original = JSON.parse(kubectl('get','httproutes','-n',namespace,'-o','json'));
  const routes = original.items.filter(item=>/^(public|internal)-/.test(item.metadata.name) && item.spec.rules[0].backendRefs);
  assert.equal(routes.length, matrix.length*2, 'Expected every installed public/private business route');
  apply({apiVersion:'v1',kind:'ConfigMap',metadata:{name:run,namespace,labels},
    data:{'server.cjs':fs.readFileSync(path.join(root,'deploy/gateway/contract/server.cjs'),'utf8')}});
  apply({apiVersion:'v1',kind:'Pod',metadata:{name:run,namespace,labels:{...labels,'app.kubernetes.io/component':'patient-service'}},
    spec:{automountServiceAccountToken:false,restartPolicy:'Never',containers:[{name:'fixture',
      image:'node:24.19.0-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43',
      command:['node','/fixture/server.cjs'],resources:{requests:{cpu:'10m',memory:'32Mi'},limits:{cpu:'100m',memory:'96Mi'}},
      securityContext:{runAsNonRoot:true,runAsUser:1000,readOnlyRootFilesystem:true,allowPrivilegeEscalation:false,capabilities:{drop:['ALL']}},
      readinessProbe:{httpGet:{path:'/',port:9090}},volumeMounts:[{name:'fixture',mountPath:'/fixture',readOnly:true}]}],
      volumes:[{name:'fixture',configMap:{name:run}}]}});
  apply({apiVersion:'v1',kind:'Service',metadata:{name:run,namespace,labels},spec:{selector:labels,
    ports:[8082,8083,8084,8085].map(port=>({name:`app-${port}`,port,targetPort:port}))}});
  apply({apiVersion:'networking.k8s.io/v1',kind:'NetworkPolicy',metadata:{name:run,namespace,labels},
    spec:{podSelector:{matchLabels:labels},policyTypes:['Ingress','Egress'],egress:[],ingress:[{
      from:[{namespaceSelector:{matchLabels:{'kubernetes.io/metadata.name':ingress}},podSelector:{matchLabels:{'clinicflow.io/edge':'gateway'}}}],
      ports:[8082,8083,8084,8085].map(port=>({port,protocol:'TCP'}))}]}});
  kubectl('wait','--for=condition=Ready',`pod/${run}`,'-n',namespace,'--timeout=120s');
  for(const route of routes) {
    const spec=structuredClone(route.spec);
    for(const rule of spec.rules) {
      for(const match of rule.matches) match.headers=[{type:'Exact',name:'x-clinicflow-contract',value:run}];
      for(const backend of rule.backendRefs) backend.name=run;
    }
    apply({apiVersion:route.apiVersion,kind:'HTTPRoute',metadata:{name:`${run}-${route.metadata.name}`,namespace,labels},spec});
  }
  let accepted=false;
  for(let i=0;i<90;i++) {
    const current=JSON.parse(kubectl('get','httproutes','-n',namespace,'-l',`clinicflow.io/contract=${run}`,'-o','json'));
    accepted=current.items.length===routes.length && current.items.every(route=>route.status?.parents?.length &&
      route.status.parents.every(parent=>['Accepted','ResolvedRefs'].every(type=>parent.conditions.some(condition=>
        condition.type===type && condition.status==='True' && condition.observedGeneration===route.metadata.generation))));
    if(accepted) break;
    await sleep(1000);
  }
  assert(accepted,'Temporary routes were not accepted at their current generations');
  let serving=false;
  for(let i=0;i<30;i++) {
    serving=true;
    // A partial provider update can serve earlier routes while later ones still
    // use their ordinary backend. Do not consume the auth buckets while waiting.
    for(const route of matrix.filter(route=>route.policy!=='auth')) {
      const response=await request(`${route.path}?ready=1`);
      serving=serving && response.status===200 && JSON.parse(response.text).port===route.port;
    }
    if(serving)break;
    await sleep(1000);
  }
  assert(serving,'Accepted fixture routes did not become routable');
  let cases=0;
  for(const route of matrix) for(const suffix of route.match==='Exact' ? [''] : ['', '/id%20encoded']) {
    const response=await request(`${route.path}${suffix}?page=2&search=live-redaction-marker`,{method:'POST',body:'{"value":"preserved"}',
      headers:{authorization:'Bearer live-redaction-token','x-correlation-id':'live-redaction-correlation',cookie:'live-secret-cookie'}});
    assert.equal(response.status,200,`${route.id}${suffix}`);
    const echo=JSON.parse(response.text);
    assert.equal(echo.port,route.port); assert.equal(echo.url,`${route.upstream}${suffix}?page=2&search=live-redaction-marker`);
    assert.equal(echo.method,'POST'); assert.equal(echo.body,'{"value":"preserved"}'); assert.equal(echo.authorization,'Bearer live-redaction-token');
    assert.equal(echo.correlation,'live-redaction-correlation'); assert.equal(response.headers['x-correlation-id'],'live-redaction-correlation');
    assert.equal(response.headers['x-content-type-options'],'nosniff'); assert.equal(response.headers['x-frame-options'],'DENY');
    assert.equal(response.headers['referrer-policy'],'no-referrer'); assert(response.headers['strict-transport-security']); cases++;
  }
  for(const target of ['/api/v1/unknown','/api/v1/patients-other','/actuator/health','/dashboard','/api/rawdata']) assert.equal((await request(target)).status,404,target);
  assert.equal((await request('/',{host:'unknown.localhost',plain:true})).status,404);
  const failure=await request('/api/v1/patients?status=503',{method:'POST',body:'mutation'});
  assert.equal(failure.status,503); assert.equal(JSON.parse(failure.text).count,1,'Mutation was retried');
  const started=Date.now(); assert.equal((await request('/api/v1/patients?delay=16000')).status,504); assert(Date.now()-started<17000);
  const control=action=>kubectl('exec',run,'-n',namespace,'--','node','-e',`fetch('http://127.0.0.1:9090/${action}?port=8082').then(r=>r.text())`);
  control('stop'); assert.equal((await request('/api/v1/patients')).status,502);
  assert.equal((await request('/api/v1/appointments')).status,200); control('start');
  const throttle=[];
  // The auth quota guards the sign-in routes, served by the real BFF: it answers a login with a redirect
  // to Keycloak, so the forwarded address is not echoed here; the Compose contract checks it.
  const login=headers=>request('/api/auth/login',{host:'clinicflow.localhost',headers});
  for(let i=0;i<8;i++) throttle.push(await login({'x-forwarded-for':`198.51.100.${i}`}));
  assert(throttle.some(response=>response.status===429),'Forwarded addresses bypassed the public auth quota');
  assert(throttle.some(response=>response.status===303),'Auth quota positive control missing');
  const frontend=JSON.parse(kubectl('get','pods','-n',namespace,'-l','app.kubernetes.io/component=frontend','-o','json')).items[0].metadata.name;
  const privateCode=`fetch('http://clinicflow-gateway-api.${ingress}.svc.cluster.local:8080/api/v1/patients',{headers:{'x-clinicflow-contract':'${run}'}}).then(async r=>{if(r.status!==200)process.exit(1);console.log((await r.json()).port)})`;
  assert.equal(kubectl('exec',frontend,'-n',namespace,'--','node','-e',privateCode).trim(),'8082');
  await sleep(6500); assert.equal((await login()).status,303,'Auth bucket did not refill');
  // Fail the real patient deployment briefly; BFF calls have no fixture marker.
  const session=await request('/api/auth/login',{host:'clinicflow.localhost',method:'POST',
    headers:{'content-type':'application/json'},body:JSON.stringify({email:'sofia.almeida@demo.clinicflow.local',password:'ClinicFlowDemo!2026'})});
  assert.equal(session.status,200,'BFF outage-test login failed');
  const cookie=session.headers['set-cookie'].map(value=>value.split(';')[0]).join('; ');
  const deployment=JSON.parse(kubectl('get','deployment','patient-service','-n',namespace,'-o','json'));
  try {
    kubectl('scale','deployment/patient-service','-n',namespace,'--replicas=0');
    let empty=false;
    for(let i=0;i<30;i++) {
      const slices=JSON.parse(kubectl('get','endpointslices','-n',namespace,'-l','kubernetes.io/service-name=patient-service','-o','json'));
      empty=slices.items.every(slice=>!slice.endpoints?.some(endpoint=>endpoint.conditions?.ready!==false));
      if(empty)break;
      await sleep(1000);
    }
    assert(empty,'Patient endpoints did not drain');
    let unavailable;
    for(let i=0;i<30;i++) {
      unavailable=await request('/api/patients',{host:'clinicflow.localhost',headers:{cookie}});
      if(unavailable.status===503)break;
      await sleep(1000);
    }
    assert.equal(unavailable.status,503,'BFF did not retain the gateway outage status');
    assert(unavailable.headers['content-type'].includes('application/json'));
    const problem=JSON.parse(unavailable.text);assert.equal(problem.status,503);assert(problem.message);
    assert.equal((await request('/api/appointments',{host:'clinicflow.localhost',headers:{cookie}})).status,200,'Unrelated BFF service failed');
  } finally {
    kubectl('scale','deployment/patient-service','-n',namespace,`--replicas=${deployment.spec.replicas}`);
    kubectl('rollout','status','deployment/patient-service','-n',namespace,'--timeout=5m');
  }
  let recovered=false;
  for(let i=0;i<30;i++) {
    recovered=(await request('/api/patients',{host:'clinicflow.localhost',headers:{cookie}})).status===200;
    if(recovered)break;
    await sleep(1000);
  }
  assert(recovered,'Patient did not recover after endpoint reconciliation');
  const redirect=await request('/api/v1/patients?x=1',{method:'POST',plain:true});
  assert.equal(redirect.status,308); assert.equal(new URL(redirect.headers.location).href,'https://api.clinicflow.localhost/api/v1/patients?x=1');
  const logs=kubectl('logs','-n',ingress,'-l','clinicflow.io/edge=gateway,app.kubernetes.io/name=traefik','--since=5m','--tail=5000');
  const access=logs.split('\n').filter(line=>line.includes('DownstreamStatus')).map(line=>JSON.parse(line));
  assert(access.length>cases,'No live access log evidence');
  for(const entry of access) for(const key of Object.keys(entry)) assert(['StartUTC','RequestMethod','RouterName','ServiceName','DownstreamStatus','OriginStatus','Duration','level','time','message','msg'].includes(key),`Unexpected access-log field ${key}`);
  for(const sentinel of ['live-redaction-token','live-secret-cookie','live-redaction-marker','live-redaction-correlation']) assert(!logs.includes(sentinel),`Unredacted log content: ${sentinel}`);
  console.log(`PASS Kubernetes HTTPS: ${cases} shared route cases; current-generation route conditions; strict CA/SAN; headers; quotas/refill/XFF/private quota; 404; timeout/outage/no retry; JSON log redaction.`);
}
main().catch(error=>{console.error(error.message);process.exitCode=1;}).finally(async()=>{
  kubectl('delete','httproutes','-n',namespace,'-l',`clinicflow.io/contract=${run}`,'--ignore-not-found');
  await sleep(2500);
  kubectl('delete','pods,services,configmaps,networkpolicies','-n',namespace,'-l',`clinicflow.io/contract=${run}`,'--ignore-not-found','--wait=false');
});
