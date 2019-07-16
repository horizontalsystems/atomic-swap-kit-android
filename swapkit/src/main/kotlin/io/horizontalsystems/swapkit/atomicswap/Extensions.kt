package io.horizontalsystems.swapkit.atomicswap

fun ByteArray.toHexString(): String {
    return this.joinToString(separator = "") {
        it.toInt().and(0xff).toString(16).padStart(2, '0')
    }
}

fun ByteArray.toReversedHex(): String {
    return reversedArray().toHexString()
}

fun String.hexToByteArray(): ByteArray {
    return ByteArray(this.length / 2) {
        this.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}

fun String.toReversedByteArray(): ByteArray {
    return hexToByteArray().reversedArray()
}

