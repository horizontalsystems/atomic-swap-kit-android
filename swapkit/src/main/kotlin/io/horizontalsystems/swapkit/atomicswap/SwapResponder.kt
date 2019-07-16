package io.horizontalsystems.swapkit.atomicswap

import java.util.logging.Logger

class SwapResponder(
        private val receivingBlockchain: ISwapBlockchain,
        private val sendingBlockchain: ISwapBlockchain,
        private val swap: Swap,
        private val db: SwapDatabase
) : ISwapBailTxListener, ISwapRedeemTxListener {

    private val logger = Logger.getLogger("AS-Responder")

    fun start() {
        logger.info("Started responder for swap ${swap.id}")

        swap.state = Swap.State.RESPONDED
        db.swapDao.save(swap)

        receivingBlockchain.setBailTxListener(this, swap.responderRedeemPKH, swap.secretHash, swap.initiatorRefundPKH, swap.initiatorRefundTime)
    }

    private var initiatorBailTx: BailTx? = null

    override fun onBailTransactionSeen(bailTx: BailTx) {
        synchronized(this) {
            logger.info("Initiator bail transaction seen $bailTx for swap with state ${swap.state}")

            if (swap.state != Swap.State.RESPONDED) return

            swap.state = Swap.State.INITIATOR_BAILED
            db.swapDao.save(swap)

            initiatorBailTx = bailTx

            val amount = "${swap.initiatorAmount.toDouble() / swap.rate}"

            val responderBailTx = sendingBlockchain.sendBailTx(swap.initiatorRedeemPKH, swap.secretHash, swap.responderRefundPKH, swap.responderRefundTime, amount)
            logger.info("Sent responder bail tx $responderBailTx")

            swap.state = Swap.State.RESPONDER_BAILED
            db.swapDao.save(swap)

            sendingBlockchain.setRedeemTxListener(this, responderBailTx)
        }
    }

    override fun onRedeemTransactionSeen(redeemTx: RedeemTx) {
        synchronized(this) {
            logger.info("Initiator redeem tx seen $redeemTx for swap with state ${swap.state}")

            if (swap.state != Swap.State.RESPONDER_BAILED) return

            swap.state = Swap.State.INITIATOR_REDEEMED
            db.swapDao.save(swap)


            initiatorBailTx?.let {
                val responderRedeemTx = receivingBlockchain.sendRedeemTx(swap.responderRedeemPKH, swap.responderRedeemPKId, redeemTx.secret, swap.secretHash, swap.initiatorRefundPKH, swap.initiatorRefundTime, it)

                swap.state = Swap.State.RESPONDER_REDEEMED
                db.swapDao.save(swap)
                logger.info("Sent responder redeem tx $responderRedeemTx")
            }
        }

    }
}
