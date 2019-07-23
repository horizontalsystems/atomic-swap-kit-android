package io.horizontalsystems.atomicswapcore

class SwapRequest(
    val id: String,
    val initiatorCoinCode: String,
    val responderCoinCode: String,
    val rate: Double,
    val initiatorAmount: String,
    val secretHash: ByteArray,
    val initiatorRedeemPKH: ByteArray,
    val initiatorRefundPKH: ByteArray
) {

    constructor(swap: Swap) : this(
        swap.id,
        swap.initiatorCoinCode,
        swap.responderCoinCode,
        swap.rate,
        swap.initiatorAmount,
        swap.secretHash,
        swap.initiatorRedeemPKH,
        swap.initiatorRefundPKH
    )

}
