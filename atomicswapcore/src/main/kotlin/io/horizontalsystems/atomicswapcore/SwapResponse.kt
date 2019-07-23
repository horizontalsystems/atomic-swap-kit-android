package io.horizontalsystems.atomicswapcore

class SwapResponse(
    val id: String,
    val initiatorRefundTime: Long,
    val responderRefundTime: Long,
    val responderRedeemPKH: ByteArray,
    val responderRefundPKH: ByteArray
) {

    constructor(swap: Swap) : this(
        swap.id,
        swap.initiatorRefundTime,
        swap.responderRefundTime,
        swap.responderRedeemPKH,
        swap.responderRefundPKH
    )

}
