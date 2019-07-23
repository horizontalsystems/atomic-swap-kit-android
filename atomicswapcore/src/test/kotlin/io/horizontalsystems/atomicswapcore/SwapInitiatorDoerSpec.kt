package io.horizontalsystems.atomicswapcore

import com.nhaarman.mockito_kotlin.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SwapInitiatorDoerSpec : Spek({
    val initiatorBlockchain = mock<ISwapBlockchain>()
    val responderBlockchain = mock<ISwapBlockchain>()
    val swap = mock<Swap>()
    val storage by memoized { mock<SwapDao>() }
    val delegate by memoized { mock<SwapInitiator>() }

    val initiatorDoer by memoized { SwapInitiatorDoer(initiatorBlockchain, responderBlockchain, swap, storage) }

    val responderRedeemPKH = byteArrayOf(1, 2, 3, 4)
    val secretHash = byteArrayOf(11, 2, 4, 54)
    val secret = byteArrayOf(2, 3, 4, 5)
    val initiatorRefundPKH = byteArrayOf(3, 4, 5, 6)
    val initiatorRedeemPKId = "initiatorRedeemPKId"
    val initiatorRefundTime = 123456798L
    val initiatorAmount = "0.5"
    val initiatorRedeemPKH = byteArrayOf(4, 5, 6, 7)
    val responderRefundPKH = byteArrayOf(5, 6, 7, 8)
    val responderRefundTime = 345567679L
    val bailTx = mock<BailTx>()
    val bailTxSerialized = byteArrayOf(1, 2, 3)

    whenever(swap.responderRedeemPKH).thenReturn(responderRedeemPKH)
    whenever(swap.secretHash).thenReturn(secretHash)
    whenever(swap.secret).thenReturn(secret)
    whenever(swap.initiatorRefundPKH).thenReturn(initiatorRefundPKH)
    whenever(swap.initiatorRedeemPKId).thenReturn(initiatorRedeemPKId)
    whenever(swap.initiatorRefundTime).thenReturn(initiatorRefundTime)
    whenever(swap.initiatorAmount).thenReturn(initiatorAmount)
    whenever(swap.initiatorRedeemPKH).thenReturn(initiatorRedeemPKH)
    whenever(swap.responderRefundPKH).thenReturn(responderRefundPKH)
    whenever(swap.responderRefundTime).thenReturn(responderRefundTime)

    beforeEachTest {
        initiatorDoer.delegate = delegate
    }

    describe("#bail") {
        beforeEach {
            initiatorDoer.bail()
        }

        it("sends bail tx") {
            verify(initiatorBlockchain).sendBailTx(
                responderRedeemPKH,
                secretHash,
                initiatorRefundPKH,
                initiatorRefundTime,
                initiatorAmount
            )
        }

        it("sets swap state to INITIATOR_BAILED and updates it in db") {
            inOrder(swap, storage).run {
                verify(swap).state = Swap.State.INITIATOR_BAILED
                verify(storage).save(swap)
            }
        }

        it("triggers delegate processNext") {
            verify(delegate).processNext()
        }
    }

    describe("#watchResponderBail") {
        it("sets watcher for responder bail tx") {
            initiatorDoer.watchResponderBail()

            verify(responderBlockchain).setBailTxListener(
                initiatorDoer,
                initiatorRedeemPKH,
                secretHash,
                responderRefundPKH,
                responderRefundTime
            )
        }
    }

    describe("#onBailTransactionSeen") {
        describe("when bail transaction is seen for the first time (INITIATOR_BAILED)") {
            beforeEach {
                whenever(swap.state).thenReturn(Swap.State.INITIATOR_BAILED)
                whenever(responderBlockchain.serializeBailTx(bailTx)).thenReturn(bailTxSerialized)
                initiatorDoer.onBailTransactionSeen(bailTx)
            }

            it("sets responder bail tx of swap and update its state to RESPONDER_BAILED") {
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

        describe("when bail transaction is seen for the second time (RESPONDER_BAILED)") {
            beforeEach {
                whenever(swap.state).thenReturn(Swap.State.RESPONDER_BAILED)
            }

            it("does nothing") {
                initiatorDoer.onBailTransactionSeen(bailTx)

                verifyNoMoreInteractions(storage)
                verifyNoMoreInteractions(delegate)
            }
        }
    }

    describe("#redeem") {
        beforeEach {
            whenever(swap.responderBailTx).thenReturn(bailTxSerialized)
            whenever(responderBlockchain.deserializeBailTx(bailTxSerialized)).thenReturn(bailTx)

            initiatorDoer.redeem()
        }

        it("redeems responder's bail tx") {
            verify(responderBlockchain).sendRedeemTx(
                initiatorRedeemPKH,
                initiatorRedeemPKId,
                secret,
                secretHash,
                responderRefundPKH,
                responderRefundTime,
                bailTx
            )
        }

        it("updates swap state to INITIATOR_REDEEMED") {
            inOrder(swap, storage).run {
                verify(swap).state = Swap.State.INITIATOR_REDEEMED
                verify(storage).save(swap)
            }
        }

        it("triggers delegate processNext") {
            verify(delegate).processNext()
        }
    }
})
