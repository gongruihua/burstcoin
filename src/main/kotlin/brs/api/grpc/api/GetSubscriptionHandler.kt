package brs.api.grpc.api

import brs.api.grpc.GrpcApiHandler
import brs.api.grpc.proto.BrsApi
import brs.api.grpc.ApiException
import brs.api.grpc.ProtoBuilder
import brs.services.SubscriptionService

class GetSubscriptionHandler(private val subscriptionService: SubscriptionService) :
    GrpcApiHandler<BrsApi.GetByIdRequest, BrsApi.Subscription> {
    override fun handleRequest(request: BrsApi.GetByIdRequest): BrsApi.Subscription {
        val subscriptionId = request.id
        val subscription = subscriptionService.getSubscription(subscriptionId)
            ?: throw ApiException("Could not find subscription")
        return ProtoBuilder.buildSubscription(subscription)
    }
}
