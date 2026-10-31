// Real pinned Traefik acceptance, using the SAME route matrix as both providers.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const http=require('node:http'),https=require('node:https'),{execFileSync}=require('node:child_process'),{randomUUID}=require('node:crypto');
const root=path.resolve(__dirname,'../..'),contract=require('../../deploy/gateway/routes.json');
const compose=['compose','--file',path.join(root,'deploy/gateway/contract/compose.yaml'),'--project-name',`clinicflow-gateway-contract-${process.pid}-${randomUUID().slice(0,8)}`];
const run=(...args)=>execFileSync('docker',[...compose,...args],{cwd:root,stdio:['ignore','pipe','pipe'],encoding:'utf8'});
const tls=process.env.GATEWAY_TEST_TLS==='true',port=Number(process.env[tls?'GATEWAY_TEST_HTTPS_PORT':'GATEWAY_TEST_HTTP_PORT']||(tls?19443:19080));
let ca;
const runtimeHost=process.env.GATEWAY_TEST_RUNTIME_HOST||'127.0.0.1';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function request(target,{host='api.clinicflow.localhost',method='GET',body='',headers={},plain=false}={}) {
  return new Promise((resolve,reject)=>{
    const encrypted=tls&&!plain;
    const req=(encrypted?https:http).request({hostname:runtimeHost,port:plain?Number(process.env.GATEWAY_TEST_HTTP_PORT||19080):port,path:target,method,servername:host,ca,headers:{host,...headers}},res=>{
      const chunks=[];res.on('data',c=>chunks.push(c));res.on('end',()=>resolve({status:res.statusCode,headers:res.headers,text:Buffer.concat(chunks).toString()}));
    });req.on('error',reject);req.end(body);
  });
}
async function main() {
  run('up','--detach','--build');
  if(tls) ca=Buffer.from(run('exec','-T','gateway','cat','/certs/ca.crt'));
  let ready=false;
  for(let i=0;i<60;i++) { try { const r=await request('/login',{host:'clinicflow.localhost'}); if(r.status===200){ready=true;break;} } catch {} await sleep(500); }
  assert(ready,'Gateway did not start or TLS identity validation failed');
  let cases=0;
  for(const route of contract) {
    const suffixes=route.match==='Exact'?['']:['','/id%20encoded'];
    for(const suffix of suffixes) {
      const target=route.path+suffix+'?page=2&search=redaction-marker';
      const result=await request(target,{method:'POST',body:'{"value":"preserved"}',headers:{authorization:'Bearer redaction-token','x-correlation-id':'redaction-correlation',cookie:'secret-cookie'}});
      assert.equal(result.status,200,`${route.id}: ${result.text}`);
      const echo=JSON.parse(result.text);
      assert.equal(echo.port,route.port);assert.equal(echo.url,route.upstream+suffix+'?page=2&search=redaction-marker');
      assert.equal(echo.method,'POST');assert.equal(echo.body,'{"value":"preserved"}');assert.equal(echo.authorization,'Bearer redaction-token');
      assert.equal(echo.correlation,'redaction-correlation');
      assert.equal(result.headers['x-content-type-options'],'nosniff');assert.equal(result.headers['x-frame-options'],'DENY');assert.equal(result.headers['referrer-policy'],'no-referrer');
      assert.equal(Boolean(result.headers['strict-transport-security']),tls);
      cases++;
    }
  }
  for(const target of ['/api/v1/unknown','/api/v1/patients-other','/actuator/health','/dashboard','/api/rawdata']) assert.equal((await request(target)).status,404,target);
  assert.equal((await request('/',{host:'unknown.localhost',plain:true})).status,404);
  const failure=await request('/api/v1/patients?status=503',{method:'POST',body:'mutation'});
  assert.equal(failure.status,503);assert.equal(JSON.parse(failure.text).count,1,'Mutations must not be retried');
  const started=Date.now();assert.equal((await request('/api/v1/patients?delay=16000')).status,504);assert(Date.now()-started<16000);
  run('exec','-T','fixture','node','-e',"fetch('http://127.0.0.1:9090/stop?port=8082').then(r=>r.text())");
  assert.equal((await request('/api/v1/patients')).status,502);
  assert.equal((await request('/api/v1/appointments')).status,200,'Unrelated backend must remain available');
  run('exec','-T','fixture','node','-e',"fetch('http://127.0.0.1:9090/start?port=8082').then(r=>r.text())");
  await sleep(1100);
  const throttle=[];
  // The sign-in routes carry the auth quota; the BFF's login is the one this gateway owns.
  const login=headers=>request('/api/auth/login',{host:'clinicflow.localhost',headers});
  for(let i=0;i<6;i++) throttle.push(await login({'x-forwarded-for':`198.51.100.${i}`}));
  assert(throttle.some(r=>r.status===429),'Spoofed forwarded addresses must not bypass observed-address quotas');
  const forwarded=JSON.parse(throttle.find(r=>r.status===200).text).forwarded;
  assert(!forwarded.startsWith('198.51.100.'),'Untrusted client address was accepted');
  console.log(`Observed client address in this topology: ${forwarded}`);
  // BFF private traffic has its independent limit/router and no public auth quota.
  const internal=run('exec','-T','gateway','wget','-q','-O','-','http://gateway:8080/api/v1/patients');
  assert.equal(JSON.parse(internal).port,8082);
  await sleep(650);assert.equal((await login()).status,200,'Token bucket did not refill');
  const metric=run('exec','-T','gateway','wget','-q','-O','-','http://127.0.0.1:9100/metrics');assert(metric.includes('traefik_entrypoint_requests_total'));
  if(tls) {
    const redirect=await request('/api/v1/patients?x=1',{method:'POST',plain:true});
    assert.equal(redirect.status,308);assert.equal(redirect.headers.location,`https://api.clinicflow.localhost:${port}/api/v1/patients?x=1`);
  }
  const logs=run('logs','--no-color','gateway');
  const access=logs.split('\n').filter(line=>line.includes('DownstreamStatus')).map(line=>JSON.parse(line.slice(line.indexOf('{'))));
  assert(access.length>cases,'No JSON access log evidence');
  for(const entry of access) for(const key of Object.keys(entry)) assert(['StartUTC','RequestMethod','RouterName','ServiceName','DownstreamStatus','OriginStatus','Duration','level','time','message','msg'].includes(key),`Unexpected log field ${key}`);
  for(const sentinel of ['redaction-token','secret-cookie','redaction-marker','redaction-correlation']) assert(!logs.includes(sentinel),`Sensitive access log content: ${sentinel}`);
  console.log(`PASS ${tls?'HTTPS':'HTTP'}: ${cases} route cases; method/body/query/encoded IDs; quotas/refill/XFF; 404; timeout/outage/no retry; metrics; log redaction.`);
}
main().catch(e=>{try{console.error(run('logs','--no-color','gateway'));}catch{} console.error(e);process.exitCode=1;}).finally(()=>{run('down','--volumes','--remove-orphans','--rmi','local');});
