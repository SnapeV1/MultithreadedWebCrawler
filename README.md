# Web Crawler 

This is a Java-based web crawler designed to crawl websites, extract relevant content, and filter results to include only English text. The crawler uses the **Jsoup** library for HTML parsing and the **langdetect** library for language detection.

---

## Features

- **Keyword-Based Crawling**: Crawls websites to find pages containing a specific keyword.
- **Language Filtering**: Detects and filters non-English content using the `com.cybozu.labs.langdetect` library.
- **Depth-Limited Crawling**: Limits the crawling depth to avoid excessive resource usage.
- **Politeness Delay**: Respects politeness delays between requests to avoid overloading servers.
- **JSON Output**: Saves the results in a structured JSON file (`output/results.json`).
- **Configurable**: Allows customization of crawling parameters (e.g., timeout, max depth, seed URLs) via a configuration file or command-line input.

---

## Prerequisites

- **Java Development Kit (JDK)**: Version 8 or higher.
- **Maven**: For building and managing dependencies.

---

## Setup

### 1. Clone the Repository

```bash
git clone 
cd web-crawler
mvn clean install
```
This will compile the project and download all required dependencies.


