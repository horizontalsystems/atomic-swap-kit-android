package io.horizontalsystems.atomicswap

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.horizontalsystems.bitcoincash.BitcoinCashKit
import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.bitcoincore.models.BlockInfo
import io.horizontalsystems.bitcoincore.models.FeePriority
import io.horizontalsystems.bitcoincore.models.TransactionInfo
import io.reactivex.disposables.CompositeDisposable

class MainViewModel : ViewModel(), BitcoinCashKit.Listener {

    enum class State {
        STARTED, STOPPED
    }

    val transactions = MutableLiveData<List<TransactionInfo>>()
    val balance = MutableLiveData<Long>()
    val lastBlock = MutableLiveData<BlockInfo>()
    val state = MutableLiveData<BitcoinCore.KitState>()
    val status = MutableLiveData<State>()
    lateinit var networkName: String
    var feePriority: FeePriority = FeePriority.Medium
    private val disposables = CompositeDisposable()

    private var started = false
        set(value) {
            field = value
            status.value = (if (value) State.STARTED else State.STOPPED)
        }

    lateinit var bitcoinKit: BitcoinCashKit

    private val walletId = "MyWallet"
    private val networkType = BitcoinCashKit.NetworkType.TestNet

    init {
        init()
    }

    private fun init() {
//        val words = "choice extend magnet about ribbon quote armed length stand color brave someone".split(" ") // initiator
        val words = "someone brave color stand length armed quote ribbon about magnet extend choice".split(" ") // responder

        bitcoinKit = BitcoinCashKit(App.instance, words, walletId, networkType, confirmationsThreshold = 1, peerSize = 2, syncMode = BitcoinCore.SyncMode.NewWallet())

        bitcoinKit.listener = this

        networkName = bitcoinKit.networkName
        balance.value = bitcoinKit.balance

        bitcoinKit.transactions().subscribe { txList: List<TransactionInfo> ->
            transactions.value = txList
        }.let {
            disposables.add(it)
        }

        lastBlock.value = bitcoinKit.lastBlockInfo
        state.value = BitcoinCore.KitState.NotSynced

        started = false
    }

    fun start() {
        if (started) return
        started = true

        bitcoinKit.start()
    }

    fun clear() {
        bitcoinKit.stop()
        BitcoinCashKit.clear(App.instance, networkType, walletId)

        init()
    }

    fun receiveAddress(): String {
        return bitcoinKit.receiveAddress()
    }

    fun send(address: String, amount: Long) {
        val feeRate = feeRateFromPriority(feePriority)
        bitcoinKit.send(address, amount, feeRate = feeRate)
    }

    fun fee(value: Long, address: String? = null): Long {
        val feeRate = feeRateFromPriority(feePriority)
        return bitcoinKit.fee(value, address, feeRate = feeRate)
    }

    fun showDebugInfo() {
        bitcoinKit.showDebugInfo()
    }

    //
    // BitcoinKit Listener implementations
    //
    override fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>) {
        bitcoinKit.transactions().subscribe { txList: List<TransactionInfo> ->
            transactions.postValue(txList)
        }.let {
            disposables.add(it)
        }
    }

    override fun onTransactionsDelete(hashes: List<String>) {
    }

    override fun onBalanceUpdate(balance: Long) {
        this.balance.postValue(balance)
    }

    override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
        this.lastBlock.postValue(blockInfo)
    }

    override fun onKitStateUpdate(state: BitcoinCore.KitState) {
        this.state.postValue(state)
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
}