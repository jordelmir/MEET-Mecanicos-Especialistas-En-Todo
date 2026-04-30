package com.elysium369.meet.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.elysium369.meet.core.obd.AdapterFingerprint
import com.elysium369.meet.core.obd.ObdSession
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

    /**
     * ═══════════════════════════════════════════════════════════
     * ObdSession ahora maneja su propio ciclo de vida de Transport.
     * NO pre-creamos un transport con dirección ficticia.
     * El transport se instancia cuando el usuario selecciona un
     * dispositivo real desde AdapterSearchSheet.
     * ═══════════════════════════════════════════════════════════
     */
    @Provides
    @Singleton
    fun provideObdSession(
        scope: CoroutineScope,
        bluetoothAdapter: BluetoothAdapter?,
        @ApplicationContext context: Context
    ): ObdSession {
        return ObdSession(scope, bluetoothAdapter, context)
    }

    @Provides
    @Singleton
    fun provideAdapterFingerprint(@ApplicationContext context: Context): AdapterFingerprint {
        return AdapterFingerprint(context)
    }

    @Provides
    @Singleton
    fun provideAdvancedDiagnosticManager(obdSession: ObdSession): com.elysium369.meet.core.obd.AdvancedDiagnosticManager {
        return com.elysium369.meet.core.obd.AdvancedDiagnosticManager(obdSession)
    }
}
