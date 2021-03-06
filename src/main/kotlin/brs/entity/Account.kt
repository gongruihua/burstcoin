package brs.entity

import brs.db.BurstKey
import brs.util.convert.toUnsignedString
import brs.util.crypto.Crypto
import brs.util.crypto.rsVerify
import brs.util.logging.safeError
import burst.kit.entity.BurstEncryptedMessage
import org.slf4j.LoggerFactory

class Account {
    val id: Long
    val nxtKey: BurstKey
    val creationHeight: Int
    private var publicKeyInternal: ByteArray? = null
    var publicKey: ByteArray?
        get() = if (this.keyHeight == -1) null else publicKeyInternal
        set(v) { publicKeyInternal = v }
    var keyHeight: Int = 0
    var balancePlanck: Long = 0
    var unconfirmedBalancePlanck: Long = 0
    var forgedBalancePlanck: Long = 0

    var name: String? = null
    var description: String? = null

    enum class Event {
        BALANCE, UNCONFIRMED_BALANCE, ASSET_BALANCE, UNCONFIRMED_ASSET_BALANCE
    }

    open class AccountAsset {
        val accountId: Long
        val assetId: Long
        val burstKey: BurstKey
        var quantity: Long = 0
        var unconfirmedQuantity: Long = 0

        protected constructor(
            accountId: Long,
            assetId: Long,
            quantity: Long,
            unconfirmedQuantity: Long,
            burstKey: BurstKey
        ) {
            this.accountId = accountId
            this.assetId = assetId
            this.quantity = quantity
            this.unconfirmedQuantity = unconfirmedQuantity
            this.burstKey = burstKey
        }

        constructor(burstKey: BurstKey, accountId: Long, assetId: Long, quantity: Long, unconfirmedQuantity: Long) {
            this.accountId = accountId
            this.assetId = assetId
            this.burstKey = burstKey
            this.quantity = quantity
            this.unconfirmedQuantity = unconfirmedQuantity
        }

        fun checkBalance() {
            checkBalance(this.accountId, this.quantity, this.unconfirmedQuantity)
        }

        override fun toString(): String {
            return ("AccountAsset account_id: "
                    + accountId.toUnsignedString()
                    + " asset_id: "
                    + assetId.toUnsignedString()
                    + " quantity: "
                    + quantity
                    + " unconfirmedQuantity: "
                    + unconfirmedQuantity)
        }
    }

    open class RewardRecipientAssignment(
        val accountId: Long,
        var prevRecipientId: Long,
        var recipientId: Long,
        fromHeight: Int,
        val burstKey: BurstKey
    ) {
        var fromHeight: Int = 0
            private set

        init {
            this.fromHeight = fromHeight
        }

        fun setRecipient(newRecipientId: Long, fromHeight: Int) {
            prevRecipientId = recipientId
            recipientId = newRecipientId
            this.fromHeight = fromHeight
        }
    }

    internal class DoubleSpendingException(message: String) : Exception(message)

    constructor(dp: DependencyProvider, id: Long) {
        if (!id.rsVerify()) {
            logger.safeError { "CRITICAL ERROR: Reed-Solomon encoding fails for $id" }
        }
        this.id = id
        this.nxtKey = dp.accountStore.accountKeyFactory.newKey(this.id)
        this.creationHeight = dp.blockchainService.height
    }

    constructor(id: Long, burstKey: BurstKey, creationHeight: Int) {
        if (!id.rsVerify()) {
            logger.safeError { "CRITICAL ERROR: Reed-Solomon encoding fails for $id" }
        }
        this.id = id
        this.nxtKey = burstKey
        this.creationHeight = creationHeight
    }

    fun encryptTo(data: ByteArray, senderSecretPhrase: String, isText: Boolean): BurstEncryptedMessage {
        requireNotNull(publicKey) { "Recipient account doesn't have a public key set" }
        return Crypto.encryptData(data, Crypto.getPrivateKey(senderSecretPhrase), publicKey!!, isText)
    }


    fun decryptFrom(encryptedData: BurstEncryptedMessage, recipientSecretPhrase: String): ByteArray {
        requireNotNull(publicKey) { "Sender account doesn't have a public key set" }
        return encryptedData.decrypt(Crypto.getPrivateKey(recipientSecretPhrase), publicKey!!)
    }

    fun apply(dp: DependencyProvider, key: ByteArray, height: Int) {
        check(dp.accountStore.setOrVerify(this, key, height)) { "Public key mismatch" }
        checkNotNull(this.publicKeyInternal) {
            ("Public key has not been set for account " + id.toUnsignedString()
                    + " at height " + height + ", key height is " + keyHeight)
        }
        if (this.keyHeight == -1 || this.keyHeight > height) {
            this.keyHeight = height
            dp.accountStore.accountTable.insert(this)
        }
    }

    fun checkBalance() {
        checkBalance(this.id, this.balancePlanck, this.unconfirmedBalancePlanck)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Account::class.java)

        private fun checkBalance(accountId: Long, confirmed: Long, unconfirmed: Long) {
            if (confirmed < 0) {
                throw DoubleSpendingException(
                    "Negative balance or quantity ("
                            + confirmed
                            + ") for account "
                            + accountId.toUnsignedString()
                )
            }
            if (unconfirmed < 0) {
                throw DoubleSpendingException(
                    "Negative unconfirmed balance or quantity ("
                            + unconfirmed
                            + ") for account "
                            + accountId.toUnsignedString()
                )
            }
            if (unconfirmed > confirmed) {
                throw DoubleSpendingException(
                    "Unconfirmed ("
                            + unconfirmed
                            + ") exceeds confirmed ("
                            + confirmed
                            + ") balance or quantity for account "
                            + accountId.toUnsignedString()
                )
            }
        }
    }
}
