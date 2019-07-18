package io.horizontalsystems.atomicswap

import io.horizontalsystems.bitcoincore.AbstractKit
import io.horizontalsystems.bitcoincore.TransactionFilter
import io.horizontalsystems.bitcoincore.WatchedTransactionManager
import io.horizontalsystems.bitcoincore.extensions.toHexString
import io.horizontalsystems.bitcoincore.extensions.toReversedHex
import io.horizontalsystems.bitcoincore.io.BitcoinInput
import io.horizontalsystems.bitcoincore.io.BitcoinOutput
import io.horizontalsystems.bitcoincore.models.Transaction
import io.horizontalsystems.bitcoincore.models.TransactionOutput
import io.horizontalsystems.bitcoincore.storage.FullTransaction
import io.horizontalsystems.bitcoincore.storage.UnspentOutput
import io.horizontalsystems.bitcoincore.transactions.scripts.*
import io.horizontalsystems.bitcoincore.utils.Utils
import io.horizontalsystems.swapkit.atomicswap.*

class BitcoinSwapBlockchainCreator(private val bitcoinKit: AbstractKit) : ISwapBlockchainCreator {
    private val scriptBuilder = SwapScriptBuilder()

    override fun create(): ISwapBlockchain {
        return BitcoinSwapBlockchain(bitcoinKit, scriptBuilder)
    }
}

class BitcoinSwapBlockchain(private val bitcoinKit: AbstractKit, private val scriptBuilder: SwapScriptBuilder) : ISwapBlockchain {

    override fun getRedeemPublicKey(): PublicKey {
        val receivePublicKey = bitcoinKit.receivePublicKey()

        return PublicKey(receivePublicKey.publicKeyHash, receivePublicKey.path)
    }

    override fun getRefundPublicKey(): PublicKey {
        val changePublicKey = bitcoinKit.changePublicKey()

        return PublicKey(changePublicKey.publicKeyHash, changePublicKey.path)
    }

    override fun sendBailTx(partnerRedeemPKH: ByteArray, secretHash: ByteArray, myRefundPKH: ByteArray, myRefundTime: Long, amount: String): BitcoinBailTx {
        val feeRate = 42
        val scriptHash = scriptBuilder.bailTxScriptHash(partnerRedeemPKH, secretHash, myRefundPKH, myRefundTime)
        val amountNumeric = amount.toDouble().times(100_000_000).toLong()

        val fullTransaction = bitcoinKit.send(scriptHash, ScriptType.P2SH, amountNumeric, true, feeRate)
        val output = fullTransaction.outputs.first {
            it.keyHash?.contentEquals(scriptHash) ?: false
        }

        return BitcoinBailTx(fullTransaction.header.hash, output.index, output.lockingScript, amountNumeric, scriptHash)
    }

    override fun setBailTxListener(listener: ISwapBailTxListener, myRedeemPKH: ByteArray, secretHash: ByteArray, partnerRefundPKH: ByteArray, partnerRefundTime: Long) {
        val scriptHash = scriptBuilder.bailTxScriptHash(myRedeemPKH, secretHash, partnerRefundPKH, partnerRefundTime)

        bitcoinKit.watchTransaction(TransactionFilter.P2SHOutput(scriptHash), object : WatchedTransactionManager.Listener {
            override fun onTransactionSeenP2SH(tx: FullTransaction, outputIndex: Int) {
                val output = tx.outputs[outputIndex]
                listener.onBailTransactionSeen(BitcoinBailTx(tx.header.hash, outputIndex, output.lockingScript, output.value, scriptHash))
            }
        })
    }

    override fun sendRedeemTx(myRedeemPKH: ByteArray, myRedeemPKId: String, secret: ByteArray, secretHash: ByteArray, partnerRefundPKH: ByteArray, partnerRefundTime: Long, bailTx: BailTx): RedeemTx {
        bailTx as BitcoinBailTx

        val publicKey = bitcoinKit.getPublicKeyByPath(myRedeemPKId)

        val bailOutput = TransactionOutput(bailTx.amount, bailTx.outputIndex, bailTx.lockingScript, ScriptType.P2SH)

        val redeemScript = scriptBuilder.bailScript(myRedeemPKH, secretHash, partnerRefundPKH, partnerRefundTime)
        bailOutput.redeemScript = redeemScript

        val fullTransaction = bitcoinKit.redeem(UnspentOutput(bailOutput, publicKey, Transaction(), null), bitcoinKit.receiveAddress(), 42) { signature, publicKeyHash ->
            OpCodes.push(signature) + OpCodes.push(publicKeyHash) + OpCodes.push(secret) + OP_1.toByte() + OpCodes.push(redeemScript)
        }

        return BitcoinRedeemTx(fullTransaction.header.hash, secret)
    }

