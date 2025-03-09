package Crawler;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CrawlerConfig {
    private static final Logger logger = Logger.getLogger(CrawlerConfig.class.getName());

    // Default configuration values
    private static final int DEFAULT_MAX_THREADS = 5;
    private static final long DEFAULT_TIMEOUT_MILLIS = 10 * 60 * 1000;
    private static final int DEFAULT_MAX_DEPTH = 3;
    private static final long DEFAULT_POLITENESS_DELAY = 1000;
    private static final String DEFAULT_OUTPUT_FILE = "output/results.json";
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final boolean DEFAULT_RESPECT_ROBOTS_TXT = true;
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (compatible; MyWebCrawler/1.0)";
    private static final double DEFAULT_MIN_RELEVANCE_SCORE = 1.0;

    // Configuration keys
    private static final String KEY_MAX_THREADS = "max_threads";
    private static final String KEY_TIMEOUT_MINUTES = "timeout_minutes";
    private static final String KEY_MAX_DEPTH = "max_depth";
    private static final String KEY_POLITENESS_DELAY = "politeness_delay";
    private static final String KEY_OUTPUT_FILE = "output_file";
    private static final String KEY_MAX_RETRIES = "max_retries";
    private static final String KEY_RESPECT_ROBOTS_TXT = "respect_robots_txt";
    private static final String KEY_USER_AGENT = "user_agent";
    private static final String KEY_MIN_RELEVANCE_SCORE = "min_relevance_score";
    private static final String KEY_SEED_URLS = "seed_urls";

    private static final CrawlerConfig INSTANCE = new CrawlerConfig();

    private  int maxThreads;
    private  long timeoutMillis;
    private  int maxDepth;
    private  long politenessDelay;
    private  List<String> seedUrls;
    private  String outputFile;
    private  int maxRetries;
    private  boolean respectRobotsTxt;
    private  String userAgent;
    private  double minRelevanceScore;

    private CrawlerConfig() {
        this.maxThreads = DEFAULT_MAX_THREADS;
        this.timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        this.maxDepth = DEFAULT_MAX_DEPTH;
        this.politenessDelay = DEFAULT_POLITENESS_DELAY;
        this.seedUrls = new ArrayList<>(getDefaultSeedUrls());
        this.outputFile = DEFAULT_OUTPUT_FILE;
        this.maxRetries = DEFAULT_MAX_RETRIES;
        this.respectRobotsTxt = DEFAULT_RESPECT_ROBOTS_TXT;
        this.userAgent = DEFAULT_USER_AGENT;
        this.minRelevanceScore = DEFAULT_MIN_RELEVANCE_SCORE;
    }

    public static CrawlerConfig getInstance() {
        return INSTANCE;
    }

    public void loadFromFile(String configFile) {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configFile)) {
            props.load(in);

            int maxThreads = getIntProperty(props, KEY_MAX_THREADS, DEFAULT_MAX_THREADS);
            long timeoutMillis = getLongProperty(props, KEY_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MILLIS / (60 * 1000)) * 60 * 1000;
            int maxDepth = getIntProperty(props, KEY_MAX_DEPTH, DEFAULT_MAX_DEPTH);
            long politenessDelay = getLongProperty(props, KEY_POLITENESS_DELAY, DEFAULT_POLITENESS_DELAY);
            String outputFile = props.getProperty(KEY_OUTPUT_FILE, DEFAULT_OUTPUT_FILE);
            int maxRetries = getIntProperty(props, KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES);
            boolean respectRobotsTxt = getBooleanProperty(props, KEY_RESPECT_ROBOTS_TXT, DEFAULT_RESPECT_ROBOTS_TXT);
            String userAgent = props.getProperty(KEY_USER_AGENT, DEFAULT_USER_AGENT);
            double minRelevanceScore = getDoubleProperty(props, KEY_MIN_RELEVANCE_SCORE, DEFAULT_MIN_RELEVANCE_SCORE);
            List<String> seedUrls = getSeedUrls(props);

            validateConfig(maxThreads, timeoutMillis, maxDepth, politenessDelay, maxRetries, minRelevanceScore);

            synchronized (this) {
                this.maxThreads = maxThreads;
                this.timeoutMillis = timeoutMillis;
                this.maxDepth = maxDepth;
                this.politenessDelay = politenessDelay;
                this.seedUrls.clear();
                this.seedUrls.addAll(seedUrls);
                this.outputFile = outputFile;
                this.maxRetries = maxRetries;
                this.respectRobotsTxt = respectRobotsTxt;
                this.userAgent = userAgent;
                this.minRelevanceScore = minRelevanceScore;
            }

            logger.info("Configuration loaded successfully from " + configFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load configuration file. Using defaults.", e);
            loadDefaults();
        }
    }

    private int getIntProperty(Properties props, String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warning("Invalid value for " + key + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    private long getLongProperty(Properties props, String key, long defaultValue) {
        try {
            return Long.parseLong(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warning("Invalid value for " + key + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    private double getDoubleProperty(Properties props, String key, double defaultValue) {
        try {
            return Double.parseDouble(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warning("Invalid value for " + key + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    private boolean getBooleanProperty(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    private List<String> getSeedUrls(Properties props) {
        List<String> urls = new ArrayList<>();
        if (props.containsKey(KEY_SEED_URLS)) {
            String seedUrlsStr = props.getProperty(KEY_SEED_URLS);
            for (String url : seedUrlsStr.split(",")) {
                url = url.trim();
                if (!url.isEmpty()) {
                    urls.add(url);
                }
            }
        }
        return urls.isEmpty() ? getDefaultSeedUrls() : urls;
    }

    private List<String> getDefaultSeedUrls() {
        List<String> defaultSeedUrls = new ArrayList<>();

       // defaultSeedUrls.add("https://www.bbc.com/news");



        return defaultSeedUrls;
    }

    private void validateConfig(int maxThreads, long timeoutMillis, int maxDepth, long politenessDelay, int maxRetries, double minRelevanceScore) {
        if (maxThreads <= 0) {
            throw new IllegalArgumentException("max_threads must be greater than 0");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeout_minutes must be greater than 0");
        }
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("max_depth must be greater than 0");
        }
        if (politenessDelay < 0) {
            throw new IllegalArgumentException("politeness_delay must be non-negative");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("max_retries must be non-negative");
        }
        if (minRelevanceScore < 0) {
            throw new IllegalArgumentException("min_relevance_score must be non-negative");
        }
    }

    public void loadDefaults() {
        synchronized (this) {
            this.maxThreads = DEFAULT_MAX_THREADS;
            this.timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
            this.maxDepth = DEFAULT_MAX_DEPTH;
            this.politenessDelay = DEFAULT_POLITENESS_DELAY;
            this.seedUrls.clear();
            this.seedUrls.addAll(getDefaultSeedUrls());
            this.outputFile = DEFAULT_OUTPUT_FILE;
            this.maxRetries = DEFAULT_MAX_RETRIES;
            this.respectRobotsTxt = DEFAULT_RESPECT_ROBOTS_TXT;
            this.userAgent = DEFAULT_USER_AGENT;
            this.minRelevanceScore = DEFAULT_MIN_RELEVANCE_SCORE;
        }
        logger.info("Loaded default configuration");
    }

    // Getters
    public int getMaxThreads() { return maxThreads; }
    public long getTimeoutMillis() { return timeoutMillis; }
    public int getMaxDepth() { return maxDepth; }
    public long getPolitenessDelay() { return politenessDelay; }
    public List<String> getSeedUrls() { return Collections.unmodifiableList(seedUrls); }
    public String getOutputFile() { return outputFile; }
    public int getMaxRetries() { return maxRetries; }
    public boolean isRespectRobotsTxt() { return respectRobotsTxt; }
    public String getUserAgent() { return userAgent; }
    public double getMinRelevanceScore() { return minRelevanceScore; }
}