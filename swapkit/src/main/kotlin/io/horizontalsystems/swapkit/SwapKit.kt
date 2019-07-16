package io.horizontalsystems.swapkit

import android.content.Context
import io.horizontalsystems.swapkit.atomicswap.ISwapBlockchainCreator
import io.horizontalsystems.swapkit.atomicswap.Swap
import io.horizontalsystems.swapkit.atomicswap.SwapDatabase
import io.horizontalsystems.swapkit.atomicswap.SwapFactory
import java.security.MessageDigest
import java.util.*

class SwapKit(context: Context) {

    private val db = SwapDatabase.getInstance(context, "Swap")
    private val swapFactory = SwapFactory(db)

    fun registerSwapBlockchainCreator(coinCode: String, creator: ISwapBlockchainCreator) {
        swapFactory.registerSwapBlockchainCreator(coinCode, creator)
    }

    fun generateSwap(initiatorCoinCode: String, responderCoinCode: String, rate: Double, amount: Double): Swap {
        val initiatorBlockchain = swapFactory.createBlockchain(initiatorCoinCode)
        val responderBlockchain = swapFactory.createBlockchain(responderCoinCode)

        val initiatorRedeemPublicKey = responderBlockchain.getRedeemPublicKey()
        val initiatorRefundPublicKey = initiatorBlockchain.getRefundPublicKey()

        val id = UUID.randomUUID().toString()
        val secret = sha256("supersecret".toByteArray())

        val swap = Swap().apply {
            this.id = id

            this.initiatorCoinCode = initiatorCoinCode
            this.responderCoinCode = responderCoinCode
            this.rate = rate
            this.initiatorAmount = "$amount"

            this.initiatorRedeemPKH = initiatorRedeemPublicKey.publicKeyHash
            this.initiatorRedeemPKId = initiatorRedeemPublicKey.publicKeyId
            this.initiatorRefundPKH = initiatorRefundPublicKey.publicKeyHash
            this.initiatorRefundPKId = initiatorRefundPublicKey.publicKeyId

            this.secret = secret
            this.secretHash = sha256(secret)
        }

        db.swapDao.save(swap)

        return swap
    }

    private fun sha256(input: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input)
    }

    fun applyAsResponder(swap: Swap) {
        val initiatorBlockchain = swapFactory.createBlockchain(swap.initiatorCoinCode)
        val responderBlockchain = swapFactory.createBlockchain(swap.responderCoinCode)

        val redeemPublicKey = initiatorBlockchain.getRedeemPublicKey()
        val refundPublicKey = responderBlockchain.getRefundPublicKey()

        swap.apply {
            responderRedeemPKH = redeemPublicKey.publicKeyHash
            responderRedeemPKId = redeemPublicKey.publicKeyId
            responderRefundPKH = refundPublicKey.publicKeyHash
            responderRefundPKId = refundPublicKey.publicKeyId

            responderRefundTime = 1563000000 // 07/13/2019 @ 6:40am (UTC)
            initiatorRefundTime = 1563100000 // 07/14/2019 @ 10:26am (UTC)
        }

        db.swapDao.save(swap)
    }

    fun startResponder(swap: Swap) {
        val atomicSwapResponder = swapFactory.createAtomicSwapResponder(swap)
        atomicSwapResponder.start()
    }

    fun startInitiator(swap: Swap) {
        val atomicSwapInitiator = swapFactory.createAtomicSwapInitiator(swap)
        atomicSwapInitiator.start()
    }

    fun applyAsInitiator(swap: Swap): Swap {
        val swapFromDB = db.swapDao.load(swap.id)

        swapFromDB.responderRedeemPKH = swap.responderRedeemPKH
        swapFromDB.responderRefundPKH = swap.responderRefundPKH

        swapFromDB.responderRefundTime = swap.responderRefundTime
        swapFromDB.initiatorRefundTime = swap.initiatorRefundTime

        db.swapDao.save(swapFromDB)

        return swapFromDB
    }

}
