package io.horizontalsystems.atomicswap

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.horizontalsystems.bitcoincore.AbstractKit
import io.horizontalsystems.swapkit.SwapKit
import io.horizontalsystems.swapkit.atomicswap.SwapManager

class ExchangeViewModel : ViewModel() {
    lateinit var bitcoinKit : AbstractKit

    val requestText = MutableLiveData<String>()

    val responseText = MutableLiveData<String>()

    val atomicSwapManager = SwapManager()

    private val swapKit = SwapKit(App.instance)

    fun init() {
        swapKit.registerSwapBlockchainCreator("BTC", BitcoinSwapBlockchainCreator(bitcoinKit))
        swapKit.registerSwapBlockchainCreator("BCH", BitcoinSwapBlockchainCreator(bitcoinKit))
    }

    fun generateRequest(coinHave: String, coinWant: String, rate: Double, amount: Double) {
        val atomicSwap = swapKit.generateSwap(coinHave, coinWant, rate, amount)

        requestText.postValue(atomicSwapManager.generateRequestString(atomicSwap))
    }

    fun generateResponse(request: String) {
        val atomicSwap = atomicSwapManager.parseFromRequestString(request)

        swapKit.applyAsResponder(atomicSwap)
        responseText.postValue(atomicSwapManager.generateResponseString(atomicSwap))

        swapKit.startResponder(atomicSwap)
    }

    fun startAtomicSwap(response: String) {
        try {
            val updatedSwap = swapKit.applyAsInitiator(atomicSwapManager.parseFromResponseString(response))

            swapKit.startInitiator(updatedSwap)
        } catch (e: Exception) {
            Log.e("AAA", "$e")
        }
    }

}
