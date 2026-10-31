const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {normalizeZap,evaluate}=require('../security-policy.cjs');
const manifest=JSON.parse(fs.readFileSync('.ci/dast-requests.json'));
const UI='https://clinicflow.localhost:8443', API='https://api.clinicflow.localhost:8443';
const verified={
  psychologist:[UI+'/api/auth/me',API+'/api/v1/patients',UI+'/api/patients'],
  patient:[UI+'/api/auth/me'],
  administrator:[UI+'/api/auth/me',UI+'/api/users/00000000-0000-4000-8000-000000000001'],
};
function report() {
  return {schemaVersion:1,complete:true,zapVersion:'2.17.0',components:['patient-service'],coverage:Object.entries(verified).map(([role,urls])=>({role,authenticated:true,verifiedUrls:[...urls]})),alerts:[]};
}
const context={fullSystem:false,components:['patient-service']};
test('complete verified API/BFF role coverage can pass an alert-free scan',()=>assert.deepEqual(normalizeZap(report(),context,manifest),[]));
test('administrator coverage must include the user lookup',()=>{
  const r=report();r.coverage[2].verifiedUrls=[UI+'/api/auth/me'];
  assert.throws(()=>normalizeZap(r,context,manifest),/user lookup/);
});
test('every role needs the BFF session check',()=>{
  const r=report();r.coverage[1].verifiedUrls=[];
  assert.throws(()=>normalizeZap(r,context,manifest),/BFF verification/);
});
test('an in-scope endpoint its role may call must be covered',()=>{
  const r=report();r.coverage[0].verifiedUrls=[UI+'/api/auth/me'];
  assert.throws(()=>normalizeZap(r,context,manifest),/endpoint discovery/);
});
test('discovery smoke runs, missing roles and lost authentication never pass DAST',()=>{
  const reports=[{...report(),complete:false},{...report(),coverage:[]},report()];
  reports[2].coverage[0].authenticated=false;
  for(const r of reports) assert.throws(()=>normalizeZap(r,context,manifest));
});
test('wrong component scopes and missing endpoint coverage fail closed',()=>{
  assert.throws(()=>normalizeZap({...report(),components:['patient-service','clinical-service']},context,manifest));
  assert.throws(()=>normalizeZap(report(),{fullSystem:true,components:['patient-service']},manifest));
});
test('high ZAP alerts block and medium alerts remain visible',()=>{
  const r=report();r.alerts=[{id:'ZAP-40018',origin:'api',path:'/api/v1/patients',risk:'High'},{id:'ZAP-10020',origin:'ui',path:'/login',risk:'Medium'}];
  const result=evaluate(normalizeZap(r,context,manifest),{schemaVersion:1,entries:[]});
  assert.deepEqual(result.map(f=>f.disposition),['blocking','reported']);
});
test('unrecognized alert schema fails rather than dropping risk',()=>{
  assert.throws(()=>normalizeZap({...report(),alerts:[{id:'x',origin:'external',path:'/',risk:'unknown'}]},context,manifest));
});
