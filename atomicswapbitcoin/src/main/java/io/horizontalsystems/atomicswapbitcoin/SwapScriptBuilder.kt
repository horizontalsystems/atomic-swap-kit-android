package io.horizontalsystems.atomicswapbitcoin

import io.horizontalsystems.bitcoincore.io.BitcoinInput
import io.horizontalsystems.bitcoincore.transactions.scripts.*
import io.horizontalsystems.bitcoincore.utils.Utils

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

    fun parseSecret(sigScript: ByteArray): ByteArray {
        val scriptData = deserialize(sigScript)
        return scriptData[2]
    }

    fun deserialize(script: ByteArray): MutableList<ByteArray> {
        val data = mutableListOf<ByteArray>()

        BitcoinInput(script).use { input ->
            while (true) {
                val dataSize = input.read()

                if (dataSize == -1) break
                when (dataSize) {
                    0x00 -> {
                        data.add(byteArrayOf())
                    }
                    in 0x01..0x4b -> {
                        data.add(input.readBytes(dataSize))
                    }
                    0x4c -> {
                        val dataSize2 = input.readUnsignedByte()
                        data.add(input.readBytes(dataSize2))
                    }
                    0x4d -> {
                        val dataSize2 = input.readUnsignedShort()
                        data.add(input.readBytes(dataSize2))
                    }
                    0x4e -> {
                        val dataSize2 = input.readUnsignedInt()
                        data.add(input.readBytes(dataSize2.toInt()))
                    }
                    0x4f -> {
                        data.add(byteArrayOf((-1).toByte()))
                    }
                    0x51 -> {
                        data.add(byteArrayOf(0x51.toByte()))
                    }
                    in 0x52..0x60 -> {
                        data.add(byteArrayOf((dataSize - 0x50).toByte()))
                    }
                }
            }
        }

        return data
    }
}