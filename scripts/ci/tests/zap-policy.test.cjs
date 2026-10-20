const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {normalizeZap,evaluate}=require('../security-policy.cjs');
const manifest=JSON.parse(fs.readFileSync('.ci/dast-requests.json'));
function report() {
  return {schemaVersion:1,complete:true,zapVersion:'2.17.0',components:['auth-service'],coverage:['psychologist','patient','administrator'].map(role=>({role,authenticated:true,verifiedUrls:['https://api.clinicflow.localhost:8443/api/v1/auth/users/me','https://clinicflow.localhost:8443/api/auth/me']})),alerts:[]};
}
const context={fullSystem:false,components:[]};
test('complete verified API/BFF role coverage can pass an alert-free scan',()=>assert.deepEqual(normalizeZap(report(),context,manifest),[]));
test('discovery smoke runs, missing roles and lost authentication never pass DAST',()=>{
  const reports=[{...report(),complete:false},{...report(),coverage:[]},report()];
  reports[2].coverage[0].authenticated=false;
  for(const r of reports) assert.throws(()=>normalizeZap(r,context,manifest));
});
test('wrong component scopes and missing endpoint coverage fail closed',()=>{
  assert.throws(()=>normalizeZap({...report(),components:['auth-service','patient-service']},context,manifest));
  assert.throws(()=>normalizeZap({...report(),components:['auth-service','patient-service']},{fullSystem:false,components:['patient-service']},manifest));
});
test('high ZAP alerts block and medium alerts remain visible',()=>{
  const r=report();r.alerts=[{id:'ZAP-40018',origin:'api',path:'/api/v1/auth/users/me',risk:'High'},{id:'ZAP-10020',origin:'ui',path:'/login',risk:'Medium'}];
  const result=evaluate(normalizeZap(r,context,manifest),{schemaVersion:1,entries:[]});
  assert.deepEqual(result.map(f=>f.disposition),['blocking','reported']);
});
test('unrecognized alert schema fails rather than dropping risk',()=>{
  assert.throws(()=>normalizeZap({...report(),alerts:[{id:'x',origin:'external',path:'/',risk:'unknown'}]},context,manifest));
});
