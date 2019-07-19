package io.horizontalsystems.atomicswapbitcoin

import io.horizontalsystems.bitcoincore.AbstractKit
import io.horizontalsystems.swapkit.atomicswap.ISwapBlockchain
import io.horizontalsystems.swapkit.atomicswap.ISwapBlockchainCreator

class BitcoinSwapBlockchainCreator(private val bitcoinKit: AbstractKit) : ISwapBlockchainCreator {
    private val scriptBuilder = SwapScriptBuilder()

    override fun create(): ISwapBlockchain {
        return BitcoinSwapBlockchain(bitcoinKit, scriptBuilder)
    }
}