version = 1

cloudstream {
    description = "Film/dizi: vidmoly (filmdozu + lovefilmizle) ve 142 site deneyicisi"
    authors = listOf("anonym")
    status = 3 // beta
    tvTypes = listOf("Movie", "TvSeries")
    requiresResources = false
    language = "tr"
    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
