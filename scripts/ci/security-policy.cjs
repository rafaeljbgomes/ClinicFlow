// No npm dependencies: repository gates run before npm ci.
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const components = ['auth-service', 'patient-service', 'appointment-service', 'clinical-service', 'notification-service', 'frontend'];
const blocking = new Set(['HIGH', 'CRITICAL']);
function assert(ok, message) { if (!ok) throw new Error(message); }
function json(file) { return JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '')); }
function git(args, cwd = process.cwd()) { return execFileSync('git', args, { cwd, encoding: 'utf8' }).trim(); }
function routeChanges(files, force = false) {
  const selected = new Set(); let fullSystem = force, dast = force;
  if (force) components.forEach(c => selected.add(c));
  for (const file of files) {
    if (/^(docs\/|public\/)|\.(md|puml|png|jpg|svg)$/.test(file)) continue;
    const component = components.find(c => file.startsWith(c + '/') || file === 'Jenkinsfile.' + c.replace('-service', ''));
    if (component) {
      selected.add(component);
      if (component === 'frontend') {
        if (/^frontend\/(src\/app\/api\/|src\/lib\/server\/|tests\/e2e\/|(src\/)?proxy\.|(Dockerfile|package(-lock)?\.json|next\.config\.ts|playwright\.config\.ts)$)/.test(file)) fullSystem = dast = true;
      } else if (file.includes('/src/main/') || file.endsWith('/Dockerfile')) dast = true;
      continue;
    }
    if (file.startsWith('event-contracts/')) {
      components.filter(c => c !== 'auth-service' && c !== 'frontend').forEach(c => selected.add(c));
      fullSystem = true; continue;
    }
    fullSystem = dast = true;
    components.forEach(c => selected.add(c));
  }
  return { components: [...selected], fullSystem, dast, system: fullSystem || dast };
}
function fingerprint(f) { return JSON.stringify([f.component, f.target, f.id, f.package, f.version]); }
function validateBaseline(baseline, now = new Date()) {
  assert(baseline?.schemaVersion === 1 && Array.isArray(baseline.entries), 'Invalid security baseline schema');
  const seen = new Set();
  for (const e of baseline.entries) {
    for (const key of ['component', 'target', 'id', 'package', 'version', 'owner', 'reason', 'issue', 'reviewedAt', 'expiresAt'])
      assert(typeof e[key] === 'string' && e[key].trim(), 'Baseline entry missing ' + key);
    assert(![e.component, e.target, e.id, e.package, e.version].some(v => /[*?]/.test(v)), 'Wildcard baseline entries are prohibited');
    assert(/^https:\/\/github\.com\/rafaeljbgomes\/ClinicFlow\/issues\/\d+$/.test(e.issue), 'Baseline requires a repository tracking issue');
    assert(/^\d{4}-\d{2}-\d{2}$/.test(e.reviewedAt) && /^\d{4}-\d{2}-\d{2}$/.test(e.expiresAt), 'Baseline dates must use YYYY-MM-DD');
    const reviewed = Date.parse(e.reviewedAt), expiry = Date.parse(e.expiresAt);
    assert(Number.isFinite(reviewed) && Number.isFinite(expiry) && reviewed <= now.getTime(), 'Invalid/future baseline review date');
    assert(expiry > reviewed && expiry - reviewed <= 30 * 86400000, 'Baseline acceptance must expire within 30 days');
    assert([...components, 'platform', 'dast'].includes(e.component), 'Unknown baseline component');
    const key = fingerprint(e); assert(!seen.has(key), 'Duplicate baseline entry'); seen.add(key);
  }
  return baseline;
}
function normalizeTrivy(report, component, target, kind) {
  assert(report?.SchemaVersion === 2 && Array.isArray(report.Results), 'Missing or malformed Trivy results');
  assert(report.Results.length > 0, 'Trivy did not recognize the scan target');
  if (kind === 'vulnerability') assert(report.Results.some(r => Array.isArray(r.Packages) && r.Packages.length), 'Dependency/image inventory is empty');
  if (kind === 'misconfiguration') assert(report.Results.some(r => r.MisconfSummary || Array.isArray(r.Misconfigurations)), 'No configuration was analyzed');
  return report.Results.flatMap(r => [
    ...(r.Vulnerabilities || []).map(v => ({ id: v.VulnerabilityID, package: v.PkgName, version: v.InstalledVersion, severity: v.Severity, fixedVersion: v.FixedVersion || '', location: r.Target })),
    ...(r.Misconfigurations || []).map(v => ({ id: v.ID, package: r.Type, version: '-', severity: v.Severity, location: r.Target }))
  ].map(f => {
    assert(f.id && f.package && f.version && typeof f.severity === 'string', 'Incomplete Trivy finding');
    return { ...f, component, target: kind === 'misconfiguration' ? target + ':' + f.location.replaceAll('\\', '/') : target };
  }));
}
function evaluate(findings, baseline, now = new Date(), inherited = () => false) {
  validateBaseline(baseline, now);
  const accepted = new Map(baseline.entries.map(e => [fingerprint(e), e]));
  return findings.map(f => {
    const e = accepted.get(fingerprint(f));
    const valid = e && Date.parse(e.expiresAt) > now.getTime();
    const disposition = !blocking.has(f.severity) ? 'reported' : valid ? 'baselined' : inherited(f) ? 'inherited' : 'blocking';
    return { ...f, disposition, exception: valid ? e : undefined };
  });
}
// PRs are judged on what they add (ADR-030). A High/Critical finding is inherited when main's latest scan of the same
// component and target already shipped that package version (vulnerabilities) or raised that finding (configuration, ZAP).
// The reference comes only from main runs (Get-SecurityReference.ps1); without one, every finding blocks as before.
function findReference(component, target, root = process.cwd()) {
  const dir = path.resolve(root, process.env.CLINICFLOW_SECURITY_REFERENCE || 'reports/reference');
  if (!fs.existsSync(dir)) return undefined;
  const suffix = '/' + component + '/' + target + '.json';
  const matches = fs.readdirSync(dir, { recursive: true }).map(f => String(f).replaceAll('\\', '/')).filter(f => ('/' + f).endsWith(suffix));
  assert(matches.length <= 1, 'Ambiguous main security reference for ' + component + '/' + target);
  return matches.length ? path.join(dir, matches[0]) : undefined;
}
function trivyInheritance(reference, component, target, kind) {
  if (kind === 'misconfiguration') {
    const seen = new Set(normalizeTrivy(reference, component, target, kind).map(fingerprint));
    return f => seen.has(fingerprint(f));
  }
  normalizeTrivy(reference, component, target, kind);
  const shipped = new Set(reference.Results.flatMap(r => [
    ...(r.Packages || []).map(p => [p.Name, p.Version]),
    ...(r.Vulnerabilities || []).map(v => [v.PkgName, v.InstalledVersion])
  ]).map(p => JSON.stringify(p)));
  return f => shipped.has(JSON.stringify([f.package, f.version]));
}
function zapAlerts(report) {
  assert(report?.schemaVersion === 1 && Array.isArray(report.alerts), 'Malformed ZAP report');
  return report.alerts.map(a => {
    assert(/^ZAP-\d+$/.test(a.id) && ['api','ui'].includes(a.origin) && typeof a.path === 'string' && ['High','Medium','Low','Informational'].includes(a.risk), 'Invalid ZAP alert');
    return { component: 'dast', target: a.origin, id: a.id, package: a.path, version: '-', severity: a.risk.toUpperCase() };
  });
}
function zapInheritance(reference) {
  const seen = new Set(zapAlerts(reference).map(fingerprint));
  return f => seen.has(fingerprint(f));
}
function loadInheritance(context, file, build) {
  if (!context.pullRequest) return () => false;
  if (!file) { console.log('No main security reference; every High/Critical finding is treated as new.'); return () => false; }
  try { return build(json(file)); }
  catch (error) { console.log('Ignoring unusable main security reference ' + file + ': ' + error.message); return () => false; }
}
function baselineOnly(files) {
  return files.includes('.ci/security-baseline.json') && files.every(f => f === '.ci/security-baseline.json' || f.endsWith('.md'));
}
function trustedBaseline(root, context) {
  const current = validateBaseline(json(path.join(root, '.ci/security-baseline.json')));
  if (!context.pullRequest || context.baselineOnly || context.policyOnly) return current;
  const file = git(['ls-tree', '--name-only', context.targetSha, '--', '.ci/security-baseline.json'], root);
  return file ? validateBaseline(JSON.parse(git(['show', context.targetSha + ':.ci/security-baseline.json'], root))) : { schemaVersion: 1, entries: [] };
}
function validateAdditions(findings, candidate, trusted, component, target) {
  const previous=new Map(trusted.entries.map(e=>[fingerprint(e),e]));
  const canonical=e=>JSON.stringify(Object.keys(e).sort().map(k=>[k,e[k]]));
  const observed=new Set(findings.map(fingerprint));
  for(const e of candidate.entries.filter(e=>e.component===component && (e.target===target || e.target.startsWith(target+':')))) {
    const old=previous.get(fingerprint(e));
    if(!old || canonical(old)!==canonical(e)) assert(observed.has(fingerprint(e)), 'Baseline addition or renewal is absent from the current main scan: '+e.id);
  }
}
function prepare(root, component, force = false) {
  assert([...components, 'system', 'repository'].includes(component), 'Unknown CI component');
  // Jenkins and GitHub Actions both export CHANGE_ID/CHANGE_TARGET for PRs. Only the local Jenkins agent, which holds
  // credentials, sets CHANGE_FORK; hosted runners give fork PRs a read-only token and no secrets.
  const pullRequest = Boolean(process.env.CHANGE_ID);
  if (pullRequest) {
    assert(/^\d+$/.test(process.env.CHANGE_ID), 'Invalid PR number');
    assert(process.env.CHANGE_TARGET === 'main' && !process.env.CHANGE_FORK, 'Only same-repository PRs targeting main may use this agent');
  }
  const sha = git(['rev-parse', 'HEAD'], root);
  const targetSha = git(['rev-parse', 'refs/remotes/origin/main'], root);
  let files;
  if (pullRequest) files = git(['diff', '--name-only', '--diff-filter=ACDMRTUXB', git(['merge-base', 'HEAD', targetSha], root) + '..HEAD'], root).split('\n').filter(Boolean);
  else {
    const previous = process.env.GIT_PREVIOUS_SUCCESSFUL_COMMIT;
    files = previous ? git(['diff', '--name-only', previous, 'HEAD'], root).split('\n').filter(Boolean) : [];
    force = force || !previous;
  }
  const routing = routeChanges(files, force);
  const context = { schemaVersion: 1, component, sha, targetSha, pullRequest, files, baselineOnly: baselineOnly(files), policyOnly: files.some(f => ['.ci/security-baseline.json','.ci/zap-exceptions.json'].includes(f)) && files.every(f => ['.ci/security-baseline.json','.ci/zap-exceptions.json'].includes(f) || f.endsWith('.md')), ...routing };
  const exemptions = files.filter(f => ['.ci/security-baseline.json', '.ci/zap-exceptions.json'].includes(f));
  const existingExemptions = git(['ls-tree', '--name-only', targetSha, '--', '.ci/security-baseline.json', '.ci/zap-exceptions.json'], root);
  const nonemptyBootstrap = exemptions.some(f => json(path.join(root, f)).entries?.length !== 0);
  if (pullRequest && exemptions.length && (existingExemptions || nonemptyBootstrap)) assert(files.every(f => exemptions.includes(f) || f.endsWith('.md')), 'Security exemptions require a separate policy-only PR');
  validateBaseline(json(path.join(root, '.ci/security-baseline.json')));
  validateBaseline(json(path.join(root, '.ci/zap-exceptions.json')));
  context.affected = component === 'repository' || (component === 'system' ? routing.system : routing.components.includes(component));
  const buildNumber = process.env.BUILD_NUMBER || (process.env.GITHUB_RUN_ID ? process.env.GITHUB_RUN_ID + '-' + (process.env.GITHUB_RUN_ATTEMPT || '1') : 'local');
  context.runId = 'ci-' + component + '-' + (pullRequest ? 'pr-' + process.env.CHANGE_ID : 'main') + '-' + sha.slice(0, 12) + '-' + buildNumber;
  fs.mkdirSync(path.join(root, 'reports/security'), { recursive: true });
  fs.writeFileSync(path.join(root, 'reports/security/context.json'), JSON.stringify(context, null, 2));
  if (process.env.GITHUB_OUTPUT) {
    fs.appendFileSync(process.env.GITHUB_OUTPUT, ['affected=' + context.affected, 'full-system=' + context.fullSystem, 'dast=' + context.dast, 'run-id=' + context.runId, ''].join('\n'));
  }
  console.log(component + ': ' + (context.affected ? 'affected' : 'not affected') + ' (' + sha.slice(0, 12) + ')');
  return context;
}

