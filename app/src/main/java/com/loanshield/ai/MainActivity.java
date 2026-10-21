package com.loanshield.ai;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import org.json.JSONObject;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "LoanShieldAI";
    private static final String APP_WEB_URL = "https://kamal19742002-siddhi.github.io/loanshield-ai/";
    private static final String PREFS_NAME = "LoanShieldPrefs";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout offlineLayout;
    private Button btnRetry;
    private TextToSpeech textToSpeech;
    private SharedPreferences preferences;
    private boolean hasLoadError = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            var systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
        }

        webView = findViewById(R.id.myWebView);
        progressBar = findViewById(R.id.progressBar);
        offlineLayout = findViewById(R.id.offlineLayout);
        btnRetry = findViewById(R.id.btnRetry);

        textToSpeech = new TextToSpeech(this, this);
        WebView.setWebContentsDebuggingEnabled(true);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setLoadsImagesAutomatically(true);

        // Offline caching support
        if (isNetworkAvailable(this)) {
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        } else {
            webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }

        webView.addJavascriptInterface(new WebAppInterface(), "LoanShieldApp");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        btnRetry.setOnClickListener(v -> {
            offlineLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            hasLoadError = false;
            if (isNetworkAvailable(this)) {
                webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            } else {
                webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            }
            webView.loadUrl(APP_WEB_URL);
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("whatsapp://") || url.startsWith("https://api.whatsapp.com/") || url.startsWith("https://wa.me/") ||
                    url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("upi://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "App not available to handle action.", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                hasLoadError = false;
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                if (!hasLoadError) {
                    webView.setVisibility(View.VISIBLE);
                    offlineLayout.setVisibility(View.GONE);
                }

                String selectedLang = preferences.getString("appLanguage", "hi");
                boolean darkMode = preferences.getBoolean("darkMode", true);

                String injectJs = "try {" +
                        "  window.appLanguage = '" + selectedLang + "';" +
                        "  window.darkMode = " + darkMode + ";" +
                        "  localStorage.setItem('appLanguage', '" + selectedLang + "');" +
                        "  localStorage.setItem('darkMode', " + darkMode + ");" +
                        "  document.body.classList.add('theme-dark');" +
                        "} catch(e){};";
                view.evaluateJavascript(injectJs, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    hasLoadError = true;
                    progressBar.setVisibility(View.GONE);
                    webView.setVisibility(View.GONE);
                    offlineLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "WebView Console: " + consoleMessage.message());
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.loadUrl(APP_WEB_URL);
    }

    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            String selectedLang = preferences.getString("appLanguage", "hi");
            Locale locale = "hi".equals(selectedLang) ? new Locale("hi", "IN") : Locale.US;
            textToSpeech.setLanguage(locale);
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void setLanguage(String langCode) {
            preferences.edit().putString("appLanguage", langCode != null ? langCode : "hi").apply();
            runOnUiThread(() -> {
                Locale locale = "hi".equals(langCode) ? new Locale("hi", "IN") : Locale.US;
                if (textToSpeech != null) textToSpeech.setLanguage(locale);
                Toast.makeText(MainActivity.this, "Language updated: " + langCode, Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void toggleDarkMode(boolean enable) {
            preferences.edit().putBoolean("darkMode", enable).apply();
            runOnUiThread(() -> {
                String js = "document.body.classList.toggle('theme-dark', " + enable + ");";
                webView.evaluateJavascript(js, null);
            });
        }

        @JavascriptInterface
        public void speak(String text) {
            if (textToSpeech != null && text != null && !text.trim().isEmpty()) {
                textToSpeech.speak(text.replaceAll("<[^>]*>", "").trim(), TextToSpeech.QUEUE_FLUSH, null, null);
            }
        }

        @JavascriptInterface
        public String calculateSafetyScore(int redFlags, int greenBenefits) {
            int score = Math.max(0, Math.min(100, 100 - (redFlags * 20) + (greenBenefits * 10)));
            try {
                JSONObject json = new JSONObject();
                json.put("score", score);
                json.put("status", score >= 75 ? "SAFE_LOAN" : score >= 50 ? "MODERATE_TRAPS" : "HIGH_RISK_TRAP");
                return json.toString();
            } catch (Exception e) {
                return "{\"score\":70,\"status\":\"MODERATE_TRAPS\"}";
            }
        }

        @JavascriptInterface
        public void openDhandaAIMarketing() {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.dhanda.aimarketing"));
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error opening Dhanda AI Marketing", e);
            }
        }

        @JavascriptInterface
        public void shareOnWhatsApp(String text) {
            if (text == null || text.trim().isEmpty()) return;
            try {
                Intent whatsappIntent = new Intent(Intent.ACTION_SEND);
                whatsappIntent.setType("text/plain");
                whatsappIntent.setPackage("com.whatsapp");
                whatsappIntent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(whatsappIntent);
            } catch (Exception e) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(shareIntent, "Share LoanShield AI Report"));
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
