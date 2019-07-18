package io.horizontalsystems.swapkit.atomicswap

import java.util.logging.Logger

class SwapResponder(
    private val receivingBlockchain: ISwapBlockchain,
    private val sendingBlockchain: ISwapBlockchain,
    private val swap: Swap,
    private val db: SwapDatabase
) : ISwapBailTxListener, ISwapRedeemTxListener {

    private val logger = Logger.getLogger("AS-Responder")

    fun processNext() {
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

        receivingBlockchain.setBailTxListener(
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

            swap.initiatorBailTx = receivingBlockchain.serializeBailTx(bailTx)
            swap.state = Swap.State.INITIATOR_BAILED

            db.swapDao.save(swap)

            sendResponderBailTx()
            watchInitiatorRedeem()
        }
    }

    private fun watchInitiatorRedeem() {
        logger.info("Start watching for initiator redeem transaction")

        sendingBlockchain.setRedeemTxListener(this, sendingBlockchain.deserializeBailTx(swap.responderBailTx))
    }

    private fun sendResponderBailTx() {
        val amount = "${swap.initiatorAmount.toDouble() / swap.rate}"

        val responderBailTx = sendingBlockchain.sendBailTx(
            swap.initiatorRedeemPKH,
            swap.secretHash,
            swap.responderRefundPKH,
            swap.responderRefundTime,
            amount
        )
        logger.info("Sent responder bail tx $responderBailTx")

        swap.responderBailTx = sendingBlockchain.serializeBailTx(responderBailTx)
        swap.state = Swap.State.RESPONDER_BAILED
        db.swapDao.save(swap)
    }

    override fun onRedeemTransactionSeen(redeemTx: RedeemTx) {
        synchronized(this) {
            logger.info("Initiator redeem tx seen $redeemTx for swap with state ${swap.state}")

            if (swap.state != Swap.State.RESPONDER_BAILED) return

            swap.initiatorRedeemTx = sendingBlockchain.serializeRedeemTx(redeemTx)
            swap.state = Swap.State.INITIATOR_REDEEMED
            db.swapDao.save(swap)

            redeem()
        }
    }

    private fun redeem() {
        val redeemTx = sendingBlockchain.deserializeRedeemTx(swap.initiatorRedeemTx)
        val initiatorBailTx = receivingBlockchain.deserializeBailTx(swap.initiatorBailTx)
        val responderRedeemTx = receivingBlockchain.sendRedeemTx(
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
    }
}
