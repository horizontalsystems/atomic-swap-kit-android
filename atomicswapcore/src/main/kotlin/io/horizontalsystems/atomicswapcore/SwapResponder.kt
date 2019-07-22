package io.horizontalsystems.atomicswapcore

import java.util.logging.Logger

class SwapResponder(
    private val initiatorBlockchain: ISwapBlockchain,
    private val responderBlockchain: ISwapBlockchain,
    private val swap: Swap,
    private val db: SwapDatabase
) : ISwapBailTxListener, ISwapRedeemTxListener {

    private val logger = Logger.getLogger("AS-Responder")

    fun processNext() {
        logger.info("Proceed responder ${swap.id} with state ${swap.state}")

        when (swap.state) {
            Swap.State.REQUESTED -> {
                start()
            }
            Swap.State.RESPONDED -> {
                watchInitiatorBail()
            }
            Swap.State.INITIATOR_BAILED -> {
                sendResponderBailTx()
                watchInitiatorRedeem()
            }
            Swap.State.RESPONDER_BAILED -> {
                watchInitiatorRedeem()
            }
            Swap.State.INITIATOR_REDEEMED -> {
                redeem()
            }
            Swap.State.RESPONDER_REDEEMED -> {

            }
        }
    }

    fun start() {
        logger.info("Started responder for swap ${swap.id}")

        swap.state = Swap.State.RESPONDED
        db.swapDao.save(swap)

        watchInitiatorBail()
    }

    private fun watchInitiatorBail() {
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

            db.swapDao.save(swap)

            sendResponderBailTx()
            watchInitiatorRedeem()
        }
    }

    private fun watchInitiatorRedeem() {
        if (swap.state != Swap.State.RESPONDER_BAILED) return

        logger.info("Start watching for initiator redeem transaction")

        responderBlockchain.setRedeemTxListener(this, responderBlockchain.deserializeBailTx(swap.responderBailTx))
    }

    private fun sendResponderBailTx() {
        try {
            if (swap.state != Swap.State.INITIATOR_BAILED) return

            val amount = "${swap.initiatorAmount.toDouble() / swap.rate}"

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
            db.swapDao.save(swap)
        } catch (e: Exception) {

        }
    }

    override fun onRedeemTransactionSeen(redeemTx: RedeemTx) {
        synchronized(this) {
            logger.info("Initiator redeem tx seen $redeemTx for swap with state ${swap.state}")

            if (swap.state != Swap.State.RESPONDER_BAILED) return

            swap.initiatorRedeemTx = responderBlockchain.serializeRedeemTx(redeemTx)
            swap.state = Swap.State.INITIATOR_REDEEMED
            db.swapDao.save(swap)

            redeem()
        }
    }

    private fun redeem() {
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
            db.swapDao.save(swap)

            logger.info("Sent responder redeem tx $responderRedeemTx")
        } catch (e: Exception) {

        }
    }
}
