// Attempts to load environment variables from a local .env file.
// This is best-effort: if dotenv is not installed or .env is absent, continue silently.
import { closePool } from './utils/db';
import { stopAllProbes } from './utils/kafka';

type DotenvModule = {
  config: (options?: Record<string, unknown>) => unknown;
};

(function loadDotenv() {
  function resolveDotenv(): DotenvModule | null {
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const required: unknown = require('dotenv');
      if (required && typeof required === 'object' && 'config' in required) {
        const candidate = required as { config?: unknown };
        if (typeof candidate.config === 'function') {
          return required as DotenvModule;
        }
      }
    } catch {
      return null;
    }
    return null;
  }

  const dotenvModule = resolveDotenv();
  if (dotenvModule) {
    dotenvModule.config();
  }
})();

// Ensure shared resources are torn down exactly once after the test run.
afterAll(async () => {
  await Promise.all([
    stopAllProbes(),
    closePool()
  ]);
});
