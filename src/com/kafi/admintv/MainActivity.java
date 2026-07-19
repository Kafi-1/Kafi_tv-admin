package com.kafi.admintv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {

    private View sidebarOverlay;
    private LinearLayout sidebar;
    private TextView sectionTitle;
    private View dashboardPage, usersPage, paymentsPage, categoriesPage, channelsPage;
    private ScrollView channelStatusPage, settingsPage;
    private View loadingView;
    private View[] allPages;
    private Button[] navButtons;
    private String[] pageNames = {"Dashboard", "Payments", "Categories", "Channels", "Users", "Ch Status", "Settings"};

    private TextView statUsers, statPending, statExpired, statChannels, statActive, statApproved;
    private TextView csTotal, csLive, csDown;

    private UserAdapter userAdapter;
    private PaymentAdapter paymentAdapter;
    private ChannelAdapter channelAdapter;
    private CategoryAdapter categoryAdapter;
    private int loadingCount = 0;

    private Handler refreshHandler = new Handler();
    private Runnable refreshRunnable;

    private List<FirestoreHelper.DocResult> allCategories = new ArrayList<FirestoreHelper.DocResult>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 21) {
            Window w = getWindow();
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(Color.parseColor("#f8fafc"));
            w.setNavigationBarColor(Color.parseColor("#f8fafc"));
            if (Build.VERSION.SDK_INT >= 23) {
                w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }

        setContentView(R.layout.activity_main);

        sidebarOverlay = findViewById(R.id.sidebarOverlay);
        sidebar = (LinearLayout) findViewById(R.id.sidebar);
        sectionTitle = (TextView) findViewById(R.id.sectionTitle);

        dashboardPage = findViewById(R.id.dashboardPage);
        paymentsPage = findViewById(R.id.paymentsPage);
        categoriesPage = findViewById(R.id.categoriesPage);
        channelsPage = findViewById(R.id.channelsPage);
        usersPage = findViewById(R.id.usersPage);
        channelStatusPage = (ScrollView) findViewById(R.id.channelStatusPage);
        settingsPage = (ScrollView) findViewById(R.id.settingsPage);
        loadingView = findViewById(R.id.loadingView);

        allPages = new View[]{dashboardPage, paymentsPage, categoriesPage, channelsPage, usersPage, channelStatusPage, settingsPage};

        statUsers = (TextView) findViewById(R.id.statUsers);
        statPending = (TextView) findViewById(R.id.statPending);
        statExpired = (TextView) findViewById(R.id.statExpired);
        statChannels = (TextView) findViewById(R.id.statChannels);
        statActive = (TextView) findViewById(R.id.statActive);
        statApproved = (TextView) findViewById(R.id.statApproved);
        csTotal = (TextView) findViewById(R.id.csTotal);
        csLive = (TextView) findViewById(R.id.csLive);
        csDown = (TextView) findViewById(R.id.csDown);

        setupSidebar();
        setupUserSection();
        setupPaymentSection();
        setupCategorySection();
        setupChannelSection();
        setupChannelStatusSection();
        setupSettingsSection();

        Button refreshBtn = (Button) findViewById(R.id.refreshBtn);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toast("Refreshing...");
                loadAllData();
            }
        });

        loadAllData();
        startAutoRefresh();
    }

    private void setupSidebar() {
        Button menuBtn = (Button) findViewById(R.id.menuBtn);
        Button closeBtn = (Button) findViewById(R.id.closeSidebarBtn);

        View.OnClickListener toggleSidebar = new View.OnClickListener() {
            public void onClick(View v) { toggleSidebar(); }
        };
        menuBtn.setOnClickListener(toggleSidebar);
        closeBtn.setOnClickListener(toggleSidebar);
        sidebarOverlay.setOnClickListener(toggleSidebar);

        Button navDashboard = (Button) findViewById(R.id.navDashboard);
        Button navPayments = (Button) findViewById(R.id.navPayments);
        Button navCategories = (Button) findViewById(R.id.navCategories);
        Button navChannels = (Button) findViewById(R.id.navChannels);
        Button navUsers = (Button) findViewById(R.id.navUsers);
        Button navChStatus = (Button) findViewById(R.id.navChStatus);
        Button navSettings = (Button) findViewById(R.id.navSettings);

        navButtons = new Button[]{navDashboard, navPayments, navCategories, navChannels, navUsers, navChStatus, navSettings};

        for (int i = 0; i < navButtons.length; i++) {
            final int idx = i;
            navButtons[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showSection(idx); }
            });
        }

        Button logoutBtn = (Button) findViewById(R.id.logoutBtn);
        logoutBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences prefs = getSharedPreferences("iptv_admin", MODE_PRIVATE);
                prefs.edit().putBoolean("authenticated", false).apply();
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish();
            }
        });
    }

    private void toggleSidebar() {
        boolean visible = sidebar.getVisibility() == View.VISIBLE;
        sidebar.setVisibility(visible ? View.GONE : View.VISIBLE);
        sidebarOverlay.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private void showSection(int index) {
        for (int i = 0; i < allPages.length; i++) {
            allPages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < navButtons.length; i++) {
            navButtons[i].setBackgroundResource(i == index ? R.drawable.bg_sidebar_active : R.drawable.bg_sidebar_item);
            navButtons[i].setTextColor(i == index ? 0xFFFFFFFF : 0xFF94A3B8);
        }
        sectionTitle.setText(pageNames[index].toUpperCase());
        if (sidebar.getVisibility() == View.VISIBLE) toggleSidebar();
    }

    // =================== USERS ===================

    private void setupUserSection() {
        ListView userList = (ListView) findViewById(R.id.userListView);
        userAdapter = new UserAdapter(this, new UserAdapter.UserActionListener() {
            public void onEdit(FirestoreHelper.DocResult user) { showUserDialog(user); }
            public void onDelete(final FirestoreHelper.DocResult user) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Delete User")
                    .setMessage("Delete " + user.getString("name") + " permanently?")
                    .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            FirestoreHelper.deleteDocument("users", user.id, new FirestoreHelper.Callback() {
                                public void onSuccess(JSONObject r) { loadUsers(); }
                                public void onError(String e) { toast("Error: " + e); }
                            });
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
            }
            public void onResetDevice(final FirestoreHelper.DocResult user) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Reset Device")
                    .setMessage("Reset device binding for " + user.getString("name") + "?")
                    .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            try {
                                JSONObject data = new JSONObject();
                                data.put("deviceIds", new JSONArray());
                                FirestoreHelper.updateDocument("users", user.id, data, new FirestoreHelper.Callback() {
                                    public void onSuccess(JSONObject r) { toast("Device reset done!"); loadUsers(); }
                                    public void onError(String e) { toast("Error: " + e); }
                                });
                            } catch (Exception e) { toast("Error"); }
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
            }
        });
        userList.setAdapter(userAdapter);

        EditText userSearch = (EditText) findViewById(R.id.userSearch);
        userSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { userAdapter.filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

        Button addUserBtn = (Button) findViewById(R.id.addUserBtn);
        addUserBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showUserDialog(null); }
        });
    }

    private void showUserDialog(final FirestoreHelper.DocResult user) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);
        layout.setBackgroundColor(0xFFFFFFFF);

        final EditText nameInput = createDialogInput(layout, "Full Name", user != null ? user.getString("name") : "");
        final EditText phoneInput = createDialogInput(layout, "Phone", user != null ? user.getString("phone") : "");
        final EditText emailInput = createDialogInput(layout, "User ID (Email)", user != null ? user.getString("email") : "");
        final EditText pinInput = createDialogInput(layout, "Password / PIN", user != null ? user.getString("pin") : "");
        final EditText deviceLimitInput = createDialogInput(layout, "Device Limit", user != null ? String.valueOf(user.getInt("device_limit") == 0 ? 2 : user.getInt("device_limit")) : "2");

        // Plan spinner (optional - defaults to "free")
        TextView planLabel = new TextView(this);
        planLabel.setText("Plan (Optional)");
        planLabel.setTextColor(0xFF64748B);
        planLabel.setPadding(4, 24, 0, 4);
        layout.addView(planLabel);

        final Spinner planSpinner = new Spinner(this);
        String[] planOptions = {"free", "premium"};
        ArrayAdapter<String> planAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, planOptions);
        planAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        planSpinner.setAdapter(planAdapter);
        if (user != null && "premium".equals(user.getString("plan"))) {
            planSpinner.setSelection(1);
        } else {
            planSpinner.setSelection(0);
        }
        layout.addView(planSpinner);

        // Start date with picker
        final EditText startInput = createDialogInput(layout, "Start Date (tap to select)", user != null ? user.getString("start_at") : "");
        startInput.setFocusable(false);
        startInput.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showDateTimePicker(startInput); }
        });

        // Expiry date with picker
        final EditText expiryInput = createDialogInput(layout, "Expiry Date (tap to select)", user != null ? user.getString("expiry") : "");
        expiryInput.setFocusable(false);
        expiryInput.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showDateTimePicker(expiryInput); }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(user == null ? "Create User" : "Edit User");
        builder.setView(layout);
        builder.setPositiveButton("Save", null);
        builder.setNegativeButton("Cancel", null);

        final AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String nameVal = nameInput.getText().toString().trim();
                String emailVal = emailInput.getText().toString().trim();
                String pinVal = pinInput.getText().toString().trim();

                if (nameVal.isEmpty()) { toast("Name khali rakha jabe na!"); return; }
                if (emailVal.isEmpty()) { toast("User ID khali rakha jabe na!"); return; }
                if (pinVal.isEmpty()) { toast("Password khali rakha jabe na!"); return; }

                try {
                    JSONObject data = new JSONObject();
                    data.put("name", nameVal);
                    data.put("phone", phoneInput.getText().toString().trim());
                    data.put("email", emailVal);
                    data.put("pin", pinVal);
                    int dl = 2;
                    try { dl = Integer.parseInt(deviceLimitInput.getText().toString()); } catch (Exception e) {}
                    data.put("device_limit", dl);
                    data.put("plan", planSpinner.getSelectedItem().toString());
                    data.put("start_at", startInput.getText().toString());
                    data.put("expiry", expiryInput.getText().toString());
                    data.put("role", "user");

                    FirestoreHelper.Callback cb = new FirestoreHelper.Callback() {
                        public void onSuccess(JSONObject r) { toast("User saved!"); loadUsers(); }
                        public void onError(String e) { toast("Error: " + e); }
                    };

                    if (user != null) {
                        FirestoreHelper.updateDocument("users", user.id, data, cb);
                    } else {
                        data.put("deviceIds", new JSONArray());
                        FirestoreHelper.createDocument("users", data, cb);
                    }
                    dialog.dismiss();
                } catch (Exception e) { toast("Error: " + e.getMessage()); }
            }
        });
    }

    private void showDateTimePicker(final EditText target) {
        final Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker view, final int year, final int month, final int day) {
                new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    public void onTimeSet(TimePicker view2, int hour, int minute) {
                        String dt = String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d", year, month + 1, day, hour, minute);
                        target.setText(dt);
                    }
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private EditText createDialogInput(LinearLayout parent, String hint, String value) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(value);
        et.setTextColor(0xFF1E293B);
        et.setHintTextColor(0xFF94A3B8);
        et.setBackgroundColor(0xFFF1F5F9);
        et.setPadding(24, 20, 24, 20);
        et.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        parent.addView(et, lp);
        return et;
    }

    // =================== PAYMENTS ===================

    private void setupPaymentSection() {
        ListView paymentList = (ListView) findViewById(R.id.paymentListView);
        paymentAdapter = new PaymentAdapter(this, new PaymentAdapter.PaymentActionListener() {
            public void onApprove(final FirestoreHelper.DocResult payment) {
                final EditText input = new EditText(MainActivity.this);
                input.setHint("How many days to add?");
                input.setText("30");
                input.setTextColor(0xFF1E293B);
                input.setHintTextColor(0xFF94A3B8);
                input.setBackgroundColor(0xFFF1F5F9);
                input.setPadding(24, 20, 24, 20);

                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Approve Payment")
                    .setView(input)
                    .setPositiveButton("Approve", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            int days;
                            try { days = Integer.parseInt(input.getText().toString()); } catch (Exception e) { toast("Invalid number!"); return; }
                            approvePayment(payment, days);
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
            }
            public void onReject(final FirestoreHelper.DocResult payment) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Reject Payment")
                    .setMessage("Reject this payment?")
                    .setPositiveButton("Reject", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            try {
                                JSONObject data = new JSONObject();
                                data.put("status", "Rejected");
                                FirestoreHelper.updateDocument("payment_requests", payment.id, data, new FirestoreHelper.Callback() {
                                    public void onSuccess(JSONObject r) { toast("Payment rejected"); loadPayments(); }
                                    public void onError(String e) { toast("Error: " + e); }
                                });
                            } catch (Exception e) { toast("Error"); }
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
            }
        });
        paymentList.setAdapter(paymentAdapter);

        // Payment filter buttons
        final Button filterAll = (Button) findViewById(R.id.payFilterAll);
        final Button filterPending = (Button) findViewById(R.id.payFilterPending);
        final Button filterApproved = (Button) findViewById(R.id.payFilterApproved);
        final Button filterRejected = (Button) findViewById(R.id.payFilterRejected);
        final Button[] filterBtns = {filterAll, filterPending, filterApproved, filterRejected};
        final String[] filterVals = {"all", "Pending", "Approved", "Rejected"};

        for (int i = 0; i < filterBtns.length; i++) {
            final int idx = i;
            filterBtns[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    paymentAdapter.filterByStatus(filterVals[idx]);
                    for (int j = 0; j < filterBtns.length; j++) {
                        filterBtns[j].setBackgroundResource(j == idx ? R.drawable.bg_btn_primary : R.drawable.bg_btn_ghost);
                        if (j == idx) filterBtns[j].setTextColor(0xFFFFFFFF);
                        else {
                            int[] colors = {0xFF475569, 0xFFF59E0B, 0xFF22C55E, 0xFFEF4444};
                            filterBtns[j].setTextColor(colors[j]);
                        }
                    }
                }
            });
        }
    }

    private void approvePayment(final FirestoreHelper.DocResult payment, final int days) {
        final String userId = payment.getString("userId");
        FirestoreHelper.getDocument("users", userId, new FirestoreHelper.Callback() {
            public void onSuccess(JSONObject doc) {
                try {
                    JSONObject fields = doc.optJSONObject("fields");
                    String currentExpiry = "";
                    if (fields != null && fields.has("expiry")) {
                        JSONObject ev = fields.getJSONObject("expiry");
                        currentExpiry = ev.optString("stringValue", "");
                    }

                    long expiryTime;
                    if (!currentExpiry.isEmpty()) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
                            expiryTime = sdf.parse(currentExpiry).getTime();
                        } catch (Exception e) { expiryTime = System.currentTimeMillis(); }
                    } else {
                        expiryTime = System.currentTimeMillis();
                    }

                    if (expiryTime < System.currentTimeMillis()) expiryTime = System.currentTimeMillis();
                    expiryTime += (long) days * 24 * 60 * 60 * 1000;

                    String newExpiry = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(new Date(expiryTime));

                    JSONObject userData = new JSONObject();
                    userData.put("expiry", newExpiry);
                    FirestoreHelper.updateDocument("users", userId, userData, new FirestoreHelper.Callback() {
                        public void onSuccess(JSONObject r) {
                            try {
                                JSONObject payData = new JSONObject();
                                payData.put("status", "Approved");
                                FirestoreHelper.updateDocument("payment_requests", payment.id, payData, new FirestoreHelper.Callback() {
                                    public void onSuccess(JSONObject r) { toast("Approved! +" + days + " days"); loadPayments(); loadUsers(); }
                                    public void onError(String e) { toast("Error: " + e); }
                                });
                            } catch (Exception e) { toast("Error"); }
                        }
                        public void onError(String e) { toast("Error: " + e); }
                    });
                } catch (Exception e) { toast("Error processing"); }
            }
            public void onError(String e) { toast("Error: " + e); }
        });
    }

    // =================== CATEGORIES ===================

    private void setupCategorySection() {
        GridView catGrid = (GridView) findViewById(R.id.catGridView);
        categoryAdapter = new CategoryAdapter(this, new CategoryAdapter.CategoryActionListener() {
            public void onEdit(FirestoreHelper.DocResult cat) { showCategoryDialog(cat); }
            public void onDelete(final FirestoreHelper.DocResult cat) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Delete Category")
                    .setMessage("Delete '" + cat.getString("name") + "'?")
                    .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            FirestoreHelper.deleteDocument("categories", cat.id, new FirestoreHelper.Callback() {
                                public void onSuccess(JSONObject r) { loadCategories(); }
                                public void onError(String e) { toast("Error: " + e); }
                            });
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
            }
        });
        catGrid.setAdapter(categoryAdapter);

        Button addCatBtn = (Button) findViewById(R.id.addCatBtn);
        addCatBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showCategoryDialog(null); }
        });

        Button deleteAllCats = (Button) findViewById(R.id.deleteAllCatsBtn);
        deleteAllCats.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Delete All Categories")
                    .setMessage("Delete all categories? This cannot be undone!")
                    .setPositiveButton("Delete All", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { deleteAllCategories(); }
                    })
                    .setNegativeButton("Cancel", null).show();
            }
        });
    }

    private void showCategoryDialog(final FirestoreHelper.DocResult cat) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);
        layout.setBackgroundColor(0xFFFFFFFF);

        final EditText nameInput = createDialogInput(layout, "Category Name", cat != null ? cat.getString("name") : "");
        final EditText logoInput = createDialogInput(layout, "Logo URL (optional)", cat != null ? cat.getString("logo") : "");

        new AlertDialog.Builder(this)
            .setTitle(cat == null ? "New Category" : "Edit Category")
            .setView(layout)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        String nameVal = nameInput.getText().toString().trim();
                        if (nameVal.isEmpty()) { toast("Category name khali rakha jabe na!"); return; }

                        JSONObject data = new JSONObject();
                        data.put("name", nameVal);
                        data.put("logo", logoInput.getText().toString().trim());

                        if (cat != null) {
                            FirestoreHelper.updateDocument("categories", cat.id, data, new FirestoreHelper.Callback() {
                                public void onSuccess(JSONObject r) { toast("Category updated!"); loadCategories(); }
                                public void onError(String e) { toast("Error: " + e); }
                            });
                        } else {
                            FirestoreHelper.createDocument("categories", data, new FirestoreHelper.Callback() {
                                public void onSuccess(JSONObject r) { toast("Category added!"); loadCategories(); }
                                public void onError(String e) { toast("Error: " + e); }
                            });
                        }
                    } catch (Exception e) { toast("Error"); }
                }
            })
            .setNegativeButton("Cancel", null).show();
    }

    private void deleteAllCategories() {
        FirestoreHelper.getCollection("categories", new FirestoreHelper.ListCallback() {
            public void onSuccess(List<FirestoreHelper.DocResult> docs) {
                List<String> ids = new ArrayList<String>();
                for (FirestoreHelper.DocResult d : docs) ids.add(d.id);
                FirestoreHelper.batchDelete("categories", ids, new FirestoreHelper.Callback() {
                    public void onSuccess(JSONObject r) { toast("All categories deleted."); loadCategories(); }
                    public void onError(String e) { toast("Error: " + e); }
                });
            }
            public void onError(String e) { toast("Error: " + e); }
        });
    }

    // =================== CHANNELS ===================

    private void setupChannelSection() {
        GridView channelGrid = (GridView) findViewById(R.id.channelGridView);
        channelAdapter = new ChannelAdapter(this, new ChannelAdapter.ChannelActionListener() {
            public void onChannelClick(FirestoreHelper.DocResult channel) { showChannelDialog(channel); }
        });
        channelGrid.setAdapter(channelAdapter);

        EditText chSearch = (EditText) findViewById(R.id.chSearch);
        chSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { channelAdapter.filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

        Button addChannelBtn = (Button) findViewById(R.id.addChannelBtn);
        addChannelBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showChannelDialog(null); }
        });

        Button bulkImportBtn = (Button) findViewById(R.id.bulkImportBtn);
        bulkImportBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showBulkImportDialog(); }
        });
    }

    private void showChannelDialog(final FirestoreHelper.DocResult channel) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);
        layout.setBackgroundColor(0xFFFFFFFF);

        final EditText nameInput = createDialogInput(layout, "Channel Name *", channel != null ? channel.getString("name") : "");
        final EditText urlInput = createDialogInput(layout, "Stream URL (m3u8/mpd/mp4/mkv) *", channel != null ? channel.getString("url") : "");

        // Channel type spinner
        TextView typeLabel = new TextView(this);
        typeLabel.setText("TYPE");
        typeLabel.setTextColor(0xFF64748B);
        typeLabel.setTextSize(10);
        typeLabel.setPadding(4, 0, 0, 8);
        layout.addView(typeLabel);

        final Spinner typeSpinner = new Spinner(this);
        String[] types = {"Live", "VOD", "DRM"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, types);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        if (channel != null) {
            String cType = channel.getString("type");
            for (int i = 0; i < types.length; i++) {
                if (types[i].equalsIgnoreCase(cType)) typeSpinner.setSelection(i);
            }
        }
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spinnerLp.bottomMargin = 16;
        layout.addView(typeSpinner, spinnerLp);

        // Category - use dropdown from loaded categories
        TextView catLabel = new TextView(this);
        catLabel.setText("CATEGORY");
        catLabel.setTextColor(0xFF64748B);
        catLabel.setTextSize(10);
        catLabel.setPadding(4, 0, 0, 8);
        layout.addView(catLabel);

        final Spinner catSpinner = new Spinner(this);
        List<String> catNames = categoryAdapter.getCategoryNames();
        if (!catNames.contains("General")) catNames.add(0, "General");
        ArrayAdapter<String> catAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, catNames);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        catSpinner.setAdapter(catAdapter);
        if (channel != null) {
            String curCat = channel.getString("category");
            for (int i = 0; i < catNames.size(); i++) {
                if (catNames.get(i).equalsIgnoreCase(curCat)) catSpinner.setSelection(i);
            }
        }
        layout.addView(catSpinner, spinnerLp);

        final EditText logoInput = createDialogInput(layout, "Logo URL (optional)", channel != null ? channel.getString("logo") : "");
        final EditText drmInput = createDialogInput(layout, "DRM License URL (optional)", channel != null ? channel.getString("drm_license_url") : "");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(channel == null ? "Add Channel" : "Edit Channel");
        builder.setView(layout);
        builder.setPositiveButton("Save", null);
        if (channel != null) {
            builder.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete Channel")
                        .setMessage("Delete '" + channel.getString("name") + "'?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d2, int w2) {
                                FirestoreHelper.deleteDocument("channels", channel.id, new FirestoreHelper.Callback() {
                                    public void onSuccess(JSONObject r) { toast("Channel deleted"); loadChannels(); }
                                    public void onError(String e) { toast("Error: " + e); }
                                });
                            }
                        })
                        .setNegativeButton("Cancel", null).show();
                }
            });
        }
        builder.setNegativeButton("Cancel", null);

        final AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String nameVal = nameInput.getText().toString().trim();
                String urlVal = urlInput.getText().toString().trim();

                if (nameVal.isEmpty()) { toast("Channel name khali rakha jabe na!"); return; }
                if (urlVal.isEmpty()) { toast("Stream URL khali rakha jabe na!"); return; }
                if (!urlVal.startsWith("http://") && !urlVal.startsWith("https://")) { toast("URL http/https diye shuru hote hobe!"); return; }

                try {
                    JSONObject data = new JSONObject();
                    data.put("name", nameVal);
                    data.put("logo", logoInput.getText().toString().trim());
                    data.put("url", urlVal);
                    data.put("category", catSpinner.getSelectedItem().toString());
                    data.put("type", typeSpinner.getSelectedItem().toString());
                    String drmVal = drmInput.getText().toString().trim();
                    data.put("drm_license_url", drmVal);

                    FirestoreHelper.Callback cb = new FirestoreHelper.Callback() {
                        public void onSuccess(JSONObject r) { toast("Channel saved!"); loadChannels(); }
                        public void onError(String e) { toast("Error: " + e); }
                    };

                    if (channel != null) {
                        FirestoreHelper.updateDocument("channels", channel.id, data, cb);
                    } else {
                        data.put("status", "live");
                        FirestoreHelper.createDocument("channels", data, cb);
                    }
                    dialog.dismiss();
                } catch (Exception e) { toast("Error"); }
            }
        });
    }

    private void showBulkImportDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);
        layout.setBackgroundColor(0xFFFFFFFF);

        TextView title = new TextView(this);
        title.setText("Import channels from M3U playlist URL.\nSupports 1000+ channels.");
        title.setTextColor(0xFF64748B);
        title.setTextSize(12);
        title.setPadding(0, 0, 0, 24);
        layout.addView(title);

        final EditText urlInput = createDialogInput(layout, "Paste M3U Link here...", "");

        new AlertDialog.Builder(this)
            .setTitle("M3U Import")
            .setView(layout)
            .setPositiveButton("Start Import", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String url = urlInput.getText().toString().trim();
                    if (!url.isEmpty()) importFromUrl(url);
                }
            })
            .setNegativeButton("Cancel", null).show();
    }

    private void importFromUrl(String url) {
        toast("Importing...");
        FirestoreHelper.fetchUrl(url, new FirestoreHelper.Callback() {
            public void onSuccess(JSONObject result) {
                try {
                    String text = result.getString("body");
                    String[] lines = text.split("\n");
                    List<JSONObject> channels = new ArrayList<JSONObject>();

                    for (int i = 0; i < lines.length; i++) {
                        if (lines[i].startsWith("#EXTINF")) {
                            String info = lines[i];
                            String stream = (i + 1 < lines.length) ? lines[i + 1].trim() : "";
                            if (stream.isEmpty() || stream.startsWith("#")) {
                                stream = (i + 2 < lines.length) ? lines[i + 2].trim() : "";
                            }
                            if (!stream.isEmpty() && stream.startsWith("http")) {
                                String[] nameParts = info.split(",");
                                String name = nameParts.length > 1 ? nameParts[1].trim() : "Unknown";

                                String logo = "";
                                int logoStart = info.indexOf("tvg-logo=\"");
                                if (logoStart >= 0) {
                                    logoStart += 10;
                                    int logoEnd = info.indexOf("\"", logoStart);
                                    if (logoEnd > logoStart) logo = info.substring(logoStart, logoEnd);
                                }

                                String category = "General";
                                int catStart = info.indexOf("group-title=\"");
                                if (catStart >= 0) {
                                    catStart += 13;
                                    int catEnd = info.indexOf("\"", catStart);
                                    if (catEnd > catStart) category = info.substring(catStart, catEnd);
                                }

                                String type = "Live";
                                String sl = stream.toLowerCase();
                                if (sl.endsWith(".mp4") || sl.endsWith(".mkv") || sl.endsWith(".avi") || sl.endsWith(".webm")) {
                                    type = "VOD";
                                }

                                JSONObject ch = new JSONObject();
                                ch.put("name", name);
                                ch.put("logo", logo);
                                ch.put("category", category);
                                ch.put("url", stream);
                                ch.put("type", type);
                                ch.put("status", "live");
                                ch.put("drm_license_url", "");
                                channels.add(ch);
                            }
                        }
                    }

                    if (channels.isEmpty()) {
                        toast("No channels found in M3U");
                        return;
                    }

                    final int total = channels.size();
                    FirestoreHelper.batchCreate("channels", channels, new FirestoreHelper.Callback() {
                        public void onSuccess(JSONObject r) { toast("Imported " + total + " channels!"); loadChannels(); }
                        public void onError(String e) { toast("Error: " + e); }
                    });
                } catch (Exception e) { toast("Error parsing M3U: " + e.getMessage()); }
            }
            public void onError(String e) { toast("Error fetching URL: " + e); }
        });
    }

    // =================== CHANNEL STATUS ===================

    private void setupChannelStatusSection() {
        final LinearLayout liveContainer = (LinearLayout) findViewById(R.id.liveListContainer);
        final LinearLayout downContainer = (LinearLayout) findViewById(R.id.downListContainer);
        final Button liveToggle = (Button) findViewById(R.id.liveToggle);
        final Button downToggle = (Button) findViewById(R.id.downToggle);

        liveToggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean visible = liveContainer.getVisibility() == View.VISIBLE;
                liveContainer.setVisibility(visible ? View.GONE : View.VISIBLE);
                liveToggle.setText(visible ? "Show \u25BC" : "Hide \u25B2");
            }
        });
        downToggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean visible = downContainer.getVisibility() == View.VISIBLE;
                downContainer.setVisibility(visible ? View.GONE : View.VISIBLE);
                downToggle.setText(visible ? "Show \u25BC" : "Hide \u25B2");
            }
        });

        Button checkAllBtn = (Button) findViewById(R.id.checkAllStreamsBtn);
        checkAllBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { checkAllStreams(); }
        });
    }

    private void checkAllStreams() {
        final List<FirestoreHelper.DocResult> all = channelAdapter.getAllChannels();
        if (all.isEmpty()) { toast("No channels to check"); return; }

        final View progressLayout = findViewById(R.id.checkProgressLayout);
        final TextView progressText = (TextView) findViewById(R.id.checkProgressText);
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.checkProgressBar);

        progressLayout.setVisibility(View.VISIBLE);
        progressBar.setMax(all.size());
        progressBar.setProgress(0);

        final AtomicInteger checked = new AtomicInteger(0);
        final AtomicInteger liveCount = new AtomicInteger(0);
        final AtomicInteger downCount = new AtomicInteger(0);

        for (final FirestoreHelper.DocResult ch : all) {
            String streamUrl = ch.getString("url");
            if (streamUrl.isEmpty()) {
                int c = checked.incrementAndGet();
                downCount.incrementAndGet();
                updateCheckProgress(progressText, progressBar, c, all.size(), liveCount.get(), downCount.get(), progressLayout);
                continue;
            }
            FirestoreHelper.checkStreamUrl(streamUrl, new FirestoreHelper.Callback() {
                public void onSuccess(JSONObject result) {
                    int c = checked.incrementAndGet();
                    try {
                        if (result.getBoolean("live")) {
                            liveCount.incrementAndGet();
                        } else {
                            downCount.incrementAndGet();
                            updateChannelStatusInDb(ch.id, "down");
                        }
                    } catch (Exception e) { downCount.incrementAndGet(); }
                    updateCheckProgress(progressText, progressBar, c, all.size(), liveCount.get(), downCount.get(), progressLayout);
                }
                public void onError(String error) {
                    int c = checked.incrementAndGet();
                    downCount.incrementAndGet();
                    updateChannelStatusInDb(ch.id, "down");
                    updateCheckProgress(progressText, progressBar, c, all.size(), liveCount.get(), downCount.get(), progressLayout);
                }
            });
        }
    }

    private void updateCheckProgress(TextView text, ProgressBar bar, int checked, int total, int live, int down, final View layout) {
        bar.setProgress(checked);
        text.setText("Checked " + checked + "/" + total + "  |  Live: " + live + "  Down: " + down);
        if (checked >= total) {
            new Handler().postDelayed(new Runnable() {
                public void run() { layout.setVisibility(View.GONE); }
            }, 3000);
            loadChannels();
        }
    }

    private void updateChannelStatusInDb(String channelId, String status) {
        try {
            JSONObject data = new JSONObject();
            data.put("status", status);
            FirestoreHelper.updateDocument("channels", channelId, data, new FirestoreHelper.Callback() {
                public void onSuccess(JSONObject r) {}
                public void onError(String e) {}
            });
        } catch (Exception e) {}
    }

    private void updateChannelStatus() {
        List<FirestoreHelper.DocResult> all = channelAdapter.getAllChannels();
        int total = all.size();
        int live = 0, down = 0;
        List<FirestoreHelper.DocResult> liveList = new ArrayList<FirestoreHelper.DocResult>();
        List<FirestoreHelper.DocResult> downList = new ArrayList<FirestoreHelper.DocResult>();

        for (FirestoreHelper.DocResult ch : all) {
            String status = ch.getString("status");
            if (status.isEmpty()) status = "live";
            if ("live".equalsIgnoreCase(status)) {
                live++;
                liveList.add(ch);
            } else {
                down++;
                downList.add(ch);
            }
        }

        csTotal.setText(String.valueOf(total));
        csLive.setText(String.valueOf(live));
        csDown.setText(String.valueOf(down));

        LinearLayout liveContainer = (LinearLayout) findViewById(R.id.liveListContainer);
        LinearLayout downContainer = (LinearLayout) findViewById(R.id.downListContainer);
        liveContainer.removeAllViews();
        downContainer.removeAllViews();

        for (FirestoreHelper.DocResult ch : liveList) {
            TextView tv = new TextView(this);
            tv.setText("\u25CF " + ch.getString("name"));
            tv.setTextColor(0xFF22C55E);
            tv.setTextSize(13);
            tv.setPadding(8, 8, 8, 8);
            liveContainer.addView(tv);
        }

        if (downList.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("All channels are live!");
            tv.setTextColor(0xFF94A3B8);
            tv.setTextSize(13);
            tv.setPadding(8, 16, 8, 16);
            tv.setGravity(android.view.Gravity.CENTER);
            downContainer.addView(tv);
        } else {
            for (FirestoreHelper.DocResult ch : downList) {
                TextView tv = new TextView(this);
                tv.setText("\u25CF " + ch.getString("name") + " - " + ch.getString("status"));
                tv.setTextColor(0xFFEF4444);
                tv.setTextSize(13);
                tv.setPadding(8, 8, 8, 8);
                downContainer.addView(tv);
            }
        }
    }

    // =================== SETTINGS ===================

    private void setupSettingsSection() {
        Button saveBtn = (Button) findViewById(R.id.saveSettingsBtn);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { saveSettings(); }
        });

        Button clearNotice = (Button) findViewById(R.id.clearNoticeBtn);
        clearNotice.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { ((EditText) findViewById(R.id.settingNotice)).setText(""); }
        });

        Button deleteAllCh = (Button) findViewById(R.id.deleteAllChBtn);
        deleteAllCh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Delete All Channels")
                    .setMessage("Delete all channels? This cannot be undone!")
                    .setPositiveButton("Delete All", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { deleteAllChannels(); }
                    })
                    .setNegativeButton("Cancel", null).show();
            }
        });
    }

    private void saveSettings() {
        try {
            JSONObject data = new JSONObject();
            data.put("monthly_price", ((EditText) findViewById(R.id.settingPrice)).getText().toString());
            data.put("expire_message", ((EditText) findViewById(R.id.settingExpireMsg)).getText().toString());
            data.put("live_notice", ((EditText) findViewById(R.id.settingNotice)).getText().toString());
            data.put("bkash_num", ((EditText) findViewById(R.id.settingBkash)).getText().toString());
            data.put("nagad_num", ((EditText) findViewById(R.id.settingNagad)).getText().toString());
            data.put("support_whatsapp", ((EditText) findViewById(R.id.settingWhatsapp)).getText().toString());
            data.put("support_telegram", ((EditText) findViewById(R.id.settingTelegram)).getText().toString());
            data.put("support_email", ((EditText) findViewById(R.id.settingEmail)).getText().toString());

            FirestoreHelper.setDocument("settings", "app_config", data, new FirestoreHelper.Callback() {
                public void onSuccess(JSONObject r) { toast("Settings Saved!"); }
                public void onError(String e) { toast("Error: " + e); }
            });
        } catch (Exception e) { toast("Error saving settings"); }
    }

    private void deleteAllChannels() {
        FirestoreHelper.getCollection("channels", new FirestoreHelper.ListCallback() {
            public void onSuccess(List<FirestoreHelper.DocResult> docs) {
                List<String> ids = new ArrayList<String>();
                for (FirestoreHelper.DocResult d : docs) ids.add(d.id);
                FirestoreHelper.batchDelete("channels", ids, new FirestoreHelper.Callback() {
                    public void onSuccess(JSONObject r) { toast("All channels deleted."); loadChannels(); }
                    public void onError(String e) { toast("Error: " + e); }
                });
            }
            public void onError(String e) { toast("Error: " + e); }
        });
    }

    // =================== DATA LOADING ===================

    private void loadAllData() {
        loadingCount = 5;
        loadingView.setVisibility(View.VISIBLE);
        loadUsers();
        loadPayments();
        loadCategories();
        loadChannels();
        loadSettings();
    }

    private void onLoadComplete() {
        loadingCount--;
        if (loadingCount <= 0) {
            loadingView.setVisibility(View.GONE);
        }
    }

    private void loadUsers() {
        FirestoreHelper.getCollection("users", new FirestoreHelper.ListCallback() {
            public void onSuccess(List<FirestoreHelper.DocResult> docs) {
                List<FirestoreHelper.DocResult> nonAdmin = new ArrayList<FirestoreHelper.DocResult>();
                for (FirestoreHelper.DocResult d : docs) {
                    if (!"admin".equals(d.getString("role"))) nonAdmin.add(d);
                }
                userAdapter.setData(nonAdmin);
                statUsers.setText(String.valueOf(userAdapter.getTotalCount()));
                statExpired.setText(String.valueOf(userAdapter.getExpiredCount()));
                int activeCount = userAdapter.getTotalCount() - userAdapter.getExpiredCount();
                if (activeCount < 0) activeCount = 0;
                statActive.setText(String.valueOf(activeCount));
                onLoadComplete();
            }
            public void onError(String e) { toast("Error loading users: " + e); onLoadComplete(); }
        });
    }

    private void loadPayments() {
        FirestoreHelper.getCollection("payment_requests", new FirestoreHelper.ListCallback() {
            public void onSuccess(List<FirestoreHelper.DocResult> docs) {
                paymentAdapter.setData(docs);
                statPending.setText(String.valueOf(paymentAdapter.getPendingCount()));
                statApproved.setText(String.valueOf(paymentAdapter.getApprovedCount()));
                onLoadComplete();
            }
            public void onError(String e) { toast("Error loading payments: " + e); onLoadComplete(); }
        });
    }

    private void loadCategories() {
        FirestoreHelper.getCollection("categories", new FirestoreHelper.ListCallback() {
            public void onSuccess(List<FirestoreHelper.DocResult> docs) {
                allCategories = docs;
                categoryAdapter.setData(docs);
                onLoadComplete();
            }
            public void onError(String e) { toast("Error loading categories: " + e); onLoadComplete(); }
        });
    }

    private void loadChannels() {
        FirestoreHelper.getCollection("channels", new FirestoreHelper.ListCallback() {
            public void onSuccess(List<FirestoreHelper.DocResult> docs) {
                channelAdapter.setData(docs);
                statChannels.setText(String.valueOf(docs.size()));
                updateChannelStatus();
                onLoadComplete();
            }
            public void onError(String e) { toast("Error loading channels: " + e); onLoadComplete(); }
        });
    }

    private void loadSettings() {
        FirestoreHelper.getDocument("settings", "app_config", new FirestoreHelper.Callback() {
            public void onSuccess(JSONObject doc) {
                try {
                    JSONObject fields = doc.optJSONObject("fields");
                    if (fields == null) { onLoadComplete(); return; }
                    setSettingField(R.id.settingPrice, fields, "monthly_price");
                    setSettingField(R.id.settingExpireMsg, fields, "expire_message");
                    setSettingField(R.id.settingNotice, fields, "live_notice");
                    setSettingField(R.id.settingBkash, fields, "bkash_num");
                    setSettingField(R.id.settingNagad, fields, "nagad_num");
                    setSettingField(R.id.settingWhatsapp, fields, "support_whatsapp");
                    setSettingField(R.id.settingTelegram, fields, "support_telegram");
                    setSettingField(R.id.settingEmail, fields, "support_email");
                } catch (Exception e) {}
                onLoadComplete();
            }
            public void onError(String e) { onLoadComplete(); }
        });
    }

    private void setSettingField(int viewId, JSONObject fields, String key) {
        try {
            JSONObject val = fields.optJSONObject(key);
            if (val != null) {
                String str = val.optString("stringValue", "");
                ((EditText) findViewById(viewId)).setText(str);
            }
        } catch (Exception e) {}
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            public void run() {
                loadAllData();
                refreshHandler.postDelayed(this, 300000);
            }
        };
        refreshHandler.postDelayed(refreshRunnable, 300000);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (sidebar.getVisibility() == View.VISIBLE) {
            toggleSidebar();
        } else if (dashboardPage.getVisibility() != View.VISIBLE) {
            showSection(0);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }
}
