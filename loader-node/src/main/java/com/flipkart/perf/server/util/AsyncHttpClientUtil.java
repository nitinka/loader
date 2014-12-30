package com.flipkart.perf.server.util;

import com.ning.http.client.AsyncHttpClient;

/**
 * Created with IntelliJ IDEA.
 * User: nitinka
 * Date: 13/4/13
 * Time: 10:10 AM
 * To change this template use File | Settings | File Templates.
 */
public class AsyncHttpClientUtil {
    private static AsyncHttpClient asyncHttpClient;
    static {
        asyncHttpClient = new AsyncHttpClient();
    }

    public static AsyncHttpClient instance() {
        return asyncHttpClient;
    }

    public static void close() {
        if(asyncHttpClient == null) {
            asyncHttpClient.close();
        }
    }
}
