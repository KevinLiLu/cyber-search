package fund.cyber.pump.common.node

import fund.cyber.common.StackCache
import fund.cyber.search.model.events.PumpEvent
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

interface BlockBundleMapper<T : BlockBundle> {
    fun map(blockBundle: T, history: StackCache<T>): List<Pair<PumpEvent, T>>
}

private val log = LoggerFactory.getLogger(CommonBlockBundleMapper::class.java)!!

@Component
class CommonBlockBundleMapper<T : BlockBundle>(
        private val blockchainInterface: FlowableBlockchainInterface<T>,
        monitoring: MeterRegistry
) : BlockBundleMapper<T> {

    private val chainReorganizationMonitor = monitoring.counter("pump_chain_reorganization_counter")

    override fun map(blockBundle: T, history: StackCache<T>): List<Pair<PumpEvent, T>> {
        val exHash = history.peek()?.hash ?: ""
        if (exHash.isNotEmpty() && blockBundle.parentHash != exHash) {
            log.info("Chain reorganization occurred. Processing involved bundles")
            chainReorganizationMonitor.increment()
            return getReorganizationBundles(blockBundle, history)
        }
        history.push(blockBundle)
        return listOf(PumpEvent.NEW_BLOCK to blockBundle)
    }

    private fun getReorganizationBundles(blockBundle: T, history: StackCache<T>): List<Pair<PumpEvent, T>> {
        var tempBlockBundle = blockBundle
        var prevBlockBundle: T? = null

        var newBlocks = listOf(PumpEvent.NEW_BLOCK to tempBlockBundle)
        var revertBlocks = listOf<Pair<PumpEvent, T>>()

        do {
            if (prevBlockBundle != null) {
                revertBlocks += PumpEvent.DROPPED_BLOCK to prevBlockBundle
                tempBlockBundle = blockchainInterface.blockBundleByNumber(tempBlockBundle.number - 1L)
                newBlocks += PumpEvent.NEW_BLOCK to tempBlockBundle
            }
            prevBlockBundle = history.pop()
        } while (prevBlockBundle?.hash != tempBlockBundle.parentHash)

        newBlocks = newBlocks.reversed()
        newBlocks.forEach { history.push(it.second) }

        return (revertBlocks + newBlocks)
    }
}