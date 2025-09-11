import type { Config } from 'jest';

const config: Config = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  testMatch: ['**/tests/**/*.e2e.spec.ts'],
  // Load .env if present (non-fatal if dotenv is missing)
  setupFiles: ['<rootDir>/src/jest-setup.ts'],
  verbose: true,
  testTimeout: 120000
};

export default config;
