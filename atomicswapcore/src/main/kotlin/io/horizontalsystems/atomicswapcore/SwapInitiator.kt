package io.horizontalsystems.atomicswapcore

import java.util.logging.Logger

class SwapInitiator(
    private val sendingBlockchain: ISwapBlockchain,
    private val receivingBlockchain: ISwapBlockchain,
    private val swap: Swap,
    val db: SwapDatabase
) : ISwapBailTxListener {

    private val logger = Logger.getLogger("AS-Initiator")

    fun processNext() {
        when (swap.state) {
            Swap.State.REQUESTED -> {
            }
            Swap.State.RESPONDED -> {
                start()
            }
            Swap.State.INITIATOR_BAILED -> {
                watchResponderBail()
            }
            Swap.State.RESPONDER_BAILED -> {
                redeem()
            }
            Swap.State.INITIATOR_REDEEMED -> {
            }
            Swap.State.RESPONDER_REDEEMED -> {
            }
        }
    }

    fun start() {
        logger.info("Started initiator for swap ${swap.id}")
        sendInitiatorBailTx()
        watchResponderBail()
    }

    private fun sendInitiatorBailTx() {
        val bailTx = sendingBlockchain.sendBailTx(
            swap.responderRedeemPKH,
            swap.secretHash,
            swap.initiatorRefundPKH,
            swap.initiatorRefundTime,
            swap.initiatorAmount
        )

        swap.state = Swap.State.INITIATOR_BAILED
        db.swapDao.save(swap)

        logger.info("Sent initiator bail tx $bailTx")
    }

    private fun watchResponderBail() {
        receivingBlockchain.setBailTxListener(
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

            swap.responderBailTx = receivingBlockchain.serializeBailTx(bailTx)
            swap.state = Swap.State.RESPONDER_BAILED
            db.swapDao.save(swap)

            redeem()
        }
    }

    private fun redeem() {
        val responderBailTx = receivingBlockchain.deserializeBailTx(swap.responderBailTx)

        val redeemTx = receivingBlockchain.sendRedeemTx(
            swap.initiatorRedeemPKH,
            swap.initiatorRedeemPKId,
            swap.secret,
            swap.secretHash,
            swap.responderRefundPKH,
            swap.responderRefundTime,
            responderBailTx
        )

        swap.state = Swap.State.INITIATOR_REDEEMED
        db.swapDao.save(swap)

        logger.info("Sent initiator redeem tx $redeemTx")
    }

}
