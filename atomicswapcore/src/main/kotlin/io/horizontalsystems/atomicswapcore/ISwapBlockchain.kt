package io.horizontalsystems.atomicswapcore

interface ISwapBlockchain {
    fun sendBailTx(redeemPKH: ByteArray, secretHash: ByteArray, refundPKH: ByteArray, refundTime: Long, amount: String) : BailTx
    fun setBailTxListener(listener: ISwapBailTxListener, redeemPKH: ByteArray, secretHash: ByteArray, refundPKH: ByteArray, refundTime: Long)
    fun sendRedeemTx(redeemPKH: ByteArray, redeemPKId: String, secret: ByteArray, secretHash: ByteArray, refundPKH: ByteArray, refundTime: Long, bailTx: BailTx): RedeemTx
    fun setRedeemTxListener(listener: ISwapRedeemTxListener, bailTx: BailTx)
    fun getRedeemPublicKey(): PublicKey
    fun getRefundPublicKey(): PublicKey
    fun serializeBailTx(bailTx: BailTx): ByteArray
    fun deserializeBailTx(data: ByteArray): BailTx
    fun serializeRedeemTx(redeemTx: RedeemTx): ByteArray
    fun deserializeRedeemTx(data: ByteArray): RedeemTx
}

interface ISwapBailTxListener {
    fun onBailTransactionSeen(bailTx: BailTx)
}

interface ISwapRedeemTxListener {
    fun onRedeemTransactionSeen(redeemTx: RedeemTx)
}

open class BailTx
open class RedeemTx {
    open var secret: ByteArray = byteArrayOf()
}

data class PublicKey(val publicKeyHash: ByteArray, val publicKeyId: String)
