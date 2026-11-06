package com.phonefarm.app.data

/**
 * Curated, real endpoints. Every entry here is a live public URL that the Live tab probes with a
 * genuine HTTP request before you add it to the farm — nothing on this list is a placeholder.
 */
data class Preset(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val note: String,
    val mode: NodeMode = NodeMode.HTTP,
    val intervalSec: Int = 300,
)

object Presets {

    val all: List<Preset> = listOf(
        Preset(
            id = "example",
            name = "Example Domain",
            url = "https://example.com/",
            category = "BASELINE",
            note = "Smallest possible HTML page · ideal keep-alive target",
        ),
        Preset(
            id = "httpbin",
            name = "HTTPBin Headers",
            url = "https://httpbin.org/headers",
            category = "ECHO",
            note = "Echoes the request headers the node sends",
        ),
        Preset(
            id = "github-rate",
            name = "GitHub API",
            url = "https://api.github.com/rate_limit",
            category = "API",
            note = "Public JSON endpoint with rate-limit state",
        ),
        Preset(
            id = "hn",
            name = "Hacker News",
            url = "https://news.ycombinator.com/",
            category = "NEWS",
            note = "Very light HTML, good for hundreds of nodes",
        ),
        Preset(
            id = "hn-api",
            name = "HN Top Stories",
            url = "https://hacker-news.firebaseio.com/v0/topstories.json",
            category = "API",
            note = "JSON list endpoint",
        ),
        Preset(
            id = "wikipedia",
            name = "Wikipedia",
            url = "https://en.wikipedia.org/wiki/Main_Page",
            category = "REFERENCE",
            note = "Full page · use SESSION mode to render it",
            mode = NodeMode.WEBVIEW,
            intervalSec = 600,
        ),
        Preset(
            id = "cloudflare-status",
            name = "Cloudflare Status",
            url = "https://www.cloudflarestatus.com/",
            category = "STATUS",
            note = "Status page · renders in SESSION mode",
            mode = NodeMode.WEBVIEW,
        ),
        Preset(
            id = "github-status",
            name = "GitHub Status",
            url = "https://www.githubstatus.com/",
            category = "STATUS",
            note = "Status page · renders in SESSION mode",
            mode = NodeMode.WEBVIEW,
        ),
        Preset(
            id = "open-meteo",
            name = "Open-Meteo",
            url = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m",
            category = "API",
            note = "Keyless weather JSON",
            intervalSec = 900,
        ),
        Preset(
            id = "dummyjson",
            name = "DummyJSON",
            url = "https://dummyjson.com/products/1",
            category = "API",
            note = "Keyless JSON sample data",
        ),
        Preset(
            id = "picsum",
            name = "Lorem Picsum",
            url = "https://picsum.photos/600/400",
            category = "MEDIA",
            note = "Image endpoint · exercises real payloads",
            mode = NodeMode.WEBVIEW,
        ),
        Preset(
            id = "osm",
            name = "OpenStreetMap",
            url = "https://www.openstreetmap.org/",
            category = "MAPS",
            note = "Heavy tile page · watch thermal throttling",
            mode = NodeMode.WEBVIEW,
            intervalSec = 900,
        ),
        Preset(
            id = "rfc",
            name = "IETF Datatracker",
            url = "https://datatracker.ietf.org/",
            category = "REFERENCE",
            note = "Server-rendered HTML",
            mode = NodeMode.WEBVIEW,
            intervalSec = 900,
        ),
    )
}
