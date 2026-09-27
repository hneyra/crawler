package crawler.model;

import java.util.List;
import java.util.Map;

public class ExtractionConfig {

    private DetailStrategy detailStrategy = DetailStrategy.HTML;
    private Map<String, String> fieldSelectors = Map.of();
    private List<String> scriptPatterns = List.of();
    private Map<String, String> jsonPaths = Map.of();
    private List<String> interceptPatterns = List.of();
    private Map<String, String> listFieldSelectors = Map.of();
    private Map<String, String> regexFields = Map.of();
    private List<LabelValueBlockConfig> labelValueBlocks = List.of();

    public DetailStrategy getDetailStrategy() {
        return detailStrategy;
    }

    public void setDetailStrategy(DetailStrategy detailStrategy) {
        this.detailStrategy = detailStrategy;
    }

    public Map<String, String> getFieldSelectors() {
        return fieldSelectors;
    }

    public void setFieldSelectors(Map<String, String> fieldSelectors) {
        this.fieldSelectors = fieldSelectors;
    }

    public List<String> getScriptPatterns() {
        return scriptPatterns;
    }

    public void setScriptPatterns(List<String> scriptPatterns) {
        this.scriptPatterns = scriptPatterns;
    }

    public Map<String, String> getJsonPaths() {
        return jsonPaths;
    }

    public void setJsonPaths(Map<String, String> jsonPaths) {
        this.jsonPaths = jsonPaths;
    }

    public List<String> getInterceptPatterns() {
        return interceptPatterns;
    }

    public void setInterceptPatterns(List<String> interceptPatterns) {
        this.interceptPatterns = interceptPatterns;
    }

    public Map<String, String> getListFieldSelectors() {
        return listFieldSelectors;
    }

    public void setListFieldSelectors(Map<String, String> listFieldSelectors) {
        this.listFieldSelectors = listFieldSelectors;
    }

    public Map<String, String> getRegexFields() {
        return regexFields;
    }

    public void setRegexFields(Map<String, String> regexFields) {
        this.regexFields = regexFields;
    }

    public List<LabelValueBlockConfig> getLabelValueBlocks() {
        return labelValueBlocks;
    }

    public void setLabelValueBlocks(List<LabelValueBlockConfig> labelValueBlocks) {
        this.labelValueBlocks = labelValueBlocks;
    }

}
