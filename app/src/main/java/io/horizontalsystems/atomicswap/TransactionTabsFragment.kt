package io.horizontalsystems.atomicswap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import kotlinx.android.synthetic.main.fragment_transaction_tabs.*

class TransactionTabsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transaction_tabs, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pager.adapter = TransactionPagesAdapter(childFragmentManager)
    }

}

class TransactionPagesAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {
    val coins = listOf("BTC", "BCH")

    override fun getCount(): Int {
        return coins.size
    }

    override fun getItem(position: Int): Fragment {
        val fragment = TransactionsFragment()
        fragment.arguments = Bundle().apply {
            putString("coin", coins[position])
        }
        return fragment
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return coins[position]
    }
}
