import type { Config } from 'jest';

const config: Config = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  testMatch: ['**/tests/**/*.e2e.spec.ts'],
  // Load .env if present (non-fatal if dotenv is missing)
  setupFilesAfterEnv: ['<rootDir>/src/jest-setup.ts'],
  verbose: true,
  testTimeout: 120000,
  coverageProvider: 'v8',
  collectCoverage: Boolean(process.env.CI === 'true' || process.env.E2E_COVERAGE === 'true'),
  collectCoverageFrom: ['src/**/*.ts', '!src/jest-setup.ts', '!src/tests/**/*.ts'],
  coverageDirectory: 'coverage',
  coverageThreshold: {
    global: {
      statements: 75,
      branches: 65,
      functions: 75,
      lines: 75
    }
  }
};

export default config;
