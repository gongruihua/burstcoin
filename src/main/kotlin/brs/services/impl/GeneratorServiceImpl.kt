package brs.services.impl

import brs.entity.DependencyProvider
import brs.objects.FluxValues
import brs.objects.Props
import brs.services.BlockchainProcessorService
import brs.services.GeneratorService
import brs.services.TaskType
import brs.util.Listeners
import brs.util.biginteger.compareTo
import brs.util.convert.fullHashToId
import brs.util.convert.toUnsignedString
import brs.util.crypto.Crypto
import brs.util.logging.safeDebug
import brs.util.logging.safeError
import burst.kit.crypto.BurstCrypto
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

open class GeneratorServiceImpl(private val dp: DependencyProvider) : GeneratorService {
    private val listeners = Listeners<GeneratorService.GeneratorState, GeneratorService.Event>()
    val generators = ConcurrentHashMap<Long, GeneratorStateImpl>()
    private val burstCrypto = BurstCrypto.getInstance()

    init {
        dp.taskSchedulerService.scheduleTaskWithDelay(TaskType.COMPUTATION, 0, 500L) {
            try {
                val currentBlock = dp.blockchainService.lastBlock.height.toLong()
                val it = generators.entries.iterator()
                while (it.hasNext()) {
                    val generator = it.next()
                    if (currentBlock < generator.value.block) {
                        generator.value.forge(dp.blockchainProcessorService)
                    } else {
                        it.remove()
                    }
                }
            } catch (e: BlockchainProcessorService.BlockNotAcceptedException) {
                logger.safeDebug(e) { "Error in block generation thread" }
            }
        }
    }

    override val numberOfGenerators: Int
        get() = generators.values.size

    override fun addListener(eventType: GeneratorService.Event, listener: (GeneratorService.GeneratorState) -> Unit) {
        return listeners.addListener(eventType, listener)
    }

    override fun addNonce(secretPhrase: String, nonce: Long?): GeneratorService.GeneratorState {
        val publicKey = Crypto.getPublicKey(secretPhrase)
        return addNonce(secretPhrase, nonce, publicKey)
    }

    override fun addNonce(secretPhrase: String, nonce: Long?, publicKey: ByteArray): GeneratorService.GeneratorState {
        val publicKeyHash = Crypto.sha256().digest(publicKey)
        val id = publicKeyHash.fullHashToId()

        val generator = GeneratorStateImpl(secretPhrase, nonce, publicKey, id)
        val curGen = generators[id]
        if (curGen == null || generator.block > curGen.block || generator.deadline < curGen.deadline) {
            generators[id] = generator
            listeners.accept(GeneratorService.Event.NONCE_SUBMITTED, generator)
            logger.safeDebug { "Account ${id.toUnsignedString()} started mining, deadline ${generator.deadline} seconds" }
        } else {
            logger.safeDebug { "Account ${id.toUnsignedString()} already has a better nonce" }
        }

        return generator
    }

    override fun calculateGenerationSignature(lastGenSig: ByteArray, lastGenId: Long): ByteArray {
        return burstCrypto.calculateGenerationSignature(lastGenSig, lastGenId)
    }

    override fun calculateScoop(genSig: ByteArray, height: Long): Int {
        return burstCrypto.calculateScoop(genSig, height)
    }

    private fun getPocVersion(blockHeight: Int): Int {
        return if (dp.fluxCapacitorService.getValue(FluxValues.POC2, blockHeight)) 2 else 1
    }

    override fun calculateHit(
        accountId: Long,
        nonce: Long,
        genSig: ByteArray,
        scoop: Int,
        blockHeight: Int
    ): BigInteger {
        return burstCrypto.calculateHit(accountId, nonce, genSig, scoop, getPocVersion(blockHeight))
    }

    override fun calculateHit(genSig: ByteArray, scoopData: ByteArray): BigInteger {
        return burstCrypto.calculateHit(genSig, scoopData)
    }

    override fun calculateDeadline(
        accountId: Long,
        nonce: Long,
        genSig: ByteArray,
        scoop: Int,
        baseTarget: Long,
        blockHeight: Int
    ): BigInteger {
        return burstCrypto.calculateDeadline(accountId, nonce, genSig, scoop, baseTarget, getPocVersion(blockHeight))
    }

    inner class GeneratorStateImpl internal constructor(
        private val secretPhrase: String,
        private val nonce: Long?,
        override val publicKey: ByteArray,
        override val accountId: Long?
    ) :
        GeneratorService.GeneratorState {
        override val deadline: BigInteger
        override val block: Long

        init {
            val lastBlock = dp.blockchainService.lastBlock

            this.block = lastBlock.height.toLong() + 1

            val lastGenSig = lastBlock.generationSignature
            val lastGenerator = lastBlock.generatorId

            val newGenSig = calculateGenerationSignature(lastGenSig, lastGenerator)

            val scoopNum = calculateScoop(newGenSig, lastBlock.height + 1L)

            deadline = calculateDeadline(accountId!!, nonce!!, newGenSig, scoopNum, lastBlock.baseTarget, lastBlock.height + 1)
        }

        internal fun forge(blockchainProcessorService: BlockchainProcessorService) {
            val lastBlock = dp.blockchainService.lastBlock

            val elapsedTime = dp.timeService.epochTime - lastBlock.timestamp
            if (elapsedTime > deadline) {
                try {
                    blockchainProcessorService.generateBlock(secretPhrase, publicKey, nonce)
                } catch (e: Exception) {
                    logger.safeError(e) { "COULD NOT FORGE A BLOCK WHEN WE HAD AN ELAPSED DEADLINE!!!" }
                }
            }
        }
    }

    class MockGeneratorService(private val dp: DependencyProvider) : GeneratorServiceImpl(dp) {
        override fun calculateHit(accountId: Long, nonce: Long, genSig: ByteArray, scoop: Int, blockHeight: Int): BigInteger =
            dp.propertyService.get(Props.DEV_MOCK_MINING_DEADLINE).toBigInteger()

        override fun calculateHit(genSig: ByteArray, scoopData: ByteArray): BigInteger =
            dp.propertyService.get(Props.DEV_MOCK_MINING_DEADLINE).toBigInteger()

        override fun calculateDeadline(accountId: Long, nonce: Long, genSig: ByteArray, scoop: Int, baseTarget: Long, blockHeight: Int): BigInteger =
            dp.propertyService.get(Props.DEV_MOCK_MINING_DEADLINE).toBigInteger()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GeneratorServiceImpl::class.java)
    }
}
