package io.horizontalsystems.swapkit

import android.content.Context
import io.horizontalsystems.swapkit.atomicswap.*

class SwapKit(context: Context) {

    private val db = SwapDatabase.getInstance(context, "Swap")
    private val swapFactory = SwapFactory(db)

    private val swapInitiators = mutableMapOf<String, SwapInitiator>()
    private val swapResponders = mutableMapOf<String, SwapResponder>()

    init {
        val swaps = db.swapDao.all()

        swaps.forEach {
            if (it.initiator) {
                swapInitiators[it.id] = swapFactory.createAtomicSwapInitiator(it)
            } else {
                swapResponders[it.id] = swapFactory.createAtomicSwapResponder(it)
            }
        }
    }

    fun processNext() {
        swapInitiators.forEach {
            it.value.processNext()
        }

        swapResponders.forEach {
            it.value.processNext()
        }
    }

    fun registerSwapBlockchainCreator(coinCode: String, creator: ISwapBlockchainCreator) {
        swapFactory.registerSwapBlockchainCreator(coinCode, creator)
    }

    fun createSwapRequest(initiatorCoinCode: String, responderCoinCode: String, rate: Double, amount: Double): SwapRequest {
        val swap = swapFactory.createSwap(initiatorCoinCode, responderCoinCode, rate, amount)

        return SwapRequest(swap)
    }

    fun createSwapResponse(swapRequest: SwapRequest): SwapResponse {
        val swap = swapFactory.createSwapAsResponder(
            swapRequest.id,
            swapRequest.initiatorCoinCode,
            swapRequest.responderCoinCode,
            swapRequest.rate,
            swapRequest.initiatorAmount.toDouble(),
            swapRequest.initiatorRefundPKH,
            swapRequest.initiatorRedeemPKH,
            swapRequest.secretHash
        )

        val atomicSwapResponder = swapFactory.createAtomicSwapResponder(swap)
        atomicSwapResponder.start()

        swapResponders[swap.id] = atomicSwapResponder

        return SwapResponse(swap)
    }

    fun initiateSwap(swapResponse: SwapResponse) {
        val swap = swapFactory.retrieveSwapForResponse(
            swapResponse.id,
            swapResponse.responderRedeemPKH,
            swapResponse.responderRefundPKH,
            swapResponse.responderRefundTime,
            swapResponse.initiatorRefundTime
        )

        val atomicSwapInitiator = swapFactory.createAtomicSwapInitiator(swap)
        atomicSwapInitiator.start()

        swapInitiators[swap.id] = atomicSwapInitiator
    }

}
