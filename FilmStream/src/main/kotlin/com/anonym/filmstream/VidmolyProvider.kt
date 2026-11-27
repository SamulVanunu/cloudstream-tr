package com.anonym.filmstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 *Filmdozu / Lovefilmizle - CloudStream 3 provider
 *
 * ADIM 1'de gercek HTML'den dogrulanan yapilar:
 *  - kart: div.poster-container.moviebox-item (lovefilmizle), baslik a[title],
 *    poster img[data-src] / img[src]
 *  - arama: /?s=... , sayfalama: /page/N/
 *  - detay: h1.movie-title, meta[property=og:image], meta[name=description]
 *  - oynatma: iframe[data-litespeed-src] = /bemoly/bemoly.php?url=<vidmoly embed>
 *    filmdozu detay: button.src-btn onclick="loadSource('<embed>')"
 *
 * Kaynak URL'leri saklanmaz; oynatma aninda cihaz IP'sinden cozulur (token IP'ye bagli).
 */
class VidmolyProvider : MainAPI() {
    override var mainUrl = "https://lovefilmizle.net"
    override var name = "Film + Dizi (TR)"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "tr"
    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "$mainUrl/yerli-film/" to "Yerli Filmler",
        "$mainUrl/turkce-dublaj/" to "Türkçe Dublaj",
        "$mainUrl/turkce-altyazili/" to "Türkçe Altyazılı"
    )

    private val embedRegex = Regex("""(?:https?:)?//[^\s"']*?(?:vidmoly\.net/embed-[a-z0-9]+|ok\.ru/videoembed/\d+|dailymotion\.com/embed/video/[a-z0-9]+)[^\s"'<]*""", RegexOption.IGNORE_CASE)
    private val m3u8Regex = Regex("""sources:\s*\[\s*\{\s*file:\s*['"]([^'"]+\.m3u8[^'"]*)['"]""")
    private val yilRegex = Regex("""\((19\d{2}|20\d{2})\)""")

    private fun Element.toSearch(): MovieSearchResponse? {
        val a = selectFirst("div.poster a") ?: return null
        val baslik = a.attr("title").ifBlank { a.text() }.trim()
        if (baslik.isBlank()) return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val img = selectFirst("div.poster img")
        val poster = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null }
                ?: img?.attr("data-lazy-src")?.ifBlank { null }
                ?: img?.attr("src")
        )
        return newMovieSearchResponse(baslik, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val adres = if (page <= 1) request.data else "${request.data}page/$page/"
        val document = app.get(adres).document
        val liste = document.select("div.poster-container.moviebox-item").mapNotNull { it.toSearch() }
        return newHomePageResponse(request.name, liste)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val document = app.get("$mainUrl?s=${URLEncoder.encode(q, "UTF-8")}").document
        return document.select("div.poster-container.moviebox-item").mapNotNull { it.toSearch() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val baslik = document.selectFirst("h1.movie-title")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
                ?: document.selectFirst("h1")?.parent()?.selectFirst("img")?.attr("data-src")
        )
        val plot = document.selectFirst("meta[name=description]")?.attr("content")?.trim()
        val yil = yilRegex.find(baslik)?.groupValues?.get(1)?.toIntOrNull()
        val temizBaslik = baslik.replace(yilRegex, "").trim()

        // Oynatma kaynagi: lovefilmizle iframe data-litespeed-src veya filmdozu src-btn
        val iframe = document.selectFirst("iframe[data-litespeed-src]")?.attr("data-litespeed-src")
        val srcBtn = document.selectFirst("button.src-btn")?.attr("onclick")
        val kaynak = listOfNotNull(iframe, srcBtn).joinToString(" ")
        val embed = embedRegex.find(kaynak)?.value?.replace("&amp;", "&") ?: url

        return newMovieLoadResponse(temizBaslik, url, TvType.Movie, embed) {
            this.posterUrl = poster
            this.year = yil
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val embed = fixUrl(data)
        // vidmoly: embed HTML icinden m3u8 (olcum: sources:[{file:'...m3u8?t=...&e=43200'}])
        if (embed.contains("vidmoly", true)) {
            val html = try { app.get(embed).text } catch (e: Exception) { return false }
            val m3u8 = m3u8Regex.find(html)?.groupValues?.get(1)?.replace("\\/", "/") ?: return false
            callback(newExtractorLink(name, "Vidmoly", fixUrl(m3u8), type = ExtractorLinkType.M3U8) {
                this.quality = 1080
                this.referer = embed
            })
            return true
        }
        // diger iframe/embed kaynaklari icin CloudStream extractor'lari
        loadExtractor(embed, mainUrl, subtitleCallback, callback)
        return true
    }
}
