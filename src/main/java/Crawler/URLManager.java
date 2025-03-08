package Crawler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class URLManager {
    private static final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    public static boolean visit(String url) {
        if (visitedUrls.contains(url)) {
            return false;
        } else {
            visitedUrls.add(url);
            return true;
        }
    }


}
