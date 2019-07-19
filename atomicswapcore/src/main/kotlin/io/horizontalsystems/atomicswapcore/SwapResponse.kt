package io.horizontalsystems.atomicswapcore

class SwapResponse(
    val id: String,
    val responderRedeemPKH: ByteArray,
    val responderRefundPKH: ByteArray,
    val responderRefundTime: Long,
    val initiatorRefundTime: Long
) {

    constructor(swap: Swap) : this(
        swap.id,
        swap.responderRedeemPKH,
        swap.responderRefundPKH,
        swap.responderRefundTime,
        swap.initiatorRefundTime
    )

}
