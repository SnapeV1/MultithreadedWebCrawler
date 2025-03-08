package Crawler;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

public class WebCrawler {
    private static final int MAX_THREADS = 5;
    private static final long TIMEOUT_MILLIS = 10 * 60 * 1000; // Timeout after 10 minutes

    public static void main(String[] args) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter keyword to search for: ");
        String keyword = scanner.nextLine().trim().toLowerCase();
        scanner.close();

        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);

        // Define starting URLs (Modify this list!)
        List<String> seedUrls = List.of(
                "https://www.bbc.com/news",
                "https://www.cnn.com/world",
                "https://www.aljazeera.com",
                "https://www.reuters.com"
        );

        // Add them to the queue
        for (String url : seedUrls) {
            queue.put(url);
        }

        long startTime = System.currentTimeMillis();
        // Start worker threads with the keyword
        for (int i = 0; i < MAX_THREADS; i++) {
            executor.execute(new WorkerThread(queue, keyword, startTime, TIMEOUT_MILLIS));
        }

        // Wait for the executor service to finish all tasks (workers stop when the time runs out)
        executor.shutdown();
        executor.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        System.out.println("Crawling completed.");
    }
}
