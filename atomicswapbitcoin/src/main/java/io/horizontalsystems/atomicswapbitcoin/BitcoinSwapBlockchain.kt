package io.horizontalsystems.atomicswapbitcoin

import io.horizontalsystems.bitcoincore.AbstractKit
import io.horizontalsystems.bitcoincore.TransactionFilter
import io.horizontalsystems.bitcoincore.WatchedTransactionManager
import io.horizontalsystems.bitcoincore.io.BitcoinInput
import io.horizontalsystems.bitcoincore.io.BitcoinOutput
import io.horizontalsystems.bitcoincore.models.Transaction
import io.horizontalsystems.bitcoincore.models.TransactionOutput
import io.horizontalsystems.bitcoincore.storage.FullTransaction
import io.horizontalsystems.bitcoincore.storage.UnspentOutput
import io.horizontalsystems.bitcoincore.transactions.scripts.OP_1
import io.horizontalsystems.bitcoincore.transactions.scripts.OpCodes
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.swapkit.atomicswap.*

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
        check(bailTx is BitcoinBailTx)

        val publicKey = bitcoinKit.getPublicKeyByPath(myRedeemPKId)

        val bailOutput = TransactionOutput(bailTx.amount, bailTx.outputIndex, bailTx.lockingScript, ScriptType.P2SH).apply {
            this.transactionHash = bailTx.txHash
        }

        val redeemScript = scriptBuilder.bailScript(myRedeemPKH, secretHash, partnerRefundPKH, partnerRefundTime)
        bailOutput.redeemScript = redeemScript

        val fullTransaction = bitcoinKit.redeem(UnspentOutput(bailOutput, publicKey, Transaction(), null), bitcoinKit.receiveAddress(), 42) { signature, publicKeyHash ->
            OpCodes.push(signature) + OpCodes.push(publicKeyHash) + OpCodes.push(secret) + OP_1.toByte() + OpCodes.push(redeemScript)
        }

        return BitcoinRedeemTx(fullTransaction.header.hash, secret)
    }

    override fun setRedeemTxListener(listener: ISwapRedeemTxListener, bailTx: BailTx) {
        check(bailTx is BitcoinBailTx)

        bitcoinKit.watchTransaction(TransactionFilter.Outpoint(bailTx.txHash, bailTx.outputIndex.toLong()), object : WatchedTransactionManager.Listener {
            override fun onTransactionSeenOutpoint(tx: FullTransaction, inputIndex: Int) {
                listener.onRedeemTransactionSeen(BitcoinRedeemTx(tx.header.hash, scriptBuilder.parseSecret(tx.inputs[inputIndex].sigScript)))
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
        return BitcoinInput(data).use { input ->
            BitcoinRedeemTx(input.readBytes(32), input.readBytes(32))
        }
    }
}
