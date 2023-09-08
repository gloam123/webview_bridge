# In Android WebView, starting from Chromium 88, when the rendering process crashes and recovers, JavaScript calls to Java Bridge interfaces added via addJavascriptInterface may prompt "TypeError: BridgeXXX.methodXXX is not a function."
### xxxxx


### cause
```

```

### patch:
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



