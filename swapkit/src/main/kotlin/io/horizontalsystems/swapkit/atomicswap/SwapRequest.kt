package io.horizontalsystems.swapkit.atomicswap

class SwapRequest(
    val id: String,
    val initiatorCoinCode: String,
    val responderCoinCode: String,
    val rate: Double,
    val initiatorAmount: String,
    val initiatorRefundPKH: ByteArray,
    val initiatorRedeemPKH: ByteArray,
    val secretHash: ByteArray
) {

    constructor(swap: Swap) : this(
        swap.id,
        swap.initiatorCoinCode,
        swap.responderCoinCode,
        swap.rate,
        swap.initiatorAmount,
        swap.initiatorRefundPKH,
        swap.initiatorRedeemPKH,
        swap.secretHash
    )

}
