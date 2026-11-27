package com.anonym.filmstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmStreamPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(VidmolyProvider())
        registerMainAPI(SiteTopluProvider())
    }
}
