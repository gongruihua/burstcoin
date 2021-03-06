package brs.services.impl

import brs.entity.DependencyProvider
import brs.entity.FluxValue
import brs.objects.HistoricalMoments
import brs.objects.Props
import brs.services.FluxCapacitorService
import java.util.concurrent.ConcurrentHashMap

class FluxCapacitorServiceImpl(dp: DependencyProvider) : FluxCapacitorService {
    private val propertyService = dp.propertyService
    private val blockchain = dp.blockchainService

    // Map of Flux Value -> Change Height -> Index of ValueChange in FluxValue. Used as a cache.
    private val valueChangesPerFluxValue = ConcurrentHashMap<FluxValue<*>, Map<Int, Int>>()

    override fun <T> getValue(fluxValue: FluxValue<T>): T {
        return getValueAt(fluxValue, blockchain.height)
    }

    override fun <T> getValue(fluxValue: FluxValue<T>, height: Int): T {
        return getValueAt(fluxValue, height)
    }

    private fun getHistoricalMomentHeight(historicalMoment: HistoricalMoments): Int {
        return if (propertyService.get(Props.DEV_TESTNET)) {
            val overridingHeight = propertyService.get(historicalMoment.overridingProperty)
            if (overridingHeight >= 0) overridingHeight else historicalMoment.testnetHeight
        } else {
            historicalMoment.mainnetHeight
        }
    }

    private fun <T> computeValuesAtHeights(fluxValue: FluxValue<T>): Map<Int, Int> {
        return valueChangesPerFluxValue.computeIfAbsent(fluxValue) {
            val valueChangeIndexAtHeight = mutableMapOf<Int, Int>()
            val valueChanges = it.valueChanges
            for (i in valueChanges.indices) {
                valueChangeIndexAtHeight[getHistoricalMomentHeight(valueChanges[i].historicalMoment)] = i
            }
            valueChangeIndexAtHeight
        }
    }

    private fun <T> getValueAt(fluxValue: FluxValue<T>, height: Int): T {
        var mostRecentValue = fluxValue.defaultValue
        var mostRecentChangeHeight = 0
        for ((entryHeight, value) in computeValuesAtHeights(fluxValue)) {
            if (entryHeight in mostRecentChangeHeight..height) {
                mostRecentValue = fluxValue.valueChanges[value].newValue
                mostRecentChangeHeight = entryHeight
            }
        }
        return mostRecentValue
    }
}