function normalizeZap(report, context, manifest) {
  assert(report?.schemaVersion === 1 && report.complete === true && Array.isArray(report.alerts) && Array.isArray(report.coverage), 'Incomplete ZAP scan');
  const expected = context.fullSystem ? components : [...new Set(['auth-service', ...context.components])];
  assert(Array.isArray(report.components) && [...report.components].sort().join(',') === [...expected].sort().join(','), 'ZAP scanned the wrong component scope');
  for (const role of ['psychologist','patient','administrator']) {
    const item = report.coverage.find(c => c.role === role);
    assert(item?.authenticated === true && Array.isArray(item.verifiedUrls), 'Missing authenticated role coverage');
    assert(item.verifiedUrls.includes('https://api.clinicflow.localhost:8443/api/v1/auth/users/me') && item.verifiedUrls.includes('https://clinicflow.localhost:8443/api/auth/me'), 'Missing authenticated API/BFF verification');
    for (const request of manifest.requests.filter(r => expected.includes(r.component) && r.roles.includes(role)))
      assert(item.verifiedUrls.includes('https://api.clinicflow.localhost:8443' + request.path) && item.verifiedUrls.includes('https://clinicflow.localhost:8443' + request.bff), 'Missing endpoint discovery: ' + request.path);
  }
  return zapAlerts(report);
}

