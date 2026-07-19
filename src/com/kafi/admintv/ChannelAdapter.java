package com.kafi.admintv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChannelAdapter extends BaseAdapter {

    public interface ChannelActionListener {
        void onChannelClick(FirestoreHelper.DocResult channel);
    }

    private Context context;
    private List<FirestoreHelper.DocResult> channels;
    private List<FirestoreHelper.DocResult> filteredChannels;
    private ChannelActionListener listener;
    private static LruCache<String, Bitmap> imageCache;
    static {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        imageCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }
    private static ExecutorService imgExecutor = Executors.newFixedThreadPool(3);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public ChannelAdapter(Context context, ChannelActionListener listener) {
        this.context = context;
        this.channels = new ArrayList<FirestoreHelper.DocResult>();
        this.filteredChannels = new ArrayList<FirestoreHelper.DocResult>();
        this.listener = listener;
    }

    public void setData(List<FirestoreHelper.DocResult> data) {
        this.channels = data;
        this.filteredChannels = new ArrayList<FirestoreHelper.DocResult>(data);
        notifyDataSetChanged();
    }

    public List<FirestoreHelper.DocResult> getAllChannels() { return channels; }

    public void filter(String query) {
        filteredChannels.clear();
        if (query == null || query.isEmpty()) {
            filteredChannels.addAll(channels);
        } else {
            String q = query.toLowerCase();
            for (FirestoreHelper.DocResult c : channels) {
                if (c.getString("name").toLowerCase().contains(q) ||
                    c.getString("category").toLowerCase().contains(q)) {
                    filteredChannels.add(c);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getCount() { return filteredChannels.size(); }
    public Object getItem(int pos) { return filteredChannels.get(pos); }
    public long getItemId(int pos) { return pos; }

    public View getView(int pos, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false);
        }

        final FirestoreHelper.DocResult channel = filteredChannels.get(pos);

        final ImageView logo = (ImageView) convertView.findViewById(R.id.chLogo);
        TextView name = (TextView) convertView.findViewById(R.id.chName);
        TextView statusDot = (TextView) convertView.findViewById(R.id.chStatus);

        name.setText(channel.getString("name"));

        String status = channel.getString("status");
        if (status.isEmpty()) status = "live";
        if (statusDot != null) {
            if ("live".equalsIgnoreCase(status)) {
                statusDot.setText("\u25CF");
                statusDot.setTextColor(0xFF4ADE80);
            } else {
                statusDot.setText("\u25CF");
                statusDot.setTextColor(0xFFF87171);
            }
        }

        String drmUrl = channel.getString("drm_license_url");
        TextView drmBadge = (TextView) convertView.findViewById(R.id.chDrmBadge);
        if (drmBadge != null) {
            drmBadge.setVisibility(!drmUrl.isEmpty() ? View.VISIBLE : View.GONE);
        }

        final String logoUrl = channel.getString("logo");
        logo.setImageBitmap(null);
        logo.setTag(logoUrl);

        if (!logoUrl.isEmpty()) {
            Bitmap cached = imageCache.get(logoUrl);
            if (cached != null) {
                logo.setImageBitmap(cached);
            } else {
                imgExecutor.execute(new Runnable() {
                    public void run() {
                        try {
                            URL url = new URL(logoUrl);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setConnectTimeout(5000);
                            conn.setReadTimeout(5000);
                            InputStream is = conn.getInputStream();
                            final Bitmap bmp = BitmapFactory.decodeStream(is);
                            is.close();
                            conn.disconnect();
                            if (bmp != null) {
                                imageCache.put(logoUrl, bmp);
                                mainHandler.post(new Runnable() {
                                    public void run() {
                                        if (logoUrl.equals(logo.getTag())) {
                                            logo.setImageBitmap(bmp);
                                        }
                                    }
                                });
                            }
                        } catch (Exception e) {}
                    }
                });
            }
        }

        convertView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { listener.onChannelClick(channel); }
        });

        return convertView;
    }
}
