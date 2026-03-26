package crawler.config;

import java.util.List;

public class SitesConfig {

    private List<SiteDefinition> sites = List.of();

    public List<SiteDefinition> getSites() {
        return sites;
    }

    public void setSites(List<SiteDefinition> sites) {
        this.sites = sites;
    }

}
