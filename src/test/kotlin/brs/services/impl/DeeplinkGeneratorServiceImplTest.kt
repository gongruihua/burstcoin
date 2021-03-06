package brs.services.impl

import brs.services.DeeplinkGeneratorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DeeplinkGeneratorServiceImplTest {
    private lateinit var deeplinkGeneratorService: DeeplinkGeneratorService

    @Before
    fun setUpDeeplinkGeneratorTest() {
        deeplinkGeneratorService = DeeplinkGeneratorServiceImpl()
    }

    @Test
    fun testDeeplinkGenerator_Success() {
        val result = deeplinkGeneratorService.generateDeepLink("generic", "testAction", "dGVzdERhdGE=")
        val expectedResult = "burst.generic://v1?action=testAction&payload=dGVzdERhdGE%3D"
        assertEquals(expectedResult, result)
    }

    @Test
    fun testDeeplinkGenerator_NoPayloadSuccess() {
        val result = deeplinkGeneratorService.generateDeepLink("generic", "testAction", null)
        val expectedResult = "burst.generic://v1?action=testAction"
        assertEquals(expectedResult, result)
    }

    @Test
    fun testDeeplinkGenerator_InvalidDomain() {
        try {
            deeplinkGeneratorService.generateDeepLink("invalid", "testAction", null)
        } catch (e: IllegalArgumentException) {
            assertEquals(e.message, "Invalid domain: \"invalid\"")
        }
    }

    @Test
    fun testDeeplinkGenerator_PayloadLengthExceeded() {
        val s = StringBuilder()
        for (i in 0..2048) {
            s.append("a")
        }

        try {
            deeplinkGeneratorService.generateDeepLink("generic", "testAction", s.toString())
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.startsWith("Maximum Payload Length "))
        }
    }
}
