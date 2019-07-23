package io.horizontalsystems.atomicswapcore

import java.util.logging.Logger

class SwapInitiator(private val swapInitiatorDoer: SwapInitiatorDoer) {

    fun processNext() = when (swapInitiatorDoer.state) {
        Swap.State.REQUESTED -> {
        }
        Swap.State.RESPONDED -> {
            swapInitiatorDoer.bail()
        }
        Swap.State.INITIATOR_BAILED -> {
            swapInitiatorDoer.watchResponderBail()
        }
        Swap.State.RESPONDER_BAILED -> {
            swapInitiatorDoer.redeem()
        }
        Swap.State.INITIATOR_REDEEMED -> {
        }
        Swap.State.RESPONDER_REDEEMED -> {
        }
    }
}

class SwapInitiatorDoer(
    private val initiatorBlockchain: ISwapBlockchain,
    private val responderBlockchain: ISwapBlockchain,
    private val swap: Swap,
    private val storage: SwapDao
) : ISwapBailTxListener {

    lateinit var delegate: SwapInitiator
    val state get() = swap.state

    private val logger = Logger.getLogger("AS-Initiator")

    fun bail() {
        try {
            val bailTx = initiatorBlockchain.sendBailTx(
                swap.responderRedeemPKH,
                swap.secretHash,
                swap.initiatorRefundPKH,
                swap.initiatorRefundTime,
                swap.initiatorAmount
            )

            swap.state = Swap.State.INITIATOR_BAILED
            storage.save(swap)

            logger.info("Sent initiator bail tx $bailTx")

            delegate.processNext()
        } catch (e: Exception) {

        }
    }

    fun watchResponderBail() {
        responderBlockchain.setBailTxListener(
            this,
            swap.initiatorRedeemPKH,
            swap.secretHash,
            swap.responderRefundPKH,
            swap.responderRefundTime
        )
    }

    override fun onBailTransactionSeen(bailTx: BailTx) {
        synchronized(this) {
            logger.info("Responder bail transaction seen $bailTx for swap with state ${swap.state}")

            if (swap.state != Swap.State.INITIATOR_BAILED) return

            swap.responderBailTx = responderBlockchain.serializeBailTx(bailTx)
            swap.state = Swap.State.RESPONDER_BAILED
            storage.save(swap)

            delegate.processNext()
        }
    }

    fun redeem() {
        try {
            val responderBailTx = responderBlockchain.deserializeBailTx(swap.responderBailTx)

            val redeemTx = responderBlockchain.sendRedeemTx(
                swap.initiatorRedeemPKH,
                swap.initiatorRedeemPKId,
                swap.secret,
                swap.secretHash,
                swap.responderRefundPKH,
                swap.responderRefundTime,
                responderBailTx
            )

            swap.state = Swap.State.INITIATOR_REDEEMED
            storage.save(swap)

            logger.info("Sent initiator redeem tx $redeemTx")

            delegate.processNext()
        } catch (e: Exception) {

        }
    }

}
