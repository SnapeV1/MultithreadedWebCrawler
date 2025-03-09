package Crawler;

import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WorkerThread implements Runnable {
    private static final Logger logger = Logger.getLogger(WorkerThread.class.getName());
    private static final Dotenv dotenv = Dotenv.load();

    private static final String API_KEY = dotenv.get("GOOGLE_API_KEY");
    private static final String SEARCH_ENGINE_ID = dotenv.get("GOOGLE_SEARCH_ENGINE_ID");
    private static final String GOOGLE_SEARCH_URL = "https://www.googleapis.com/customsearch/v1";

    private final BlockingQueue<UrlDepthPair> queue;
    private final String keyword;
    private final long startTime;
    private final long timeoutMillis;
    private final int maxDepth;
    private final double minRelevanceScore;
    private final String outputFile;
    private static final AtomicInteger processedUrlCount = new AtomicInteger(0);
    private static final AtomicInteger matchedUrlCount = new AtomicInteger(0);
    private static volatile boolean running = true;

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
        // Start a new thread to listen for keypress to stop crawling
        startKeyListenerThread();

        try {
            if (queue.isEmpty()) {
                logger.info("Queue is empty, performing Google Custom Search to seed the URLs...");
                fetchAndAddSeedUrls(keyword);
            }

            while (running && (System.currentTimeMillis() - startTime < timeoutMillis)) {
                UrlDepthPair pair = queue.poll(500, TimeUnit.MILLISECONDS);
                if (!running) break;

                if (pair == null) continue;

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
            logger.info("Stopping crawling...");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Worker thread interrupted", e);
        }
    }

    private void startKeyListenerThread() {
        Thread keyListenerThread = new Thread(() -> {
            System.out.println("Press any key to stop crawling...");
            try (Scanner scanner = new Scanner(System.in)) {
                scanner.nextLine(); // Wait for user to press ENTER
                running = false; // Stop the crawling loop
                System.out.println("Stopping crawler...");
            }
        });
        keyListenerThread.setDaemon(true); // Allows the program to exit when the main thread finishes
        keyListenerThread.start();
    }

    private void crawl(String url, int depth) {
        try {
            logger.info("Crawling: " + url + " (depth: " + depth + ")");
            processedUrlCount.incrementAndGet();

            // Fetch search results from Google Custom Search API
            JSONObject searchResults = fetchGoogleSearchResults(keyword);
            if (searchResults == null) {
                return;
            }

            JSONArray items = searchResults.optJSONArray("items");
            if (items == null) {
                return;
            }

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String resultUrl = item.getString("link");
                String title = item.optString("title", "No Title");
                String snippet = item.optString("snippet", "No Snippet");

                // Calculate relevance score (example: based on keyword frequency in snippet)
                double relevanceScore = calculateRelevanceScore(snippet);

                if (relevanceScore >= minRelevanceScore) {
                    JSONObject result = new JSONObject();
                    result.put("url", resultUrl);
                    result.put("title", title);
                    result.put("content", snippet);
                    result.put("relevance_score", relevanceScore);
                    result.put("crawl_depth", depth);
                    result.put("crawl_time", System.currentTimeMillis());

                    saveData(result);
                    matchedUrlCount.incrementAndGet();
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to crawl: " + url, e);
        }
    }

    void fetchAndAddSeedUrls(String keyword) {
        try {
            JSONObject searchResults = fetchGoogleSearchResults(keyword);
            if (searchResults == null) {
                return;
            }

            JSONArray items = searchResults.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String resultUrl = item.getString("link");

                    // Add the URLs to the queue as seed URLs
                    queue.put(new UrlDepthPair(resultUrl, 1)); // Start at depth 1
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Error while adding seed URLs to the queue", e);
        }
    }

    private JSONObject fetchGoogleSearchResults(String query) {
        try {
            // URL-encode the query to handle spaces and special characters
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String requestUrl = String.format("%s?q=%s&key=%s&cx=%s", GOOGLE_SEARCH_URL, encodedQuery, API_KEY, SEARCH_ENGINE_ID);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return new JSONObject(response.body());
            } else {
                logger.warning("Failed to fetch search results: " + response.body());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            logger.log(Level.WARNING, "Error fetching Google search results", e);
            return null;
        }
    }

    private double calculateRelevanceScore(String text) {
        int keywordCount = 0;
        int index = text.toLowerCase().indexOf(keyword);

        while (index != -1) {
            keywordCount++;
            index = text.toLowerCase().indexOf(keyword, index + 1);
        }

        return keywordCount * 1.0; // Simple relevance score based on keyword frequency
    }

    private synchronized void saveData(JSONObject result) {
        // Define the output file path
        File outputFile = new File("output/result.json");

        try {
            // Create directories if they do not exist
            outputFile.getParentFile().mkdirs();

            JSONArray resultsArray;

            // If the file exists and is not empty, read the existing JSON array
            if (outputFile.exists() && outputFile.length() > 0) {
                try (Scanner scanner = new Scanner(outputFile)) {
                    StringBuilder jsonContent = new StringBuilder();
                    while (scanner.hasNextLine()) {
                        jsonContent.append(scanner.nextLine());
                    }
                    resultsArray = new JSONArray(jsonContent.toString());
                }
            } else {
                resultsArray = new JSONArray();
            }

            // Add the new result to the JSON array
            resultsArray.put(result);

            // Write the updated JSON array back to the file
            try (FileWriter writer = new FileWriter(outputFile, false)) { // false -> overwrite file
                writer.write(resultsArray.toString(4)); // Pretty print with 4-space indentation
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save data to file", e);
        }
    }


    public record UrlDepthPair(String url, int depth) {
    }
}
