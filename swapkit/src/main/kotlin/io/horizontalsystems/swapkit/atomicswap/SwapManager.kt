package io.horizontalsystems.swapkit.atomicswap

const val separator = "|"

class SwapManager {

    fun generateRequestString(swap: Swap): String {
        val params = listOf(
                swap.id,
                swap.initiatorCoinCode,
                swap.responderCoinCode,
                "${swap.rate}",
                swap.initiatorAmount,
                swap.initiatorRedeemPKH.toHexString(),
                swap.initiatorRefundPKH.toHexString(),
                swap.secretHash.toHexString()
        )



        return params.joinToString(separator)
    }

    fun parseFromRequestString(requestString: String): Swap {
        val params = requestString.split(separator)
        val paramsIterator = params.iterator()

        return Swap().apply {
            id = paramsIterator.next()
            initiatorCoinCode = paramsIterator.next()
            responderCoinCode = paramsIterator.next()
            rate = paramsIterator.next().toDouble()
            initiatorAmount = paramsIterator.next()
            initiatorRedeemPKH = paramsIterator.next().hexToByteArray()
            initiatorRefundPKH = paramsIterator.next().hexToByteArray()
            secretHash = paramsIterator.next().hexToByteArray()
        }
    }

    fun generateResponseString(swap: Swap) : String {
        val params = listOf(
                swap.id,
                swap.responderRedeemPKH.toHexString(),
                swap.responderRefundPKH.toHexString(),
                "${swap.responderRefundTime}",
                "${swap.initiatorRefundTime}"
        )

        return params.joinToString(separator)
    }

    fun parseFromResponseString(responseString: String): Swap {
        val params = responseString.split(separator)
        val paramsIterator = params.iterator()

        return Swap().apply {
            id = paramsIterator.next()
            responderRedeemPKH = paramsIterator.next().hexToByteArray()
            responderRefundPKH = paramsIterator.next().hexToByteArray()
            responderRefundTime = paramsIterator.next().toLong()
            initiatorRefundTime = paramsIterator.next().toLong()
        }
    }
}
