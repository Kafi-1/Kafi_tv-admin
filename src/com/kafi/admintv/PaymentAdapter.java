package com.kafi.admintv;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PaymentAdapter extends BaseAdapter {

    public interface PaymentActionListener {
        void onApprove(FirestoreHelper.DocResult payment);
        void onReject(FirestoreHelper.DocResult payment);
    }

    private Context context;
    private List<FirestoreHelper.DocResult> allPayments;
    private List<FirestoreHelper.DocResult> filteredPayments;
    private PaymentActionListener listener;
    private String currentFilter = "all";

    public PaymentAdapter(Context context, PaymentActionListener listener) {
        this.context = context;
        this.allPayments = new ArrayList<FirestoreHelper.DocResult>();
        this.filteredPayments = new ArrayList<FirestoreHelper.DocResult>();
        this.listener = listener;
    }

    public void setData(List<FirestoreHelper.DocResult> data) {
        Collections.sort(data, new Comparator<FirestoreHelper.DocResult>() {
            public int compare(FirestoreHelper.DocResult a, FirestoreHelper.DocResult b) {
                int pa = getPriority(a.getString("status"));
                int pb = getPriority(b.getString("status"));
                if (pa != pb) return pa - pb;
                return b.getString("timestamp").compareTo(a.getString("timestamp"));
            }
        });
        this.allPayments = data;
        applyFilter();
    }

    private int getPriority(String status) {
        if ("Pending".equals(status)) return 0;
        if ("Approved".equals(status)) return 1;
        return 2;
    }

    public void filterByStatus(String status) {
        this.currentFilter = status;
        applyFilter();
    }

    private void applyFilter() {
        filteredPayments.clear();
        if ("all".equals(currentFilter)) {
            filteredPayments.addAll(allPayments);
        } else {
            for (FirestoreHelper.DocResult p : allPayments) {
                if (currentFilter.equalsIgnoreCase(p.getString("status"))) {
                    filteredPayments.add(p);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getPendingCount() {
        int count = 0;
        for (FirestoreHelper.DocResult p : allPayments) {
            if ("Pending".equals(p.getString("status"))) count++;
        }
        return count;
    }

    public int getApprovedCount() {
        int count = 0;
        for (FirestoreHelper.DocResult p : allPayments) {
            if ("Approved".equals(p.getString("status"))) count++;
        }
        return count;
    }

    public int getCount() { return filteredPayments.size(); }
    public Object getItem(int pos) { return filteredPayments.get(pos); }
    public long getItemId(int pos) { return pos; }

    public View getView(int pos, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_payment, parent, false);
        }

        final FirestoreHelper.DocResult payment = filteredPayments.get(pos);

        TextView userName = (TextView) convertView.findViewById(R.id.payUserName);
        TextView number = (TextView) convertView.findViewById(R.id.payNumber);
        TextView trxId = (TextView) convertView.findViewById(R.id.payTrxId);
        TextView time = (TextView) convertView.findViewById(R.id.payTime);
        TextView status = (TextView) convertView.findViewById(R.id.payStatus);
        LinearLayout actions = (LinearLayout) convertView.findViewById(R.id.payActions);
        Button btnApprove = (Button) convertView.findViewById(R.id.btnApprove);
        Button btnReject = (Button) convertView.findViewById(R.id.btnReject);

        userName.setText(payment.getString("userName"));
        number.setText("\u260E " + payment.getString("number"));
        trxId.setText("TrxID: " + payment.getString("trxId"));

        String timestamp = payment.getString("timestamp");
        if (!timestamp.isEmpty()) {
            try {
                long ts = Long.parseLong(timestamp);
                time.setText(new SimpleDateFormat("dd/MM/yy HH:mm", Locale.US).format(new Date(ts)));
            } catch (Exception e) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                    Date d = sdf.parse(timestamp);
                    time.setText(new SimpleDateFormat("dd/MM/yy HH:mm", Locale.US).format(d));
                } catch (Exception e2) {
                    try {
                        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
                        Date d2 = sdf2.parse(timestamp);
                        time.setText(new SimpleDateFormat("dd/MM/yy HH:mm", Locale.US).format(d2));
                    } catch (Exception e3) {
                        time.setText(timestamp);
                    }
                }
            }
        } else {
            time.setText("");
        }

        String st = payment.getString("status");
        status.setText(st.toUpperCase());
        if ("Approved".equals(st)) {
            status.setTextColor(0xFF4ADE80);
            actions.setVisibility(View.GONE);
        } else if ("Pending".equals(st)) {
            status.setTextColor(0xFFFBBF24);
            actions.setVisibility(View.VISIBLE);
        } else {
            status.setTextColor(0xFFF87171);
            actions.setVisibility(View.GONE);
        }

        btnApprove.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { listener.onApprove(payment); }
        });
        btnReject.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { listener.onReject(payment); }
        });

        return convertView;
    }
}
