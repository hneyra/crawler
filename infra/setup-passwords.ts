/**
 * setup-passwords.ts
 *
 * Generates random passwords for the PostgreSQL infrastructure and stores them
 * as Pulumi secrets in the stack config file (Pulumi.<stack>.yaml).
 *
 * Idempotent: existing passwords are NOT overwritten on subsequent runs.
 *
 * Usage:
 *   npm run setup-passwords                  # uses stack 'dev' by default
 *   PULUMI_STACK=prod npm run setup-passwords
 *   npx ts-node setup-passwords.ts prod
 *
 * After running locally, commit the updated Pulumi.<stack>.yaml:
 *   git add infra/Pulumi.*.yaml
 *   git commit -m "chore: update Pulumi password config"
 *
 * Read a stored password later:
 *   pulumi config get postgres:adminPassword --stack dev
 *   pulumi config get postgres:appPassword   --stack dev
 */

import { execSync } from "child_process";
import * as crypto from "crypto";
import * as path from "path";

const INFRA_DIR = __dirname;
const stack = process.argv[2] ?? process.env.PULUMI_STACK ?? "dev";

function generatePassword(length = 32): string {
  return crypto.randomBytes(length).toString("base64url").slice(0, length);
}

function configExists(key: string): boolean {
  try {
    execSync(`pulumi config get ${key} --stack ${stack}`, {
      stdio: "pipe",
      cwd: INFRA_DIR,
    });
    return true;
  } catch {
    return false;
  }
}

function configSetSecret(key: string, value: string): void {
  execSync(`pulumi config set --secret ${key} "${value}" --stack ${stack}`, {
    stdio: "inherit",
    cwd: INFRA_DIR,
  });
}

const passwords: Record<string, string> = {
  "postgres:adminPassword": generatePassword(),
  "postgres:appPassword": generatePassword(),
};

let changed = false;

for (const [key, value] of Object.entries(passwords)) {
  if (configExists(key)) {
    console.log(`  [skip] ${key} already set`);
  } else {
    console.log(`  [set]  ${key}`);
    configSetSecret(key, value);
    changed = true;
  }
}

if (changed) {
  console.log(`\nPasswords written to infra/Pulumi.${stack}.yaml (encrypted).`);
  console.log(`Commit the file before running pulumi up:\n`);
  console.log(`  git add infra/Pulumi.*.yaml`);
  console.log(`  git commit -m "chore: update Pulumi password config"`);
} else {
  console.log("\nAll passwords already configured — nothing changed.");
}
