package com.ablematch.able.job

import com.ablematch.able.ai.OpenAiClient
import com.ablematch.able.maps.JobBoardDto
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium.By
import org.openqa.selenium.UnhandledAlertException
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/api/jobs")
class JobController(
    private val webScrapingClient: WebScrapingClient,
    private val seleniumScrapingClient: SeleniumScrapingClient,
    private val openAiClient: OpenAiClient,
    private val jobRepository: JobRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun scrapeAndUpsert(url: String, fallbackCompany: String) {
        val normalized = normalizeUrl(url)
        val hash = hashUrl(normalized)

        val existing = jobRepository.findBySourceHash(hash)
        if (existing != null &&
            existing.lastFetchedAt.isAfter(Instant.now().minus(24, ChronoUnit.HOURS))
        ) return

        val job = existing ?: Job.create(hash, normalized)

        val meta = webScrapingClient.scrapeWanted(url)

        val ai = openAiClient.extractJobStructured(
            """
TITLE: ${meta.title ?: ""}
COMPANY: ${meta.company ?: ""}
DESCRIPTION:
${meta.bodyText}
""".trimIndent()
        )

        job.title =
            meta.title?.takeIf { it.isNotBlank() }
                ?: ai.title

        job.company =
            meta.company?.takeIf { it.isNotBlank() }
                ?: ai.company.ifBlank { fallbackCompany }

        job.requiredSkills = ai.requiredSkills.toSet()
        job.accessibilityOptions = ai.accessibilityOptions
        job.workType = ai.workType
        job.lastFetchedAt = Instant.now()

        jobRepository.save(job)
    }


    @PostMapping("/scrape")
    fun scrapeAndCreate(@RequestBody req: JobScrapeRequest): Job {
        val normalized = normalizeUrl(req.url)
        val hash = hashUrl(normalized)

        val raw = try {
            webScrapingClient.scrapeJobDescription(req.url)
        } catch (e: Exception) {
            seleniumScrapingClient.scrape(req.url)
        }

        require(raw.length >= 100) { "Scraped content too short" }

        val job = jobRepository.findBySourceHash(hash)
            ?: Job.create(hash, normalized)

        val meta = webScrapingClient.scrapeWanted(req.url)

        val ai = openAiClient.extractJobStructured(
            """
TITLE: ${meta.title ?: ""}
COMPANY: ${meta.company ?: ""}
DESCRIPTION:
${meta.bodyText}
""".trimIndent()
        )

        job.title =
            meta.title?.takeIf { it.isNotBlank() }
                ?: ai.title

        job.company =
            meta.company?.takeIf { it.isNotBlank() }
                ?: ai.company.ifBlank { req.company }

        job.requiredSkills = ai.requiredSkills.toSet()
        job.accessibilityOptions = ai.accessibilityOptions
        job.workType = ai.workType
        job.lastFetchedAt = Instant.now()


        return jobRepository.save(job)
    }
}

data class JobScrapeRequest(
    val company: String,
    val url: String
)


private val ALLOWED_HOSTS = setOf(
    "saramin.co.kr", "www.saramin.co.kr",
    "jobkorea.co.kr", "www.jobkorea.co.kr",
    "wanted.co.kr", "www.wanted.co.kr",
    "catch.co.kr", "www.catch.co.kr"
)

@Component
class WebScrapingClient {
    fun scrapeJobDescription(url: String): String {
        val uri = URI(url)
        require(uri.host in ALLOWED_HOSTS)

        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0")
            .timeout(10_000)
            .get()

        return extractMainText(uri.host, doc)
    }

    private fun extractMainText(host: String, doc: Document): String =
        when {
            host.contains("saramin") ->
                doc.select("div.job-description, div.user-content").text()

            host.contains("jobkorea") ->
                doc.select("div.tbDetail, div.job_description").text()

            host.contains("wanted") ->
                doc.select(
                    "header, div.JobHeader, div.JobDescription"
                ).text()

            host.contains("catch") ->
                doc.select("div.view_cont").text()

            else -> doc.body().text()
        }

    data class WantedJobMeta(
        val title: String?,
        val company: String?,
        val bodyText: String
    )

    fun scrapeWanted(url: String): WantedJobMeta {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0")
            .timeout(10_000)
            .get()


        val title = doc.selectFirst("header h1")?.text()
        val company = doc.selectFirst("header a[href*=\"company\"]")?.text()
            ?: doc.selectFirst("header span")?.text()

        val body = doc.select("div.JobDescription").text()

        return WantedJobMeta(title, company, body)
    }

}
@Component
class SeleniumScrapingClient {
    fun scrape(url: String): String {
        val options = ChromeOptions().apply {
            addArguments(
                "--headless=new",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage"
            )
        }
        val driver = ChromeDriver(options)
        return try {
            driver.get(url)
            Thread.sleep(3000)

            try {
                driver.switchTo().alert().accept()
                return ""
            } catch (_: Exception) {}

            driver.findElement(By.tagName("body")).text
        } catch (e: UnhandledAlertException) {
            ""
        } finally {
            driver.quit()
        }
    }
}


