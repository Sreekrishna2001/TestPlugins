package com.example

import android.util.Log
//import com.lagradost.cloudstream3.TvType
//import com.lagradost.cloudstream3.MainAPI
//import com.lagradost.cloudstream3.SearchResponse
//import com.lagradost.cloudstream3.HomePageResponse
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
//import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.nicehttp.RequestBodyTypes
//import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
//import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.helper.HttpConnection
//import kotlinx.serialization.json.Jso
import java.util.*
import okhttp3.Interceptor


class ExampleProvider(val plugin: TestPlugin) : MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://animerulz.to"
    override var name = "AnimeRulz Returns"
    override val supportedTypes = setOf(TvType.Anime)

    override var lang = "en"

    // enable this when your provider has a main page
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/telugudubbed/" to "TeluguDub",
        "$mainUrl/tamildubbed/" to "TamilDub",
        "$mainUrl/hindidubbed/" to "HindiDub",

    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data).document
        val home =
            document.select("#main > div.container-tr > div > div > div.new-dubbed-animes > div > div:nth-child(2) > a")
                .mapNotNull {
                    it.toSearchResult()
                }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.anime-content-a > div > div.title-a.medium-txt > h2")?.text() ?: return null
        val href = fixUrl(this.attr("href"))
        val posterUrl = fixUrlNull(this.select("div.image-div-a > img")?.attr("src"))
