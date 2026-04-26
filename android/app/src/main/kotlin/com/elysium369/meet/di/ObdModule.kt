package com.elysium369.meet.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.elysium369.meet.core.obd.AdapterFingerprint
import com.elysium369.meet.core.obd.ElmNegotiator
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.transport.BtClassicTransport
import com.elysium369.meet.core.transport.TransportInterface
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ObdModule {

    @Provides
    @Singleton
    fun provideObdCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Provides
    fun provideBluetoothAdapter(@ApplicationContext context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter
    }

    @Provides
    fun provideTransportInterface(
        bluetoothAdapter: BluetoothAdapter?
    ): TransportInterface {
        // En una implementación real, leería DataStore para saber qué Transport
        // usar (BLE, WiFi, BT Clásico). Por ahora, BT Clásico con MAC ficticia o vacía
        return BtClassicTransport(
            "00:00:00:00:00:00",
            bluetoothAdapter ?: return object: TransportInterface {
                override val isConnected: Boolean = false
                override suspend fun connect() {}
                override suspend fun disconnect() {}
                override suspend fun write(data: ByteArray) {}
                override suspend fun read(maxBytes: Int): ByteArray? = null
            }
        )
    }

    @Provides
    @Singleton
    fun provideObdSession(
        transport: TransportInterface,
        scope: CoroutineScope,
        bluetoothAdapter: BluetoothAdapter?
    ): ObdSession {
        return ObdSession(transport, scope, bluetoothAdapter)
    }

    @Provides
    @Singleton
    fun provideElmNegotiator(transport: TransportInterface): ElmNegotiator {
        return ElmNegotiator(transport)
    }

    @Provides
    @Singleton
    fun provideAdapterFingerprint(@ApplicationContext context: Context): AdapterFingerprint {
        return AdapterFingerprint(context)
    }
}