if (require.main === module) {
  try {
    const [command, ...args] = process.argv.slice(2);
    if (command === 'prepare') prepare(process.cwd(), args[0], args.includes('--force') || process.env.CLINICFLOW_FORCE === 'true');
    else if (command === 'evaluate') {
      const [component, target, kind, reportPath] = args;
      const context = json('reports/security/context.json');
      const findings=normalizeTrivy(json(reportPath),component,target,kind);
      const baseline=trustedBaseline(process.cwd(),context);
      if(context.pullRequest && context.policyOnly) {
        const exists=git(['ls-tree','--name-only',context.targetSha,'--','.ci/security-baseline.json']);
        const previous=exists?JSON.parse(git(['show',context.targetSha+':.ci/security-baseline.json'])):{schemaVersion:1,entries:[]};
        validateAdditions(findings,baseline,previous,component,target);
      }
      const inherited = loadInheritance(context, findReference(component, target), reference => trivyInheritance(reference, component, target, kind));
      const result = evaluate(findings,baseline,new Date(),inherited);
      fs.writeFileSync(reportPath + '.gate.json', JSON.stringify(result, null, 2));
      const failed = result.filter(f => f.disposition === 'blocking');
      const kept = result.filter(f => f.disposition === 'inherited').length;
      console.log(component + '/' + target + ': ' + result.length + ' findings, ' + failed.length + ' new High/Critical' + (kept ? ', ' + kept + ' inherited from main' : ''));
      if (failed.length) process.exitCode = 2;
    } else if (command === 'evaluate-zap') {
      const context=json('reports/security/context.json');
      const report=json(args[0]);
      const findings=normalizeZap(report,context,json('.ci/dast-requests.json'));
      let baseline=json('.ci/zap-exceptions.json');
      if(context.pullRequest && !context.policyOnly) {
        const exists=git(['ls-tree','--name-only',context.targetSha,'--','.ci/zap-exceptions.json']);
        baseline=exists?JSON.parse(git(['show',context.targetSha+':.ci/zap-exceptions.json'])):{schemaVersion:1,entries:[]};
      }
      if(context.pullRequest && context.policyOnly) {
        const exists=git(['ls-tree','--name-only',context.targetSha,'--','.ci/zap-exceptions.json']);
        const previous=exists?JSON.parse(git(['show',context.targetSha+':.ci/zap-exceptions.json'])):{schemaVersion:1,entries:[]};
        for(const target of ['api','ui']) validateAdditions(findings,baseline,previous,'dast',target);
      }
      const result=evaluate(findings,baseline,new Date(),loadInheritance(context,findReference('dast','zap'),zapInheritance));
      fs.writeFileSync(args[0]+'.gate.json',JSON.stringify(result,null,2));
      if(result.some(f=>f.disposition==='blocking')) process.exitCode=2;
    } else throw new Error('Unknown security policy command');
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
module.exports = { components, routeChanges, validateBaseline, fingerprint, normalizeTrivy, evaluate, baselineOnly, trustedBaseline, prepare, normalizeZap, validateAdditions, findReference, trivyInheritance, zapInheritance, loadInheritance };
