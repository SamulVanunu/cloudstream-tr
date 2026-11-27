package com.anonym.filmstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

/** filmdozu.com + lovefilmizle.net -> vidmoly embed.
 *  Baglantilar cihaz IP'sinde cozulur; token baska IP'de calismadigi icin
 *  URL saklanmaz, her oynatmada embed sayfasi yeniden okunur. */
class VidmolyProvider : MainAPI() {
    override var mainUrl = "https://lovefilmizle.net"
    override var name = "Filmdozu + Lovefilm (Vidmoly)"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "tr"
    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "film" to "Filmler",
        "dizi" to "Diziler"
    )

    private val embedRegex = Regex("""sources:\s*\[\s*\{\s*file:\s*['"]([^'"]+\.m3u8[^'"]*)['"]""")
    private val adTemizle = Regex("""\s+izle\s+(Full HD|HD|4K).*$""", RegexOption.IGNORE_CASE)

    private fun baslik(e: VidmolyEntry) = e.ad.replace(adTemizle, "").trim().ifBlank { e.ad }

    private fun VidmolyEntry.toSearch(): MovieSearchResponse =
        newMovieSearchResponse(baslik(this), embed, TvType.Movie)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val filtre = VidmolyData.entries.filter { e ->
            val dizi = baslik(e).contains(Regex("""\bB[oö]l[uü]m\b|S\d+E\d+""", RegexOption.IGNORE_CASE))
            if (request.data == "dizi") dizi else !dizi
        }
        val sonuc = filtre.chunked(24).getOrNull((page - 1).coerceAtLeast(0)).orEmpty()
        return newHomePageResponse(request.name, sonuc.map { it.toSearch() })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return VidmolyData.entries.filter { baslik(it).contains(q, true) }
            .take(60).map { it.toSearch() }
    }

    override suspend fun load(url: String): LoadResponse {
        val entry = VidmolyData.entries.firstOrNull { it.embed == url }
        val ad = entry?.let { baslik(it) }
            ?: url.substringAfterLast("/").removeSuffix(".html")
        return newMovieLoadResponse(ad, url, TvType.Movie, url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = try {
            app.get(data).text
        } catch (e: Exception) {
            return false
        }
        val m3u8 = embedRegex.find(html)?.groupValues?.get(1) ?: return false
        callback(newExtractorLink(name, "Vidmoly", m3u8, type = ExtractorLinkType.M3U8) {
            this.quality = 1080
            this.referer = data
        })
        return true
    }
}
