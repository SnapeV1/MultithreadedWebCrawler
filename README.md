
# Web Crawler Project

## Overview

This project implements a web crawler that crawls websites based on a given keyword. It uses Google Custom Search to find seed URLs, processes those URLs based on user-provided criteria (such as maximum depth, crawl time, and relevance score), and saves relevant results to an output file. The project is designed to be scalable, utilizing multiple worker threads for efficient crawling, and provides logs and real-time monitoring to track progress.

## Features

- **Keyword-based Search**: Crawls based on a user-defined keyword and fetches relevant pages.
- **Multi-threading**: Uses worker threads to handle concurrent crawling and enhance performance.
- **Google Custom Search**: If no seed URLs are provided, it fetches initial URLs using Google Custom Search.
- **Depth-based Crawling**: User-defined maximum depth of crawling for a more controlled search.
- **Crawl Time Limit**: The user can specify a maximum crawl duration in minutes.
- **Relevance Filtering**: Results are filtered based on a minimum relevance score calculated from keyword frequency in page snippets.
- **Real-time Monitoring**: Displays crawl progress in terms of URLs processed and relevance.
- **Customizable Configuration**: Allows setting parameters such as depth, time limit, and relevance score.

## Getting Started

### Prerequisites

- **Java 11+**: This project is built using Java and requires Java 11 or higher to run.
- **Google Custom Search API Key**: You need a valid API key from Google to use the Google Custom Search API.
    - Visit the [Google Custom Search Engine](https://cse.google.com/) to create a search engine and obtain your API key.
- **Internet Connection**: The crawler fetches data from the web using Google Custom Search API.

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/web-crawler.git
   ```

2. Navigate to the project directory:
   ```bash
   cd web-crawler
   ```

3. Build the project (if using Maven or Gradle):
    - **Using Maven**:
      ```bash
      mvn clean install
      ```

    - **Using Gradle**:
      ```bash
      gradle build
      ```

4. **Setup API Key**:
   Create a `.env` file or update your configuration with your Google Custom Search API Key:
   ```bash
   GOOGLE_API_KEY=your_api_key
   GOOGLE_SEARCH_ENGINE_ID=your_search_engine_id
   ```

5. **Run the Project**:
   Run the project using your preferred IDE or directly from the command line:
   ```bash
   java -jar target/web-crawler.jar
   ```

### Configuration

The following configuration options are available to customize the crawler’s behavior:

- **Keyword**: The search term used to crawl relevant pages.
- **Seed URLs**: A comma-separated list of initial URLs to start the crawl.
- **Max Crawl Time**: The maximum time (in minutes) the crawler should run.
- **Max Depth**: The maximum crawl depth (i.e., how deep the crawler will follow links).
- **Min Relevance Score**: A minimum relevance score to filter results. Results with a lower score will be ignored.

### Command-line Arguments (Optional)

Alternatively, you can pass these options as arguments when running the program (overriding interactive input).

Example:
```bash
java -jar target/web-crawler.jar --keyword "Java programming" --max-time 10 --max-depth 3 --relevance 2
```

---

## Usage

1. **Start the Crawler**: The program will prompt you for inputs such as:
    - Keyword to search for.
    - Seed URLs (optional).
    - Maximum crawl time (default: 10 minutes).
    - Maximum crawl depth (default: 3).

2. **Crawl Process**: The crawler will:
    - Use Google Custom Search (if no seed URLs are provided) to find relevant URLs.
    - Crawl each URL and search for the provided keyword in the page snippets.
    - Save the results to a file named `output/result.json` in JSON format.

3. **Monitoring Progress**: The program provides real-time logs showing the progress of the crawl, including the number of URLs processed and the total number of matches found.

4. **Stopping the Crawl**: Press any key during the crawl to stop the process gracefully.

---

## Example Output

The output will be stored in a JSON file (`output/result.json`) and will contain relevant URLs and metadata such as:
```json
{
    "url": "https://example.com",
    "title": "Example Page",
    "content": "This is a sample page content related to Java programming...",
    "relevance_score": 3.5,
    "crawl_depth": 1,
    "crawl_time": 1617221234000
}
```

---

## Logging

The crawler logs its activity in two places:
- **Console**: Real-time progress updates.
- **Log File**: `crawler.log` for detailed logs.

Example log:
```
INFO: Starting crawler with keyword: Java programming
INFO: Max threads: 5
INFO: Max depth: 3
INFO: Timeout: 10 minutes
INFO: Output file: output/result.json
```

---

## Contributing

Feel free to fork the project, make changes, and submit a pull request. All contributions are welcome!

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- This project uses the **Google Custom Search API** to find relevant URLs.
- The objective of this project was to get myself familiar with Threads in a cool and practical way by building a web crawler that efficiently handles multiple tasks concurrently