## Overview
In Android WebView, starting from Chromium 88, when the rendering process crashes and recovers, JavaScript calls to Java Bridge interfaces added via addJavascriptInterface may prompt "TypeError: BridgeXXX.methodXXX is not a function."

## How to reproduce
- Define and register the JavaBridge
```
  // In MainActivity.java
  class JavaBridge extends Object {
    private int mCount = 0;

    @JavascriptInterface
    public String helloJava(String msg) {
      Toast.makeText(MainActivity.this, "CallJava.helloJava msg=" + msg, Toast.LENGTH_LONG).show();
      return String.format("Javabridge was called %d times!", ++mCount);
    }
  }
  // register
  mWebView.addJavascriptInterface(new JavaBridge(), "JavaBridge");
```

 - Overload onRenderProcessGone to avoid crashing the main process and display a reload button
```
    // In MainActivity.java
    mWebView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        mCrashView.setVisibility(View.VISIBLE);
        view.postInvalidate();
        return true;
      }
    });
```
 - Call javabridge and display the return results
```
  // In index.html
  function updateBridgeInfo(info, color) {
    let oInfo = document.getElementById("bridge_info");
    oInfo.innerHTML = info;
    if (color) {
      oInfo.style.color = color;
    }
  }
  
  var gCallCount = 1;
  function callJavaBridge() {
    try {
      let result = JavaBridge.helloJava("from javascript: call count=" + gCallCount++);
      updateBridgeInfo(result, '#6d6');
    } catch (err) {
      updateBridgeInfo(err, '#d66');
    }
  }
```

## chromium 119 reproduction demonstration
Error snapshot | Steps to reproduce
:-: | :-:
<img src='https://raw.githubusercontent.com/gloam123/webview_bridge/main/docs/cr119_bug.png' width=290 /> | <video poster='https://raw.githubusercontent.com/gloam123/webview_bridge/main/docs/cr119_bug.png' src='https://github.com/gloam123/webview_bridge/assets/47805211/46f490ee-c80a-4769-aa11-c6d3e4acfdad' />

## chromium 119 fixed demonstration
Fixed snapshot | Steps to reproduce
:-: | :-:
<img src='https://raw.githubusercontent.com/gloam123/webview_bridge/main/docs/cr119_fixed.png' width=290 /> | <video poster='https://raw.githubusercontent.com/gloam123/webview_bridge/main/docs/cr119_fixed.png' src='https://github.com/gloam123/webview_bridge/assets/47805211/3a68d996-84da-48ce-9e2e-5ab6155ea5d0' />

## Detailed reasons
After the Renderer process crashes and recovers, the main process creates a new RenderFrameHost. However, GinJavaBridgeMessageFilter does not update the 'host_' property to point to the new RenderFrameHost and remove the old one at the appropriate time. When an H5 webpage calls JavaBridge, it tries to find the corresponding RenderFrameHost using the routing_id of the current RenderFrame. At this point, it cannot be found, leading to a JavaScript error: 'TypeError: JavaBridge.helloJava is not a function.'

