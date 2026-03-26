package crawler.config;

import crawler.model.PaginationType;

public class CategoryDefinition {

    private String name;
    private String url;
    private PaginationType paginationType = PaginationType.PAGE_PARAM;
    private String itemLinkSelector;
    private String nextPageSelector;
    private boolean enabled = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public PaginationType getPaginationType() {
        return paginationType;
    }

    public void setPaginationType(PaginationType paginationType) {
        this.paginationType = paginationType;
    }

    public String getItemLinkSelector() {
        return itemLinkSelector;
    }

    public void setItemLinkSelector(String itemLinkSelector) {
        this.itemLinkSelector = itemLinkSelector;
    }

    public String getNextPageSelector() {
        return nextPageSelector;
    }

    public void setNextPageSelector(String nextPageSelector) {
        this.nextPageSelector = nextPageSelector;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
