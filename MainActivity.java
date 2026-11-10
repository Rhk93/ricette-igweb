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


public class MainActivity extends Activity {
    private static final String IG = "https://www.instagram.com";
    private WebView ui;
    private WebView web;
    private WebView igw;
    private boolean igwReady = false;
    private final java.util.ArrayList<String[]> queue = new java.util.ArrayList<>();
    private LinearLayout overlay;
    private TextView overlayTitle;
    private boolean loggingIn = false;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("ricette", MODE_PRIVATE);
        try { getWindow().setStatusBarColor(Color.parseColor("#C9471F")); } catch (Exception ignored) {}

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);

        FrameLayout root = new FrameLayout(this);

        igw = new WebView(this);
        setup(igw);
        cm.setAcceptThirdPartyCookies(igw, true);
        igw.getSettings().setMediaPlaybackRequiresUserGesture(true);
        igw.setAlpha(0.01f);
        igw.addJavascriptInterface(new Worker(), "IGW");
        igw.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                if (url != null && url.startsWith(IG)) {
                    igwReady = true;
                    flushQueue();
                }
            }
        });
        root.addView(igw, new FrameLayout.LayoutParams(dp(2), dp(2)));

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
        igwReady = false;
        igw.loadUrl("about:blank");
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
                igwReady = false;
                igw.loadUrl("about:blank");
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
            main.post(() -> {
                if (url == null || !url.startsWith(IG + "/")) { deliver(cb, 0, "url"); return; }
                queue.add(new String[]{url, cb});
                if (igwReady) flushQueue();
                else if (igw.getUrl() == null || !igw.getUrl().startsWith(IG)) igw.loadUrl(IG + "/");
            });
        }
    }

    private void flushQueue() {
        while (!queue.isEmpty()) {
            String[] r = queue.remove(0);
            String js = "(function(u,cb){var m=document.cookie.match(/(?:^|; )csrftoken=([^;]+)/);var c='0';"
                    + "try{c=sessionStorage.getItem('www-claim-v2')||'0'}catch(e){}"
                    + "fetch(u,{credentials:'include',headers:{'X-IG-App-ID':'936619743392459','X-ASBD-ID':'129477',"
                    + "'X-Requested-With':'XMLHttpRequest','X-IG-WWW-Claim':c,'X-CSRFToken':m?m[1]:''}})"
                    + ".then(function(x){return x.text().then(function(t){IGW.done(cb,x.status,t)})})"
                    + ".catch(function(e){IGW.done(cb,0,String(e))})})(" + JSONObject.quote(r[0]) + "," + JSONObject.quote(r[1]) + ")";
            igw.evaluateJavascript(js, null);
        }
    }

    private void deliver(String cb, int code, String body) {
        String js = "window.__res(" + JSONObject.quote(cb) + "," + code + "," + JSONObject.quote(body == null ? "" : body) + ")";
        ui.evaluateJavascript(js, null);
    }

    private class Worker {
        @JavascriptInterface
        public void done(String cb, int code, String body) {
            main.post(() -> deliver(cb, code, body));
        }
    }
}