    override fun setRedeemTxListener(listener: ISwapRedeemTxListener, bailTx: BailTx) {
        bailTx as BitcoinBailTx

        bitcoinKit.watchTransaction(TransactionFilter.Outpoint(bailTx.txHash, bailTx.outputIndex.toLong()), object : WatchedTransactionManager.Listener {
            override fun onTransactionSeenOutpoint(tx: FullTransaction, inputIndex: Int) {
                listener.onRedeemTransactionSeen(BitcoinRedeemTx(tx.header.hash, byteArrayOf()))
            }
        })
    }

    override fun serializeBailTx(bailTx: BailTx): ByteArray {
        check(bailTx is BitcoinBailTx)

        return BitcoinOutput()
            .write(bailTx.txHash)
            .writeInt(bailTx.outputIndex)
            .writeLong(bailTx.amount)
            .writeVarInt(bailTx.lockingScript.size.toLong())
            .write(bailTx.lockingScript)
            .write(bailTx.scriptHash)
            .toByteArray()
    }

    override fun deserializeBailTx(data: ByteArray): BailTx {
        return BitcoinInput(data).use { input ->
            val txHash = input.readBytes(32)
            val outputIndex = input.readInt()
            val amount = input.readLong()
            val lockingScript = input.readBytes(input.readVarInt().toInt())
            val scriptHash = input.readBytes(20)

            BitcoinBailTx(txHash, outputIndex, lockingScript, amount, scriptHash)
        }
    }

    override fun serializeRedeemTx(redeemTx: RedeemTx): ByteArray {
        check(redeemTx is BitcoinRedeemTx)

        return BitcoinOutput()
            .write(redeemTx.txHash)
            .write(redeemTx.secret)
            .toByteArray()
    }

    override fun deserializeRedeemTx(data: ByteArray): RedeemTx {
        return BitcoinInput(data).use {input ->
            BitcoinRedeemTx(input.readBytes(32), input.readBytes(32))
        }

    }
}

class BitcoinRedeemTx(val txHash: ByteArray, override var secret: ByteArray) : RedeemTx() {
    override fun toString(): String {
        return "txHash: ${txHash.toReversedHex()}"
    }
}

class BitcoinBailTx(val txHash: ByteArray, val outputIndex: Int, val lockingScript: ByteArray, val amount: Long, val scriptHash: ByteArray) : BailTx() {

    override fun toString(): String {
        return "txHash: ${txHash.toReversedHex()}, scriptHash: ${scriptHash.toHexString()}"
    }

}

class SwapScriptBuilder {
    fun bailTxScriptHash(redeemPKH: ByteArray, secretHash: ByteArray, refundPKH: ByteArray, refundTime: Long): ByteArray {
        val script = bailScript(redeemPKH, secretHash, refundPKH, refundTime)

        return Utils.sha256Hash160(script)

    }

    fun bailScript(redeemPKH: ByteArray, secretHash: ByteArray, refundPKH: ByteArray, refundTime: Long): ByteArray {
        //63
        //82 01 20 88 a8 20 f7e8e457e8ae38429d286dbc8b7c50ce76d131ce9eaf6b09e18ab72a29a57680 88 76 a9 14 765f12e7048ca905e4313123cb53c49d5814142b
        //67
        //04 03b5135d b1 75 76 a9 14 d450397d177910255346a3f85262884bd68c1c22
        //68
        //88 ac

        // OP_IF
        // OP_SIZE OP_PUSHBYTES_1 20
        // OP_EQUALVERIFY OP_SHA256 OP_PUSHBYTES_32 f7e8e457e8ae38429d286dbc8b7c50ce76d131ce9eaf6b09e18ab72a29a57680
        // OP_EQUALVERIFY OP_DUP OP_HASH160
        // OP_PUSHBYTES_20 765f12e7048ca905e4313123cb53c49d5814142b
        //OP_ELSE
        //OP_PUSHBYTES_4 03b5135d
        // OP_CHECKLOCKTIMEVERIFY OP_DROP OP_DUP OP_HASH160 OP_PUSHBYTES_20 d450397d177910255346a3f85262884bd68c1c22
        //OP_ENDIF
        //OP_EQUALVERIFY OP_CHECKSIG

        return byteArrayOf() +
                opCodeByte(OP_IF) +
                opCodeByte(OP_SIZE) + OpCodes.push(byteArrayOf(0x20)) +
                opCodeByte(OP_EQUALVERIFY) + opCodeByte(OP_SHA256) + OpCodes.push(secretHash) +
                opCodeByte(OP_EQUALVERIFY) + opCodeByte(OP_DUP) + opCodeByte(OP_HASH160) +
                OpCodes.push(redeemPKH) +
                opCodeByte(OP_ELSE) +
                OpCodes.push(Utils.intToByteArray(refundTime.toInt()).reversedArray()) +
                opCodeByte(OP_CHECKLOCKTIMEVERIFY) +
                opCodeByte(OP_DROP) + opCodeByte(OP_DUP) + opCodeByte(OP_HASH160) + OpCodes.push(refundPKH) +
                opCodeByte(OP_ENDIF) +
                opCodeByte(OP_EQUALVERIFY) + opCodeByte(OP_CHECKSIG)
    }


    fun opCodeByte(v: Int): Byte {
        return v.toByte()
    }
}