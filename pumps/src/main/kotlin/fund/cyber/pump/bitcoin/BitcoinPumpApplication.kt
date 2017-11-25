package fund.cyber.pump.bitcoin

import fund.cyber.node.model.BtcdBlock
import fund.cyber.pump.PumpsContext
import getStartBlockNumber
import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable


private val log = LoggerFactory.getLogger(BitcoinPumpContext::class.java)!!

fun main(args: Array<String>) {


    PumpsContext.schemaMigrationEngine.executeSchemaUpdate(BitcoinMigrations.migrations)
    val startBlockNumber = getStartBlockNumber(BitcoinMigrations.applicationId, PumpsContext.pumpDaoService)
    log.info("Bitcoin application started from block $startBlockNumber")


    val startBlock: Callable<Long> = Callable { 0L }

    Flowable.generate<BtcdBlock, Long>(startBlock, downloadNextBlockFunction(BitcoinPumpContext.btcdClient))
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .unsubscribeOn(Schedulers.trampoline())
            .doAfterTerminate(PumpsContext::closeContext)
            .subscribe { btcdBlock ->
                Thread.sleep(200)
                log.info(btcdBlock.toString())
            }
}

fun downloadNextBlockFunction(btcdClient: BtcdClient) = BiFunction { blockNumber: Long, subscriber: Emitter<BtcdBlock> ->
    try {
        log.info("Pulling block $blockNumber")
        val block = btcdClient.getBlockByNumber(blockNumber)
        if (block != null) {
            subscriber.onNext(block)
            blockNumber + 1
        } else blockNumber
    } catch (e: Exception) {
        blockNumber
    }
}