@Component
class JobBatchScheduler(
    private val jobSourceRepo: JobSourceRepository,
    private val wantedListFetcher: WantedListFetcher,
    private val jobController: JobController,
    private val jobRepository: JobRepository
) {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)
    @Scheduled(
        initialDelay = 0,
        fixedDelay = 10 * 60 * 1000
    )
    fun refreshJobs() {
        log.info("[SCHEDULER] refreshJobs triggered")

        val sources = jobSourceRepo.findAllByActiveTrue()
        log.info("[SCHEDULER] active sources size={}", sources.size)

        sources.forEach { source ->
            log.info(
                "[SCHEDULER] source id={}, platform={}, company={}",
                source.id, source.platform, source.company
            )

            when (source.platform) {
                JobPlatform.WANTED -> {
                    log.info("[SCHEDULER] entering WANTED branch")
                    val urls = wantedListFetcher.fetchAliveWantedJobs(source)
                    log.info("[WANTED] fetched {} urls", urls.size)

                    urls.forEach { url ->
                        log.info("[SCRAPE] start url={}", url)
                        jobController.scrapeAndUpsert(url, source.company)
                    }

                    if (urls.isNotEmpty()) {
                        val deleted = jobRepository.deleteDeadJobs(urls)
                        log.info("[WANTED] deleted {} dead jobs", deleted)
                    } else {
                        log.warn("[WANTED] urls empty → skip deleteDeadJobs()")
                    }
                }
                else -> log.info("[SCHEDULER] unsupported platform={}", source.platform)
            }
        }
    }
}

@Component
class WantedListFetcher(
    private val restTemplate: RestTemplate
) {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)
    private val PAGE_SIZE = 20
    private val MAX_PAGES = 50

    fun fetchAliveWantedJobs(source: JobSource): List<String> {
        val ids = mutableListOf<Int>()
        var offset = 0
        var page = 0

        while (true) {
            log.info("[WANTED] calling api offset={}", offset)

            val res = try {
                callWantedApi(source.listUrl, offset)
            } catch (e: Exception) {
                log.error("[WANTED] api call failed offset={}", offset, e)
                break
            }

            val size = res.data.size
            log.info("[WANTED] received {} items", size)

            if (size == 0) {
                log.info("[WANTED] empty page → stop")
                break
            }

            ids += res.data.map { it.id }

            if (size < PAGE_SIZE) {
                log.info("[WANTED] last page detected (size < PAGE_SIZE)")
                break
            }

            offset += PAGE_SIZE
            page++

            // 🛡 안전장치 (무한루프 방지)
            if (page >= MAX_PAGES) {
                log.warn("[WANTED] reached MAX_PAGES={}, force stop", MAX_PAGES)
                break
            }
        }

        return ids.map { "https://www.wanted.co.kr/wd/$it" }
    }
    private fun callWantedApi(listUrl: String, offset: Int): WantedApiResponse {
        val headers = org.springframework.http.HttpHeaders().apply {
            set("User-Agent", "Mozilla/5.0")
            set("Accept", "application/json")
            set("Referer", "https://www.wanted.co.kr/")
        }

        val entity = HttpEntity<Void>(headers)

        val url = "$listUrl&offset=$offset&limit=$PAGE_SIZE"
        log.info("[WANTED] request url={}", url)

        val response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            entity,
            WantedApiResponse::class.java
        )



        return response.body
            ?: throw IllegalStateException("Wanted API returned null body")
    }

}




fun normalizeUrl(url: String): String {
    val uri = URI(url)
    return URI(uri.scheme, uri.host, uri.path, null).toString()
}

fun hashUrl(url: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(normalizeUrl(url).toByteArray())
        .joinToString("") { "%02x".format(it) }

@Configuration
class RestTemplateConfig {

    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}

fun inferSkillsFromText(text: String): Set<String> =
    listOf(
        "Communication",
        "Teamwork",
        "Problem Solving",
        "Time Management"
    ).filter { text.contains(it, ignoreCase = true) }.toSet()

@RestController
@RequestMapping("/api/jobs/board")
class JobBoardController(
    private val jobRepo: JobRepository
) {

    @GetMapping
    fun board(
        @RequestParam(defaultValue = "latest") sort: String
    ): List<JobBoardDto> {

        val jobs = jobRepo.findAllForBoard()

        val sorted = when (sort) {
            "popular" -> jobs.sortedByDescending { it.viewCount }
            "likes" -> jobs.sortedByDescending { it.likeCount }
            "company" -> jobs.sortedBy { it.company }
            else -> jobs.sortedByDescending { it.lastFetchedAt }
        }

        return sorted.map {
            JobBoardDto(
                id = it.id!!,
                title = it.title,
                company = it.company,
                workType = it.workType,
                sourceUrl = it.sourceUrl,
                viewCount = it.viewCount,
                likeCount = it.likeCount
            )
        }
    }

    @PostMapping("/{id}/view")
    fun incrementView(@PathVariable id: UUID) {
        val job = jobRepo.findById(id).orElseThrow()
        job.viewCount += 1
        jobRepo.save(job)
    }

}
