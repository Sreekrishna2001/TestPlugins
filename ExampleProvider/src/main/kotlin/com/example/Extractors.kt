package com.example

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked

class Rubystream : Streamruby() {
    override val name = "Rubystream"
    override var mainUrl = "https://rubystream.xyz"
}

class Stmruby : Streamruby() {
    override var mainUrl = "https://stmruby.com"
    override val name = "Stmruby"
}

class Rubystm : Streamruby() {
    override var mainUrl = "https://rubystm.com"
    override val name = "Rubystm"
}

open class Streamruby : ExtractorApi() {
    override val name = "Streamruby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = "/e/(\\w+)".toRegex().find(getEmbedUrl(url))?.groupValues?.get(1) ?: return
        val response = app.post("$mainUrl/dl", data = mapOf(
            "op" to "embed",
            "file_code" to id,
            "auto" to "1",
            "referer" to "",
        ), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        M3u8Helper.generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl
        ).forEach(callback)
    }

    private fun getEmbedUrl(url: String): String {
        if (url.contains("/embed-")) {
            val id1 = url.substringAfter("/embed-")
            return "$mainUrl/e/$id1"
        }
        return url
    }

}