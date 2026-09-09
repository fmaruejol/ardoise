package io.github.fmaruejol.ardoise.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** The platform's view of the network, said outright. */
class FakeConnectivity(offline: Boolean = false) : Connectivity {
    private val networks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val offline = MutableStateFlow(offline)

    override fun available(): Flow<Unit> = networks

    override fun offline(): Flow<Boolean> = offline

    /** A network turned up. */
    suspend fun connect() = networks.emit(Unit)

    fun setOffline(value: Boolean) {
        offline.value = value
    }
}