## Fix patch:
```
---
 .../java/gin_java_bridge_dispatcher_host.cc   | 37 ++++++++-----------
 .../java/gin_java_bridge_dispatcher_host.h    |  3 +-
 2 files changed, 18 insertions(+), 22 deletions(-)

diff --git a/content/browser/android/java/gin_java_bridge_dispatcher_host.cc b/content/browser/android/java/gin_java_bridge_dispatcher_host.cc
index 695688d285ad4..5c3516bc380a5 100644
--- a/content/browser/android/java/gin_java_bridge_dispatcher_host.cc
+++ b/content/browser/android/java/gin_java_bridge_dispatcher_host.cc
@@ -59,9 +59,16 @@ void GinJavaBridgeDispatcherHost::InstallFilterAndRegisterAllRoutingIds() {
       ->GetPrimaryMainFrame()
       ->ForEachRenderFrameHost(
           [this](RenderFrameHostImpl* frame) {
-            AgentSchedulingGroupHost& agent_scheduling_group =
-                frame->GetAgentSchedulingGroup();
+            InstallFilterAndRegisterRoutingId(frame);
+          });
+}
 
+void GinJavaBridgeDispatcherHost::InstallFilterAndRegisterRoutingId(
+    RenderFrameHost* render_frame_host) {
+  DCHECK_CURRENTLY_ON(BrowserThread::UI);
+  AgentSchedulingGroupHost& agent_scheduling_group =
+      static_cast<RenderFrameHostImpl*>(render_frame_host)
+          ->GetAgentSchedulingGroup();
   scoped_refptr<GinJavaBridgeMessageFilter> per_asg_filter =
       GinJavaBridgeMessageFilter::FromHost(
           agent_scheduling_group,
@@ -72,11 +79,10 @@ void GinJavaBridgeDispatcherHost::InstallFilterAndRegisterAllRoutingIds() {
             GinJavaBridgeObjectDeletionMessageFilter::FromHost(
                 agent_scheduling_group.GetProcess(),
                 /*create_if_not_exists=*/true);
-              process_global_filter->AddRoutingIdForHost(this, frame);
+    process_global_filter->AddRoutingIdForHost(this, render_frame_host);
   }
 
-            per_asg_filter->AddRoutingIdForHost(this, frame);
-          });
+  per_asg_filter->AddRoutingIdForHost(this, render_frame_host);
 }
 
 WebContentsImpl* GinJavaBridgeDispatcherHost::web_contents() const {
@@ -86,16 +92,7 @@ WebContentsImpl* GinJavaBridgeDispatcherHost::web_contents() const {
 void GinJavaBridgeDispatcherHost::RenderFrameCreated(
     RenderFrameHost* render_frame_host) {
   DCHECK_CURRENTLY_ON(BrowserThread::UI);
-  AgentSchedulingGroupHost& agent_scheduling_group =
-      static_cast<RenderFrameHostImpl*>(render_frame_host)
-          ->GetAgentSchedulingGroup();
-  if (scoped_refptr<GinJavaBridgeMessageFilter> filter =
-          GinJavaBridgeMessageFilter::FromHost(
-              agent_scheduling_group, /*create_if_not_exists=*/false)) {
-    filter->AddRoutingIdForHost(this, render_frame_host);
-  } else {
-    InstallFilterAndRegisterAllRoutingIds();
-  }
+  InstallFilterAndRegisterRoutingId(render_frame_host);
   for (NamedObjectMap::const_iterator iter = named_objects_.begin();
        iter != named_objects_.end();
        ++iter) {
@@ -104,19 +101,17 @@ void GinJavaBridgeDispatcherHost::RenderFrameCreated(
   }
 }
 
-void GinJavaBridgeDispatcherHost::WebContentsDestroyed() {
-  // Unretained() is safe because ForEachRenderFrameHost() is synchronous.
-  web_contents()->GetPrimaryMainFrame()->ForEachRenderFrameHost(
-      [this](RenderFrameHostImpl* frame) {
+void GinJavaBridgeDispatcherHost::RenderFrameDeleted(
+    RenderFrameHost* render_frame_host) {
   AgentSchedulingGroupHost& agent_scheduling_group =
-            frame->GetAgentSchedulingGroup();
+      static_cast<RenderFrameHostImpl*>(render_frame_host)
+          ->GetAgentSchedulingGroup();
   scoped_refptr<GinJavaBridgeMessageFilter> filter =
       GinJavaBridgeMessageFilter::FromHost(
           agent_scheduling_group, /*create_if_not_exists=*/false);
 
   if (filter)
     filter->RemoveHost(this);
-      });
 }
 
 void GinJavaBridgeDispatcherHost::PrimaryPageChanged(Page& page) {
diff --git a/content/browser/android/java/gin_java_bridge_dispatcher_host.h b/content/browser/android/java/gin_java_bridge_dispatcher_host.h
index 2e29b17be9865..eba544c67f8f7 100644
--- a/content/browser/android/java/gin_java_bridge_dispatcher_host.h
+++ b/content/browser/android/java/gin_java_bridge_dispatcher_host.h
@@ -51,8 +51,8 @@ class GinJavaBridgeDispatcherHost
 
   // WebContentsObserver
   void RenderFrameCreated(RenderFrameHost* render_frame_host) override;
+  void RenderFrameDeleted(RenderFrameHost* render_frame_host) override;
   void PrimaryMainDocumentElementAvailable() override;
-  void WebContentsDestroyed() override;
   void PrimaryPageChanged(Page& page) override;
 
   // GinJavaMethodInvocationHelper::DispatcherDelegate
@@ -84,6 +84,7 @@ class GinJavaBridgeDispatcherHost
 
   // Run on the UI thread.
   void InstallFilterAndRegisterAllRoutingIds();
+  void InstallFilterAndRegisterRoutingId(RenderFrameHost* render_frame_host);
   WebContentsImpl* web_contents() const;
 
   // Run on any thread.
-- 
2.25.1
```
