package io.horizontalsystems.swapkit.atomicswap

class SwapRequest(
    val id: String,
    val initiatorCoinCode: String,
    val responderCoinCode: String,
    val rate: Double,
    val initiatorAmount: String,
    val initiatorRedeemPKH: ByteArray,
    val initiatorRefundPKH: ByteArray,
    val secretHash: ByteArray
) {

    constructor(swap: Swap) : this(
        swap.id,
        swap.initiatorCoinCode,
        swap.responderCoinCode,
        swap.rate,
        swap.initiatorAmount,
        swap.initiatorRedeemPKH,
        swap.initiatorRefundPKH,
        swap.secretHash
    )

}
