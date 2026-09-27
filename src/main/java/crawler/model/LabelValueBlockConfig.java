package crawler.model;

public class LabelValueBlockConfig {

    private String containerSelector;
    private String itemSelector;
    private String labelSelector;
    private String valueSelector;
    private String targetKey;

    public String getContainerSelector() {
        return containerSelector;
    }

    public void setContainerSelector(String containerSelector) {
        this.containerSelector = containerSelector;
    }

    public String getItemSelector() {
        return itemSelector;
    }

    public void setItemSelector(String itemSelector) {
        this.itemSelector = itemSelector;
    }

    public String getLabelSelector() {
        return labelSelector;
    }

    public void setLabelSelector(String labelSelector) {
        this.labelSelector = labelSelector;
    }

    public String getValueSelector() {
        return valueSelector;
    }

    public void setValueSelector(String valueSelector) {
        this.valueSelector = valueSelector;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public void setTargetKey(String targetKey) {
        this.targetKey = targetKey;
    }

}
