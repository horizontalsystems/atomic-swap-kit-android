package io.horizontalsystems.swapkit.atomicswap

interface ISwapBlockchain {
    fun sendBailTx(partnerRedeemPKH: ByteArray, secretHash: ByteArray, myRefundPKH: ByteArray, myRefundTime: Long, amount: String) : BailTx
    fun setBailTxListener(listener: ISwapBailTxListener, myRedeemPKH: ByteArray, secretHash: ByteArray, partnerRefundPKH: ByteArray, partnerRefundTime: Long)
    fun sendRedeemTx(myRedeemPKH: ByteArray, myRedeemPKId: String, secret: ByteArray, secretHash: ByteArray, partnerRefundPKH: ByteArray, partnerRefundTime: Long, bailTx: BailTx): RedeemTx
    fun setRedeemTxListener(listener: ISwapRedeemTxListener, bailTx: BailTx)
    fun getRedeemPublicKey(): PublicKey
    fun getRefundPublicKey(): PublicKey
}

interface ISwapBailTxListener {
    fun onBailTransactionSeen(bailTx: BailTx)
}

interface ISwapRedeemTxListener {
    fun onRedeemTransactionSeen(redeemTx: RedeemTx)
}

open class BailTx
open class RedeemTx {
    var secret: ByteArray = byteArrayOf()
}

data class PublicKey(val publicKeyHash: ByteArray, val publicKeyId: String)
