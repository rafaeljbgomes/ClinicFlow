'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');

const { isEligible, main } = require('../dependabot-auto-merge.cjs');

test('patch updates are eligible', () => {
  // Arrange
  const update = { updateType: 'version-update:semver-patch', dependencyNames: 'hono' };

  // Act
  const eligible = isEligible(update);

  // Assert
  assert.equal(eligible, true);
});

test('minor updates are eligible', () => {
  // Arrange
  const update = { updateType: 'version-update:semver-minor', dependencyNames: 'hono' };

  // Act
  const eligible = isEligible(update);

  // Assert
  assert.equal(eligible, true);
});

test('major updates need manual review', () => {
  // Arrange
  const update = { updateType: 'version-update:semver-major', dependencyNames: 'vitest' };

  // Act
  const eligible = isEligible(update);

  // Assert
  assert.equal(eligible, false);
});

test('an unknown update type needs manual review', () => {
  // Arrange
  const update = { updateType: '', dependencyNames: 'postgres' };

  // Act
  const eligible = isEligible(update);

  // Assert
  assert.equal(eligible, false);
});

test('Keycloak patch updates need manual review', () => {
  // Arrange
  const update = { updateType: 'version-update:semver-patch', dependencyNames: 'quay.io/keycloak/keycloak' };

  // Act
  const eligible = isEligible(update);

  // Assert
  assert.equal(eligible, false);
});

test('an eligible update is queued as a squash merge', () => {
  // Arrange
  const env = { UPDATE_TYPE: 'version-update:semver-patch', DEPENDENCY_NAMES: 'hono', PR_URL: 'https://example.test/pr/1', GH_TOKEN: 'test-token' };
  const calls = [];

  // Act
  main(env, (command, args) => calls.push([command, ...args]));

  // Assert
  assert.deepEqual(calls, [['gh', 'pr', 'merge', '--auto', '--squash', 'https://example.test/pr/1']]);
});

test('an eligible update without a token fails closed', () => {
  // Arrange
  const env = { UPDATE_TYPE: 'version-update:semver-patch', DEPENDENCY_NAMES: 'hono', PR_URL: 'https://example.test/pr/1' };

  // Act
  const run = () => main(env);

  // Assert
  assert.throws(run, /DEPENDABOT_AUTOMERGE_TOKEN/);
});
