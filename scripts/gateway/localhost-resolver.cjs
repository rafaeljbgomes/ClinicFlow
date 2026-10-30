// Preloaded by Test-GatewayBrowser.ps1 (NODE_OPTIONS=--require). RFC 6761 reserves *.localhost for loopback
// and Chromium resolves it so, but Node on Windows asks the system resolver, which knows only "localhost".
// Playwright's request contexts run in Node, so this maps the gateway hosts to the loopback LoadBalancer.
const dns = require('node:dns');
const address = process.env.CLINICFLOW_GATEWAY_ADDRESS ?? '127.0.0.1';
const local = hostname => typeof hostname === 'string' && hostname.toLowerCase().endsWith('.localhost');
const answer = options => ((typeof options === 'object' && options?.all) ? [{ address, family: 4 }] : null);

const lookup = dns.lookup;
dns.lookup = function (hostname, options, callback) {
  if (!local(hostname)) return lookup.apply(this, arguments);
  const done = typeof options === 'function' ? options : callback;
  const all = answer(options);
  process.nextTick(() => (all ? done(null, all) : done(null, address, 4)));
};
const promisesLookup = dns.promises.lookup;
dns.promises.lookup = function (hostname, options) {
  if (!local(hostname)) return promisesLookup.apply(this, arguments);
  return Promise.resolve(answer(options) ?? { address, family: 4 });
};
