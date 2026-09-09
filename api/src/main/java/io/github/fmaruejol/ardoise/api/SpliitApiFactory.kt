package io.github.fmaruejol.ardoise.api

import io.github.fmaruejol.ardoise.api.trpc.TrpcClient
import io.ktor.client.HttpClient

/**
 * Builds a client pointed at one specific instance.
 *
 * The injected [SpliitApi] follows whatever base URL is stored, which is right
 * for everything the app does, except deciding what that base URL should be.
 * Checking an address the user just typed has to happen before it is saved,
 * and this is what makes that possible without a temporary write.
 */
fun interface SpliitApiFactory {
    fun create(baseUrl: String): SpliitApi
}

class DefaultSpliitApiFactory(private val httpClient: HttpClient) : SpliitApiFactory {
    override fun create(baseUrl: String): SpliitApi =
        SpliitApiClient(TrpcClient(httpClient) { baseUrl })
}
