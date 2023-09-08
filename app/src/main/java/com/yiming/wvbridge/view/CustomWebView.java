package com.yiming.wvbridge.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * Created by gqg on 23-9-8.
 */

public class CustomWebView extends WebView {
  private Context mContext;

  public CustomWebView(Context context) {
    this(context, null);
  }

  public CustomWebView(Context context, AttributeSet attrs) {
    super(context, attrs, android.R.attr.webViewStyle);

    mContext = context;

    if (!isInEditMode()) {
      loadSettings();
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  public void loadSettings() {
    WebSettings settings = getSettings();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

    settings.setJavaScriptEnabled(true);
    settings.setLoadsImagesAutomatically(true);
    settings.setUseWideViewPort(true);
    settings.setLoadWithOverviewMode(false);

    settings.setGeolocationEnabled(true);
    settings.setSaveFormData(true);
    settings.setSavePassword(true);

    settings.setTextZoom(100);

    settings.setSupportZoom(true);
    settings.setDisplayZoomControls(false);
    settings.setBuiltInZoomControls(true);
    settings.setSupportMultipleWindows(true);
    settings.setEnableSmoothTransition(true);

    // HTML5 API flags
//    settings.setAppCacheEnabled(true);
    settings.setDatabaseEnabled(true);
    settings.setDomStorageEnabled(true);

    settings.setDatabasePath(mContext.getDir("databases", 0).getPath());
    settings.setGeolocationDatabasePath(mContext.getDir("geolocation", 0).getPath());
    settings.setJavaScriptEnabled(true);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    settings.setMediaPlaybackRequiresUserGesture(false);

    setLongClickable(true);
  }

}

