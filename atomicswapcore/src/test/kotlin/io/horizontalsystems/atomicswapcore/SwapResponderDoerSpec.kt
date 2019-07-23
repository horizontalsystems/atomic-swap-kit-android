package io.horizontalsystems.atomicswapcore

import com.nhaarman.mockito_kotlin.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SwapResponderDoerSpec : Spek({
    val initiatorBlockchain = mock<ISwapBlockchain>()
    val responderBlockchain = mock<ISwapBlockchain>()
    val swap = mock<Swap>()
    val storage by memoized { mock<SwapDao>() }
    val delegate by memoized { mock<SwapResponder>() }

    val responderDoer by memoized { SwapResponderDoer(initiatorBlockchain, responderBlockchain, swap, storage) }

    val responderRedeemPKH = byteArrayOf(1, 2, 3, 4)
    val responderRedeemPKId = "responderRedeemPKId"
    val secretHash = byteArrayOf(11, 2, 4, 54)
    val initiatorRefundPKH = byteArrayOf(3, 4, 5, 6)
    val initiatorRefundTime = 123456798L
    val initiatorAmount = "0.5"
    val rate = 0.5
    val responderAmount = "1.0"

    val initiatorRedeemPKH = byteArrayOf(4, 5, 6, 7)
    val responderRefundPKH = byteArrayOf(5, 6, 7, 8)
    val responderRefundTime = 345567679L
    val bailTx = mock<BailTx>()
    val bailTxSerialized = byteArrayOf(1, 2, 3)

    val redeemTx = mock<RedeemTx>()
    val redeemTxSerialized = byteArrayOf(123, 123, 123)

    whenever(swap.responderRedeemPKH).thenReturn(responderRedeemPKH)
    whenever(swap.responderRedeemPKId).thenReturn(responderRedeemPKId)
    whenever(swap.secretHash).thenReturn(secretHash)
    whenever(swap.initiatorRefundPKH).thenReturn(initiatorRefundPKH)
    whenever(swap.initiatorRefundTime).thenReturn(initiatorRefundTime)
    whenever(swap.initiatorAmount).thenReturn(initiatorAmount)
    whenever(swap.rate).thenReturn(rate)
    whenever(swap.initiatorRedeemPKH).thenReturn(initiatorRedeemPKH)
    whenever(swap.responderRefundPKH).thenReturn(responderRefundPKH)
    whenever(swap.responderRefundTime).thenReturn(responderRefundTime)
    whenever(swap.initiatorRedeemTx).thenReturn(redeemTxSerialized)
    whenever(swap.initiatorBailTx).thenReturn(bailTxSerialized)


    beforeEachTest {
        responderDoer.delegate = delegate
    }

    describe("#watchInitiatorBail") {
        it("sets watcher for initiator bail tx") {
            responderDoer.watchInitiatorBail()

            verify(initiatorBlockchain).setBailTxListener(
                responderDoer,
                responderRedeemPKH,
                secretHash,
                initiatorRefundPKH,
                initiatorRefundTime
            )
        }
    }

    describe("#onBailTransactionSeen") {
        describe("when bail tx seen for the first time (RESPONDED)") {
            beforeEach {
                whenever(swap.state).thenReturn(Swap.State.RESPONDED)
                whenever(initiatorBlockchain.serializeBailTx(bailTx)).thenReturn(bailTxSerialized)

                responderDoer.onBailTransactionSeen(bailTx)
            }

            it("sets swap's initiator bail tx and update its state to INITIATOR_BAILED") {
                inOrder(swap, storage).run {
                    verify(swap).initiatorBailTx = bailTxSerialized
                    verify(swap).state = Swap.State.INITIATOR_BAILED
                    verify(storage).save(swap)
                }
            }

            it("triggers delegate processNext") {
                verify(delegate).processNext()
            }
        }

        describe("when bail tx seen for the second time (RESPONDED)") {
            beforeEach {
                whenever(swap.state).thenReturn(Swap.State.INITIATOR_BAILED)
                responderDoer.onBailTransactionSeen(bailTx)
            }

            it("does nothing") {
                verifyNoMoreInteractions(storage)
                verifyNoMoreInteractions(delegate)
            }
        }
    }

    describe("#bail") {
        beforeEach {
            whenever(responderBlockchain.sendBailTx(initiatorRedeemPKH,
                secretHash,
                responderRefundPKH,
                responderRefundTime,
                responderAmount
            )).thenReturn(bailTx)

            whenever(responderBlockchain.serializeBailTx(bailTx)).thenReturn(bailTxSerialized)

            responderDoer.bail()
        }

        it("sends responder bail tx") {
            verify(responderBlockchain).sendBailTx(
                initiatorRedeemPKH,
                secretHash,
                responderRefundPKH,
                responderRefundTime,
                responderAmount
            )
        }

        it("sets swaps responderBailTx and update its state to RESPONDER_BAILED") {
            inOrder(swap, storage).run {
                verify(swap).responderBailTx = bailTxSerialized
                verify(swap).state = Swap.State.RESPONDER_BAILED
                verify(storage).save(swap)
            }
        }

        it("triggers delegate processNext") {
            verify(delegate).processNext()
        }
    }

    describe("#watchInitiatorRedeem") {
        it("sets watcher for initiator redeem tx") {
            whenever(swap.responderBailTx).thenReturn(bailTxSerialized)
            whenever(responderBlockchain.deserializeBailTx(bailTxSerialized)).thenReturn(bailTx)

            responderDoer.watchInitiatorRedeem()

            verify(responderBlockchain).setRedeemTxListener(responderDoer, bailTx)
        }
    }

    describe("#onRedeemTransactionSeen") {
        describe("when redeem tx is seen for the first time (RESPONDER_BAILED)") {
            beforeEach {
                whenever(swap.state).thenReturn(Swap.State.RESPONDER_BAILED)
                whenever(responderBlockchain.serializeRedeemTx(redeemTx)).thenReturn(redeemTxSerialized)

                responderDoer.onRedeemTransactionSeen(redeemTx)
            }

            it("sets swap initiatorRedeemTx and update its status to INITIATOR_REDEEMED") {
                inOrder(swap, storage).run {
                    verify(swap).initiatorRedeemTx = redeemTxSerialized
                    verify(swap).state = Swap.State.INITIATOR_REDEEMED
                    verify(storage).save(swap)
                }
            }

            it("triggers delegate processNext") {
                verify(delegate).processNext()
            }
        }

        describe("when redeem tx is seen for the second time (INITIATOR_REDEEMED)") {
            it("does nothing") {
                whenever(swap.state).thenReturn(Swap.State.INITIATOR_REDEEMED)

                responderDoer.onRedeemTransactionSeen(redeemTx)

                verifyNoMoreInteractions(storage)
                verifyNoMoreInteractions(delegate)
            }
        }
    }

    describe("#redeem") {
        val secret = byteArrayOf(2, 3, 4, 5)

        beforeEach {
            whenever(redeemTx.secret).thenReturn(secret)
            whenever(responderBlockchain.deserializeRedeemTx(redeemTxSerialized)).thenReturn(redeemTx)
            whenever(initiatorBlockchain.deserializeBailTx(bailTxSerialized)).thenReturn(bailTx)

            responderDoer.redeem()
        }

        it("redeems responder tx") {
            verify(initiatorBlockchain).sendRedeemTx(
                responderRedeemPKH,
                responderRedeemPKId,
                secret,
                secretHash,
                initiatorRefundPKH,
                initiatorRefundTime,
                bailTx
            )
        }

        it("sets swap status to RESPONDER_REDEEMED") {
            inOrder(swap, storage).run {
                verify(swap).state = Swap.State.RESPONDER_REDEEMED
                verify(storage).save(swap)
            }
        }

        it("triggers delegate processNext") {
            verify(delegate).processNext()
        }
    }


})