//        val quality = getQualityFromString(this.select("span.quality").text())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
//            this.quality = quality
        }
    }

    fun aniQuery(id:String): String {
        //creating query for Anilist API using ID
        return """
            query {
                Media(id: $id, type: ANIME) {
                    title {
                        romaji
                        english
                    }
                    description
                    coverImage {
					  large
					  medium
					}
                    id
                    genres
                    format
                    episodes
                    seasonYear
                }
            }
        """
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?search/?$query").document
        // this is not yet implemented the below return statement is just placeholder
        return document.select("div.ml-item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.substringAfterLast("-").dropLast(1)
//        Log.d("MyDoc","$id")
//        val title = url.substringAfter("https://animerulz.to/").substringBeforeLast("-")
//        Log.d("MyDoc","$url/Watch-Now/details.json")
        val res = app.post("https://graphql.anilist.co",
            headers = mapOf(
                "Accept"  to "application/json",
                "Content-Type" to "application/json",
            ),
            data = mapOf(
                "query" to aniQuery(id),
            )
        ).parsedSafe<SyncInfo>()
//        Log.d("MyDoc","${res?.data?.media}")
//        Log.d("MyDoc",url)
//        val detailsJson:String = URL("$url/Watch-Now/details.json").readText()
        val typeRef: TypeReference<Map<String, Root>> = object : TypeReference<Map<String, Root>>(){}
//        val dJson = app.get("$url/Watch-Now/details.json").parsedSafe<DetailsSync>()
        val detailsText = app.get("$url/Watch-Now/details.json").text
        Log.d("MyDoc",detailsText)
        val detailsJson = mapper.readValue(detailsText, typeRef)
        Log.d("MyDoc","$detailsJson")
        val episodesToAdd = arrayListOf<Episode>()
        detailsJson.forEach { (k, value) ->
            Log.d("MyDoc","${value}")
//            episodesToAdd.add(newEpisode(value?.source?.subbed?.vidCloud){
              episodesToAdd.add(newEpisode(value?.source?.otherLanguages?.multi?.getOrNull(0)?.link){
                this.episode= k.toIntOrNull()
                this.name = value.title
        })
        }
        val info = res?.data?.media
        return newAnimeLoadResponse(info?.title?.english ?: "No Title", url, TvType.Anime) {
            engName = info?.title?.english
            posterUrl = info?.coverImage?.large
            tags = info?.genres
            plot = info?.description
            addEpisodes(DubStatus.Subbed, episodesToAdd)
//            addEpisodes(DubStatus.Dubbed, episodes.second)
//            addActors(characters)
//            //this.recommendations = recommendations
//
//            showStatus = getStatus(showData.status.toString())
//            addMalId(trackers?.idMal)
//            addAniListId(trackers?.id)
//            plot = description?.replace(Regex("""<(.*?)>"""), "")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean{
        Log.d("MyDoc",data)
        val txt = app.get(data).text
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(txt ?:"")?.groupValues?.getOrNull(1).toString()
        Log.d("MyDoc",m3u8)
        val headrs = mapOf(
            "accept" to "*/*",
            "accept-language" to "en-US,en;q=0.6",
            "origin" to "https://rubystm.com",
            "sec-ch-ua" to "\"Google Chrome\";v=\"123\", \"Not:A-Brand\";v=\"8\", \"Chromium\";v=\"123\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "sec-fetch-dest" to "empty",
            "sec-fetch-mode" to "cors",
            "sec-fetch-site" to "cross-site",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )
          val url = "https://3uho6lzsf1c2o3i8oun9.streamruby.net/hls2/04/00076/k2n4k5y6ts7m_,l,n,h,.urlset/master.m3u8?t=OWD7MWZx8NLWxj3R9bqY3OwXbi7mYMhJzR5FF_84Kpw&s=1713523514&e=43200&f=380491&srv=127.0.0.1"
        callback.invoke(
            ExtractorLink(
                source = this.name,
                name = "RubyStream",
                url = m3u8,
                referer = "https://rubystm.com/",
                quality = Qualities.Unknown.value,
                isM3u8 = true,
                headers = headrs,
            )
        )
        return true
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                // Change user agent here
                val request = chain.request().newBuilder()
                                .removeHeader("User-Agent")
                                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .build()
                val response = chain.proceed(request)

                return response
            }
        }
    }
    //JSON formatter for data fetched from anilistApi
    data class SyncTitle(
        @JsonProperty("romaji") val romaji: String? = null,
        @JsonProperty("english") val english: String? = null,
    )

    data class  CoverImage(
        @JsonProperty("large") val large: String? = null,
        @JsonProperty("medium") val medium: String? = null,
    )
    data class Media(
        @JsonProperty("title") val title: SyncTitle? = null,
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("episodes") val episodes: Int,
//        @JsonProperty("season") val season: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("genres") val genres: List<String>? = null,
        @JsonProperty("format") val format: String? = null,
        @JsonProperty("coverImage") val coverImage: CoverImage? = null,
        @JsonProperty("seasonYear") val year: Int? = null,
    )

    data class Data(
        @JsonProperty("Media") val media: Media? = null,
    )

    @Serializable
    data class Root(
        val intro: Intro? =null,
        val outro: Outro? =null,
        val title: String? =null,
        val source: Source? = null,
        val downloadLink: DownloadLink? =null,
    )
    data class Intro(
        val end: Long? =null,
        val start: Long? =null,
    )

    data class SyncInfo(
        @JsonProperty("data") val data: Data? = null,
    )

    data class Outro(
        val end: Long? =null,
        val start: Long? =null,
    )

    data class Source(
        @JsonProperty("DUBBED")
        val dubbed: Dubbed? =null,
        @JsonProperty("SUBBED")
        val subbed: Subbed? = null,
        @JsonProperty("OTHER LANGUAGES")
        val otherLanguages: OtherLanguages? =null,
    )

    data class Dubbed(
        @JsonProperty("Vid Cloud")
        val vidCloud: String? =null,
    )

    data class Subbed(
        @JsonProperty("Vid Cloud")
        val vidCloud: String? =null,
    )

    data class OtherLanguages(
        @JsonProperty("Hindi")
        val hindi: String? =null,
        @JsonProperty("Multi")
        val multi: List<Multi>? =null,
        @JsonProperty("Tamil")
        val tamil: String? =null,
        @JsonProperty("Telugu")
        val telugu: String? =null,
    )

    data class Multi(
        @JsonProperty("Link")
        val link: String? =null,
        val audios: List<String>? =null,
    )

    data class Caption(
        val url: String? =null,
        val lang: String? =null,
    )

    data class DownloadLink(
        @JsonProperty("SUBBED")
        val subbed: String? = null,
    )

}