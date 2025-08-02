package com.astralx.browser.core.webview

import android.net.Uri
import android.webkit.*
import android.view.View

class AstralWebChromeClient : WebChromeClient() {
    
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        // Update loading progress in UI
    }
    
    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        // Update tab title
    }
    
    override fun onPermissionRequest(request: PermissionRequest?) {
        super.onPermissionRequest(request)
        
        // Handle permissions for adult content sites
        request?.let { permissionRequest ->
            val requestedResources = permissionRequest.resources
            
            // Allow camera/microphone for interactive adult content
            if (requestedResources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) ||
                requestedResources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                
                // Grant permission for adult content sites
                permissionRequest.grant(requestedResources)
            } else {
                permissionRequest.deny()
            }
        }
    }
    
    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        // Handle file uploads for adult content sites
        return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
    }
    
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?
    ): Boolean {
        // Handle popup windows (common on adult content sites)
        return true // Allow popups
    }
    
    override fun onJsAlert(
        view: WebView?,
        url: String?,
        message: String?,
        result: JsResult?
    ): Boolean {
        // Handle JavaScript alerts
        result?.confirm()
        return true
    }
}