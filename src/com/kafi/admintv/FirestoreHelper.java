package com.kafi.admintv;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FirestoreHelper {

    private static final String PROJECT_ID = "notebyak";
    private static final String API_KEY = "AIzaSyD59XK0cdjRZ6XQC1LSqnrw8EQxWa_mnGE";
    private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents/";

    private static final ExecutorService executor = Executors.newFixedThreadPool(6);
    private static final ExecutorService urlCheckExecutor = Executors.newFixedThreadPool(10);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject result);
        void onError(String error);
    }

    public interface ListCallback {
        void onSuccess(List<DocResult> docs);
        void onError(String error);
    }

    public static class DocResult {
        public String id;
        public JSONObject fields;
        public DocResult(String id, JSONObject fields) {
            this.id = id;
            this.fields = fields;
        }
        public String getString(String key) {
            try {
                JSONObject val = fields.optJSONObject(key);
                if (val == null) return "";
                if (val.has("stringValue")) return val.getString("stringValue");
                if (val.has("integerValue")) return val.getString("integerValue");
                return "";
            } catch (Exception e) { return ""; }
        }
        public int getInt(String key) {
            try {
                JSONObject val = fields.optJSONObject(key);
                if (val == null) return 0;
                if (val.has("integerValue")) return Integer.parseInt(val.getString("integerValue"));
                if (val.has("stringValue")) {
                    try { return Integer.parseInt(val.getString("stringValue")); } catch (Exception e2) { return 0; }
                }
                return 0;
            } catch (Exception e) { return 0; }
        }
        public long getLong(String key) {
            try {
                JSONObject val = fields.optJSONObject(key);
                if (val == null) return 0;
                if (val.has("integerValue")) return Long.parseLong(val.getString("integerValue"));
                return 0;
            } catch (Exception e) { return 0; }
        }
        public JSONArray getArray(String key) {
            try {
                JSONObject val = fields.optJSONObject(key);
                if (val == null) return new JSONArray();
                if (val.has("arrayValue")) {
                    JSONObject arrObj = val.getJSONObject("arrayValue");
                    if (arrObj.has("values")) return arrObj.getJSONArray("values");
                }
                return new JSONArray();
            } catch (Exception e) { return new JSONArray(); }
        }
        public int getArrayLength(String key) {
            return getArray(key).length();
        }
    }

    public static void checkStreamUrl(final String streamUrl, final Callback callback) {
        urlCheckExecutor.execute(new Runnable() {
            public void run() {
                try {
                    URL url = new URL(streamUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("HEAD");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    int code = conn.getResponseCode();
                    conn.disconnect();
                    final JSONObject result = new JSONObject();
                    result.put("code", code);
                    result.put("live", code >= 200 && code < 400);
                    mainHandler.post(new Runnable() { public void run() { callback.onSuccess(result); } });
                } catch (final Exception e) {
                    try {
                        URL url2 = new URL(streamUrl);
                        HttpURLConnection conn2 = (HttpURLConnection) url2.openConnection();
                        conn2.setRequestMethod("GET");
                        conn2.setConnectTimeout(8000);
                        conn2.setReadTimeout(8000);
                        conn2.setInstanceFollowRedirects(true);
                        conn2.setRequestProperty("User-Agent", "Mozilla/5.0");
                        int code2 = conn2.getResponseCode();
                        conn2.disconnect();
                        final JSONObject result2 = new JSONObject();
                        result2.put("code", code2);
                        result2.put("live", code2 >= 200 && code2 < 400);
                        mainHandler.post(new Runnable() { public void run() { callback.onSuccess(result2); } });
                    } catch (final Exception e2) {
                        mainHandler.post(new Runnable() { public void run() { callback.onError(e2.getMessage()); } });
                    }
                }
            }
        });
    }

    public static void getCollection(final String collection, final ListCallback callback) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    String url = BASE_URL + collection + "?key=" + API_KEY;
                    List<DocResult> allDocs = new ArrayList<DocResult>();
                    String pageToken = null;

                    do {
                        String pageUrl = url;
                        if (pageToken != null) pageUrl += "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8");
                        pageUrl += "&pageSize=300";

                        String response = httpGet(pageUrl);
                        JSONObject json = new JSONObject(response);
                        JSONArray docs = json.optJSONArray("documents");
                        if (docs != null) {
                            for (int i = 0; i < docs.length(); i++) {
                                JSONObject doc = docs.getJSONObject(i);
                                String name = doc.getString("name");
                                String id = name.substring(name.lastIndexOf('/') + 1);
                                JSONObject fields = doc.optJSONObject("fields");
                                if (fields == null) fields = new JSONObject();
                                allDocs.add(new DocResult(id, fields));
                            }
                        }
                        pageToken = json.optString("nextPageToken", null);
                        if ("".equals(pageToken)) pageToken = null;
                    } while (pageToken != null);

                    final List<DocResult> result = allDocs;
                    mainHandler.post(new Runnable() { public void run() { callback.onSuccess(result); } });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() { public void run() { callback.onError(e.getMessage()); } });
                }
            }
        });
    }

    public static void getDocument(final String collection, final String docId, final Callback callback) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    String url = BASE_URL + collection + "/" + docId + "?key=" + API_KEY;
                    String response = httpGet(url);
                    final JSONObject json = new JSONObject(response);
                    mainHandler.post(new Runnable() { public void run() { callback.onSuccess(json); } });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() { public void run() { callback.onError(e.getMessage()); } });
                }
            }
        });
    }

    public static void queryCollection(final String collection, final String field, final String value, final ListCallback callback) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    String url = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents:runQuery?key=" + API_KEY;
                    JSONObject query = new JSONObject();
                    JSONObject structuredQuery = new JSONObject();
                    JSONObject from = new JSONObject();
                    from.put("collectionId", collection);
                    structuredQuery.put("from", new JSONArray().put(from));

                    JSONObject where = new JSONObject();
                    JSONObject fieldFilter = new JSONObject();
                    JSONObject fieldRef = new JSONObject();
                    fieldRef.put("fieldPath", field);
                    fieldFilter.put("field", fieldRef);
                    fieldFilter.put("op", "EQUAL");
                    JSONObject val = new JSONObject();
                    val.put("stringValue", value);
                    fieldFilter.put("value", val);
                    where.put("fieldFilter", fieldFilter);
                    structuredQuery.put("where", where);

                    query.put("structuredQuery", structuredQuery);

                    String response = httpPost(url, query.toString());
                    JSONArray results = new JSONArray(response);
                    List<DocResult> docs = new ArrayList<DocResult>();
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject item = results.getJSONObject(i);
                        if (item.has("document")) {
                            JSONObject doc = item.getJSONObject("document");
                            String name = doc.getString("name");
                            String id = name.substring(name.lastIndexOf('/') + 1);
                            JSONObject fields = doc.optJSONObject("fields");
                            if (fields == null) fields = new JSONObject();
                            docs.add(new DocResult(id, fields));
                        }
                    }
                    final List<DocResult> result = docs;
                    mainHandler.post(new Runnable() { public void run() { callback.onSuccess(result); } });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() { public void run() { callback.onError(e.getMessage()); } });
                }
            }
        });
    }

    public static void createDocument(final String collection, final JSONObject data, final Callback callback) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    String url = BASE_URL + collection + "?key=" + API_KEY;
                    JSONObject body = new JSONObject();
                    body.put("fields", toFirestoreFields(data));
                    String response = httpPost(url, body.toString());
                    final JSONObject json = new JSONObject(response);
                    mainHandler.post(new Runnable() { public void run() { callback.onSuccess(json); } });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() { public void run() { callback.onError(e.getMessage()); } });
                }
            }
        });
    }

    public static void updateDocument(final String collection, final String docId, final JSONObject data, final Callback callback) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    JSONObject fields = toFirestoreFields(data);
                    StringBuilder updateMask = new StringBuilder();
                    Iterator<String> keys = data.keys();
                    boolean first = true;
                    while (keys.hasNext()) {
                        if (!first) updateMask.append("&");
                        updateMask.append("updateMask.fieldPaths=").append(URLEncoder.encode(keys.next(), "UTF-8"));
                        first = false;
                    }
                    String url = BASE_URL + collection + "/" + docId + "?" + updateMask.toString() + "&key=" + API_KEY;
                    JSONObject body = new JSONObject();
                    body.put("fields", fields);
                    String response = httpPatch(url, body.toString());
                    final JSONObject json = new JSONObject(response);
                    mainHandler.post(new Runnable() { public void run() { callback.onSuccess(json); } });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() { public void run() { callback.onError(e.getMessage()); } });
                }
            }
        });
    }

    public static void setDocument(final String collection, final String docId, final JSONObject data, final Callback callback) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    String url = BASE_URL + collection + "/" + docId + "?key=" + API_KEY;
                    JSONObject body = new JSONObject();
                    body.put("fields", toFirestoreFields(data));
                    String response = httpPatch(url, body.toString());
                    final JSONObject json = new JSONObject(response);
                    mainHandler.post(new Runnable() { public void run() { callback.onSuccess(json); } });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() { public void run() { callback.onError(e.getMessage()); } });
                }
            }
        });
    }

    public static void deleteDocument(final String collection, final String docId, final Callback callback) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    String url = BASE_URL + collection + "/" + docId + "?key=" + API_KEY;
                    httpDelete(url);
                    mainHandler.post(new Runnable() { public void run() { callback.onSuccess(new JSONObject()); } });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() { public void run() { callback.onError(e.getMessage()); } });
                }
            }
        });
    }

    public static void batchCreate(final String collection, final List<JSONObject> items, final Callback callback) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    String commitUrl = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents:commit?key=" + API_KEY;
                    int total = items.size();
                    for (int i = 0; i < total; i += 500) {
                        JSONObject commitBody = new JSONObject();
                        JSONArray writes = new JSONArray();
                        int end = Math.min(i + 500, total);
                        for (int j = i; j < end; j++) {
                            JSONObject write = new JSONObject();
                            JSONObject update = new JSONObject();
                            String docPath = "projects/" + PROJECT_ID + "/databases/(default)/documents/" + collection + "/" + generateId();
                            update.put("name", docPath);
                            update.put("fields", toFirestoreFields(items.get(j)));
                            write.put("update", update);
                            writes.put(write);
                        }
                        commitBody.put("writes", writes);
                        httpPost(commitUrl, commitBody.toString());
                    }
                    mainHandler.post(new Runnable() { public void run() { callback.onSuccess(new JSONObject()); } });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() { public void run() { callback.onError(e.getMessage()); } });
                }
            }
        });
    }

    public static void batchDelete(final String collection, final List<String> docIds, final Callback callback) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    String commitUrl = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents:commit?key=" + API_KEY;
                    int total = docIds.size();
                    for (int i = 0; i < total; i += 500) {
                        JSONObject commitBody = new JSONObject();
                        JSONArray writes = new JSONArray();
                        int end = Math.min(i + 500, total);
                        for (int j = i; j < end; j++) {
                            JSONObject write = new JSONObject();
                            String docPath = "projects/" + PROJECT_ID + "/databases/(default)/documents/" + collection + "/" + docIds.get(j);
                            write.put("delete", docPath);
                            writes.put(write);
                        }
                        commitBody.put("writes", writes);
                        httpPost(commitUrl, commitBody.toString());
                    }
                    mainHandler.post(new Runnable() { public void run() { callback.onSuccess(new JSONObject()); } });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() { public void run() { callback.onError(e.getMessage()); } });
                }
            }
        });
    }

    public static void fetchUrl(final String urlStr, final Callback callback) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    String response = httpGet(urlStr);
                    final JSONObject result = new JSONObject();
                    result.put("body", response);
                    mainHandler.post(new Runnable() { public void run() { callback.onSuccess(result); } });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() { public void run() { callback.onError(e.getMessage()); } });
                }
            }
        });
    }

    private static JSONObject toFirestoreFields(JSONObject data) throws Exception {
        JSONObject fields = new JSONObject();
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = data.get(key);
            JSONObject fieldVal = new JSONObject();
            if (val instanceof String) {
                fieldVal.put("stringValue", val);
            } else if (val instanceof Integer) {
                fieldVal.put("integerValue", val);
            } else if (val instanceof Long) {
                fieldVal.put("integerValue", val);
            } else if (val instanceof Double || val instanceof Float) {
                fieldVal.put("doubleValue", val);
            } else if (val instanceof Boolean) {
                fieldVal.put("booleanValue", val);
            } else if (val instanceof JSONArray) {
                JSONObject arrVal = new JSONObject();
                JSONArray values = new JSONArray();
                JSONArray arr = (JSONArray) val;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject sv = new JSONObject();
                    sv.put("stringValue", arr.getString(i));
                    values.put(sv);
                }
                arrVal.put("values", values);
                fieldVal.put("arrayValue", arrVal);
            } else {
                fieldVal.put("stringValue", String.valueOf(val));
            }
            fields.put(key, fieldVal);
        }
        return fields;
    }

    private static String generateId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(chars.charAt((int)(Math.random() * chars.length())));
        }
        return sb.toString();
    }

    private static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        return readResponse(conn);
    }

    private static String httpPost(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();
        return readResponse(conn);
    }

    private static String httpPatch(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PATCH");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();
        return readResponse(conn);
    }

    private static void httpDelete(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.getResponseCode();
        conn.disconnect();
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();
        if (code >= 400) {
            throw new Exception("HTTP " + code + ": " + sb.toString());
        }
        return sb.toString();
    }
}
