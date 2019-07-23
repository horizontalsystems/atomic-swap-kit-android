package io.horizontalsystems.atomicswapcore

import java.util.logging.Logger

class SwapResponder(private val swapResponderDoer: SwapResponderDoer) {

    fun processNext() = when (swapResponderDoer.state) {
        Swap.State.REQUESTED -> {
        }
        Swap.State.RESPONDED -> {
            swapResponderDoer.watchInitiatorBail()
        }
        Swap.State.INITIATOR_BAILED -> {
            swapResponderDoer.bail()
        }
        Swap.State.RESPONDER_BAILED -> {
            swapResponderDoer.watchInitiatorRedeem()
        }
        Swap.State.INITIATOR_REDEEMED -> {
            swapResponderDoer.redeem()
        }
        Swap.State.RESPONDER_REDEEMED -> {

        }
    }
}

class SwapResponderDoer(
    private val initiatorBlockchain: ISwapBlockchain,
    private val responderBlockchain: ISwapBlockchain,
    private val swap: Swap,
    private val storage: SwapDao
) : ISwapBailTxListener, ISwapRedeemTxListener {

    lateinit var delegate: SwapResponder
    val state get() = swap.state

    private val logger = Logger.getLogger("AS-Responder")

    fun watchInitiatorBail() {
        logger.info("Start watching for initiator bail transaction")

        initiatorBlockchain.setBailTxListener(
            this,
            swap.responderRedeemPKH,
            swap.secretHash,
            swap.initiatorRefundPKH,
            swap.initiatorRefundTime
        )
    }

    override fun onBailTransactionSeen(bailTx: BailTx) {
        synchronized(this) {
            logger.info("Initiator bail transaction seen $bailTx for swap with state ${swap.state}")

            if (swap.state != Swap.State.RESPONDED) return

            swap.initiatorBailTx = initiatorBlockchain.serializeBailTx(bailTx)
            swap.state = Swap.State.INITIATOR_BAILED

            storage.save(swap)

            delegate.processNext()
        }
    }

    fun bail() {
        try {
            val amount = "${swap.initiatorAmount.toDouble() * swap.rate}"

            val responderBailTx = responderBlockchain.sendBailTx(
                swap.initiatorRedeemPKH,
                swap.secretHash,
                swap.responderRefundPKH,
                swap.responderRefundTime,
                amount
            )
            logger.info("Sent responder bail tx $responderBailTx")

            swap.responderBailTx = responderBlockchain.serializeBailTx(responderBailTx)
            swap.state = Swap.State.RESPONDER_BAILED
            storage.save(swap)

            delegate.processNext()
        } catch (e: Exception) {

        }
    }

    fun watchInitiatorRedeem() {
        logger.info("Start watching for initiator redeem transaction")

        responderBlockchain.setRedeemTxListener(this, responderBlockchain.deserializeBailTx(swap.responderBailTx))
    }

    override fun onRedeemTransactionSeen(redeemTx: RedeemTx) {
        synchronized(this) {
            logger.info("Initiator redeem tx seen $redeemTx for swap with state ${swap.state}")

            if (swap.state != Swap.State.RESPONDER_BAILED) return

            swap.initiatorRedeemTx = responderBlockchain.serializeRedeemTx(redeemTx)
            swap.state = Swap.State.INITIATOR_REDEEMED
            storage.save(swap)

            delegate.processNext()
        }
    }

    fun redeem() {
        try {
            val redeemTx = responderBlockchain.deserializeRedeemTx(swap.initiatorRedeemTx)
            val initiatorBailTx = initiatorBlockchain.deserializeBailTx(swap.initiatorBailTx)
            val responderRedeemTx = initiatorBlockchain.sendRedeemTx(
                swap.responderRedeemPKH,
                swap.responderRedeemPKId,
                redeemTx.secret,
                swap.secretHash,
                swap.initiatorRefundPKH,
                swap.initiatorRefundTime,
                initiatorBailTx
            )

            swap.state = Swap.State.RESPONDER_REDEEMED
            storage.save(swap)

            logger.info("Sent responder redeem tx $responderRedeemTx")

            delegate.processNext()
        } catch (e: Exception) {

        }
    }
}