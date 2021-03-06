package brs.api.http

import brs.entity.DependencyProvider
import brs.objects.Props
import brs.util.Subnet
import brs.util.jetty.InverseExistsOrRewriteRegexRule
import brs.util.logging.safeError
import brs.util.logging.safeInfo
import org.eclipse.jetty.rewrite.handler.RewriteHandler
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.HandlerList
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.servlets.DoSFilter
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.net.UnknownHostException

class ApiServerImpl(dp: DependencyProvider) : ApiServer {
    private val apiServer: Server?

    init {
        val allowedBotHostsList = dp.propertyService.get(Props.API_ALLOWED)
        val allowedBotHosts = if (allowedBotHostsList.contains("*")) null else {
            allowedBotHostsList.mapNotNullTo(mutableSetOf<Subnet>()) {
                try {
                    Subnet.createInstance(it)
                } catch (e: UnknownHostException) {
                    logger.safeError(e) { "Error adding allowed host/subnet '$it'" }
                    null
                }
            }
        }

        val enableAPIServer = dp.propertyService.get(Props.API_SERVER)
        if (enableAPIServer) {
            val host = dp.propertyService.get(Props.API_LISTEN)
            val port =
                if (dp.propertyService.get(Props.DEV_TESTNET)) dp.propertyService.get(Props.DEV_API_PORT) else dp.propertyService.get(
                    Props.API_PORT
                )
            apiServer = Server()
            val connector: ServerConnector

            val enableSSL = dp.propertyService.get(Props.API_SSL)
            if (enableSSL) {
                logger.safeInfo { "Using SSL (https) for the API server" }
                val httpsConfig = HttpConfiguration()
                httpsConfig.secureScheme = "https"
                httpsConfig.securePort = port
                httpsConfig.addCustomizer(SecureRequestCustomizer())
                val sslContextFactory = SslContextFactory.Server()
                sslContextFactory.keyStorePath = dp.propertyService.get(Props.API_SSL_KEY_STORE_PATH)
                sslContextFactory.setKeyStorePassword(dp.propertyService.get(Props.API_SSL_KEY_STORE_PASSWORD))
                sslContextFactory.setExcludeCipherSuites(
                    "SSL_RSA_WITH_DES_CBC_SHA",
                    "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                    "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                    "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                    "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA"
                )
                sslContextFactory.setExcludeProtocols("SSLv3")
                connector = ServerConnector(
                    apiServer, SslConnectionFactory(sslContextFactory, "http/1.1"),
                    HttpConnectionFactory(httpsConfig)
                )
            } else {
                connector = ServerConnector(apiServer)
            }

            connector.host = host
            connector.port = port
            connector.idleTimeout = dp.propertyService.get(Props.API_SERVER_IDLE_TIMEOUT).toLong()
            // defaultProtocol
            // stopTimeout
            // acceptQueueSize
            connector.reuseAddress = true
            // soLingerTime
            apiServer.addConnector(connector)

            val apiHandlers = HandlerList()

            val apiHandler = ServletContextHandler()
            val apiResourceBase = dp.propertyService.get(Props.API_UI_DIR)
            if (apiResourceBase.isNotEmpty()) {
                val defaultServletHolder = ServletHolder(DefaultServlet())
                defaultServletHolder.setInitParameter("resourceBase", apiResourceBase)
                defaultServletHolder.setInitParameter("dirAllowed", "false")
                defaultServletHolder.setInitParameter("welcomeServlets", "true")
                defaultServletHolder.setInitParameter("redirectWelcome", "true")
                defaultServletHolder.setInitParameter("gzip", "true")
                apiHandler.addServlet(defaultServletHolder, "/*")
                apiHandler.welcomeFiles = arrayOf("index.html")
            }

            val apiServlet = APIServlet(dp, allowedBotHosts)
            val apiServletHolder = ServletHolder(apiServlet)
            apiHandler.addServlet(apiServletHolder, API_PATH)

            if (dp.propertyService.get(Props.JETTY_API_DOS_FILTER)) {
                val dosFilterHolder = apiHandler.addFilter(DoSFilter::class.java, API_PATH, null)
                dosFilterHolder.setInitParameter(
                    "maxRequestsPerSec",
                    dp.propertyService.get(Props.JETTY_API_DOS_FILTER_MAX_REQUEST_PER_SEC)
                )
                dosFilterHolder.setInitParameter(
                    "throttledRequests",
                    dp.propertyService.get(Props.JETTY_API_DOS_FILTER_THROTTLED_REQUESTS)
                )
                dosFilterHolder.setInitParameter("delayMs", dp.propertyService.get(Props.JETTY_API_DOS_FILTER_DELAY_MS))
                dosFilterHolder.setInitParameter(
                    "maxWaitMs",
                    dp.propertyService.get(Props.JETTY_API_DOS_FILTER_MAX_WAIT_MS)
                )
                dosFilterHolder.setInitParameter(
                    "maxRequestMs",
                    dp.propertyService.get(Props.JETTY_API_DOS_FILTER_MAX_REQUEST_MS)
                )
                dosFilterHolder.setInitParameter(
                    "maxthrottleMs",
                    dp.propertyService.get(Props.JETTY_API_DOS_FILTER_THROTTLE_MS)
                )
                dosFilterHolder.setInitParameter(
                    "maxIdleTrackerMs",
                    dp.propertyService.get(Props.JETTY_API_DOS_FILTER_MAX_IDLE_TRACKER_MS)
                )
                dosFilterHolder.setInitParameter(
                    "trackSessions",
                    dp.propertyService.get(Props.JETTY_API_DOS_FILTER_TRACK_SESSIONS)
                )
                dosFilterHolder.setInitParameter(
                    "insertHeaders",
                    dp.propertyService.get(Props.JETTY_API_DOS_FILTER_INSERT_HEADERS)
                )
                dosFilterHolder.setInitParameter(
                    "remotePort",
                    dp.propertyService.get(Props.JETTY_API_DOS_FILTER_REMOTE_PORT)
                )
                dosFilterHolder.setInitParameter(
                    "ipWhitelist",
                    dp.propertyService.get(Props.JETTY_API_DOS_FILTER_IP_WHITELIST)
                )
                dosFilterHolder.setInitParameter(
                    "managedAttr",
                    dp.propertyService.get(Props.JETTY_API_DOS_FILTER_MANAGED_ATTR)
                )
                dosFilterHolder.isAsyncSupported = true
            }

            apiHandler.addServlet(ServletHolder(APITestServlet(apiServlet, allowedBotHosts)), API_TEST_PATH)

            val rewriteHandler = RewriteHandler()
            rewriteHandler.isRewriteRequestURI = true
            rewriteHandler.isRewritePathInfo = false
            rewriteHandler.originalPathAttribute = "requestedPath"
            rewriteHandler.handler = apiHandler
            val rewriteToRoot = InverseExistsOrRewriteRegexRule(
                File(dp.propertyService.get(Props.API_UI_DIR)),
                Regex("^/?(?:burst|test)"),
                "/"
            )
            rewriteHandler.addRule(rewriteToRoot)
            apiHandlers.addHandler(rewriteHandler)

            if (dp.propertyService.get(Props.JETTY_API_GZIP_FILTER)) {
                val gzipHandler = GzipHandler()
                gzipHandler.setIncludedPaths(API_PATH)
                gzipHandler.includedMethodList = dp.propertyService.get(Props.JETTY_API_GZIP_FILTER_METHODS)
                gzipHandler.inflateBufferSize = dp.propertyService.get(Props.JETTY_API_GZIP_FILTER_BUFFER_SIZE)
                gzipHandler.minGzipSize = dp.propertyService.get(Props.JETTY_API_GZIP_FILTER_MIN_GZIP_SIZE)
                gzipHandler.handler = apiHandler
                apiHandlers.addHandler(gzipHandler)
            } else {
                apiHandlers.addHandler(apiHandler)
            }

            apiServer.handler = apiHandlers
            apiServer.stopAtShutdown = true

            dp.taskSchedulerService.runBeforeStart {
                try {
                    apiServer.start()
                    logger.safeInfo { "Started API server at $host:$port" }
                } catch (e: Exception) {
                    logger.safeError(e) { "Failed to start API server" }
                }
            }
        } else {
            apiServer = null
            logger.safeInfo { "API server not enabled" }
        }
    }

    override fun shutdown() {
        if (apiServer != null) {
            try {
                apiServer.stop()
            } catch (e: Exception) {
                logger.safeInfo(e) { "Failed to stop API server" }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ApiServerImpl::class.java)

        private const val API_PATH = "/burst"
        private const val API_TEST_PATH = "/test"
    }
}
