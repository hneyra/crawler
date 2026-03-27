package crawler.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "pagination_type", nullable = false)
    private PaginationType paginationType;

    @Column(name = "item_link_selector", nullable = false)
    private String itemLinkSelector;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_extraction_type", nullable = false)
    private LinkExtractionType linkExtractionType = LinkExtractionType.HREF;

    @Column(name = "link_attribute")
    private String linkAttribute;

    @Column(name = "next_page_selector")
    private String nextPageSelector;

    @Column(nullable = false)
    private boolean enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

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

    public String getLinkAttribute() {
        return linkAttribute;
    }

    public void setLinkAttribute(String linkAttribute) {
        this.linkAttribute = linkAttribute;
    }

    public LinkExtractionType getLinkExtractionType() {
        return linkExtractionType;
    }

    public void setLinkExtractionType(LinkExtractionType linkExtractionType) {
        this.linkExtractionType = linkExtractionType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
