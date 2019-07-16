package io.horizontalsystems.swapkit.atomicswap

import java.util.logging.Logger

class SwapInitiator(private val sendingBlockchain: ISwapBlockchain,
                    private val receivingBlockchain: ISwapBlockchain,
                    private val swap: Swap,
                    val db: SwapDatabase
) : ISwapBailTxListener {

    private val logger = Logger.getLogger("AS-Initiator")

    fun start() {
        logger.info("Started initiator for swap ${swap.id}")

        val bailTx = sendingBlockchain.sendBailTx(swap.responderRedeemPKH, swap.secretHash, swap.initiatorRefundPKH, swap.initiatorRefundTime, swap.initiatorAmount)

        swap.state = Swap.State.INITIATOR_BAILED
        db.swapDao.save(swap)

        logger.info("Sent initiator bail tx $bailTx")

        receivingBlockchain.setBailTxListener(this, swap.initiatorRedeemPKH, swap.secretHash, swap.responderRefundPKH, swap.responderRefundTime)
    }

    override fun onBailTransactionSeen(bailTx: BailTx) {
        synchronized(this) {
            logger.info("Responder bail transaction seen $bailTx for swap with state ${swap.state}")

            if (swap.state != Swap.State.INITIATOR_BAILED) return

            swap.state = Swap.State.RESPONDER_BAILED
            db.swapDao.save(swap)


            val redeemTx = receivingBlockchain.sendRedeemTx(swap.initiatorRedeemPKH, swap.initiatorRedeemPKId, swap.secret, swap.secretHash, swap.responderRefundPKH, swap.responderRefundTime, bailTx)

            swap.state = Swap.State.INITIATOR_REDEEMED
            db.swapDao.save(swap)

            logger.info("Sent initiator redeem tx $redeemTx")
        }
    }

}
