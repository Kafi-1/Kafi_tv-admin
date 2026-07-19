package com.kafi.admintv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.util.List;

public class LoginActivity extends Activity {

    private EditText emailInput, pinInput;
    private Button loginBtn;
    private TextView errorText;
    private int failedAttempts = 0;
    private long lockoutUntil = 0;

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

        SharedPreferences prefs = getSharedPreferences("iptv_admin", MODE_PRIVATE);
        if (prefs.getBoolean("authenticated", false)) {
            long loginTime = prefs.getLong("login_time", 0);
            if (System.currentTimeMillis() - loginTime < 24 * 60 * 60 * 1000) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            } else {
                prefs.edit().putBoolean("authenticated", false).putLong("login_time", 0).apply();
            }
        }

        setContentView(R.layout.activity_login);

        emailInput = (EditText) findViewById(R.id.loginEmail);
        pinInput = (EditText) findViewById(R.id.loginPin);
        loginBtn = (Button) findViewById(R.id.loginBtn);
        errorText = (TextView) findViewById(R.id.loginError);

        loginBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                handleLogin();
            }
        });
    }

    private void handleLogin() {
        final String email = emailInput.getText().toString().trim().toLowerCase();
        final String pin = pinInput.getText().toString().trim();

        if (email.isEmpty() || pin.isEmpty()) return;

        if (System.currentTimeMillis() < lockoutUntil) {
            long secondsLeft = (lockoutUntil - System.currentTimeMillis()) / 1000;
            errorText.setText("Too many attempts! Wait " + secondsLeft + "s");
            errorText.setVisibility(View.VISIBLE);
            return;
        }

        loginBtn.setText("VERIFYING...");
        loginBtn.setEnabled(false);
        errorText.setVisibility(View.GONE);

        FirestoreHelper.queryCollection("users", "email", email, new FirestoreHelper.ListCallback() {
            public void onSuccess(List<FirestoreHelper.DocResult> docs) {
                boolean found = false;
                for (FirestoreHelper.DocResult doc : docs) {
                    if (doc.getString("pin").equals(pin) && "admin".equals(doc.getString("role"))) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    SharedPreferences prefs = getSharedPreferences("iptv_admin", MODE_PRIVATE);
                    prefs.edit().putBoolean("authenticated", true).putLong("login_time", System.currentTimeMillis()).apply();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else {
                    failedAttempts++;
                    if (failedAttempts >= 3) {
                        lockoutUntil = System.currentTimeMillis() + 30000;
                        errorText.setText("Too many attempts! Locked for 30s");
                        failedAttempts = 0;
                    } else {
                        errorText.setText("Invalid Admin Access! (" + (3 - failedAttempts) + " left)");
                    }
                    errorText.setVisibility(View.VISIBLE);
                    loginBtn.setText("AUTHENTICATE");
                    loginBtn.setEnabled(true);
                }
            }
            public void onError(String error) {
                errorText.setText("Server e connect korte pareni!");
                errorText.setVisibility(View.VISIBLE);
                loginBtn.setText("AUTHENTICATE");
                loginBtn.setEnabled(true);
            }
        });
    }
}
