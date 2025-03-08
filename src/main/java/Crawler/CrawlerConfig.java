package Crawler;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

class CrawlerConfig {
    private static final CrawlerConfig instance = new CrawlerConfig();

    private int maxThreads = 5;
    private long timeoutMillis = 10 * 60 * 1000; // 10 minutes
    private int maxDepth = 3;
    private long politenessDelay = 1000;
    private List<String> seedUrls = new ArrayList<>();
    private String outputFile = "output/results.json";
    private int maxRetries = 2;
    private boolean respectRobotsTxt = true;
    private String userAgent = "Mozilla/5.0 (compatible; MyWebCrawler/1.0)";
    private double minRelevanceScore = 1.0;

    private CrawlerConfig() {
        // Private constructor for singleton
    }

    public static CrawlerConfig getInstance() {
        return instance;
    }

    public void loadFromFile(String configFile) {
        Properties props = new Properties();
        Logger logger = Logger.getLogger(CrawlerConfig.class.getName());

        try (FileInputStream in = new FileInputStream(configFile)) {
            props.load(in);

            if (props.containsKey("max_threads"))
                maxThreads = Integer.parseInt(props.getProperty("max_threads"));

            if (props.containsKey("timeout_minutes"))
                timeoutMillis = Long.parseLong(props.getProperty("timeout_minutes")) * 60 * 1000;

            if (props.containsKey("max_depth"))
                maxDepth = Integer.parseInt(props.getProperty("max_depth"));

            if (props.containsKey("politeness_delay"))
                politenessDelay = Long.parseLong(props.getProperty("politeness_delay"));

            if (props.containsKey("output_file"))
                outputFile = props.getProperty("output_file");

            if (props.containsKey("max_retries"))
                maxRetries = Integer.parseInt(props.getProperty("max_retries"));

            if (props.containsKey("respect_robots_txt"))
                respectRobotsTxt = Boolean.parseBoolean(props.getProperty("respect_robots_txt"));

            if (props.containsKey("user_agent"))
                userAgent = props.getProperty("user_agent");

            if (props.containsKey("min_relevance_score"))
                minRelevanceScore = Double.parseDouble(props.getProperty("min_relevance_score"));

            seedUrls.clear();
            if (props.containsKey("seed_urls")) {
                String seedUrlsStr = props.getProperty("seed_urls");
                for (String url : seedUrlsStr.split(",")) {
                    url = url.trim();
                    if (!url.isEmpty()) {
                        seedUrls.add(url);
                    }
                }
            }

            logger.info("Loaded configuration from " + configFile);
        } catch (IOException e) {
            logger.warning("Failed to load configuration file. Using defaults.");
            loadDefaults();
        }
    }

    public void loadDefaults() {
        maxThreads = 5;
        timeoutMillis = 10 * 60 * 1000;
        maxDepth = 3;
        politenessDelay = 1000;
        seedUrls.clear();
        seedUrls.add("https://www.wikipedia.org");
        outputFile = "output/results.json";
        maxRetries = 2;
        respectRobotsTxt = true;
        userAgent = "Mozilla/5.0 (compatible; MyWebCrawler/1.0)";
        minRelevanceScore = 1.0;
    }

    public int getMaxThreads() { return maxThreads; }
    public long getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(long timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    public long getPolitenessDelay() { return politenessDelay; }
    public List<String> getSeedUrls() { return seedUrls; }
    public String getOutputFile() { return outputFile; }
    public int getMaxRetries() { return maxRetries; }
    public boolean isRespectRobotsTxt() { return respectRobotsTxt; }
    public String getUserAgent() { return userAgent; }
    public double getMinRelevanceScore() { return minRelevanceScore; }
}