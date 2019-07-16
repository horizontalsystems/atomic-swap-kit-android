package io.horizontalsystems.swapkit.atomicswap

class SwapFactory(private val db: SwapDatabase) {
    private val swapBlockchainCreator = mutableMapOf<String, ISwapBlockchainCreator>()

    fun registerSwapBlockchainCreator(coinCode: String, creator: ISwapBlockchainCreator) {
        swapBlockchainCreator[coinCode] = creator
    }

    fun createBlockchain(coinCode: String): ISwapBlockchain {
        return swapBlockchainCreator[coinCode]?.create()
                ?: throw AtomicSwapNotSupported(coinCode)
    }

    fun createAtomicSwapResponder(swap: Swap): SwapResponder {
        val initiatorBlockchain = createBlockchain(swap.initiatorCoinCode)
        val responderBlockchain = createBlockchain(swap.responderCoinCode)

        return SwapResponder(initiatorBlockchain, responderBlockchain, swap, db)
    }

    fun createAtomicSwapInitiator(swap: Swap): SwapInitiator {
        val initiatorBlockchain = createBlockchain(swap.initiatorCoinCode)
        val responderBlockchain = createBlockchain(swap.responderCoinCode)

        return SwapInitiator(initiatorBlockchain, responderBlockchain, swap, db)
    }
}

interface ISwapBlockchainCreator {
    fun create(): ISwapBlockchain
}

class AtomicSwapNotSupported(coinCode: String) : Exception("Atomic swap is not supported for $coinCode")
