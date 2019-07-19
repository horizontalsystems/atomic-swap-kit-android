package io.horizontalsystems.atomicswapbitcoin

import io.horizontalsystems.atomicswapcore.BailTx
import io.horizontalsystems.bitcoincore.extensions.toHexString
import io.horizontalsystems.bitcoincore.extensions.toReversedHex

class BitcoinBailTx(val txHash: ByteArray, val outputIndex: Int, val lockingScript: ByteArray, val amount: Long, val scriptHash: ByteArray) : BailTx() {

    override fun toString(): String {
        return "txHash: ${txHash.toReversedHex()}, scriptHash: ${scriptHash.toHexString()}"
    }

}