package crawler.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class CloudflareDetectorTest {

    // -------------------------------------------------------------------------
    // isCloudflareBlock – title signals
    // -------------------------------------------------------------------------

    @Test
    void isCloudflareBlock_detectsJustAMomentTitle() {
        Document doc = Jsoup.parse("<html><head><title>Just a moment...</title></head><body></body></html>");
        assertThat(CloudflareDetector.isCloudflareBlock(doc)).isTrue();
    }

    @Test
    void isCloudflareBlock_detectsAttentionRequiredTitle() {
        Document doc = Jsoup.parse("<html><head><title>Attention Required!</title></head><body></body></html>");
        assertThat(CloudflareDetector.isCloudflareBlock(doc)).isTrue();
    }

    @Test
    void isCloudflareBlock_detectsBlockedTitle() {
        Document doc = Jsoup.parse("<html><head><title>Sorry, you have been blocked</title></head><body></body></html>");
        assertThat(CloudflareDetector.isCloudflareBlock(doc)).isTrue();
    }

    // -------------------------------------------------------------------------
    // isCloudflareBlock – body signals
    // -------------------------------------------------------------------------

    @Test
    void isCloudflareBlock_detectsCfBrowserVerification() {
        Document doc = Jsoup.parse(
                "<html><body><div id='cf-browser-verification'>challenge</div></body></html>");
        assertThat(CloudflareDetector.isCloudflareBlock(doc)).isTrue();
    }

    @Test
    void isCloudflareBlock_detectsCfChlProg() {
        Document doc = Jsoup.parse(
                "<html><body><script>var cf_chl_prog = 'x';</script></body></html>");
        assertThat(CloudflareDetector.isCloudflareBlock(doc)).isTrue();
    }

    @Test
    void isCloudflareBlock_detectsCfImUnderAttack() {
        Document doc = Jsoup.parse(
                "<html><body><div class='cf-im-under-attack'></div></body></html>");
        assertThat(CloudflareDetector.isCloudflareBlock(doc)).isTrue();
    }

    // -------------------------------------------------------------------------
    // isCloudflareBlock – normal page
    // -------------------------------------------------------------------------

    @Test
    void isCloudflareBlock_returnsFalseForNormalPage() {
        Document doc = Jsoup.parse(
                "<html><head><title>Product Listing</title></head>"
                + "<body><a href='/product/1'>Item 1</a></body></html>");
        assertThat(CloudflareDetector.isCloudflareBlock(doc)).isFalse();
    }

    // -------------------------------------------------------------------------
    // isCloudflareStatusCode
    // -------------------------------------------------------------------------

    @Test
    void isCloudflareStatusCode_trueFor403() {
        assertThat(CloudflareDetector.isCloudflareStatusCode(403)).isTrue();
    }

    @Test
    void isCloudflareStatusCode_trueFor429() {
        assertThat(CloudflareDetector.isCloudflareStatusCode(429)).isTrue();
    }

    @Test
    void isCloudflareStatusCode_trueFor503() {
        assertThat(CloudflareDetector.isCloudflareStatusCode(503)).isTrue();
    }

    @Test
    void isCloudflareStatusCode_falseFor200() {
        assertThat(CloudflareDetector.isCloudflareStatusCode(200)).isFalse();
    }

    @Test
    void isCloudflareStatusCode_falseFor404() {
        assertThat(CloudflareDetector.isCloudflareStatusCode(404)).isFalse();
    }
}
