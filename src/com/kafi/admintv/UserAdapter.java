package com.kafi.admintv;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UserAdapter extends BaseAdapter {

    public interface UserActionListener {
        void onEdit(FirestoreHelper.DocResult user);
        void onDelete(FirestoreHelper.DocResult user);
        void onResetDevice(FirestoreHelper.DocResult user);
    }

    private Context context;
    private List<FirestoreHelper.DocResult> users;
    private List<FirestoreHelper.DocResult> filteredUsers;
    private UserActionListener listener;

    public UserAdapter(Context context, UserActionListener listener) {
        this.context = context;
        this.users = new ArrayList<FirestoreHelper.DocResult>();
        this.filteredUsers = new ArrayList<FirestoreHelper.DocResult>();
        this.listener = listener;
    }

    public void setData(List<FirestoreHelper.DocResult> data) {
        this.users = data;
        this.filteredUsers = new ArrayList<FirestoreHelper.DocResult>(data);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        filteredUsers.clear();
        if (query == null || query.isEmpty()) {
            filteredUsers.addAll(users);
        } else {
            String q = query.toLowerCase();
            for (FirestoreHelper.DocResult u : users) {
                if (u.getString("name").toLowerCase().contains(q) || u.getString("email").toLowerCase().contains(q)) {
                    filteredUsers.add(u);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getCount() { return filteredUsers.size(); }
    public Object getItem(int pos) { return filteredUsers.get(pos); }
    public long getItemId(int pos) { return pos; }

    public int getTotalCount() { return users.size(); }

    public int getExpiredCount() {
        int count = 0;
        for (FirestoreHelper.DocResult u : users) {
            String expiry = u.getString("expiry");
            if (!expiry.isEmpty()) {
                try {
                    long expTime = parseDateTime(expiry);
                    if (expTime < System.currentTimeMillis()) count++;
                } catch (Exception e) {}
            }
        }
        return count;
    }

    private long parseDateTime(String dt) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            return sdf.parse(dt).getTime();
        } catch (Exception e) {
            try {
                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                return sdf2.parse(dt).getTime();
            } catch (Exception e2) { return 0; }
        }
    }

    public View getView(int pos, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_user, parent, false);
        }

        final FirestoreHelper.DocResult user = filteredUsers.get(pos);

        TextView name = (TextView) convertView.findViewById(R.id.userName);
        TextView email = (TextView) convertView.findViewById(R.id.userEmail);
        TextView phone = (TextView) convertView.findViewById(R.id.userPhone);
        TextView pin = (TextView) convertView.findViewById(R.id.userPin);
        TextView devices = (TextView) convertView.findViewById(R.id.userDevices);
        TextView start = (TextView) convertView.findViewById(R.id.userStart);
        TextView expiry = (TextView) convertView.findViewById(R.id.userExpiry);
        Button btnReset = (Button) convertView.findViewById(R.id.btnReset);
        Button btnEdit = (Button) convertView.findViewById(R.id.btnEdit);
        Button btnDel = (Button) convertView.findViewById(R.id.btnDel);

        name.setText(user.getString("name"));
        email.setText(user.getString("email"));
        String ph = user.getString("phone");
        phone.setText(ph.isEmpty() ? "" : "\u260E " + ph);
        phone.setVisibility(ph.isEmpty() ? View.GONE : View.VISIBLE);
        pin.setText(user.getString("pin"));

        int deviceCount = user.getArrayLength("deviceIds");
        int limit = user.getInt("device_limit");
        if (limit == 0) limit = 2;
        devices.setText(deviceCount + "/" + limit + " device");

        String startAt = user.getString("start_at");
        String exp = user.getString("expiry");
        start.setText("S: " + (startAt.isEmpty() ? "N/A" : startAt.replace("T", " ")));
        expiry.setText("E: " + (exp.isEmpty() ? "LIFETIME" : exp.replace("T", " ")));

        boolean isExpired = false;
        if (!exp.isEmpty()) {
            try {
                isExpired = parseDateTime(exp) < System.currentTimeMillis();
            } catch (Exception e) {}
        }
        expiry.setTextColor(isExpired ? 0xFFF87171 : 0xFF64748B);

        btnReset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { listener.onResetDevice(user); }
        });
        btnEdit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { listener.onEdit(user); }
        });
        btnDel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { listener.onDelete(user); }
        });

        return convertView;
    }
}
