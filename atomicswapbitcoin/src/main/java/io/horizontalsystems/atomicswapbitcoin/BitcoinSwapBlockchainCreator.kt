package io.horizontalsystems.atomicswapbitcoin

import io.horizontalsystems.atomicswapcore.ISwapBlockchain
import io.horizontalsystems.atomicswapcore.ISwapBlockchainCreator
import io.horizontalsystems.bitcoincore.AbstractKit

class BitcoinSwapBlockchainCreator(private val bitcoinKit: AbstractKit) : ISwapBlockchainCreator {
    private val scriptBuilder = SwapScriptBuilder()

    override fun create(): ISwapBlockchain {
        return BitcoinSwapBlockchain(bitcoinKit, scriptBuilder)
    }
}