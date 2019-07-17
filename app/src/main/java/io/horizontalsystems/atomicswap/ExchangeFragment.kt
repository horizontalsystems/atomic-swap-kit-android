package io.horizontalsystems.atomicswap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.fragment_exchange.*

class ExchangeFragment : Fragment() {
    private lateinit var mainViewModel: MainViewModel
    private lateinit var exchangeViewModel: ExchangeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.let {
            mainViewModel = ViewModelProviders.of(it).get(MainViewModel::class.java)
            exchangeViewModel = ViewModelProviders.of(it).get(ExchangeViewModel::class.java)

            exchangeViewModel.bitcoinKit = mainViewModel.bitcoinKit
            exchangeViewModel.init()

            exchangeViewModel.requestText.observe(this, Observer {
                it?.let {
                    textRequest.text = it
                }
            })
            exchangeViewModel.responseText.observe(this, Observer {
                it?.let {
                    textResponse.text = it
                }
            })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_exchange, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val coins = ArrayAdapter<String>(this.context, android.R.layout.simple_list_item_1, arrayOf("BTC", "BCH"))

        fieldHave.adapter = coins
        fieldWant.adapter = coins

        buttonRequest.setOnClickListener {
            val coinHave = fieldHave.selectedItem.toString()
            val coinWant = fieldWant.selectedItem.toString()
            val rate = fieldRate.text.toString().toDoubleOrNull() ?: 0.0
            val amount = fieldAmount.text.toString().toDoubleOrNull() ?: 0.0

            exchangeViewModel.generateRequest(coinHave, coinWant, rate, amount)
        }

        buttonRespond.setOnClickListener {
            val request = fieldRequest.text.toString()
            exchangeViewModel.generateResponse(request)
        }

        buttonInit.setOnClickListener {
            val response = fieldResponse.text.toString()
            exchangeViewModel.startAtomicSwap(response)
        }

    }
}