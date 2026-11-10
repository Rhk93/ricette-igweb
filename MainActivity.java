package it.ricette.ig;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String IG = "https://www.instagram.com";
    private WebView ui;
    private WebView web;
    private LinearLayout overlay;
    private TextView overlayTitle;
    private boolean loggingIn = false;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private SharedPreferences prefs;
    private String userAgent;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("ricette", MODE_PRIVATE);
        try { getWindow().setStatusBarColor(Color.parseColor("#C9471F")); } catch (Exception ignored) {}

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);

        FrameLayout root = new FrameLayout(this);

        ui = new WebView(this);
        setup(ui);
        ui.addJavascriptInterface(new Bridge(), "Android");
        ui.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("file:")) return false;
                openExternal(u);
                return true;
            }
        });
        userAgent = ui.getSettings().getUserAgentString();
        root.addView(ui, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(Color.WHITE);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor("#D4542B"));
        bar.setPadding(dp(6), dp(4), dp(12), dp(4));
        Button close = new Button(this);
        close.setText("← Torna alle ricette");
        close.setAllCaps(false);
        close.setTextColor(Color.WHITE);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(v -> hideOverlay());
        overlayTitle = new TextView(this);
        overlayTitle.setTextColor(Color.WHITE);
        overlayTitle.setTextSize(14);
        overlayTitle.setGravity(Gravity.END);
        bar.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.addView(overlayTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        overlay.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        web = new WebView(this);
        setup(web);
        cm.setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String s = r.getUrl().getScheme();
                return !("http".equals(s) || "https".equals(s));
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                CookieManager.getInstance().flush();
                if (loggingIn && isLoggedIn() && url != null && !url.contains("/accounts/login")
                        && !url.contains("/challenge") && !url.contains("two_factor")) {
                    loggingIn = false;
                    hideOverlay();
                }
            }
        });
        overlay.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        ui.loadUrl("file:///android_asset/index.html");
    }

    private void setup(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private boolean isLoggedIn() {
        String c = CookieManager.getInstance().getCookie(IG);
        return c != null && c.contains("sessionid=");
    }

    private String cookie(String name) {
        String c = CookieManager.getInstance().getCookie(IG);
        if (c == null) return "";
        for (String part : c.split(";")) {
            String p = part.trim();
            if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
        }
        return "";
    }

    private void showOverlay(String url, String title) {
        overlayTitle.setText(title);
        overlay.setVisibility(View.VISIBLE);
        web.loadUrl(url);
    }

    private void hideOverlay() {
        overlay.setVisibility(View.GONE);
        web.loadUrl("about:blank");
        loggingIn = false;
        ui.evaluateJavascript("window.onLoginChanged && window.onLoginChanged(" + isLoggedIn() + ")", null);
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) {
            if (web.canGoBack() && !"about:blank".equals(web.getUrl())) web.goBack();
            else hideOverlay();
        } else {
            super.onBackPressed();
        }
    }

    private class Bridge {
        @JavascriptInterface
        public String load(String key) {
            return prefs.getString(key, "");
        }

        @JavascriptInterface
        public void save(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }

        @JavascriptInterface
        public boolean loggedIn() {
            return isLoggedIn();
        }

        @JavascriptInterface
        public void login() {
            main.post(() -> {
                loggingIn = true;
                showOverlay(IG + "/accounts/login/", "Accedi a Instagram");
            });
        }

        @JavascriptInterface
        public void logout() {
            main.post(() -> {
                CookieManager.getInstance().removeAllCookies(ok -> {
                    CookieManager.getInstance().flush();
                    ui.evaluateJavascript("window.onLoginChanged && window.onLoginChanged(false)", null);
                });
            });
        }

        @JavascriptInterface
        public void browse(String url) {
            if (url == null || !url.startsWith(IG)) return;
            main.post(() -> showOverlay(url, "Instagram"));
        }

        @JavascriptInterface
        public void open(String url) {
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
            main.post(() -> openExternal(url));
        }

        @JavascriptInterface
        public void get(String url, String cb) {
            pool.submit(() -> {
                int code = 0;
                String body = "";
                HttpURLConnection c = null;
                try {
                    if (!url.startsWith(IG + "/")) throw new IllegalArgumentException("url");
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    c.setInstanceFollowRedirects(false);
                    c.setRequestProperty("User-Agent", userAgent);
                    c.setRequestProperty("Accept", "*/*");
                    c.setRequestProperty("Accept-Language", "it-IT,it;q=0.9");
                    c.setRequestProperty("X-IG-App-ID", "936619743392459");
                    c.setRequestProperty("X-ASBD-ID", "129477");
                    c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                    c.setRequestProperty("X-CSRFToken", cookie("csrftoken"));
                    c.setRequestProperty("Referer", IG + "/");
                    String ck = CookieManager.getInstance().getCookie(IG);
                    if (ck != null) c.setRequestProperty("Cookie", ck);
                    code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    if (in != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        in.close();
                        body = out.toString("UTF-8");
                    }
                } catch (Exception e) {
                    code = 0;
                    body = String.valueOf(e.getMessage());
                } finally {
                    if (c != null) c.disconnect();
                }
                final String js = "window.__res(" + JSONObject.quote(cb) + "," + code + "," + JSONObject.quote(body) + ")";
                main.post(() -> ui.evaluateJavascript(js, null));
            });
        }
    }
}
