package io.horizontalsystems.atomicswap

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.horizontalsystems.atomicswapbitcoin.BitcoinSwapBlockchainCreator
import io.horizontalsystems.bitcoincash.BitcoinCashKit
import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.bitcoincore.models.BlockInfo
import io.horizontalsystems.bitcoincore.models.FeePriority
import io.horizontalsystems.bitcoincore.models.TransactionInfo
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.swapkit.SwapKit
import io.horizontalsystems.swapkit.atomicswap.SwapRequest
import io.horizontalsystems.swapkit.atomicswap.SwapResponse
import io.horizontalsystems.swapkit.atomicswap.hexToByteArray
import io.horizontalsystems.swapkit.atomicswap.toHexString
import io.reactivex.disposables.CompositeDisposable

class MainViewModel : ViewModel(), BitcoinKit.Listener {

    enum class State {
        STARTED, STOPPED
    }

    val supportedCoins = MutableLiveData<Collection<String>>()
    val transactionsBtc = MutableLiveData<List<TransactionInfo>>()
    val transactionsBch = MutableLiveData<List<TransactionInfo>>()
    val balanceBtc = MutableLiveData<Long>()
    val balanceBch = MutableLiveData<Long>()
    val lastBlockBtc = MutableLiveData<BlockInfo>()
    val lastBlockBch = MutableLiveData<BlockInfo>()
    val stateBtc = MutableLiveData<BitcoinCore.KitState>()
    val stateBch = MutableLiveData<BitcoinCore.KitState>()
    val status = MutableLiveData<State>()
    lateinit var networkNameBtc: String
    lateinit var networkNameBch: String
    var feePriorityBtc: FeePriority = FeePriority.Medium
    private val disposables = CompositeDisposable()

    private var started = false
        set(value) {
            field = value
            status.value = (if (value) State.STARTED else State.STOPPED)
        }

    lateinit var bitcoinKit: BitcoinKit
    lateinit var bitcoinCashKit: BitcoinCashKit

    private val walletId = "MyWallet"
    private val networkTypeBtc = BitcoinKit.NetworkType.TestNet
    private val networkTypeBch = BitcoinCashKit.NetworkType.TestNet

    val requestText = MutableLiveData<String>()
    val responseText = MutableLiveData<String>()
    private val swapKit = SwapKit(App.instance)
    private val separator = "|"

    init {
        init()
    }

    private fun init() {
//        val words = "choice extend magnet about ribbon quote armed length stand color brave someone".split(" ") // initiator
        val words = "someone brave color stand length armed quote ribbon about magnet extend choice".split(" ") // responder

        bitcoinKit = BitcoinKit(App.instance, words, walletId, networkTypeBtc, confirmationsThreshold = 1, peerSize = 2, syncMode = BitcoinCore.SyncMode.NewWallet())
        bitcoinCashKit = BitcoinCashKit(App.instance, words, walletId, networkTypeBch, confirmationsThreshold = 1, peerSize = 2, syncMode = BitcoinCore.SyncMode.NewWallet())

        networkNameBtc = bitcoinKit.networkName
        networkNameBch = bitcoinCashKit.networkName
        balanceBtc.value = bitcoinKit.balance
        balanceBch.value = bitcoinCashKit.balance

        retrieveBtcTransactions()
        retrieveBchTransactions()

        lastBlockBtc.value = bitcoinKit.lastBlockInfo
        lastBlockBch.value = bitcoinCashKit.lastBlockInfo
        stateBtc.value = BitcoinCore.KitState.NotSynced
        stateBch.value = BitcoinCore.KitState.NotSynced

        started = false

        swapKit.registerSwapBlockchainCreator("BTC", BitcoinSwapBlockchainCreator(bitcoinKit))
        swapKit.registerSwapBlockchainCreator("BCH", BitcoinSwapBlockchainCreator(bitcoinCashKit))
        swapKit.init()
        swapKit.processNext()

        supportedCoins.value = swapKit.supportedCoins

        bitcoinKit.listener = object : BitcoinKit.Listener {
            override fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>) {
                retrieveBtcTransactions()
            }

            override fun onBalanceUpdate(balance: Long) {
                balanceBtc.postValue(balance)
            }

            override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
                lastBlockBtc.postValue(blockInfo)
            }

            override fun onKitStateUpdate(state: BitcoinCore.KitState) {
                stateBtc.postValue(state)
            }
        }

        bitcoinCashKit.listener = object : BitcoinCashKit.Listener {
            override fun onBalanceUpdate(balance: Long) {
                balanceBch.postValue(balance)
            }

            override fun onKitStateUpdate(state: BitcoinCore.KitState) {
                stateBch.postValue(state)
            }

            override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
                lastBlockBch.postValue(blockInfo)
            }

            override fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>) {
                retrieveBchTransactions()
            }
        }
    }

    private fun retrieveBtcTransactions() {
        bitcoinKit.transactions().subscribe { txList: List<TransactionInfo> ->
            transactionsBtc.value = txList
        }.let {
            disposables.add(it)
        }
    }

    private fun retrieveBchTransactions() {
        bitcoinCashKit.transactions().subscribe { txList: List<TransactionInfo> ->
            transactionsBch.value = txList
        }.let {
            disposables.add(it)
        }
    }

    fun start() {
        if (started) return
        started = true

        bitcoinKit.start()
        bitcoinCashKit.start()
    }

    fun clear() {
        bitcoinKit.stop()
        BitcoinKit.clear(App.instance, networkTypeBtc, walletId)

        bitcoinCashKit.stop()
        BitcoinCashKit.clear(App.instance, networkTypeBch, walletId)

        init()
    }

    fun showDebugInfo() {
        bitcoinKit.showDebugInfo()
        bitcoinCashKit.showDebugInfo()
    }

    private fun feeRateFromPriority(feePriority: FeePriority): Int {
        val lowPriority = 20
        val mediumPriority = 42
        val highPriority = 81
        return when (feePriority) {
            FeePriority.Lowest -> lowPriority
            FeePriority.Low -> (lowPriority + mediumPriority) / 2
            FeePriority.Medium -> mediumPriority
            FeePriority.High -> (mediumPriority + highPriority) / 2
            FeePriority.Highest -> highPriority
            is FeePriority.Custom -> feePriority.feeRate.toInt()
        }
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

    val modeLiveData = MutableLiveData<Mode>(Mode.INITIATOR)

    fun setMode(mode: Mode) {
        modeLiveData.postValue(mode)
    }

    enum class Mode {
        INITIATOR, RESPONDER
    }

}
