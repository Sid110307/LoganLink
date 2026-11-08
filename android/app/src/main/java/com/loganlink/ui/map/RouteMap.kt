package com.loganlink.ui.map

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.loganlink.ui.theme.Ember
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@Composable
fun RouteMap(
    points: List<Pair<Double, Double>>,
    modifier: Modifier,
    marker: Pair<Double, Double>? = null,
    speeds: List<Double>? = null,
    markerAccuracyM: Float? = null,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bg = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
    val html = remember(points, marker, speeds, markerAccuracyM, dark) {
        buildHtml(points, marker, speeds, markerAccuracyM, dark, bg)
    }
    AndroidView(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
        factory = { ctx -> buildWebView(ctx).also { load(it, html) } },
        update = { w -> if (w.tag != html) load(w, html) },
        onRelease = { w -> runCatching { w.stopLoading(); w.destroy() } },
    )
}

private fun load(w: WebView, html: String) {
    w.tag = html
    w.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "utf-8", null)
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
private fun buildWebView(context: Context): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true

    settings.userAgentString = settings.userAgentString + " LoganLink (${context.packageName})"
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    setBackgroundColor(android.graphics.Color.TRANSPARENT)

    setOnTouchListener { v, e ->
        if (e.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
        false
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return true
        }
    }
}

private fun valid(lat: Double, lon: Double) =
    lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)

private fun cssColor(argb: Int) = String.format(Locale.US, "#%06X", argb and 0xFFFFFF)

private fun buildHtml(
    points: List<Pair<Double, Double>>, marker: Pair<Double, Double>?, speeds: List<Double>?,
    accuracy: Float?, dark: Boolean, bg: Int,
): String {
    val keep = points.indices.filter { valid(points[it].first, points[it].second) }
    val pts = JSONArray().apply { keep.forEach { i -> put(JSONArray().put(points[i].first).put(points[i].second)) } }
    val spd = if (speeds != null && speeds.size == points.size)
        JSONArray().apply { keep.forEach { i -> put(speeds[i].takeIf { it.isFinite() } ?: 0.0) } } else null
    val data = JSONObject().apply {
        put("pts", pts)
        put("spd", spd ?: JSONObject.NULL)
        put("marker", marker?.takeIf { valid(it.first, it.second) }?.let { JSONArray().put(it.first).put(it.second) } ?: JSONObject.NULL)
        put("acc", accuracy?.takeIf { it.isFinite() && it > 0f }?.toDouble() ?: JSONObject.NULL)
        put("dark", dark)
        put("accent", cssColor(Ember.toArgb()))
    }.toString()
    return TEMPLATE
        .replace("__BG__", cssColor(bg))
        .replace("__DATA__", scriptJson(data))
}

private fun scriptJson(value: String): String = value
    .replace("<", "\\u003c")
    .replace(" ", "\\u2028")
    .replace(" ", "\\u2029")

