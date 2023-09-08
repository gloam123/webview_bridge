package com.yiming.wvbridge;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
  private final String TAG = "wvbridge";
  private WebView mWebView;
  private EditText mUrlView;
  private ViewGroup mCrashView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    mWebView = findViewById(R.id.id_webview);
    mUrlView = findViewById(R.id.id_url_edit);
    mCrashView = findViewById(R.id.id_crash_view);

    findViewById(R.id.id_crash_reload).setOnClickListener((v) -> {
      mWebView.reload();
      mCrashView.setVisibility(View.INVISIBLE);
    });
    findViewById(R.id.id_go_url).setOnClickListener((v) -> {
      String url = mUrlView.getText().toString();
      if (!url.startsWith("http") && !url.startsWith("file")) {
        url = "http://" + url;
      }
      mWebView.loadUrl(url);
    });

    mWebView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return false;
      }

      @Override
      public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        mUrlView.setText(url);
      }

      @Override
      public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        mCrashView.setVisibility(View.VISIBLE);
        view.postInvalidate();
        return true;
      }
    });
    mWebView.addJavascriptInterface(new JavaBridge(), "JavaBridge");
    mWebView.loadUrl("file:///android_asset/index.html");
  }

  @Override
  public void onBackPressed() {
    if (mWebView.canGoBack()) {
      mWebView.goBack();
      return;
    }
    super.onBackPressed();
  }

  class JavaBridge extends Object {
    private int mCount = 0;

    public JavaBridge() {}

    @JavascriptInterface
    public String helloJava(String msg) {
      Log.e(TAG, "helloJava msg=" + msg);
      Toast.makeText(MainActivity.this, "CallJava.helloJava msg=" + msg, Toast.LENGTH_LONG).show();
      return String.format("Javabridge was called %d times!", ++mCount);
    }
  }

}