// Attempts to load environment variables from a local .env file.
// This is best-effort: if dotenv is not installed or .env is absent, continue silently.
try {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const dotenv = require('dotenv');
  if (dotenv && typeof dotenv.config === 'function') {
    dotenv.config();
  }
} catch (_) {
  // ignore: dotenv not installed; env vars can still be provided via the shell
}

