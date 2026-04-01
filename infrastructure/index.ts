import * as pulumi from "@pulumi/pulumi";
import * as docker from "@pulumi/docker";

// ─── Config ───────────────────────────────────────────────────────────────────
// Project config (crawler:*)
const config = new pulumi.Config();
const imageTag        = config.require("imageTag");           // set in CI: short SHA
const repoOwner       = config.require("repoOwner");          // set in CI: github.repository_owner
const registryServer  = config.get("registryServer") ?? "ghcr.io";
const dbPassword      = config.requireSecret("dbPassword");   // set once as secret

// Docker provider config (docker:host)
// Set in CI: pulumi config set docker:host "ssh://USER@HOST" --stack prod
const dockerConfig = new pulumi.Config("docker");
const dockerHost   = dockerConfig.get("host");               // falls back to DOCKER_HOST env var

// ─── Remote Docker provider (SSH) ────────────────────────────────────────────
// Connects to the remote server's Docker daemon via SSH.
// The runner must have the deploy SSH key loaded in ssh-agent.
const provider = new docker.Provider("remote", {
    host: dockerHost,
    sshOpts: [
        "-o", "StrictHostKeyChecking=no",
        "-o", "UserKnownHostsFile=/dev/null",
        "-o", "BatchMode=yes",
    ],
});

const opts: pulumi.ResourceOptions = { provider };

// ─── Network ─────────────────────────────────────────────────────────────────
const network = new docker.Network("crawler-net", {
    name: "crawler-net",
}, opts);

// ─── Volumes ─────────────────────────────────────────────────────────────────
const pgVolume = new docker.Volume("crawler-pgdata", {
    name: "crawler-pgdata",
}, opts);

const rawDataVolume = new docker.Volume("crawler-rawdata", {
    name: "crawler-rawdata",
}, opts);

// ─── PostgreSQL ───────────────────────────────────────────────────────────────
const postgres = new docker.Container("crawler-postgres", {
    name: "crawler-postgres",
    image: "postgres:16",
    restart: "unless-stopped",
    networksAdvanced: [{ name: network.name }],
    envs: [
        "POSTGRES_DB=crawler",
        "POSTGRES_USER=crawler",
        pulumi.interpolate`POSTGRES_PASSWORD=${dbPassword}`,
    ],
    volumes: [{
        volumeName: pgVolume.name,
        containerPath: "/var/lib/postgresql/data",
    }],
}, opts);

// ─── Renderer ─────────────────────────────────────────────────────────────────
const rendererImageName =
    `${registryServer}/${repoOwner.toLowerCase()}/crawler-renderer:${imageTag}`;

const rendererImage = new docker.RemoteImage("crawler-renderer-image", {
    name: rendererImageName,
    keepLocally: false,
}, opts);

const renderer = new docker.Container("crawler-renderer", {
    name: "crawler-renderer",
    image: rendererImage.repoDigest,
    restart: "unless-stopped",
    networksAdvanced: [{ name: network.name }],
    envs: [
        "PORT=3000",
        "DEFAULT_TIMEOUT=30000",
    ],
}, opts);

// ─── App (backend + frontend) ────────────────────────────────────────────────
const appImageName =
    `${registryServer}/${repoOwner.toLowerCase()}/crawler-app:${imageTag}`;

const appImage = new docker.RemoteImage("crawler-app-image", {
    name: appImageName,
    keepLocally: false,
}, opts);

const app = new docker.Container("crawler-app", {
    name: "crawler-app",
    image: appImage.repoDigest,
    restart: "unless-stopped",
    networksAdvanced: [{ name: network.name }],
    ports: [{ internal: 8080, external: 8080 }],
    envs: [
        "SPRING_PROFILES_ACTIVE=prod",
        "SPRING_DATASOURCE_URL=jdbc:postgresql://crawler-postgres:5432/crawler",
        "SPRING_DATASOURCE_USERNAME=crawler",
        pulumi.interpolate`SPRING_DATASOURCE_PASSWORD=${dbPassword}`,
        "CRAWLER_RENDERER_BASE_URL=http://crawler-renderer:3000",
        "CRAWLER_RAW_DATA_DIR=/data/raw",
    ],
    volumes: [{
        volumeName: rawDataVolume.name,
        containerPath: "/data/raw",
    }],
}, { ...opts, dependsOn: [postgres, renderer] });

// ─── Outputs ─────────────────────────────────────────────────────────────────
export const networkName   = network.name;
export const appContainerName = app.name;
export const appPort       = 8080;