private val TEMPLATE = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="referrer" content="strict-origin-when-cross-origin"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" integrity="sha384-sHL9NAb7lN7rfvG5lfHpm643Xkcjzp4jFvuavGOndn6pjVqS6ny56CAt3nsEVT4H" crossorigin="anonymous"/>
<style>
  html, body { height: 100%; margin: 0; background: __BG__; }
  #map { position: fixed; inset: 0; background: __BG__; }
  .leaflet-control-attribution { font-size: 9px; }
  body.dark .leaflet-tile-pane { filter: invert(1) hue-rotate(180deg) brightness(.92) contrast(.9) saturate(.7); }
  body.dark .leaflet-control-attribution { background: rgba(20,22,28,.7); color: #9aa0a6; }
  body.dark .leaflet-control-attribution a { color: #c9d1d9; }
  .msg { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; text-align: center;
         font: 14px sans-serif; color: #8a8f98; z-index: 500; pointer-events: none; padding: 24px; }
  .car { width: 22px; height: 22px; border-radius: 50%; background: #FF7A3D; border: 3px solid #fff;
         box-shadow: 0 1px 6px rgba(0,0,0,.45); box-sizing: border-box; }
</style>
</head>
<body>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" integrity="sha384-cxOPjt7s7Iz04uaHJceBmS+qpjv2JkIHNVcuOrM+YHwZOmJGBXI00mdUXEq65HTH" crossorigin="anonymous"></script>
<script>
(function () {
  var D = __DATA__;
  if (D.dark) document.body.classList.add('dark');
  function msg(text) {
    var m = document.createElement('div'); m.className = 'msg'; m.textContent = text; document.body.appendChild(m);
  }
  if (typeof L === 'undefined') { msg('Map unavailable offline'); return; }
  var map = L.map('map', { zoomControl: false, attributionControl: true });
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map);

  var G = [53, 199, 89], A = [255, 159, 10], R = [255, 59, 78];
  function col(f) {
    f = Math.max(0, Math.min(1, f));
    var c0 = f < 0.5 ? G : A, c1 = f < 0.5 ? A : R, t = f < 0.5 ? f * 2 : (f - 0.5) * 2;
    return 'rgb(' + [0, 1, 2].map(function (i) { return Math.round(c0[i] + (c1[i] - c0[i]) * t); }).join(',') + ')';
  }

  var pts = D.pts || [], bounds = null;
  if (pts.length > 1) {
    var spd = D.spd;
    if (spd && spd.length === pts.length) {
      var lo = Infinity, hi = -Infinity;
      for (var k = 0; k < spd.length; k++) { if (spd[k] < lo) lo = spd[k]; if (spd[k] > hi) hi = spd[k]; }
      var span = Math.max(hi - lo, 1);
      for (var i = 1; i < pts.length; i++) {
        var f = ((spd[i - 1] + spd[i]) / 2 - lo) / span;
        L.polyline([pts[i - 1], pts[i]], { color: col(f), weight: 5, opacity: 0.95, lineCap: 'round', lineJoin: 'round' }).addTo(map);
      }
    } else {
      L.polyline(pts, { color: D.accent, weight: 5, opacity: 0.95, lineCap: 'round', lineJoin: 'round' }).addTo(map);
    }
    L.circleMarker(pts[0], { radius: 6, color: '#fff', weight: 2, fillColor: '#35C759', fillOpacity: 1 }).addTo(map);
    L.circleMarker(pts[pts.length - 1], { radius: 6, color: '#fff', weight: 2, fillColor: '#FF3B4E', fillOpacity: 1 }).addTo(map);
    bounds = L.latLngBounds(pts);
  }
  if (D.marker) {
    if (D.acc) L.circle(D.marker, { radius: D.acc, color: D.accent, weight: 1, fillColor: D.accent, fillOpacity: 0.12 }).addTo(map);
    L.marker(D.marker, { icon: L.divIcon({ className: '', html: '<div class="car"></div>', iconSize: [22, 22], iconAnchor: [11, 11] }) }).addTo(map);
    if (bounds) bounds.extend(D.marker);
  }
  if (bounds && bounds.isValid()) map.fitBounds(bounds, { padding: [28, 28], maxZoom: 17 });
  else if (D.marker) map.setView(D.marker, 17);
  else if (pts.length === 1) map.setView(pts[0], 16);
  else { map.setView([20.59, 78.96], 4); msg('No GPS route recorded'); }
})();
</script>
</body>
</html>
""".trimIndent()

fun openInMaps(context: Context, lat: Double, lon: Double, label: String) {
    val ll = String.format(Locale.US, "%.6f,%.6f", lat, lon)
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$ll&mode=w")).setPackage("com.google.android.apps.maps"),
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:$ll?q=$ll(${Uri.encode(label)})")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$ll&travelmode=walking")),
    )
    for (i in attempts) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (e: ActivityNotFoundException) {

        } catch (e: SecurityException) {

        }
    }
}
