package io.horizontalsystems.atomicswap

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.horizontalsystems.bitcoincore.AbstractKit
import io.horizontalsystems.swapkit.SwapKit
import io.horizontalsystems.swapkit.atomicswap.SwapRequest
import io.horizontalsystems.swapkit.atomicswap.SwapResponse
import io.horizontalsystems.swapkit.atomicswap.hexToByteArray
import io.horizontalsystems.swapkit.atomicswap.toHexString

class ExchangeViewModel : ViewModel() {
    lateinit var bitcoinKit: AbstractKit

    val requestText = MutableLiveData<String>()

    val responseText = MutableLiveData<String>()

    private val swapKit = SwapKit(App.instance)

    private val separator = "|"

    fun init() {
        swapKit.registerSwapBlockchainCreator("BTC", BitcoinSwapBlockchainCreator(bitcoinKit))
        swapKit.init()
        swapKit.processNext()
    }

    fun generateRequest(coinHave: String, coinWant: String, rate: Double, amount: Double) {
        val swapRequest = swapKit.createSwapRequest(coinHave, coinWant, rate, amount)

        requestText.postValue(generateRequestString(swapRequest))
    }

    fun generateResponse(request: String) {
        val swapRequest = parseFromRequestString(request)

        val swapResponse = swapKit.createSwapResponse(swapRequest)
        responseText.postValue(generateResponseString(swapResponse))
    }

    fun startAtomicSwap(response: String) {
        try {
            swapKit.initiateSwap(parseFromResponseString(response))
        } catch (e: Exception) {
            Log.e("AAA", "$e")
        }
    }

    private fun generateRequestString(swapRequest: SwapRequest): String {
        val params = listOf(
            swapRequest.id,
            swapRequest.initiatorCoinCode,
            swapRequest.responderCoinCode,
            "${swapRequest.rate}",
            swapRequest.initiatorAmount,
            swapRequest.initiatorRedeemPKH.toHexString(),
            swapRequest.initiatorRefundPKH.toHexString(),
            swapRequest.secretHash.toHexString()
        )

        return params.joinToString(separator)
    }

    private fun parseFromRequestString(requestString: String): SwapRequest {
        val params = requestString.split(separator)
        val paramsIterator = params.iterator()

        return SwapRequest(
            paramsIterator.next(),
            paramsIterator.next(),
            paramsIterator.next(),
            paramsIterator.next().toDouble(),
            paramsIterator.next(),
            paramsIterator.next().hexToByteArray(),
            paramsIterator.next().hexToByteArray(),
            paramsIterator.next().hexToByteArray()
        )
    }

    private fun generateResponseString(swapResponse: SwapResponse): String {
        val params = listOf(
            swapResponse.id,
            swapResponse.responderRedeemPKH.toHexString(),
            swapResponse.responderRefundPKH.toHexString(),
            "${swapResponse.responderRefundTime}",
            "${swapResponse.initiatorRefundTime}"
        )

        return params.joinToString(separator)
    }

    private fun parseFromResponseString(responseString: String): SwapResponse {
        val params = responseString.split(separator)
        val paramsIterator = params.iterator()

        return SwapResponse(
            paramsIterator.next(),
            paramsIterator.next().hexToByteArray(),
            paramsIterator.next().hexToByteArray(),
            paramsIterator.next().toLong(),
            paramsIterator.next().toLong()
        )
    }

}
