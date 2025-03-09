package Crawler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.logging.*;

public class WebCrawler {
    private static final Logger logger = Logger.getLogger(WebCrawler.class.getName());
    private static final int MONITORING_INTERVAL_MS = 30000;
    private static final int TERMINATION_WAIT_MS = 5000;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        logger.info("Enter keyword to search for: ");
        String keyword = scanner.nextLine().trim();

        logger.info("Enter seed URLs (comma-separated) or press Enter to use Google Search:");
        String seedUrlsInput = scanner.nextLine().trim();

        logger.info("Enter maximum crawl time in minutes [default: 10]: ");
        String crawlTimeInput = scanner.nextLine().trim();
        long crawlTimeMillis = (crawlTimeInput.isEmpty() ? 10 : Integer.parseInt(crawlTimeInput)) * 60 * 1000;

        logger.info("Enter maximum crawl depth [default: 3]: ");
        String maxDepthInput = scanner.nextLine().trim();
        int maxDepth = maxDepthInput.isEmpty() ? 3 : Integer.parseInt(maxDepthInput);

        double minRelevanceScore = 1.0;

        BlockingQueue<WorkerThread.UrlDepthPair> queue = new LinkedBlockingQueue<>();

        if (seedUrlsInput.isEmpty()) {
            logger.info("No seed URLs entered. Using Google Custom Search to find seed URLs...");
            WorkerThread worker = new WorkerThread(queue, keyword, System.currentTimeMillis(), crawlTimeMillis, maxDepth, minRelevanceScore, "output/result.json");
            worker.fetchAndAddSeedUrls(keyword);
        } else {
            String[] seedUrls = seedUrlsInput.split(",");
            for (String url : seedUrls) {
                queue.add(new WorkerThread.UrlDepthPair(url.trim(), 1));
            }
        }

        WorkerThread worker = new WorkerThread(queue, keyword, System.currentTimeMillis(), crawlTimeMillis, maxDepth, minRelevanceScore, "output/result.json");
        new Thread(worker).start();
    }


    private static String promptForKeyword(Scanner scanner) {
        System.out.print("Enter keyword to search for: ");
        return scanner.nextLine().trim().toLowerCase();
    }

    private static long promptForTimeout(Scanner scanner, CrawlerConfig config) {
        while (true) {
            System.out.print("Enter maximum crawl time in minutes [default: " + (config.getTimeoutMillis() / 60000) + "]: ");
            String timeoutInput = scanner.nextLine().trim();
            if (timeoutInput.isEmpty()) {
                return config.getTimeoutMillis();
            }
            try {
                long timeoutMinutes = Long.parseLong(timeoutInput);
                if (timeoutMinutes > 0) {
                    return timeoutMinutes * 60000;
                } else {
                    System.out.println("Timeout must be greater than 0. Please try again.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
    }

    private static int promptForMaxDepth(Scanner scanner, CrawlerConfig config) {
        while (true) {
            System.out.print("Enter maximum crawl depth [default: " + config.getMaxDepth() + "]: ");
            String depthInput = scanner.nextLine().trim();
            if (depthInput.isEmpty()) {
                return config.getMaxDepth();
            }
            try {
                int maxDepth = Integer.parseInt(depthInput);
                if (maxDepth > 0) {
                    return maxDepth;
                } else {
                    System.out.println("Depth must be greater than 0. Please try again.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
    }

    private static int initializeQueueWithSeedUrls(BlockingQueue<WorkerThread.UrlDepthPair> queue, CrawlerConfig config) throws InterruptedException {
        int seedCount = 0;
        for (String url : config.getSeedUrls()) {
            if (URLManager.shouldProcess(url)) {
                queue.put(new WorkerThread.UrlDepthPair(url, 0));
                seedCount++;
            }
        }
        return seedCount;
    }

    private static void createOutputDirectory(String outputFile) throws IOException {
        Files.createDirectories(Paths.get(outputFile).getParent());
    }

    private static void logCrawlerConfiguration(String keyword, CrawlerConfig config) {
        logger.info("Starting crawler with keyword: " + keyword);
        logger.info("Max threads: " + config.getMaxThreads());
        logger.info("Max depth: " + config.getMaxDepth());
        logger.info("Timeout: " + (config.getTimeoutMillis() / 60000) + " minutes");
        logger.info("Output file: " + config.getOutputFile());
    }

    private static void startWorkerThreads(ExecutorService executor, BlockingQueue<WorkerThread.UrlDepthPair> queue, String keyword, long startTime, long timeoutMillis, int maxDepth, double minRelevanceScore, String outputFile) {
        for (int i = 0; i < CrawlerConfig.getInstance().getMaxThreads(); i++) {
            executor.execute(new WorkerThread(
                    queue,
                    keyword,
                    startTime,
                    timeoutMillis,
                    maxDepth,
                    minRelevanceScore,
                    outputFile
            ));
        }
    }

    private static void startMonitoringThread(ExecutorService executor, long startTime, long timeoutMillis) {
        new Thread(() -> {
            try {
                while (!executor.isTerminated() &&
                        System.currentTimeMillis() - startTime < timeoutMillis) {
                    Thread.sleep(MONITORING_INTERVAL_MS);

                    long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                    int visitedCount = URLManager.getVisitedCount();

                    System.out.printf("Status: %d URLs processed in %d seconds (%.2f URLs/sec)%n",
                            visitedCount, elapsedSeconds,
                            elapsedSeconds > 0 ? (double) visitedCount / elapsedSeconds : 0);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Monitoring thread interrupted.");
            }
        }).start();
    }

    private static void logCrawlerCompletion(boolean completed, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Crawling " + (completed ? "completed" : "timed out") + " after " +
                (duration / 1000) + " seconds.");
        logger.info("URLs visited: " + URLManager.getVisitedCount());
    }

    private static void setupLogging() {
        try {
            FileHandler fileHandler = new FileHandler("crawler.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger("").addHandler(fileHandler);

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger("").addHandler(consoleHandler);

            Logger.getLogger("").setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Could not set up logger: " + e.getMessage());
        }
    }
}
