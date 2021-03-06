package brs.api.grpc.api

import brs.api.grpc.GrpcApiHandler
import brs.api.grpc.proto.BrsApi
import brs.api.grpc.ProtoBuilder
import brs.services.EscrowService

class GetAccountEscrowTransactionsHandler(private val escrowService: EscrowService) :
    GrpcApiHandler<BrsApi.GetAccountRequest, BrsApi.EscrowTransactions> {
    override fun handleRequest(request: BrsApi.GetAccountRequest): BrsApi.EscrowTransactions {
        val accountId = request.accountId
        val builder = BrsApi.EscrowTransactions.newBuilder()
        escrowService.getEscrowTransactionsByParticipant(accountId)
            .forEach { escrow -> builder.addEscrowTransactions(ProtoBuilder.buildEscrowTransaction(escrow)) }
        return builder.build()
    }
}
