package Crawler;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class WorkerThread implements Runnable {
    private final BlockingQueue<String> queue;
    private final String keyword;
    private final long startTime;
    private final long timeoutMillis;
    private static final String OUTPUT_FILE = "output.json"; // Declare the OUTPUT_FILE constant

    public WorkerThread(BlockingQueue<String> queue, String keyword, long startTime, long timeoutMillis) {
        this.queue = queue;
        this.keyword = keyword.toLowerCase();
        this.startTime = startTime;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public void run() {
        try {
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                String url = queue.take(); // Get a URL from the queue
                if (URLManager.visit(url)) { // Check if already visited
                    crawl(url);
                }
            }
            System.out.println("Timeout reached, stopping crawling...");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void crawl(String url) {
        try {
            System.out.println("Crawling: " + url);
            Document doc = Jsoup.connect(url).timeout(5000).get(); // Correct Jsoup document usage

            JSONArray resultsArray = new JSONArray(); // Stores extracted data

            // Attempt to extract the publication date from <time> or other common places
            String publicationDate = extractPublicationDate(doc);

            // Extract paragraphs containing the keyword
            for (Element paragraph : doc.select("p")) {
                String text = paragraph.text().trim();
                if (!text.isEmpty() && text.toLowerCase().contains(keyword)) { // Ensure it's not empty
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("url", url);
                    jsonObject.put("content", text);
                    jsonObject.put("date", publicationDate); // Include publication date
                    resultsArray.put(jsonObject); // Add to JSON array
                }
            }

            // Save only if relevant content exists
            if (resultsArray.length() > 0) {
                saveData(resultsArray);
            }

            // Extract new links and add them to the queue
            for (Element link : doc.select("a[href]")) {
                String nextUrl = link.absUrl("href");
                if (!nextUrl.isEmpty()) {
                    queue.put(nextUrl);
                }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to crawl: " + url);
        }
    }

    private String extractPublicationDate(Document doc) {
        // Try to extract publication date from common HTML tags
        Element timeElement = doc.selectFirst("time");
        if (timeElement != null) {
            return timeElement.attr("datetime"); // Some websites use 'datetime' attribute
        }
        // You can add more date extraction logic here for different sites, e.g.:
        // <meta property="article:published_time" content="2023-03-05T14:30:00Z">
        Element metaDate = doc.selectFirst("meta[property=article:published_time]");
        if (metaDate != null) {
            return metaDate.attr("content");
        }
        return "Unknown Date"; // Default if no date found
    }

    private synchronized void saveData(JSONArray jsonArray) {
        try {
            List<String> existingData = Files.exists(Paths.get(OUTPUT_FILE)) ? Files.readAllLines(Paths.get(OUTPUT_FILE)) : List.of();

            // Convert existing JSON file to an array (if it exists)
            JSONArray finalArray = existingData.isEmpty() ? new JSONArray() : new JSONArray(String.join("", existingData));

            // Append new data
            for (int i = 0; i < jsonArray.length(); i++) {
                finalArray.put(jsonArray.getJSONObject(i));
            }

            // Save updated JSON array to file
            Files.write(Paths.get(OUTPUT_FILE), finalArray.toString(4).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to save data.");
        }
    }
}
