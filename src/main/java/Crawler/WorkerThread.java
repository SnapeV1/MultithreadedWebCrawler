package Crawler;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class WorkerThread implements Runnable {

    private final BlockingQueue<UrlDepthPair> queue;
    private final String keyword;
    private final long startTime;
    private final long timeoutMillis;
    private final int maxDepth;
    private final double minRelevanceScore;
    private final Set<String> contentHashes = new HashSet<>();
    private final String outputFile;
    private static final AtomicInteger processedUrlCount = new AtomicInteger(0);
    private static final AtomicInteger matchedUrlCount = new AtomicInteger(0);
    private static final int MAX_RETRIES = 2;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; MyWebCrawler/1.0)";
    private static final Logger logger = Logger.getLogger(WorkerThread.class.getName());
    private final ReentrantLock lock = new ReentrantLock();

    public WorkerThread(BlockingQueue<UrlDepthPair> queue, String keyword, long startTime,
                        long timeoutMillis, int maxDepth, double minRelevanceScore, String outputFile) {
        this.queue = queue;
        this.keyword = keyword.toLowerCase();
        this.startTime = startTime;
        this.timeoutMillis = timeoutMillis;
        this.maxDepth = maxDepth;
        this.minRelevanceScore = minRelevanceScore;
        this.outputFile = outputFile;
    }

    @Override
    public void run() {
        try {
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                UrlDepthPair pair = queue.take();
                String url = pair.url();
                int depth = pair.depth();

                if (depth > maxDepth) {
                    continue;
                }

                if (URLManager.shouldProcess(url) && URLManager.visit(url)) {
                    crawl(url, depth);
                }

                int processed = processedUrlCount.get();
                if (processed % 100 == 0) {
                    logger.info(String.format("Progress: %d URLs processed, %d matches found",
                            processed, matchedUrlCount.get()));
                }
            }
            logger.info("Timeout reached, stopping crawling...");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Worker thread interrupted", e);
        }
    }

    private void crawl(String url, int depth) {
        URLManager.applyPolitenessDelay(url);

        try {
            logger.info("Crawling: " + url + " (depth: " + depth + ")");
            processedUrlCount.incrementAndGet();

            Document doc = fetchWithRetry(url);
            if (doc == null) {
                return;
            }

            double relevanceScore = calculateRelevanceScore(doc);

            if (relevanceScore >= minRelevanceScore) {
                JSONArray resultsArray = new JSONArray();

                String title = doc.title();
                String publicationDate = extractPublicationDate(doc);
                String author = extractAuthor(doc);

                for (Element paragraph : doc.select("p, article, section")) {
                    String text = paragraph.text().trim();
                    if (!text.isEmpty() && text.toLowerCase().contains(keyword)) {
                        String contentHash = String.valueOf(text.hashCode());
                        if (contentHashes.add(contentHash)) {
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("url", url);
                            jsonObject.put("title", title);
                            jsonObject.put("content", text);
                            jsonObject.put("date", publicationDate);
                            jsonObject.put("author", author);
                            jsonObject.put("relevance_score", relevanceScore);
                            jsonObject.put("crawl_depth", depth);
                            jsonObject.put("crawl_time", System.currentTimeMillis());
                            resultsArray.put(jsonObject);
                        }
                    }
                }

                if (resultsArray.length() > 0) {
                    saveData(resultsArray);
                    matchedUrlCount.incrementAndGet();
                }
            }

            if (depth < maxDepth) {
                Elements links = doc.select("a[href]");
                int linkCount = 0;

                for (Element link : links) {
                    if (linkCount > 50) break;

                    String nextUrl = normalizeUrl(link.absUrl("href"));
                    if (!nextUrl.isEmpty() && URLManager.shouldProcess(nextUrl) && !URLManager.isVisited(nextUrl)) {
                        queue.put(new UrlDepthPair(nextUrl, depth + 1));
                        linkCount++;
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to crawl: " + url, e);
        }
    }

    private Document fetchWithRetry(String url) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                int timeout = 5000 + (attempt * 2000);

                return Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(timeout)
                        .followRedirects(true)
                        .get();

            } catch (HttpStatusException e) {
                if (e.getStatusCode() >= 400 && e.getStatusCode() < 500) {
                    logger.warning("Client error " + e.getStatusCode() + " for URL: " + url);
                    return null;
                }
                lastException = e;
            } catch (IOException e) {
                lastException = e;
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(1000 * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        logger.warning("Failed to fetch after " + MAX_RETRIES + " retries: " + url +
                " (" + lastException.getMessage() + ")");
        return null;
    }

    private String normalizeUrl(String url) {
        try {
            URL parsedUrl = new URL(url);
            String normalizedUrl = new URL(parsedUrl.getProtocol(),
                    parsedUrl.getHost(),
                    parsedUrl.getPort(),
                    parsedUrl.getPath()).toString();
            return normalizedUrl;
        } catch (Exception e) {
            return url;
        }
    }

    private double calculateRelevanceScore(Document doc) {
        String pageText = doc.text().toLowerCase();
        int keywordCount = 0;
        int index = pageText.indexOf(keyword);

        while (index != -1) {
            keywordCount++;
            index = pageText.indexOf(keyword, index + 1);
        }

        String title = doc.title().toLowerCase();
        boolean inTitle = title.contains(keyword);

        String url = doc.location().toLowerCase();
        boolean inUrl = url.contains(keyword);

        boolean inHeading = false;
        Elements headings = doc.select("h1, h2, h3");
        for (Element heading : headings) {
            if (heading.text().toLowerCase().contains(keyword)) {
                inHeading = true;
                break;
            }
        }

        double score = keywordCount * 0.5;
        if (inTitle) score += 5.0;
        if (inUrl) score += 3.0;
        if (inHeading) score += 2.0;

        int contentLength = pageText.length();
        if (contentLength > 2000) score *= 1.2;

        return score;
    }

    private String extractPublicationDate(Document doc) {
        Element timeElement = doc.selectFirst("time");
        if (timeElement != null) {
            String datetime = timeElement.attr("datetime");
            if (!datetime.isEmpty()) {
                return datetime;
            }
        }

        String[] dateMetaTags = {
                "meta[property=article:published_time]",
                "meta[name=pubdate]",
                "meta[name=publication_date]",
                "meta[name=date]",
                "meta[name=article.published]"
        };

        for (String selector : dateMetaTags) {
            Element metaDate = doc.selectFirst(selector);
            if (metaDate != null) {
                String content = metaDate.attr("content");
                if (!content.isEmpty()) {
                    return content;
                }
            }
        }

        String[] dateSelectors = {
                ".date", ".publish-date", ".timestamp", ".article-date",
                "#date", "#published-date", ".posted-on"
        };

        for (String selector : dateSelectors) {
            Element dateElement = doc.selectFirst(selector);
            if (dateElement != null) {
                String dateText = dateElement.text().trim();
                if (!dateText.isEmpty()) {
                    return dateText;
                }
            }
        }

        return "Unknown Date";
    }

    private String extractAuthor(Document doc) {
        String[] authorMetaTags = {
                "meta[property=article:author]",
                "meta[name=author]",
                "meta[name=dc.creator]"
        };

        for (String selector : authorMetaTags) {
            Element metaAuthor = doc.selectFirst(selector);
            if (metaAuthor != null) {
                String content = metaAuthor.attr("content");
                if (!content.isEmpty()) {
                    return content;
                }
            }
        }

        String[] authorSelectors = {
                ".author", ".byline", ".article-author", ".entry-author",
                "#author", "[rel=author]", ".contributor"
        };

        for (String selector : authorSelectors) {
            Element authorElement = doc.selectFirst(selector);
            if (authorElement != null) {
                String authorText = authorElement.text().trim();
                if (!authorText.isEmpty()) {
                    return authorText;
                }
            }
        }

        return "Unknown Author";
    }

    private void saveData(JSONArray jsonArray) {
        lock.lock();
        try {
            Path path = Paths.get(outputFile);
            List<String> existingData = Files.exists(path) ?
                    Files.readAllLines(path) : List.of();

            JSONArray finalArray = existingData.isEmpty() ?
                    new JSONArray() : new JSONArray(String.join("", existingData));

            for (int i = 0; i < jsonArray.length(); i++) {
                finalArray.put(jsonArray.getJSONObject(i));
            }

            Files.write(
                    path,
                    finalArray.toString(4).getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            logger.info("Saved " + jsonArray.length() + " results to " + outputFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save data", e);
        } finally {
            lock.unlock();
        }
    }

    public record UrlDepthPair(String url, int depth) {
    }
}