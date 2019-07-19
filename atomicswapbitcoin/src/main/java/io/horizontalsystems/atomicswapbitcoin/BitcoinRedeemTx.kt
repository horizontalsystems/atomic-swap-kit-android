package io.horizontalsystems.atomicswapbitcoin

import io.horizontalsystems.bitcoincore.extensions.toReversedHex
import io.horizontalsystems.swapkit.atomicswap.RedeemTx

class BitcoinRedeemTx(val txHash: ByteArray, override var secret: ByteArray) : RedeemTx() {
    override fun toString(): String {
        return "txHash: ${txHash.toReversedHex()}"
    }
}