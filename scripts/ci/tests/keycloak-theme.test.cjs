// The clinicflow login theme (docker/keycloak/themes): its wiring, the upstream hooks its template must
// keep, and its colours, which must stay those of the frontend. Runs in the always-on repository gate,
// so a change on either side that breaks the match fails the pull request that makes it.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../../..');
const keycloak = path.join(root, 'docker/keycloak');
const theme = path.join(keycloak, 'themes/clinicflow/login');
const read = (file) => fs.readFileSync(file, 'utf8').replace(/^﻿/, '');

function properties(file) {
  const values = {};
  for (const line of read(file).split(/\r?\n/)) {
    if (!line.trim() || line.trimStart().startsWith('#')) continue;
    const separator = line.indexOf('=');
    values[line.slice(0, separator).trim()] = line.slice(separator + 1).trim();
  }
  return values;
}

// Custom properties declared in the first `selector {` block of a stylesheet.
function customProperties(css, selector) {
  const start = css.indexOf(selector + ' {');
  assert.notEqual(start, -1, `${selector} block not found`);
  const block = css.slice(start, css.indexOf('}', start));
  return Object.fromEntries([...block.matchAll(/(--[\w-]+):\s*([^;]+);/g)].map(([, name, value]) => [name, value.trim()]));
}

test('the realm selects the clinicflow login theme', () => {
  // Arrange
  const realm = JSON.parse(read(path.join(keycloak, 'realm/clinicflow-realm.json')));

  // Act
  const themeDirectory = path.join(keycloak, 'themes', realm.loginTheme, 'login');

  // Assert
  assert.ok(fs.existsSync(path.join(themeDirectory, 'theme.properties')), `no login theme named ${realm.loginTheme}`);
});

test('the image copies the themes into Keycloak', () => {
  // Arrange
  const dockerfile = read(path.join(keycloak, 'Dockerfile'));

  // Act
  const copiesThemes = /^COPY themes\/ \/opt\/keycloak\/themes\/$/m.test(dockerfile);

  // Assert
  assert.ok(copiesThemes);
});

test('the theme extends keycloak.v2 and loads its stylesheet after the parent', () => {
  // Arrange
  const settings = properties(path.join(theme, 'theme.properties'));

  // Act
  const styles = settings.styles.split(/\s+/);

  // Assert
  assert.equal(settings.parent, 'keycloak.v2');
  assert.deepEqual(styles, ['css/styles.css', 'css/clinicflow.css']);
});

test('the sign-in pages stay light, as the app opens', () => {
  // Arrange
  const settings = properties(path.join(theme, 'theme.properties'));

  // Act
  const darkMode = settings.darkMode;

  // Assert
  assert.equal(darkMode, 'false');
});

test('every file the stylesheet loads is in the theme', () => {
  // Arrange
  const stylesheet = path.join(theme, 'resources/css/clinicflow.css');
  const urls = [...read(stylesheet).matchAll(/url\("?([^")]+)"?\)/g)].map(([, url]) => url);

  // Act
  const missing = urls.filter((url) => !fs.existsSync(path.resolve(path.dirname(stylesheet), url)));

  // Assert
  assert.ok(urls.length > 0);
  assert.deepEqual(missing, []);
});

test('every bundled font ships with its licence', () => {
  // Arrange
  const fonts = path.join(theme, 'resources/fonts');
  const files = fs.readdirSync(fonts);

  // Act
  const unlicensed = files
    .filter((file) => file.endsWith('.woff2'))
    .filter((file) => !files.includes(`OFL-${file.split('-latin-')[0]}.txt`));

  // Assert
  assert.deepEqual(unlicensed, []);
});

test('the template keeps the upstream hooks the pages and tests rely on', () => {
  // Arrange
  const template = read(path.join(theme, 'template.ftl'));
  const hooks = [
    '<#macro registrationLayout',
    'id="kc-page-title"',
    '<#nested "header">',
    '<#nested "show-username">',
    '<#nested "form">',
    '<#nested "socialProviders">',
    '<#nested "info">',
    'data-page-id="login-${pageId}"',
    'js/authChecker.js',
    'js/passwordVisibility.js',
    'kc-feedback-text',
    'id="kc-select-try-another-way-form"',
  ];

  // Act
  const missing = hooks.filter((hook) => !template.includes(hook));

  // Assert
  assert.deepEqual(missing, []);
});

test('every ClinicFlow message the template uses is defined', () => {
  // Arrange
  const template = read(path.join(theme, 'template.ftl'));
  const messages = properties(path.join(theme, 'messages/messages_en.properties'));
  const used = [...template.matchAll(/msg\("(clinicflow\w+)"\)/g)].map(([, key]) => key);

  // Act
  const undefinedKeys = used.filter((key) => !(key in messages));

  // Assert
  assert.ok(used.length > 0);
  assert.deepEqual(undefinedKeys, []);
});

test('the sign-in and registration pages carry the app subtitles', () => {
  // Arrange
  const messages = properties(path.join(theme, 'messages/messages_en.properties'));

  // Act
  const subtitles = ['login', 'register'].map((page) => messages[`clinicflowSubtitle-${page}`]);

  // Assert
  assert.ok(subtitles.every((subtitle) => subtitle && subtitle.length > 0));
});

test('the theme colours are the frontend light theme tokens', () => {
  // Arrange
  const frontend = customProperties(read(path.join(root, 'frontend/src/app/globals.css')), ':root');
  const login = customProperties(read(path.join(theme, 'resources/css/clinicflow.css')), ':root');
  const pairs = {
    '--cf-surface': '--surface-container-low',
    '--cf-card': '--card',
    '--cf-foreground': '--foreground',
    '--cf-muted': '--muted',
    '--cf-muted-foreground': '--muted-foreground',
    '--cf-primary': '--primary',
    '--cf-primary-foreground': '--primary-foreground',
    '--cf-secondary': '--secondary',
    '--cf-border': '--border',
    '--cf-ring': '--ring',
    '--cf-danger-bg': '--status-danger-bg',
    '--cf-danger-fg': '--status-danger-fg',
    '--cf-warning-bg': '--status-warning-bg',
    '--cf-warning-fg': '--status-warning-fg',
    '--cf-success-bg': '--status-success-bg',
    '--cf-success-fg': '--status-success-fg',
    '--cf-info-bg': '--status-info-bg',
    '--cf-info-fg': '--status-info-fg',
  };

  // Act
  const drift = Object.entries(pairs)
    .filter(([themeToken, appToken]) => login[themeToken]?.toLowerCase() !== frontend[appToken]?.toLowerCase())
    .map(([themeToken, appToken]) => `${themeToken}=${login[themeToken]} but ${appToken}=${frontend[appToken]}`);

  // Assert
  assert.deepEqual(drift, []);
});
