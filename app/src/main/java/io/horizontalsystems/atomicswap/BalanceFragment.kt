package io.horizontalsystems.atomicswap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import io.horizontalsystems.bitcoincore.BitcoinCore
import kotlinx.android.synthetic.main.fragment_balance.*
import java.text.SimpleDateFormat
import java.util.*

class BalanceFragment : Fragment() {

    lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(MainViewModel::class.java)

            viewModel.balanceBtc.observe(this, Observer { balance ->
                balanceValue.text = when (balance) {
                    null -> ""
                    else -> NumberFormatHelper.cryptoAmountFormat.format(balance / 100_000_000.0)
                }
            })

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            viewModel.lastBlockBtc.observe(this, Observer {
                it?.let { blockInfo ->
                    lastBlockValue.text = blockInfo.height.toString()

                    val strDate = dateFormat.format(Date(blockInfo.timestamp * 1000))
                    lastBlockDateValue.text = strDate
                }
            })

            viewModel.stateBtc.observe(this, Observer { state ->
                when (state) {
                    is BitcoinCore.KitState.Synced -> {
                        stateValue.text = "synced"
                    }
                    is BitcoinCore.KitState.Syncing -> {
                        stateValue.text = "syncing ${"%.3f".format(state.progress)}"
                    }
                    is BitcoinCore.KitState.NotSynced -> {
                        stateValue.text = "not synced"
                    }
                }
            })

            viewModel.status.observe(this, Observer {
                buttonStart.isEnabled = it != MainViewModel.State.STARTED
            })

        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_balance, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        networkName.text = viewModel.networkNameBtc

        buttonStart.setOnClickListener {
            viewModel.start()
        }

        buttonClear.setOnClickListener {
            viewModel.clear()
        }

        buttonDebug.setOnClickListener {
            viewModel.showDebugInfo()
        }
    }
}
