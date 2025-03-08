package Crawler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class WebCrawler {
    private static final Logger logger = Logger.getLogger(WebCrawler.class.getName());

    public static void main(String[] args) {
        try {
            setupLogging();

            CrawlerConfig config = CrawlerConfig.getInstance();
            if (args.length > 0) {
                config.loadFromFile(args[0]);
            } else {
                config.loadDefaults();
            }

            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter keyword to search for: ");
            String keyword = scanner.nextLine().trim().toLowerCase();

            System.out.print("Enter maximum crawl time in minutes [default: " + (config.getTimeoutMillis() / 60000) + "]: ");
            String timeoutInput = scanner.nextLine().trim();
            if (!timeoutInput.isEmpty()) {
                try {
                    long timeoutMinutes = Long.parseLong(timeoutInput);
                    config.setTimeoutMillis(timeoutMinutes * 60000);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input, using default timeout.");
                }
            }

            System.out.print("Enter maximum crawl depth [default: " + config.getMaxDepth() + "]: ");
            String depthInput = scanner.nextLine().trim();
            if (!depthInput.isEmpty()) {
                try {
                    int maxDepth = Integer.parseInt(depthInput);
                    config.setMaxDepth(maxDepth);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input, using default depth.");
                }
            }

            scanner.close();

            BlockingQueue<WorkerThread.UrlDepthPair> queue = new PriorityBlockingQueue<>(100,
                    (p1, p2) -> Integer.compare(p1.depth(), p2.depth()));

            ExecutorService executor = Executors.newFixedThreadPool(config.getMaxThreads());

            long startTime = System.currentTimeMillis();

            int seedCount = 0;
            for (String url : config.getSeedUrls()) {
                if (URLManager.shouldProcess(url)) {
                    queue.put(new WorkerThread.UrlDepthPair(url, 0));
                    seedCount++;
                }
            }

            if (seedCount == 0) {
                logger.severe("No valid seed URLs. Exiting.");
                executor.shutdown();
                return;
            }

            String outputFile = config.getOutputFile();
            Files.createDirectories(Paths.get(outputFile).getParent());

            logger.info("Starting crawler with keyword: " + keyword);
            logger.info("Max threads: " + config.getMaxThreads());
            logger.info("Max depth: " + config.getMaxDepth());
            logger.info("Timeout: " + (config.getTimeoutMillis() / 60000) + " minutes");
            logger.info("Output file: " + outputFile);

            for (int i = 0; i < config.getMaxThreads(); i++) {
                executor.execute(new WorkerThread(
                        queue,
                        keyword,
                        startTime,
                        config.getTimeoutMillis(),
                        config.getMaxDepth(),
                        config.getMinRelevanceScore(),
                        outputFile
                ));
            }

            startMonitoringThread(executor, startTime, config.getTimeoutMillis());

            executor.shutdown();
            boolean completed = executor.awaitTermination(config.getTimeoutMillis() + 5000, TimeUnit.MILLISECONDS);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Crawling " + (completed ? "completed" : "timed out") + " after " +
                    (duration / 1000) + " seconds.");
            logger.info("URLs visited: " + URLManager.getVisitedCount());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Crawler failed with error", e);
        }
    }

    private static void setupLogging() {
        try {
            java.util.logging.FileHandler fileHandler = new java.util.logging.FileHandler("crawler.log", true);
            fileHandler.setFormatter(new java.util.logging.SimpleFormatter());
            Logger.getLogger("").addHandler(fileHandler);

            java.util.logging.ConsoleHandler consoleHandler = new java.util.logging.ConsoleHandler();
            consoleHandler.setFormatter(new java.util.logging.SimpleFormatter());
            Logger.getLogger("").addHandler(consoleHandler);

            Logger.getLogger("").setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Could not set up logger: " + e.getMessage());
        }
    }

    private static void startMonitoringThread(ExecutorService executor, long startTime, long timeoutMillis) {
        new Thread(() -> {
            try {
                while (!executor.isTerminated() &&
                        System.currentTimeMillis() - startTime < timeoutMillis) {
                    Thread.sleep(30000);

                    long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                    int visitedCount = URLManager.getVisitedCount();

                    System.out.printf("Status: %d URLs processed in %d seconds (%.2f URLs/sec)%n",
                            visitedCount, elapsedSeconds,
                            elapsedSeconds > 0 ? (double)visitedCount / elapsedSeconds : 0);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}