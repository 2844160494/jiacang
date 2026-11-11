package dev.local.globalclip;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 把新内容推给 DSH 的「剪贴板整理台」插件。
 *
 * 只连本机 127.0.0.1:3080；DSH 没在运行时连接失败，条目留在本地等下一轮重试。
 * 同步可以在设置里随时关掉，关闭后不建立任何连接。
 */
public final class HubSync {
    public static final String BASE = "http://127.0.0.1:3080";
    private static final String ITEMS_URL = BASE + "/plugins/clipboard-hub/items";
    private static final String STATS_URL = BASE + "/plugins/clipboard-hub/stats";

    private HubSync() {
    }

    public static boolean post(String text, String source) throws Exception {
        HttpURLConnection connection = open(ITEMS_URL, "POST");
        try {
            JSONObject payload = new JSONObject();
            payload.put("text", text);
            payload.put("source", "manual".equals(source) ? "manual" : "copy");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    while (reader.readLine() != null) {
                        // discard
                    }
                }
            }
            return code == 200;
        } finally {
            connection.disconnect();
        }
    }

    public static boolean check() throws Exception {
        HttpURLConnection connection = open(STATS_URL, "GET");
        try {
            return connection.getResponseCode() == 200;
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(1500);
        connection.setReadTimeout(1500);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Cookie", "native=1");
        return connection;
    }
}
