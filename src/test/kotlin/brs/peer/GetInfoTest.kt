package brs.peer

import brs.common.QuickMocker
import brs.services.PeerService
import brs.services.TimeService
import com.nhaarman.mockitokotlin2.mock
import org.junit.Before
import org.junit.Test

class GetInfoTest {
    private lateinit var t: GetInfo
    private lateinit var peerService: PeerService
    private lateinit var timeService: TimeService
    private lateinit var peer: Peer

    @Before
    fun setUp() {
        peerService = mock()
        timeService = mock()
        peer = mock()
        t = GetInfo(QuickMocker.dependencyProvider(peerService, timeService))
    }

    // TODO normal circumstances test

    @Test
    fun test_nothingProvided() {
        PeerApiTestUtils.testWithNothingProvided(t)
    }
}