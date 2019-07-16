package io.horizontalsystems.swapkit.atomicswap

import androidx.room.*

@Entity
class Swap {

    @PrimaryKey
    var id = ""

    @TypeConverters(StateConverter::class)
    var state = State.REQUESTED

    var initiatorCoinCode = ""
    var responderCoinCode = ""

    var initiatorRedeemPKH = byteArrayOf()
    var initiatorRedeemPKId = ""
    var initiatorRefundPKH = byteArrayOf()
    var initiatorRefundPKId = ""
    var initiatorRefundTime = 0L
    var initiatorAmount = "0.0"
    var rate = 0.0
    var secret = byteArrayOf()
    var secretHash = byteArrayOf()
    var responderRedeemPKH = byteArrayOf()
    var responderRedeemPKId = ""
    var responderRefundPKH = byteArrayOf()
    var responderRefundPKId = ""
    var responderRefundTime = 0L
    var responderAmount = "0.0"

    enum class State(val value: Int) {
        REQUESTED(1),
        RESPONDED(2),
        INITIATOR_BAILED(3),
        RESPONDER_BAILED(4),
        INITIATOR_REDEEMED(5),
        RESPONDER_REDEEMED(6);

        companion object {
            fun fromValue(value: Int): State? {
                return values().find { it.value == value }
            }
        }
    }

    class StateConverter {
        @TypeConverter
        fun fromState(state: State) : Int {
            return state.value
        }

        @TypeConverter
        fun toState(value: Int) : State {
            return State.fromValue(value) ?: throw Exception("Unknown State")
        }
    }
}

@Dao
interface SwapDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(swap: Swap)

    @Query("SELECT * FROM Swap WHERE id = :id")
    fun load(id: String) : Swap

}
