package Crawler;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class URLManager {
    // Thread-safe set to track visited URLs
    private static final Set<String> visitedUrls = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Track the last access time for each domain for politeness
    private static final Map<String, Long> lastAccessTimes = new ConcurrentHashMap<>();

    // Track robots.txt rules
    private static final Map<String, Set<String>> robotsDisallowRules = new ConcurrentHashMap<>();

    // Politeness delay (milliseconds)
    private static final long POLITENESS_DELAY = 1000;

    // Common binary file extensions to avoid
    private static final Pattern BINARY_EXTENSIONS = Pattern.compile(
            ".*\\.(jpg|jpeg|png|gif|bmp|webp|mp3|mp4|wav|avi|mov|wmv|flv|pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|tar|gz|exe|dmg|iso|bin)$",
            Pattern.CASE_INSENSITIVE
    );

    public static boolean visit(String url) {
        return visitedUrls.add(url);
    }

    public static boolean shouldProcess(String url) {
        // Skip empty URLs
        if (url == null || url.isEmpty()) {
            return false;
        }

        try {
            URL parsedUrl = new URL(url);
            String protocol = parsedUrl.getProtocol();

            if (!protocol.equals("http") && !protocol.equals("https")) {
                return false;
            }

            if (url.contains("#")) {
                String baseUrl = url.substring(0, url.indexOf('#'));
                if (visitedUrls.contains(baseUrl)) {
                    return false;
                }
            }

            if (BINARY_EXTENSIONS.matcher(url).matches()) {
                return false;
            }

            if (!isAllowedByRobotsTxt(url)) {
                return false;
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    public static void applyPolitenessDelay(String url) {
        try {
            URL parsedUrl = new URL(url);
            String domain = parsedUrl.getHost();

            Long lastAccess = lastAccessTimes.get(domain);
            if (lastAccess != null) {
                long timeSinceLastAccess = System.currentTimeMillis() - lastAccess;
                if (timeSinceLastAccess < POLITENESS_DELAY) {
                    try {
                        Thread.sleep(POLITENESS_DELAY - timeSinceLastAccess);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            lastAccessTimes.put(domain, System.currentTimeMillis());

        } catch (Exception e) {
        }
    }

    private static boolean isAllowedByRobotsTxt(String url) {
        try {
            URL parsedUrl = new URL(url);
            String domain = parsedUrl.getHost();
            String path = parsedUrl.getPath();

            if (!robotsDisallowRules.containsKey(domain)) {
                fetchRobotsTxtRules(parsedUrl);
            }

            Set<String> disallowRules = robotsDisallowRules.get(domain);
            if (disallowRules != null) {
                for (String rule : disallowRules) {
                    if (path.startsWith(rule)) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            return true;
        }
    }

    // Fetch and parse robots.txt for a domain
    private static void fetchRobotsTxtRules(URL url) {
        try {
            String domain = url.getHost();
            String robotsUrl = url.getProtocol() + "://" + domain + "/robots.txt";

            // Use a short timeout for robots.txt
            Document robotsTxt = Jsoup.connect(robotsUrl).timeout(3000).get();
            String content = robotsTxt.text();

            // Parse robots.txt content (basic implementation)
            Set<String> disallowRules = new HashSet<>();
            Scanner scanner = new Scanner(content);
            boolean relevantUserAgent = false;

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                // Check for User-agent line
                if (line.toLowerCase().startsWith("user-agent:")) {
                    String agent = line.substring(11).trim();
                    // Check if rule applies to us or all agents
                    relevantUserAgent = agent.equals("*") || agent.contains("bot");
                }
                // Process Disallow rules if they apply to us
                else if (relevantUserAgent && line.toLowerCase().startsWith("disallow:")) {
                    String path = line.substring(9).trim();
                    if (!path.isEmpty()) {
                        disallowRules.add(path);
                    }
                }
            }
            scanner.close();

            // Store rules for this domain
            robotsDisallowRules.put(domain, disallowRules);

        } catch (IOException e) {
            // If robots.txt cannot be fetched, assume nothing is disallowed
            robotsDisallowRules.put(url.getHost(), new HashSet<>());
        }
    }

    // Get count of visited URLs
    public static int getVisitedCount() {
        return visitedUrls.size();
    }

    // Check if URL has been visited without marking it
    public static boolean isVisited(String url) {
        return visitedUrls.contains(url);
    }
}