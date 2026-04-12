import * as pulumi from "@pulumi/pulumi";
import { CrawlerResource } from "./src/crawler";

const config = new pulumi.Config();

// Read outputs from the iaac base-infrastructure stack
const iaacStackName = config.require("iaacStack");
const iaac          = new pulumi.StackReference(iaacStackName);

const registryHost = iaac.getOutput("registryEndpoint") as pulumi.Output<string>;
const kubeconfig   = iaac.requireOutput("k3sKubeconfig") as pulumi.Output<string>;
const dbPassword   = iaac.requireOutput("crawlerPostgresPassword") as pulumi.Output<string>;

const image             = config.get("image")             ?? "crawler";
const imageTag          = config.get("imageTag")          ?? "latest";
const rendererImage     = config.get("rendererImage")     ?? "crawler-renderer";
const rendererImageTag  = config.get("rendererImageTag")  ?? "latest";

// PostgreSQL is provisioned by the iaac stack in namespace "postgres", service "postgres".
// Role and database name are both "crawler" (defined in iaac/src/databases.yml).
const crawler = new CrawlerResource("crawler", {
    image:          pulumi.interpolate`${registryHost}/${image}:${imageTag}`,
    rendererImage:  pulumi.interpolate`${registryHost}/${rendererImage}:${rendererImageTag}`,
    kubeconfig,
    dbPassword,
    namespace:      config.get("namespace"),
    replicas:       config.getNumber("replicas"),
    host:           config.get("host"),
});

export const namespaceName  = crawler.namespaceName;
export const appServiceName = crawler.appServiceName;
export const imageRef       = pulumi.interpolate`${registryHost}/${image}:${imageTag}`;
