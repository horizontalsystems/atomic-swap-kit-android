package io.horizontalsystems.atomicswapbitcoin

import io.horizontalsystems.atomicswapcore.RedeemTx
import io.horizontalsystems.bitcoincore.extensions.toReversedHex

class BitcoinRedeemTx(val txHash: ByteArray, override var secret: ByteArray) : RedeemTx() {
    override fun toString(): String {
        return "txHash: ${txHash.toReversedHex()}"
    }
}