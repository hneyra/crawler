import * as pulumi from "@pulumi/pulumi";
import * as k8s from "@pulumi/kubernetes";

export interface CrawlerArgs {
    /** Full image reference for the app, e.g. "localhost:30500/crawler:latest" */
    image: pulumi.Input<string>;
    /** Full image reference for the renderer, e.g. "localhost:30500/crawler-renderer:latest" */
    rendererImage: pulumi.Input<string>;
    /** Raw kubeconfig YAML string to connect to the k3s cluster */
    kubeconfig: pulumi.Input<string>;
    /** PostgreSQL password for the crawler role (provisioned by iaac) */
    dbPassword: pulumi.Input<string>;
    /** Kubernetes namespace to deploy into. Default: "crawler" */
    namespace?: string;
    /** Number of app replicas. Default: 1 */
    replicas?: number;
    /** Hostname for the Ingress rule, e.g. "crawler.local". Default: no host filter */
    host?: string;
    /** PVC storage size for raw crawl data. Default: "20Gi" */
    storageSize?: string;
}

export class CrawlerResource extends pulumi.ComponentResource {
    public readonly namespaceName: pulumi.Output<string>;
    public readonly appServiceName: pulumi.Output<string>;

    constructor(name: string, args: CrawlerArgs, opts?: pulumi.ComponentResourceOptions) {
        super("crawler:app:Crawler", name, {}, opts);

        const provider = new k8s.Provider(`${name}-k8s`, {
            kubeconfig: args.kubeconfig,
        }, { parent: this });

        const child = { parent: this, provider };
        const ns = args.namespace ?? "crawler";
        const labels = (component: string) => ({ app: "crawler", component });

        // ── Namespace ─────────────────────────────────────────────────────────

        const namespace = new k8s.core.v1.Namespace(`${name}-ns`, {
            metadata: { name: ns },
        }, child);

        const meta = (component: string, extra?: object) => ({
            namespace: namespace.metadata.name,
            labels: labels(component),
            ...extra,
        });

        // ── DB Secret ─────────────────────────────────────────────────────────
        // PostgreSQL is in namespace "postgres", service "postgres" (provisioned by iaac).
        // Role and database are both "crawler".

        const dbSecret = new k8s.core.v1.Secret(`${name}-db-secret`, {
            metadata: meta("app", { name: "crawler-db" }),
            stringData: {
                url:      "jdbc:postgresql://postgres.postgres.svc.cluster.local:5432/crawler",
                username: "crawler",
                password: args.dbPassword,
            },
        }, child);

        // ── Raw data PVC ──────────────────────────────────────────────────────

        const rawDataPvc = new k8s.core.v1.PersistentVolumeClaim(`${name}-raw-data-pvc`, {
            metadata: meta("app", { name: "crawler-raw-data" }),
            spec: {
                accessModes: ["ReadWriteOnce"],
                resources: { requests: { storage: args.storageSize ?? "20Gi" } },
            },
        }, child);

        // ── Renderer Deployment + Service ─────────────────────────────────────
        // The renderer is a Playwright/Chromium service that handles JS-heavy pages.
        // Crawler connects to it via http://renderer:3000 within the same namespace.

        const rendererLabels = labels("renderer");

        const rendererDeploy = new k8s.apps.v1.Deployment(`${name}-renderer`, {
            metadata: meta("renderer", { name: "renderer" }),
            spec: {
                replicas: 1,
                selector: { matchLabels: rendererLabels },
                template: {
                    metadata: { labels: rendererLabels },
                    spec: {
                        containers: [{
                            name: "renderer",
                            image: args.rendererImage,
                            imagePullPolicy: "Always",
                            ports: [{ containerPort: 3000 }],
                            env: [
                                { name: "PORT",            value: "3000" },
                                { name: "DEFAULT_TIMEOUT", value: "30000" },
                            ],
                            readinessProbe: {
                                httpGet: { path: "/health", port: 3000 },
                                initialDelaySeconds: 5,
                                periodSeconds: 10,
                                failureThreshold: 6,
                            },
                        }],
                    },
                },
            },
        }, child);

        const rendererSvc = new k8s.core.v1.Service(`${name}-renderer-svc`, {
            metadata: meta("renderer", { name: "renderer" }),
            spec: {
                selector: rendererLabels,
                ports: [{ port: 3000, targetPort: 3000, name: "http" }],
                type: "ClusterIP",
            },
        }, { ...child, dependsOn: [rendererDeploy] });

        // ── App Deployment ────────────────────────────────────────────────────

        const appLabels = labels("app");

        const appDeploy = new k8s.apps.v1.Deployment(`${name}-app`, {
            metadata: meta("app", { name: "crawler" }),
            spec: {
                replicas: args.replicas ?? 1,
                selector: { matchLabels: appLabels },
                template: {
                    metadata: { labels: appLabels },
                    spec: {
                        containers: [{
                            name: "crawler",
                            image: args.image,
                            imagePullPolicy: "Always",
                            ports: [{ containerPort: 8080 }],
                            env: [
                                {
                                    name: "SPRING_DATASOURCE_URL",
                                    valueFrom: {
                                        secretKeyRef: {
                                            name: dbSecret.metadata.name,
                                            key:  "url",
                                        },
                                    },
                                },
                                {
                                    name: "SPRING_DATASOURCE_USERNAME",
                                    valueFrom: {
                                        secretKeyRef: {
                                            name: dbSecret.metadata.name,
                                            key:  "username",
                                        },
                                    },
                                },
                                {
                                    name: "SPRING_DATASOURCE_PASSWORD",
                                    valueFrom: {
                                        secretKeyRef: {
                                            name: dbSecret.metadata.name,
                                            key:  "password",
                                        },
                                    },
                                },
                                // Raw snapshot storage — override application.yaml default (E:/crawler-data/)
                                { name: "CRAWLER_RAW_DATA_DIR",       value: "/crawler-data/" },
                                // Renderer lives in the same namespace as a ClusterIP service
                                { name: "CRAWLER_RENDERER_BASE_URL",  value: "http://renderer:3000" },
                            ],
                            volumeMounts: [{
                                name:      "raw-data",
                                mountPath: "/crawler-data",
                            }],
                            startupProbe: {
                                httpGet: { path: "/actuator/health/liveness", port: 8080 },
                                initialDelaySeconds: 10,
                                periodSeconds: 10,
                                failureThreshold: 36,
                            },
                            readinessProbe: {
                                httpGet: { path: "/actuator/health/readiness", port: 8080 },
                                periodSeconds: 10,
                                failureThreshold: 3,
                            },
                            livenessProbe: {
                                httpGet: { path: "/actuator/health/liveness", port: 8080 },
                                periodSeconds: 20,
                                failureThreshold: 3,
                            },
                        }],
                        volumes: [{
                            name: "raw-data",
                            persistentVolumeClaim: { claimName: rawDataPvc.metadata.name },
                        }],
                    },
                },
            },
        }, { ...child, dependsOn: [dbSecret, rawDataPvc, rendererSvc] });

        const appSvc = new k8s.core.v1.Service(`${name}-app-svc`, {
            metadata: meta("app", { name: "crawler" }),
            spec: {
                selector: appLabels,
                ports: [{ port: 80, targetPort: 8080, name: "http" }],
                type: "ClusterIP",
            },
        }, { ...child, dependsOn: [appDeploy] });

        // ── Ingress (Traefik — default k3s ingress controller) ────────────────
        // Traefik IngressRoute in iaac/src/services.yml routes /crawler → this service
        // with StripPrefix, so Spring Boot receives the request at /.

        new k8s.networking.v1.Ingress(`${name}-ingress`, {
            metadata: meta("ingress", {
                name: "crawler",
                annotations: {
                    "traefik.ingress.kubernetes.io/router.entrypoints": "web",
                },
            }),
            spec: {
                rules: [{
                    ...(args.host ? { host: args.host } : {}),
                    http: {
                        paths: [{
                            path:     "/",
                            pathType: "Prefix",
                            backend: {
                                service: {
                                    name: appSvc.metadata.name,
                                    port: { name: "http" },
                                },
                            },
                        }],
                    },
                }],
            },
        }, { ...child, dependsOn: [appSvc] });

        this.namespaceName  = namespace.metadata.name;
        this.appServiceName = appSvc.metadata.name;

        this.registerOutputs({
            namespaceName:  this.namespaceName,
            appServiceName: this.appServiceName,
        });
    }
}
