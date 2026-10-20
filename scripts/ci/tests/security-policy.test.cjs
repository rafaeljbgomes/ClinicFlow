const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { execFileSync } = require('node:child_process');
const policy = require('../security-policy.cjs');
const now = new Date('2026-10-15T12:00:00Z');
const finding = { component: 'auth-service', target: 'dependencies', id: 'CVE-TEST-1', package: 'example', version: '1.0', severity: 'HIGH' };
const entry = { ...finding, owner: 'maintainer', reason: 'Synthetic test exception', issue: 'https://github.com/rafaeljbgomes/ClinicFlow/issues/1', reviewedAt: '2026-10-10', expiresAt: '2026-10-20' };
const empty = { schemaVersion: 1, entries: [] };
test('new high and critical findings block, including unfixed dependencies', () => {
  assert.equal(policy.evaluate([finding], empty, now)[0].disposition, 'blocking');
  assert.equal(policy.evaluate([{ ...finding, severity: 'CRITICAL', fixedVersion: '' }], empty, now)[0].disposition, 'blocking');
});
test('reviewed findings remain visible and package version changes do not inherit acceptance', () => {
  assert.equal(policy.evaluate([finding], { schemaVersion: 1, entries: [entry] }, now)[0].disposition, 'baselined');
  assert.equal(policy.evaluate([{ ...finding, version: '2.0' }], { schemaVersion: 1, entries: [entry] }, now)[0].disposition, 'blocking');
});
test('expiry, component, target and finding identity cannot be widened', () => {
  const base = { schemaVersion: 1, entries: [entry] };
  assert.equal(policy.evaluate([finding], base, new Date('2026-10-20T00:00:00Z'))[0].disposition, 'blocking');
  for (const key of ['component', 'target', 'id', 'package']) assert.equal(policy.evaluate([{ ...finding, [key]: 'different' }], base, now)[0].disposition, 'blocking');
});
test('low and medium findings stay in the report', () => {
  assert.deepEqual(policy.evaluate([{ ...finding, severity: 'LOW' }, { ...finding, severity: 'MEDIUM' }], empty, now).map(f => f.disposition), ['reported','reported']);
});
test('malformed, wildcard, duplicate, future and indefinite acceptances fail closed', () => {
  for (const invalid of [{}, {schemaVersion: 1}, {schemaVersion:1,entries:[{...entry,owner:''}]}, {schemaVersion:1,entries:[{...entry,package:'*'}]}, {schemaVersion:1,entries:[entry,entry]}, {schemaVersion:1,entries:[{...entry,reviewedAt:'2026-10-16'}]}, {schemaVersion:1,entries:[{...entry,expiresAt:'2027-01-01'}]}]) assert.throws(() => policy.validateBaseline(invalid, now));
});
test('scanner failures and empty inventories cannot become clean scans', () => {
  for (const report of [{}, {SchemaVersion:2}, {SchemaVersion:2,Results:[]}, {SchemaVersion:2,Results:[{}]}]) assert.throws(() => policy.normalizeTrivy(report, 'frontend','dependencies','vulnerability'));
});
test('clean scans require a recognized inventory; malformed findings fail', () => {
  const report={SchemaVersion:2,Results:[{Target:'package-lock.json',Packages:[{Name:'a',Version:'1'}]}]};
  assert.deepEqual(policy.normalizeTrivy(report,'frontend','dependencies','vulnerability'),[]);
  report.Results[0].Vulnerabilities=[{VulnerabilityID:'x'}];
  assert.throws(()=>policy.normalizeTrivy(report,'frontend','dependencies','vulnerability'));
});
test('configuration exceptions are tied to the affected file', () => {
  const report={SchemaVersion:2,Results:['a.yaml','b.yaml'].map(Target=>({Target,Type:'kubernetes',MisconfSummary:{Failures:1},Misconfigurations:[{ID:'AVD-1',Severity:'HIGH'}]}))};
  const findings=policy.normalizeTrivy(report,'platform','manifests','misconfiguration');
  assert.notEqual(policy.fingerprint(findings[0]),policy.fingerprint(findings[1]));
});
const cases = [
  ['docs/change.md', [], false, false],
  ['frontend/src/app/dashboard/clinical/page.tsx', ['frontend'], false, false],
  ['auth-service/src/main/java/Controller.java', ['auth-service'], false, true],
  ['patient-service/src/test/java/PatientTest.java', ['patient-service'], false, false],
  ['event-contracts/src/main/resources/contracts/a.json', ['patient-service','appointment-service','clinical-service','notification-service'], true, false],
  ['frontend/src/lib/server/jwt.ts', ['frontend'], true, true],
  ['pom.xml', policy.components, true, true],
  ['deploy/helm/service/templates/deployment.yaml', policy.components, true, true],
  ['.ci/security.json', policy.components, true, true],
  ['unknown-executable.sh', policy.components, true, true],
];
for (const [file, components, full, dast] of cases) test('routing: ' + file, () => {
  const result=policy.routeChanges([file]);
  assert.deepEqual(result.components,components);
  assert.equal(result.fullSystem,full); assert.equal(result.dast,dast);
});
test('all components run on first main build or scheduled sweep', () => assert.deepEqual(policy.routeChanges([],true).components,policy.components));
test('baseline maintenance must be separate from application changes', () => {
  assert.equal(policy.baselineOnly(['.ci/security-baseline.json','docs/review.md']),true);
  assert.equal(policy.baselineOnly(['.ci/security-baseline.json','frontend/package-lock.json']),false);
});
test('PRs cannot use candidate acceptances or mix code with exemption changes', t => {
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'clinicflow-policy-'));
  t.after(()=>{ assert.equal(path.dirname(fs.realpathSync(root)),fs.realpathSync(os.tmpdir())); fs.rmSync(root,{recursive:true,force:true}); });
  const git=args=>execFileSync('git',args,{cwd:root,stdio:'pipe',encoding:'utf8'}).trim();
  git(['init','-b','main']); git(['config','user.email','ci-test@example.invalid']); git(['config','user.name','CI test']);
  fs.mkdirSync(path.join(root,'.ci')); fs.writeFileSync(path.join(root,'.ci/security-baseline.json'),JSON.stringify(empty)); fs.writeFileSync(path.join(root,'README.md'),'test');
  git(['add','.']);git(['commit','-m','test: initial']);git(['update-ref','refs/remotes/origin/main','HEAD']);
  const targetSha=git(['rev-parse','HEAD']);
  fs.writeFileSync(path.join(root,'.ci/security-baseline.json'),JSON.stringify({schemaVersion:1,entries:[entry]}));
  assert.deepEqual(policy.trustedBaseline(root,{pullRequest:true,targetSha,baselineOnly:false}).entries,[]);
  fs.mkdirSync(path.join(root,'auth-service'));fs.writeFileSync(path.join(root,'auth-service/change.java'),'test');
  git(['add','.']);git(['commit','-m','test: forbidden mixed exception']);
  const old={CHANGE_ID:process.env.CHANGE_ID,CHANGE_TARGET:process.env.CHANGE_TARGET,CHANGE_FORK:process.env.CHANGE_FORK};
  process.env.CHANGE_ID='1';process.env.CHANGE_TARGET='main';delete process.env.CHANGE_FORK;
  try {
    assert.throws(()=>policy.prepare(root,'auth-service'),/separate policy-only PR/);
    process.env.CHANGE_FORK='untrusted'; assert.throws(()=>policy.prepare(root,'repository'),/same-repository/);
  } finally { for(const [key,value] of Object.entries(old)) { if(value===undefined) delete process.env[key]; else process.env[key]=value; } }
});

test('baseline additions and renewals must be observed in the current main scan', () => {
  assert.throws(()=>policy.validateAdditions([],{schemaVersion:1,entries:[entry]},empty,'auth-service','dependencies'),/absent/);
  assert.doesNotThrow(()=>policy.validateAdditions([finding],{schemaVersion:1,entries:[entry]},empty,'auth-service','dependencies'));
  const renewed={...entry,reason:'Renewed after review'};
  assert.throws(()=>policy.validateAdditions([],{schemaVersion:1,entries:[renewed]},{schemaVersion:1,entries:[entry]},'auth-service','dependencies'));
});
