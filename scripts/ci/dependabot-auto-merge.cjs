'use strict';

const { execFileSync } = require('node:child_process');

const ELIGIBLE_UPDATE_TYPES = new Set([
  'version-update:semver-minor',
  'version-update:semver-patch',
]);

// ADR-027: Keycloak minors need the upgrading guide, and its version is also pinned in the chart values.
const MANUAL_REVIEW_DEPENDENCY = /keycloak/i;

function isEligible({ updateType, dependencyNames }) {
  return ELIGIBLE_UPDATE_TYPES.has(updateType) && !MANUAL_REVIEW_DEPENDENCY.test(dependencyNames ?? '');
}

function main(env = process.env, run = execFileSync) {
  const { UPDATE_TYPE: updateType, DEPENDENCY_NAMES: dependencyNames, PR_URL: pullRequestUrl, GH_TOKEN: token } = env;

  if (!isEligible({ updateType, dependencyNames })) {
    console.log(`Leaving for manual review: ${updateType || 'unknown update type'} (${dependencyNames || 'no dependency names'}).`);
    return;
  }
  if (!token) {
    throw new Error('DEPENDABOT_AUTOMERGE_TOKEN must be set as a Dependabot secret.');
  }

  run('gh', ['pr', 'merge', '--auto', '--squash', pullRequestUrl], { stdio: 'inherit' });
}

if (require.main === module) {
  main();
}

module.exports = { isEligible, main };
