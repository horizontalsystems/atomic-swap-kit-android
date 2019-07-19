package io.horizontalsystems.atomicswap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import io.horizontalsystems.bitcoincore.BitcoinCore
import kotlinx.android.synthetic.main.fragment_balance.*
import kotlinx.android.synthetic.main.view_holder_account.view.*
import java.text.SimpleDateFormat
import java.util.*

class BalanceFragment : Fragment() {

    lateinit var viewModel: MainViewModel
    private val accountsAdapter = AccountsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(MainViewModel::class.java)

            viewModel.accountsLiveData.observe(this, Observer { accounts ->
                accountsAdapter.accounts = accounts.toList()
                accountsAdapter.notifyDataSetChanged()
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

        listAccounts.adapter = accountsAdapter

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

class ViewHolderAccount(private val containerView: View) : RecyclerView.ViewHolder(containerView) {

    fun bind(account: MainViewModel.Account) {
        containerView.networkName.text = account.networkName
        containerView.receiveAddressValue.text = account.receiveAddress
        containerView.balanceValue

        containerView.balanceValue.text = NumberFormatHelper.cryptoAmountFormat.format(account.balance / 100_000_000.0)

        account.lastBlockInfo?.let { blockInfo ->
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            containerView.lastBlockValue.text = blockInfo.height.toString()

            val strDate = dateFormat.format(Date(blockInfo.timestamp * 1000))
            containerView.lastBlockDateValue.text = strDate
        }

        containerView.stateValue.text = when (val state = account.state) {
            is BitcoinCore.KitState.Synced -> "synced"
            is BitcoinCore.KitState.Syncing -> "syncing ${"%.3f".format(state.progress)}"
            is BitcoinCore.KitState.NotSynced -> "not synced"
        }
    }

}

class AccountsAdapter : RecyclerView.Adapter<ViewHolderAccount>() {
    var accounts = listOf<MainViewModel.Account>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderAccount {
        return ViewHolderAccount(
            LayoutInflater.from(parent.context).inflate(
                R.layout.view_holder_account,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return accounts.size
    }

    override fun onBindViewHolder(holder: ViewHolderAccount, position: Int) {
        holder.bind(accounts[position])
    }

}
