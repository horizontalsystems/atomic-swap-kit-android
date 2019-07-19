package io.horizontalsystems.atomicswap

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.horizontalsystems.atomicswapbitcoin.BitcoinSwapBlockchainCreator
import io.horizontalsystems.atomicswapcore.*
import io.horizontalsystems.bitcoincash.BitcoinCashKit
import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.bitcoincore.models.BlockInfo
import io.horizontalsystems.bitcoincore.models.FeePriority
import io.horizontalsystems.bitcoincore.models.TransactionInfo
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.reactivex.disposables.CompositeDisposable

class MainViewModel : ViewModel(), BitcoinKit.Listener {

    enum class State {
        STARTED, STOPPED
    }

    class Account(
            var networkName: String,
            var balance: Long,
            var lastBlockInfo: BlockInfo?,
            var state: BitcoinCore.KitState
    )


    val supportedCoins = MutableLiveData<Collection<String>>()
    val accounts = mutableMapOf<String, Account>()
    val accountsLiveData = MutableLiveData<Collection<Account>>()
    val transactionsBtc = MutableLiveData<List<TransactionInfo>>()
    val transactionsBch = MutableLiveData<List<TransactionInfo>>()
    val status = MutableLiveData<State>()
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

        accounts["BTC"] = Account(bitcoinKit.networkName, bitcoinKit.balance, bitcoinKit.lastBlockInfo, BitcoinCore.KitState.NotSynced)
        accounts["BCH"] = Account(bitcoinCashKit.networkName, bitcoinCashKit.balance, bitcoinCashKit.lastBlockInfo, BitcoinCore.KitState.NotSynced)
        accountsLiveData.postValue(accounts.values)

        retrieveBtcTransactions()
        retrieveBchTransactions()

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
                accounts["BTC"]?.balance = balance
                accountsLiveData.postValue(accounts.values)
            }

            override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
                accounts["BTC"]?.lastBlockInfo = blockInfo
                accountsLiveData.postValue(accounts.values)
            }

            override fun onKitStateUpdate(state: BitcoinCore.KitState) {
                accounts["BTC"]?.state = state
                accountsLiveData.postValue(accounts.values)
            }
        }

        bitcoinCashKit.listener = object : BitcoinCashKit.Listener {
            override fun onBalanceUpdate(balance: Long) {
                accounts["BCH"]?.balance = balance
                accountsLiveData.postValue(accounts.values)
            }

            override fun onKitStateUpdate(state: BitcoinCore.KitState) {
                accounts["BCH"]?.state = state
                accountsLiveData.postValue(accounts.values)
            }

            override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
                accounts["BCH"]?.lastBlockInfo = blockInfo
                accountsLiveData.postValue(accounts.values)
            }

            override fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>) {
                retrieveBchTransactions()
            }
        }
    }

    private fun retrieveBtcTransactions() {
        bitcoinKit.transactions().subscribe { txList: List<TransactionInfo> ->
            transactionsBtc.postValue(txList)
        }.let {
            disposables.add(it)
        }
    }

    private fun retrieveBchTransactions() {
        bitcoinCashKit.transactions().subscribe { txList: List<TransactionInfo> ->
            transactionsBch.postValue(txList)
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
