package com.github.senocak.jobscheduler.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.thread.Threading
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import org.springframework.web.filter.OncePerRequestFilter
import java.net.http.HttpClient
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Configuration
class SchedulingConfig(
    private val restClientInterceptor: RestClientInterceptor
) : OncePerRequestFilter() {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val t: Thread = Thread.currentThread()
        log.info("Thread: ${t.name}, isVirtual: ${if (t.isVirtual) Threading.VIRTUAL else Threading.PLATFORM}, class: ${t.javaClass}")
        filterChain.doFilter(request, response)
    }

    @Bean
    fun taskScheduler(): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 10
        scheduler.setThreadNamePrefix("job-scheduler-")
        scheduler.initialize()
        return scheduler
    }

    @Bean
    fun scheduledExecutorService(): ScheduledExecutorService = Executors.newScheduledThreadPool(10)

    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()

    /**
     * Creates a `RestClient` bean using the provided `RestTemplate`.
     *
     * @param restTemplate the `RestTemplate` to be used by the `RestClient`
     * @return a configured `RestClient` instance
     */
    @Bean
    @Primary
    @Profile("!local")
    @ConditionalOnMissingBean(RestClient::class)
    fun getRestClient(restTemplate: RestTemplate): RestClient =
        RestClient.builder(restTemplate).requestInterceptor(restClientInterceptor).build()

    /**
     * Creates a `RestClient` bean using the provided `RestTemplate` that disables SSL certificate validation.
     *
     * @param  restTemplate the `RestTemplate` to be used by the `RestClient`
     * @return  a configured `RestClient` instance
     */
    @Profile("local")
    @Bean(name = ["restClientSslDisable"])
    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    fun getRestClientSslDisable(restTemplate: RestTemplate): RestClient {
        // 1. Create a trust manager that does not validate certificate chains
        val trustAllCerts: Array<TrustManager> = arrayOf(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate?>? {
                return null
            }
            override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
            override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
        })

        // 2. Set up the SSLContext with the trust-all manager
        val sslContext: SSLContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        // 3. Build the JDK HttpClient
        val httpClient = HttpClient.newBuilder()
            .sslContext(sslContext)
            .build()

        // 4. Plug it into RestClient via the RequestFactory
        return RestClient
            .builder(restTemplate)
            .requestFactory(JdkClientHttpRequestFactory(httpClient))
            .requestInterceptor(restClientInterceptor)
            .build()
    }
}
