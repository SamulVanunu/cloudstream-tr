package com.anonym.filmstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

/** Kullanıcının verdiği 142 domain için ortak deneyici provider.
 *  Ölü / ISP engelli / Cloudflare 403 siteler boş sonuç veya hata verir;
 *  çalışan sitelerde doğrudan m3u8/mp4 veya vidmoly embed aranır. */
class SiteTopluProvider : MainAPI() {
    override var mainUrl = "https://filmdozu.com"
    override var name = "Toplu Siteler (142)"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "tr"
    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "ok" to "Açık siteler",
        "all" to "Tüm 142 site",
        "dead" to "Ölü / engelli"
    )

    private val adult = Regex(
        """18\+|\bXXX\b|\bPORN\b|\bEROTIK\b|\bADULT\b|\bYETIŞKİN\b|\bSEKS\b""",
        RegexOption.IGNORE_CASE
    )
    private val blocked = Regex(
        """195\.175\.254\.2|erişim engeli|access denied|attention required|captcha""",
        RegexOption.IGNORE_CASE
    )
    private val videoUrl = Regex("""https?://[^"'\s<>]+\.(?:m3u8|mp4)(?:\?[^"'\s<>]*)?""", RegexOption.IGNORE_CASE)
    private val embedId = Regex("""embed-([a-z0-9]{8,})""", RegexOption.IGNORE_CASE)

    private fun filtre(mod: String) = when (mod) {
        "ok" -> SiteData.sites.filter { it.status == "ACIK" }
        "dead" -> SiteData.sites.filter { it.status != "ACIK" }
        else -> SiteData.sites
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val liste = filtre(request.data).chunked(24)
            .getOrNull((page - 1).coerceAtLeast(0)).orEmpty()
            .map { site ->
                newMovieSearchResponse(site.domain, "site:${site.domain}", TvType.Movie)
            }
        return newHomePageResponse(request.name, liste)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.startsWith("site:", true)) {
            val domain = q.substring(5).trim().substringBefore(" ")
            return SiteData.sites.filter { it.domain.contains(domain, true) }.map {
                newMovieSearchResponse(it.domain, "site:${it.domain}", TvType.Movie)
            }
        }
        if (q.length < 2) return emptyList()
        return SiteData.sites.filter { it.domain.contains(q, true) || it.note.contains(q, true) }
            .take(40).map { newMovieSearchResponse(it.domain, "site:${it.domain}", TvType.Movie) }
    }

    override suspend fun load(url: String): LoadResponse {
        val domain = url.removePrefix("site:")
        return newMovieLoadResponse(domain, url, TvType.Movie, "https://$domain/")
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
        if (blocked.containsMatchIn(html) || adult.containsMatchIn(html.take(20000))) return false

        val id = embedId.find(html)?.groupValues?.get(1)
        if (id != null) {
            val embed = try {
                app.get("https://vidmoly.net/embed-$id.html").text
            } catch (e: Exception) {
                return false
            }
            val m3u8 = Regex("""sources:\s*\[\s*\{\s*file:\s*['"]([^'"]+\.m3u8[^'"]*)['"]""")
                .find(embed)?.groupValues?.get(1)
            if (m3u8 != null) {
                callback(newExtractorLink(name, "Vidmoly", m3u8, type = ExtractorLinkType.M3U8) {
                    this.quality = 1080
                    this.referer = "https://vidmoly.net/embed-$id.html"
                })
                return true
            }
        }

        val direct = videoUrl.find(html)?.value ?: return false
        callback(newExtractorLink(name, "Direkt", direct, type = ExtractorLinkType.M3U8) {
            this.quality = 720
            this.referer = data
        })
        return true
    }
}
