package cr.dragon.calc.ui.neural

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dragoncore.neural.NeuralProvider
import dragoncore.neural.NeuralRouter
import dragoncore.security.NeuralVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UplinkStatus {
    IDLE, VALIDATING, STABLE, ERROR
}

@HiltViewModel
class NeuralSettingsViewModel @Inject constructor(
    private val vault: NeuralVault,
    private val router: NeuralRouter // I need to provide this in CoreModule too
) : ViewModel() {

    private val _uplinkStatus = MutableStateFlow(UplinkStatus.IDLE)
    val uplinkStatus = _uplinkStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage = _statusMessage.asStateFlow()

    fun getSavedKey(provider: String): String {
        return vault.getApiKey(provider) ?: ""
    }

    fun saveAndVerifyKey(provider: String, key: String) {
        viewModelScope.launch {
            _uplinkStatus.value = UplinkStatus.VALIDATING
            _statusMessage.value = "Iniciando Ping Neuronal..."

            vault.saveApiKey(provider, key)

            // Enviamos un prompt de prueba para verificar el enlace
            val result = router.processMathIntent("1+1")

            if (result.isSuccess) {
                _uplinkStatus.value = UplinkStatus.STABLE
                _statusMessage.value = "Uplink Establecido: ${result.getOrNull()}"
            } else {
                _uplinkStatus.value = UplinkStatus.ERROR
                _statusMessage.value = "Error de Enlace: ${result.exceptionOrNull()?.message ?: "Check Key"}"
            }
        }
    }
}
