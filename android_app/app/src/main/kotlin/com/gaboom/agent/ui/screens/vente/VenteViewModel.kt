package com.gaboom.agent.ui.screens.vente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaboom.agent.data.repository.TicketRepository
import com.gaboom.agent.data.repository.DrawRepository
import com.gaboom.agent.data.model.TicketCreateRequest
import com.gaboom.agent.data.model.TicketLine
import com.gaboom.agent.data.model.TicketLineWithOptions
import com.gaboom.agent.data.model.Tirage
import com.gaboom.agent.data.model.MultiTicketCreateRequest
import com.gaboom.agent.data.model.MultiTicketEntry
import com.gaboom.agent.data.model.MultiTicketCreateResponse
import com.gaboom.agent.data.model.PrintData
import com.gaboom.agent.data.config.AgentConfigDataStore
import com.gaboom.agent.data.repository.AuthRepository
import com.gaboom.agent.data.local.PendingTicketDao
import com.gaboom.agent.data.local.PendingTicketEntity
import com.gaboom.agent.data.local.SyncStatus
import com.gaboom.agent.data.network.NetworkMonitor
import com.gaboom.agent.data.model.*
import com.gaboom.agent.print.BluetoothPrinter
import com.gaboom.agent.util.GameGenerators
import com.gaboom.agent.util.HmacUtil
import com.gaboom.agent.util.LotoOptionsHelper
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TicketShareInfo(
    val ticketNo: String,
    val tirageNom: String,
    val date: String,
    val time: String,
    val lines: List<Pair<String, Double>>, // jeu:valeur -> mise
    val totalMise: Double,
    val groupId: String?,
    val ticketId: String = "",
    val borletteName: String = "",
    val borletteSlogan: String = "",
    val borletteTel: String = "",
    val borletteAdresse: String = "",
    val borletteLogoUrl: String = "",
    val agentName: String = "",
    val ticketFooterText: String = "",
    val mariageGratuitActif: Boolean = false,
    val mariageGratuitMontant: String = "0",
    val isOffline: Boolean = false
)

data class VenteUiState(
    val isLoading: Boolean = false,
    val lines: List<TicketLineWithOptions> = emptyList(),
    val ticketCreated: Boolean = false,
    val error: String? = null,
    val autoMariageEnabled: Boolean = false,
    val autoLoto4Enabled: Boolean = false,
    val autoLoto5Enabled: Boolean = false,
    val inverseEnabled: Boolean = false,
    // Global options - apply to all new Loto4/Loto5 lines
    val globalLoto4Options: Set<Int> = setOf(1),  // Default: Option 1 checked
    val globalLoto5Options: Set<Int> = setOf(1),  // Default: Option 1 checked
    val useGlobalOptions: Boolean = true,  // Whether to apply global options to new lines
    val applyGlobalToAllLoto4: Boolean = true,  // Apply global options to all Loto4
    val applyGlobalToAllLoto5: Boolean = true,  // Apply global options to all Loto5
    // Multi-tirage mode - ENABLED BY DEFAULT
    val multiTirageMode: Boolean = true,
    val availableTirages: List<Tirage> = emptyList(),
    val selectedTirageIds: Set<Int> = emptySet(),
    val isLoadingTirages: Boolean = false,
    // Multi-tirage creation results
    val createdTickets: List<CreatedTicketInfo> = emptyList(),
    val currentPrintIndex: Int = 0,
    val printError: String? = null,
    val creationProgress: String? = null,
    // Free marriage option (from admin settings)
    val freeMariageEnabled: Boolean = false,
    val freeMariageChecked: Boolean = false,
    // Ticket data for sharing after creation
    val ticketToShare: TicketShareInfo? = null,
    // Borlette info for print preview
    val borletteName: String = "",
    val borletteSlogan: String = "",
    val borletteTel: String = "",
    val borletteAdresse: String = "",
    val borletteLogoUrl: String = "",
    val agentName: String = "",
    val ticketFooterText: String = "",
    val mariageGratuitActif: Boolean = false,
    val mariageGratuitMontant: String = "0"
)

@HiltViewModel
class VenteViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val drawRepository: DrawRepository,
    private val printer: BluetoothPrinter,
    private val agentConfigDataStore: AgentConfigDataStore,
    private val authRepository: AuthRepository,
    private val pendingTicketDao: PendingTicketDao,
    private val networkMonitor: NetworkMonitor,
    private val gson: Gson,
    // Phase 3 — Local-First gatekeeper
    private val offlineLimitEnforcer: com.gaboom.agent.data.sync.OfflineLimitEnforcer
) : ViewModel() {

    private val _uiState = MutableStateFlow(VenteUiState())
    val uiState: StateFlow<VenteUiState> = _uiState.asStateFlow()

    init {
        // Load free marriage setting from config and load tirages automatically
        viewModelScope.launch {
            val enabled = agentConfigDataStore.getFreeMariageEnabled()
            // Load borlette and agent info for print preview
            val borletteName = authRepository.getBorletteNom() ?: "GABOOM BORLETTE"
            val borletteSlogan = authRepository.getBorletteSlogan() ?: ""
            val borletteTel = authRepository.getBorletteTel() ?: ""
            val borletteAdresse = authRepository.getBorletteAdresse() ?: ""
            val borletteLogoUrl = authRepository.getBorletteLogoUrl() ?: ""
            val agentName = authRepository.getAgentNom() ?: ""
            val ticketFooterText = authRepository.getTicketFooterText() ?: ""
            val mariageGratuitActif = authRepository.getMariageGratuitActif()
            val mariageGratuitMontant = authRepository.getMariageGratuitMontant()
            _uiState.value = _uiState.value.copy(
                freeMariageEnabled = enabled,
                borletteName = borletteName,
                borletteSlogan = borletteSlogan,
                borletteTel = borletteTel,
                borletteAdresse = borletteAdresse,
                borletteLogoUrl = borletteLogoUrl,
                agentName = agentName,
                ticketFooterText = ticketFooterText,
                mariageGratuitActif = mariageGratuitActif,
                mariageGratuitMontant = mariageGratuitMontant
            )
            // Auto-load tirages since multi-tirage is default
            loadAvailableTiragesOnInit()
        }
    }
    
    private suspend fun loadAvailableTiragesOnInit() {
        _uiState.value = _uiState.value.copy(isLoadingTirages = true)
        try {
            val response = drawRepository.getTiragesActifs()
            if (response.isSuccessful) {
                offlineLimitEnforcer.recordServerContact()
                val allTirages = response.body()?.tirages ?: emptyList()
                agentConfigDataStore.saveCachedTirages(allTirages)
                _uiState.value = _uiState.value.copy(
                    availableTirages = allTirages,  // Include all for display (closed ones grayed out)
                    isLoadingTirages = false
                )
            } else {
                val cached = agentConfigDataStore.getCachedTirages()
                _uiState.value = _uiState.value.copy(
                    availableTirages = cached,
                    isLoadingTirages = false,
                    error = if (cached.isEmpty()) "Erreur chargement tirages" else null
                )
            }
        } catch (e: Exception) {
            val cached = agentConfigDataStore.getCachedTirages()
            _uiState.value = _uiState.value.copy(
                availableTirages = cached,
                isLoadingTirages = false,
                error = if (cached.isEmpty()) "Erreur: ${e.message}" else null
            )
        }
    }

    /**
     * Add a new line with proper options handling.
     * For Loto4/Loto5: creates a SEPARATE LINE for each option so user can edit each price individually.
     * 
     * @return Error message if validation fails, null if success
     */
    fun addLine(jeu: String, valeur: String, mise: Double): String? {
        if (valeur.isBlank()) return "Numéro requis"
        if (mise <= 0) return "Mise invalide"

        val isLoto = jeu.lowercase() in listOf("loto4", "loto5")
        
        if (isLoto) {
            // Get selected options
            val options = when (jeu.lowercase()) {
                "loto4" -> _uiState.value.globalLoto4Options.ifEmpty { setOf(1) }
                "loto5" -> _uiState.value.globalLoto5Options.ifEmpty { setOf(1) }
                else -> emptySet()
            }
            
            // Validate options
            val (isValid, errorMsg) = LotoOptionsHelper.validateLotoOptions(jeu, options)
            if (!isValid) {
                return errorMsg
            }
            
            // Create a SEPARATE LINE for each option
            val newLines = options.sorted().map { opt ->
                TicketLineWithOptions(
                    jeu = jeu,
                    valeur = valeur,
                    miseBase = mise,
                    options = setOf(opt),  // Single option per line
                    useGlobalOptions = false
                )
            }
            
            _uiState.value = _uiState.value.copy(
                lines = _uiState.value.lines + newLines,
                error = null
            )
        } else {
            // Non-Loto: single line as before
            val isBouleTwoDigits = (jeu.lowercase() == "boule") && valeur.length == 2
            val shouldInverse = isBouleTwoDigits && _uiState.value.inverseEnabled && (valeur[0] != valeur[1])

            val newLine = TicketLineWithOptions(
                jeu = jeu,
                valeur = valeur,
                miseBase = mise,
                options = emptySet(),
                useGlobalOptions = false
            )
            
            val linesToAdd = mutableListOf(newLine)
            if (shouldInverse) {
                linesToAdd.add(
                    TicketLineWithOptions(
                        jeu = jeu,
                        valeur = valeur.reversed(),
                        miseBase = mise,
                        options = emptySet(),
                        useGlobalOptions = false
                    )
                )
            }

            _uiState.value = _uiState.value.copy(
                lines = _uiState.value.lines + linesToAdd,
                error = null
            )
        }
        return null  // Success
    }

    fun removeLine(index: Int) {
        val newLines = _uiState.value.lines.toMutableList()
        if (index in newLines.indices) {
            newLines.removeAt(index)
            _uiState.value = _uiState.value.copy(lines = newLines)
        }
    }

    /**
     * Update base mise for a line.
     * Total effective mise will be recalculated (miseBase * options.size)
     */
    fun updateLineMise(index: Int, newMise: Double) {
        val newLines = _uiState.value.lines.toMutableList()
        if (index in newLines.indices) {
            newLines[index] = newLines[index].copy(miseBase = newMise)
            _uiState.value = _uiState.value.copy(lines = newLines)
        }
    }

    /**
     * Add a new line with a specific option for the same Loto number.
     * Used to add additional options to an existing number.
     */
    fun addOptionLine(jeu: String, valeur: String, mise: Double, option: Int) {
        val newLine = TicketLineWithOptions(
            jeu = jeu,
            valeur = valeur,
            miseBase = mise,
            options = setOf(option),
            useGlobalOptions = false
        )
        _uiState.value = _uiState.value.copy(
            lines = _uiState.value.lines + newLine,
            error = null
        )
    }

    /**
     * Update options for a specific line (override global options)
     */
    fun updateLineOptions(index: Int, options: Set<Int>) {
        val newLines = _uiState.value.lines.toMutableList()
        if (index in newLines.indices) {
            newLines[index] = newLines[index].copy(
                options = options,
                useGlobalOptions = false
            )
            _uiState.value = _uiState.value.copy(lines = newLines)
        }
    }

    /**
     * Toggle a line to use/not use global options
     */
    fun toggleLineUseGlobalOptions(index: Int) {
        val newLines = _uiState.value.lines.toMutableList()
        if (index in newLines.indices) {
            val line = newLines[index]
            val newUseGlobal = !line.useGlobalOptions
            val newOptions = if (newUseGlobal) {
                // Apply current global options (use exact set, don't force opt1)
                when (line.jeu.lowercase()) {
                    "loto4" -> _uiState.value.globalLoto4Options
                    "loto5" -> _uiState.value.globalLoto5Options
                    else -> emptySet()
                }
            } else {
                line.options  // Keep current options when switching to custom
            }
            newLines[index] = line.copy(
                useGlobalOptions = newUseGlobal,
                options = newOptions
            )
            _uiState.value = _uiState.value.copy(lines = newLines)
        }
    }

    // ─── Global Options Management ───────────────────────────────────────────

    fun toggleGlobalLoto4Option(option: Int) {
        val current = _uiState.value.globalLoto4Options.toMutableSet()
        if (current.contains(option)) {
            current.remove(option)
        } else {
            current.add(option)
        }
        _uiState.value = _uiState.value.copy(globalLoto4Options = current)
        // Propager aux lignes existantes si "Appliquer à tous" est coché
        if (_uiState.value.applyGlobalToAllLoto4) {
            applyGlobalOptionsToAllLoto4()
        }
    }

    fun toggleGlobalLoto5Option(option: Int) {
        val current = _uiState.value.globalLoto5Options.toMutableSet()
        if (current.contains(option)) {
            current.remove(option)
        } else {
            current.add(option)
        }
        _uiState.value = _uiState.value.copy(globalLoto5Options = current)
        // Propager aux lignes existantes si "Appliquer à tous" est coché
        if (_uiState.value.applyGlobalToAllLoto5) {
            applyGlobalOptionsToAllLoto5()
        }
    }

    fun setUseGlobalOptions(useGlobal: Boolean) {
        _uiState.value = _uiState.value.copy(useGlobalOptions = useGlobal)
    }

    /**
     * Set FULL option (1+2+3) for Loto4
     * When enabling FULL: set {1,2,3} and auto-apply to existing lines
     * When disabling FULL: keep current selection (don't reset to {1})
     */
    fun setLoto4Full(isFull: Boolean) {
        if (isFull) {
            _uiState.value = _uiState.value.copy(
                globalLoto4Options = setOf(1, 2, 3),
                applyGlobalToAllLoto4 = true  // Auto-enable apply to all
            )
            applyGlobalOptionsToAllLoto4()
        }
    }

    /**
     * Set FULL option (1+2+3) for Loto5
     * When enabling FULL: set {1,2,3} and auto-apply to existing lines
     * When disabling FULL: keep current selection (don't reset to {1})
     */
    fun setLoto5Full(isFull: Boolean) {
        if (isFull) {
            _uiState.value = _uiState.value.copy(
                globalLoto5Options = setOf(1, 2, 3),
                applyGlobalToAllLoto5 = true  // Auto-enable apply to all
            )
            applyGlobalOptionsToAllLoto5()
        }
    }

    /**
     * Toggle "Apply to all" for Loto4
     */
    fun toggleApplyGlobalToAllLoto4(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(applyGlobalToAllLoto4 = enabled)
        if (enabled) {
            applyGlobalOptionsToAllLoto4()
        }
    }

    /**
     * Toggle "Apply to all" for Loto5
     */
    fun toggleApplyGlobalToAllLoto5(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(applyGlobalToAllLoto5 = enabled)
        if (enabled) {
            applyGlobalOptionsToAllLoto5()
        }
    }

    /**
     * Apply global Loto4 options to all existing Loto4 lines.
     * FULL option: Expande chaque ligne en plusieurs lignes séparées (une par option).
     * Ex: loto4 = 1234 avec FULL → 3 lignes: opt1, opt2, opt3
     */
    private fun applyGlobalOptionsToAllLoto4() {
        val globalOpts = _uiState.value.globalLoto4Options
        if (globalOpts.isEmpty()) return
        
        val newLines = mutableListOf<TicketLineWithOptions>()
        val processedNumbers = mutableSetOf<String>()  // Track processed loto4 numbers
        
        for (line in _uiState.value.lines) {
            if (line.jeu.lowercase() == "loto4") {
                // Skip if we already processed this number (avoid duplicates)
                if (line.valeur in processedNumbers) continue
                processedNumbers.add(line.valeur)
                
                // Create separate line for each global option
                globalOpts.sorted().forEach { opt ->
                    newLines.add(
                        TicketLineWithOptions(
                            jeu = line.jeu,
                            valeur = line.valeur,
                            miseBase = line.miseBase,
                            options = setOf(opt),
                            useGlobalOptions = true
                        )
                    )
                }
            } else {
                newLines.add(line)
            }
        }
        _uiState.value = _uiState.value.copy(lines = newLines)
    }

    /**
     * Apply global Loto5 options to all existing Loto5 lines.
     * FULL option: Expande chaque ligne en plusieurs lignes séparées (une par option).
     */
    private fun applyGlobalOptionsToAllLoto5() {
        val globalOpts = _uiState.value.globalLoto5Options
        if (globalOpts.isEmpty()) return
        
        val newLines = mutableListOf<TicketLineWithOptions>()
        val processedNumbers = mutableSetOf<String>()  // Track processed loto5 numbers
        
        for (line in _uiState.value.lines) {
            if (line.jeu.lowercase() == "loto5") {
                // Skip if we already processed this number (avoid duplicates)
                if (line.valeur in processedNumbers) continue
                processedNumbers.add(line.valeur)
                
                // Create separate line for each global option
                globalOpts.sorted().forEach { opt ->
                    newLines.add(
                        TicketLineWithOptions(
                            jeu = line.jeu,
                            valeur = line.valeur,
                            miseBase = line.miseBase,
                            options = setOf(opt),
                            useGlobalOptions = true
                        )
                    )
                }
            } else {
                newLines.add(line)
            }
        }
        _uiState.value = _uiState.value.copy(lines = newLines)
    }

    // ─── Automations ─────────────────────────────────────────────────────────

    fun toggleInverseEnabled() {
        _uiState.value = _uiState.value.copy(
            inverseEnabled = !_uiState.value.inverseEnabled
        )
    }

    fun generateBoulesPaires(mise: Double) {
        val existingLines = _uiState.value.lines.filter { it.jeu.lowercase() == "boule" }.map { it.valeur }.toSet()
        val paires = listOf("00", "11", "22", "33", "44", "55", "66", "77", "88", "99")
        val newLines = paires.filter { it !in existingLines }.map { valeur ->
            TicketLineWithOptions(jeu = "boule", valeur = valeur, miseBase = mise, options = emptySet(), useGlobalOptions = false)
        }
        if (newLines.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(lines = _uiState.value.lines + newLines)
        }
    }

    fun generateGrap(mise: Double) {
        val individualMise = mise / 10.0
        val existingLines = _uiState.value.lines.filter { it.jeu.lowercase() == "loto3" }.map { it.valeur }.toSet()
        val grap = listOf("000", "111", "222", "333", "444", "555", "666", "777", "888", "999")
        val newLines = grap.filter { it !in existingLines }.map { valeur ->
            TicketLineWithOptions(jeu = "loto3", valeur = valeur, miseBase = individualMise, options = emptySet(), useGlobalOptions = false)
        }
        if (newLines.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(lines = _uiState.value.lines + newLines)
        }
    }

    fun generatePointes(chiffre: Int, mise: Double) {
        val existingLines = _uiState.value.lines.filter { it.jeu.lowercase() == "boule" }.map { it.valeur }.toSet()
        val pointes = (0..9).map { "$it$chiffre" }
        val newLines = pointes.filter { it !in existingLines }.map { valeur ->
            TicketLineWithOptions(jeu = "boule", valeur = valeur, miseBase = mise, options = emptySet(), useGlobalOptions = false)
        }
        if (newLines.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(lines = _uiState.value.lines + newLines)
        }
    }

    fun generateFreeMariages() {
        val existingLines = _uiState.value.lines.filter { it.jeu.lowercase() == "mariage" }.map { it.valeur }.toSet()
        val generated = mutableSetOf<String>()
        val random = java.util.Random()
        
        var attempts = 0
        while (generated.size < 3 && attempts < 100) {
            attempts++
            val num1 = random.nextInt(100)
            val num2 = random.nextInt(100)
            if (num1 == num2) continue
            val val1 = String.format("%02d", num1)
            val val2 = String.format("%02d", num2)
            val format1 = "$val1-$val2"
            val format2 = "$val2-$val1"
            
            if (format1 !in existingLines && format2 !in existingLines && 
                format1 !in generated && format2 !in generated) {
                generated.add(format1)
            }
        }
        
        val newLines = generated.map { valeur ->
            TicketLineWithOptions(
                jeu = "mariage",
                valeur = valeur,
                miseBase = 0.0,
                options = emptySet(),
                useGlobalOptions = false,
                gratuit = true
            )
        }
        
        if (newLines.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(lines = _uiState.value.lines + newLines)
        }
    }

    fun toggleAutoMariage(enabled: Boolean, defaultMise: Double) {
        _uiState.value = _uiState.value.copy(autoMariageEnabled = enabled)
        if (enabled) {
            generateAutoMariages(defaultMise)
        }
    }

    fun toggleAutoLoto4(enabled: Boolean, defaultMise: Double) {
        _uiState.value = _uiState.value.copy(autoLoto4Enabled = enabled)
        if (enabled) {
            generateAutoLoto4(defaultMise)
        }
    }

    fun toggleLoto4Option(option: Int) {
        toggleGlobalLoto4Option(option)
    }

    fun toggleLoto5Option(option: Int) {
        toggleGlobalLoto5Option(option)
    }

    private fun generateAutoMariages(defaultMise: Double) {
        val existingLines = _uiState.value.lines.map { Pair(it.jeu, it.valeur) }
        val autoMariages = GameGenerators.generateAutoMariages(existingLines, defaultMise)
        
        val newLines = autoMariages.map { (jeu, valeur, mise) ->
            TicketLineWithOptions(jeu = jeu, valeur = valeur, miseBase = mise, options = emptySet())
        }
        
        if (newLines.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                lines = _uiState.value.lines + newLines
            )
        }
    }

    private fun generateAutoLoto4(defaultMise: Double) {
        val existingLines = _uiState.value.lines.map { Pair(it.jeu, it.valeur) }
        val autoLoto4 = GameGenerators.generateAutoLoto4(existingLines, defaultMise)
        
        val globalOpts = _uiState.value.globalLoto4Options.ifEmpty { setOf(1) }
        val newLines = autoLoto4.map { (jeu, valeur, mise) ->
            TicketLineWithOptions(
                jeu = jeu, 
                valeur = valeur, 
                miseBase = mise, 
                options = globalOpts,
                useGlobalOptions = true
            )
        }
        
        if (newLines.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                lines = _uiState.value.lines + newLines
            )
        }
    }

    private fun generateAutoLoto5(defaultMise: Double) {
        val boules = _uiState.value.lines
            .filter { it.jeu.lowercase() == "boule" }
            .map { it.valeur }
            .distinct()
        
        if (boules.isEmpty()) return
        
        val existingLoto5 = _uiState.value.lines
            .filter { it.jeu.lowercase() == "loto5" }
            .map { it.valeur }
            .toSet()
        
        val globalOpts = _uiState.value.globalLoto5Options.ifEmpty { setOf(1) }
        val newLines = mutableListOf<TicketLineWithOptions>()
        
        boules.forEach { boule ->
            (1..9).forEach { digit ->
                val loto5Value = "$digit$boule$boule"
                if (loto5Value !in existingLoto5) {
                    newLines.add(TicketLineWithOptions(
                        jeu = "loto5",
                        valeur = loto5Value,
                        miseBase = defaultMise,
                        options = globalOpts,
                        useGlobalOptions = true
                    ))
                }
            }
        }
        
        if (newLines.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                lines = _uiState.value.lines + newLines
            )
        }
    }

    fun toggleAutoLoto5(enabled: Boolean, defaultMise: Double) {
        _uiState.value = _uiState.value.copy(autoLoto5Enabled = enabled)
        if (enabled) {
            generateAutoLoto5(defaultMise)
        }
    }

    fun regenerateAutomations(defaultMise: Double) {
        if (_uiState.value.autoMariageEnabled) {
            generateAutoMariages(defaultMise)
        }
        if (_uiState.value.autoLoto4Enabled) {
            generateAutoLoto4(defaultMise)
        }
    }

    fun createTicket(tirageId: Int) {
        if (_uiState.value.lines.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val apiLines = _uiState.value.lines.flatMap { it.toApiLines() }
            if (apiLines.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Aucune ligne valide"
                )
                return@launch
            }

            // Phase 3: Gate check
            if (!offlineLimitEnforcer.isAllowedToSell()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = offlineLimitEnforcer.getBlockMessage()
                )
                return@launch
            }

            try {
                val request = TicketCreateRequest(
                    drawIds = listOf(tirageId),
                    lines = apiLines
                )

                // Phase 3: All ticket creations are local-first
                val response = ticketRepository.createTicket(request, tirageId, apiLines)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.ticket != null) {
                        printTicket(body.ticket.id)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            ticketCreated = true
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = body?.error ?: "Erreur création ticket"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Erreur création ticket: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Erreur: ${e.message}"
                )
            }
        }
    }

    fun createAndShareTicket(tirageId: Int) {
        if (_uiState.value.lines.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val apiLines = _uiState.value.lines.flatMap { it.toApiLines() }
            if (apiLines.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Aucune ligne valide"
                )
                return@launch
            }

            // Phase 3: Gate check
            if (!offlineLimitEnforcer.isAllowedToSell()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = offlineLimitEnforcer.getBlockMessage()
                )
                return@launch
            }

            try {
                val request = TicketCreateRequest(
                    drawIds = listOf(tirageId),
                    lines = apiLines
                )

                // Phase 3: All ticket creations are local-first
                val response = ticketRepository.createTicket(request, tirageId, apiLines)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.ticket != null) {
                        val ticket = body.ticket
                        val tirage = _uiState.value.availableTirages.find { it.id == tirageId }
                        val now = java.time.LocalDateTime.now()
                        
                        val shareInfo = TicketShareInfo(
                            ticketNo = ticket.numero,
                            tirageNom = tirage?.nom ?: "Tirage",
                            date = now.toLocalDate().toString(),
                            time = now.toLocalTime().toString().take(5),
                            lines = apiLines.map { "${it.jeu}:${it.valeur}:${it.option}" to it.mise },
                            totalMise = apiLines.sumOf { it.mise }.toDouble(),
                            groupId = ticket.groupId,
                            ticketId = ticket.id.toString(),
                            borletteName = _uiState.value.borletteName,
                            borletteSlogan = _uiState.value.borletteSlogan,
                            borletteTel = _uiState.value.borletteTel,
                            borletteAdresse = _uiState.value.borletteAdresse,
                            borletteLogoUrl = _uiState.value.borletteLogoUrl,
                            agentName = _uiState.value.agentName,
                            ticketFooterText = _uiState.value.ticketFooterText,
                            mariageGratuitActif = _uiState.value.mariageGratuitActif,
                            mariageGratuitMontant = _uiState.value.mariageGratuitMontant,
                            isOffline = true // All tickets are locally created first
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            ticketCreated = true,
                            ticketToShare = shareInfo
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = body?.error ?: "Erreur création ticket"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Erreur création ticket: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Erreur: ${e.message}"
                )
            }
        }
    }

    fun clearTicketToShare() {
        _uiState.value = _uiState.value.copy(ticketToShare = null)
    }

    fun printLastTicket() {
        viewModelScope.launch {
            val ticketId = _uiState.value.ticketToShare?.ticketId
            if (!ticketId.isNullOrBlank()) {
                printTicket(ticketId)
            }
        }
    }

    fun createAndShareMultiTickets() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedTirageIds.toList()
            if (selectedIds.isEmpty()) {
                _uiState.value = _uiState.value.copy(error = "Sélectionnez au moins un tirage")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                creationProgress = "Création..."
            )

            val entries = try {
                val list = _uiState.value.lines.flatMap { line ->
                    val expandedBets = LotoOptionsHelper.expandLineToBets(line)
                    expandedBets.map { bet ->
                        MultiTicketEntry(
                            game = bet.game,
                            number = bet.number,
                            stake = bet.mise,
                            gratuit = bet.gratuit,
                            option = if (bet.option > 0) bet.option else null
                        )
                    }
                }.toMutableList()

                if (_uiState.value.freeMariageEnabled && _uiState.value.freeMariageChecked) {
                    val linePairs = _uiState.value.lines.map { Pair(it.jeu, it.valeur) }
                    val boules = GameGenerators.extractBoulesFromLines(linePairs)
                    if (boules.size >= 2) {
                        val mariages = GameGenerators.generateMariages(boules)
                        mariages.forEach { mariage ->
                            list.add(MultiTicketEntry(game = "mariage", number = mariage, stake = 0.0))
                        }
                    }
                }
                list
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Erreur préparation tickets: ${e.message}", creationProgress = null)
                return@launch
            }

            if (entries.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Aucune entrée valide", creationProgress = null)
                return@launch
            }

            // Phase 3: Gate check
            if (!offlineLimitEnforcer.isAllowedToSell()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = offlineLimitEnforcer.getBlockMessage()
                )
                return@launch
            }

            try {
                val sessionKey = _uiState.value.availableTirages
                    .find { it.id == selectedIds.first() }
                    ?.sessionKey

                val request = MultiTicketCreateRequest(
                    tirageIds = selectedIds,
                    entries = entries,
                    sessionKey = sessionKey,
                    createdAt = com.gaboom.agent.data.clock.SecuredClock.now(),
                    clientTime = com.gaboom.agent.data.clock.SecuredClock.now()
                )

                // Phase 3: All ticket creations are local-first
                val response = ticketRepository.createMultiTicket(request)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && !body.tickets.isNullOrEmpty()) {
                        val tickets = body.tickets
                        val now = java.time.LocalDateTime.now()
                        
                        // Use first ticket info for sharing (group_id is shared)
                        val firstTicket = tickets.first()
                        val tirageNames = tickets.mapNotNull { t -> 
                            val tirage = _uiState.value.availableTirages.find { it.id == t.tirageId }
                            val heure = tirage?.heureTirage ?: ""
                            if (tirage != null && heure.isNotBlank()) {
                                "${tirage.nom} ($heure)"
                            } else {
                                t.tirageNom
                            }
                        }.joinToString(", ")
                        
                        val shareInfo = TicketShareInfo(
                            ticketNo = firstTicket.ticketNo,
                            tirageNom = tirageNames,
                            date = now.toLocalDate().toString(),
                            time = now.toLocalTime().toString().take(5),
                            lines = entries.map { "${it.game}:${it.number}:${it.option ?: 1}" to it.stake },
                            totalMise = tickets.sumOf { it.totalMise },
                            groupId = firstTicket.groupId,
                            ticketId = firstTicket.ticketId.toString(),
                            borletteName = _uiState.value.borletteName,
                            borletteSlogan = _uiState.value.borletteSlogan,
                            borletteTel = _uiState.value.borletteTel,
                            borletteAdresse = _uiState.value.borletteAdresse,
                            borletteLogoUrl = _uiState.value.borletteLogoUrl,
                            agentName = _uiState.value.agentName,
                            ticketFooterText = _uiState.value.ticketFooterText,
                            mariageGratuitActif = _uiState.value.mariageGratuitActif,
                            mariageGratuitMontant = _uiState.value.mariageGratuitMontant,
                            isOffline = false // Remove offline watermark
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            ticketCreated = true,
                            ticketToShare = shareInfo,
                            creationProgress = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = body?.error ?: "Erreur création tickets",
                            creationProgress = null
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Erreur création tickets: ${response.code()}",
                        creationProgress = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Erreur: ${e.message}",
                    creationProgress = null
                )
            }
        }
    }

    private fun buildOfflinePrintData(ticket: CreatedTicketInfo, apiLines: List<TicketLine>): PrintData {
        val lines = apiLines.map { line ->
            val isLoto = line.jeu.lowercase() in listOf("loto4", "loto5")
            val jeuDisplay = if (isLoto && line.option >= 1) {
                "${line.jeu.uppercase()}-OPT${line.option}"
            } else {
                line.jeu.uppercase()
            }
            String.format("%-8s %-9s %6.0f", jeuDisplay, line.valeur, line.mise)
        }
        val now = java.util.Date(com.gaboom.agent.data.clock.SecuredClock.now())
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

        val deviceCreds = kotlinx.coroutines.runBlocking { agentConfigDataStore.getDeviceCredentials() }
        val deviceId = deviceCreds?.deviceId ?: "unknown_device"
        val agentId = _uiState.value.agentName.ifEmpty { "unknown_agent" }
        val ts = now.time
        val sig = ticket.signature ?: ""
        val hash = ticket.hash ?: ""
        val qrUrl = "https://www.gaboombos.com/ticket/scan/?uuid=${ticket.ticketId}&no=${ticket.ticketNo}&draw=${ticket.tirageId}&station=$agentId&term=$deviceId&ts=$ts&sig=$sig&hash=$hash"

        return PrintData(
            borletteName = _uiState.value.borletteName.ifEmpty { "Gaboom" },
            borletteSlogan = _uiState.value.borletteSlogan,
            borletteTel = _uiState.value.borletteTel,
            borletteAdresse = _uiState.value.borletteAdresse,
            borletteLogoUrl = _uiState.value.borletteLogoUrl,
            agentName = _uiState.value.agentName.ifEmpty { "Agent" },
            ticketNumber = ticket.ticketNo,
            date = dateFormat.format(now),
            time = timeFormat.format(now),
            tirages = listOf(ticket.tirageNom),
            lines = lines,
            totalMise = ticket.totalMise,
            isOffline = false,
            ticketFooterText = _uiState.value.ticketFooterText,
            mariageGratuitActif = _uiState.value.mariageGratuitActif,
            mariageGratuitMontant = _uiState.value.mariageGratuitMontant,
            qrCodeUrl = qrUrl
        )
    }

    private fun buildCombinedPrintData(tickets: List<CreatedTicketInfo>, apiLines: List<TicketLine>): PrintData {
        val firstTicket = tickets.first()
        val lines = apiLines.map { line ->
            val isLoto = line.jeu.lowercase() in listOf("loto4", "loto5")
            val jeuDisplay = if (isLoto && line.option >= 1) {
                "${line.jeu.uppercase()}-OPT${line.option}"
            } else {
                line.jeu.uppercase()
            }
            String.format("%-8s %-9s %6.0f", jeuDisplay, line.valeur, line.mise)
        }
        val now = java.util.Date(com.gaboom.agent.data.clock.SecuredClock.now())
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

        val deviceCreds = kotlinx.coroutines.runBlocking { agentConfigDataStore.getDeviceCredentials() }
        val deviceId = deviceCreds?.deviceId ?: "unknown_device"
        val agentId = _uiState.value.agentName.ifEmpty { "unknown_agent" }
        val ts = now.time
        val groupId = firstTicket.groupId ?: ""
        
        val qrUrl = "https://www.gaboombos.com/ticket/scan/?group_id=$groupId&station=$agentId&term=$deviceId&ts=$ts"

        val tirageNames = tickets.map { t ->
            val tirage = _uiState.value.availableTirages.find { it.id == t.tirageId }
            val heure = tirage?.heureTirage ?: ""
            val nameWithHeure = if (heure.isNotBlank()) "${t.tirageNom} ($heure)" else t.tirageNom
            "$nameWithHeure - ${t.ticketNo}"
        }

        return PrintData(
            borletteName = _uiState.value.borletteName.ifEmpty { "Gaboom" },
            borletteSlogan = _uiState.value.borletteSlogan,
            borletteTel = _uiState.value.borletteTel,
            borletteAdresse = _uiState.value.borletteAdresse,
            borletteLogoUrl = _uiState.value.borletteLogoUrl,
            agentName = _uiState.value.agentName.ifEmpty { "Agent" },
            ticketNumber = firstTicket.ticketNo,
            date = dateFormat.format(now),
            time = timeFormat.format(now),
            tirages = tirageNames,
            lines = lines,
            totalMise = tickets.sumOf { it.totalMise },
            isOffline = false,
            ticketFooterText = _uiState.value.ticketFooterText,
            mariageGratuitActif = _uiState.value.mariageGratuitActif,
            mariageGratuitMontant = _uiState.value.mariageGratuitMontant,
            qrCodeUrl = qrUrl,
            groupId = groupId
        )
    }

    private suspend fun printTicket(ticketId: String): com.gaboom.agent.data.model.PrintData? {
        return try {
            val pendingTicket = pendingTicketDao.getById(ticketId)
            val printData = if (pendingTicket != null) {
                val req = gson.fromJson(pendingTicket.payloadJson, MultiTicketCreateRequest::class.java)
                val lines = req.entries.map { entry ->
                    TicketLine(
                        jeu = entry.game,
                        valeur = entry.number,
                        mise = entry.stake,
                        option = entry.option ?: 1,
                        gratuit = entry.gratuit
                    )
                }
                val ticketNo = pendingTicket.serverTicketNo ?: pendingTicket.localTicketNo ?: "HL-${pendingTicket.id.take(8).uppercase()}"
                val ticketIdToUse = pendingTicket.serverTicketId ?: pendingTicket.id
                val createdInfo = CreatedTicketInfo(
                    ticketId = ticketIdToUse,
                    ticketNo = ticketNo,
                    tirageId = pendingTicket.tirageId ?: 0,
                    tirageNom = _uiState.value.availableTirages.find { it.id == pendingTicket.tirageId }?.nom ?: "Tirage",
                    totalMise = pendingTicket.totalMise,
                    printed = false,
                    isOffline = true,
                    signature = pendingTicket.hmacSignature,
                    hash = pendingTicket.hmacSignature
                )
                buildOfflinePrintData(createdInfo, lines)
            } else {
                val printResponse = ticketRepository.getTicketPrint(ticketId)
                if (printResponse.isSuccessful) {
                    printResponse.body()?.printData
                } else {
                    null
                }
            }

            if (printData != null) {
                val result = printer.printTicket(printData)
                if (result.isFailure) {
                    val errMsg = result.exceptionOrNull()?.message ?: "Erreur inconnue"
                    android.util.Log.e("VenteViewModel", "Impression echouee: $errMsg")
                    _uiState.value = _uiState.value.copy(
                        printError = "Impression echouee: $errMsg"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(printError = null)
                }
                printData
            } else {
                _uiState.value = _uiState.value.copy(printError = "Donnees d'impression vides")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("VenteViewModel", "Erreur printTicket", e)
            _uiState.value = _uiState.value.copy(
                printError = "Erreur: ${e.message}"
            )
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MULTI-TIRAGE MODE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Initialize with a default tirage pre-selected.
     * Called when screen opens with a selected tirage from TirageSelectionScreen.
     */
    fun setDefaultTirage(defaultTirageId: Int, defaultTirageNom: String) {
        viewModelScope.launch {
            // Always load available tirages for potential multi-selection
            if (_uiState.value.availableTirages.isEmpty()) {
                loadAvailableTiragesForDefault(defaultTirageId)
            } else {
                // Just ensure default is selected
                val current = _uiState.value.selectedTirageIds.toMutableSet()
                current.add(defaultTirageId)
                _uiState.value = _uiState.value.copy(selectedTirageIds = current)
            }
        }
    }

    private suspend fun loadAvailableTiragesForDefault(defaultTirageId: Int) {
        _uiState.value = _uiState.value.copy(isLoadingTirages = true)
        try {
            val response = drawRepository.getTiragesActifs()
            if (response.isSuccessful) {
                offlineLimitEnforcer.recordServerContact()
                val allTirages = response.body()?.tirages ?: emptyList()
                agentConfigDataStore.saveCachedTirages(allTirages)
                val openTirages = allTirages.filter { it.etat == "OUVERT" }
                val defaultSelected = setOf(defaultTirageId)
                _uiState.value = _uiState.value.copy(
                    availableTirages = openTirages,
                    selectedTirageIds = defaultSelected,
                    isLoadingTirages = false,
                    multiTirageMode = false // Start in single mode, user can enable multi
                )
            } else {
                val cached = agentConfigDataStore.getCachedTirages()
                val defaultSelected = setOf(defaultTirageId)
                _uiState.value = _uiState.value.copy(
                    availableTirages = cached,
                    selectedTirageIds = defaultSelected,
                    isLoadingTirages = false,
                    multiTirageMode = false,
                    error = if (cached.isEmpty()) "Erreur chargement tirages" else null
                )
            }
        } catch (e: Exception) {
            val cached = agentConfigDataStore.getCachedTirages()
            val defaultSelected = setOf(defaultTirageId)
            _uiState.value = _uiState.value.copy(
                availableTirages = cached,
                selectedTirageIds = defaultSelected,
                isLoadingTirages = false,
                multiTirageMode = false,
                error = if (cached.isEmpty()) "Erreur: ${e.message}" else null
            )
        }
    }

    fun toggleMultiTirageMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            multiTirageMode = enabled,
            error = null
        )
        if (enabled && _uiState.value.availableTirages.isEmpty()) {
            loadAvailableTirages()
        }
    }

    private fun loadAvailableTirages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingTirages = true)
            try {
                val response = drawRepository.getTiragesActifs()
                if (response.isSuccessful) {
                    offlineLimitEnforcer.recordServerContact()
                    val allTirages = response.body()?.tirages ?: emptyList()
                    agentConfigDataStore.saveCachedTirages(allTirages)
                    val openTirages = allTirages.filter { it.etat == "OUVERT" }
                    _uiState.value = _uiState.value.copy(
                        availableTirages = openTirages,
                        isLoadingTirages = false
                    )
                } else {
                    val cached = agentConfigDataStore.getCachedTirages()
                    _uiState.value = _uiState.value.copy(
                        availableTirages = cached.filter { it.etat == "OUVERT" },
                        isLoadingTirages = false,
                        error = if (cached.isEmpty()) "Erreur chargement tirages" else null
                    )
                }
            } catch (e: Exception) {
                val cached = agentConfigDataStore.getCachedTirages()
                _uiState.value = _uiState.value.copy(
                    availableTirages = cached.filter { it.etat == "OUVERT" },
                    isLoadingTirages = false,
                    error = if (cached.isEmpty()) "Erreur: ${e.message}" else null
                )
            }
        }
    }

    fun toggleTirageSelection(tirageId: Int) {
        val current = _uiState.value.selectedTirageIds.toMutableSet()
        if (current.contains(tirageId)) {
            current.remove(tirageId)
        } else {
            current.add(tirageId)
        }
        _uiState.value = _uiState.value.copy(selectedTirageIds = current)
    }

    fun selectAllTirages() {
        val allIds = _uiState.value.availableTirages.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(selectedTirageIds = allIds)
    }

    fun clearAllTirages() {
        _uiState.value = _uiState.value.copy(selectedTirageIds = emptySet())
    }

    /**
     * Create multi-tirage tickets using create-multi endpoint.
     * Returns N tickets for N selected tirages.
     */
    fun createMultiTickets() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedTirageIds.toList()
            if (selectedIds.isEmpty()) {
                _uiState.value = _uiState.value.copy(error = "Sélectionnez au moins un tirage")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                creationProgress = "Création 0/${selectedIds.size}..."
            )

            val entries = try {
                val list = _uiState.value.lines.flatMap { line ->
                    val expandedBets = LotoOptionsHelper.expandLineToBets(line)
                    expandedBets.map { bet ->
                        MultiTicketEntry(
                            game = bet.game,
                            number = bet.number,
                            stake = bet.mise,
                            gratuit = bet.gratuit,
                            option = if (bet.option > 0) bet.option else null
                        )
                    }
                }.toMutableList()

                if (_uiState.value.freeMariageEnabled && _uiState.value.freeMariageChecked) {
                    val linePairs = _uiState.value.lines.map { Pair(it.jeu, it.valeur) }
                    val boules = GameGenerators.extractBoulesFromLines(linePairs)
                    if (boules.size >= 2) {
                        val mariages = GameGenerators.generateMariages(boules)
                        mariages.forEach { mariage ->
                            list.add(MultiTicketEntry(
                                game = "mariage",
                                number = mariage,
                                stake = 0.0
                            ))
                        }
                    }
                }
                list
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Erreur préparation tickets: ${e.message}")
                return@launch
            }

            if (entries.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Aucune entrée valide"
                )
                return@launch
            }

            // Phase 3: Gate check
            if (!offlineLimitEnforcer.isAllowedToSell()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = offlineLimitEnforcer.getBlockMessage()
                )
                return@launch
            }

            try {
                val sessionKey = _uiState.value.availableTirages
                    .find { it.id == selectedIds.first() }
                    ?.sessionKey

                val request = MultiTicketCreateRequest(
                    tirageIds = selectedIds,
                    entries = entries,
                    sessionKey = sessionKey,
                    createdAt = com.gaboom.agent.data.clock.SecuredClock.now(),
                    clientTime = com.gaboom.agent.data.clock.SecuredClock.now()
                )

                // Phase 3: All ticket creations are local-first (see TicketRepository)
                val response = ticketRepository.createMultiTicket(request)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    handleMultiTicketResponse(body, selectedIds)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Erreur création ticket: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Erreur: ${e.message}"
                )
            }
        }
    }

    private fun handleMultiTicketResponse(
        body: MultiTicketCreateResponse?, 
        requestedIds: List<Int>
    ) {
        if (body?.success != true) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = body?.error ?: "Erreur création tickets"
            )
            return
        }

        val tickets = body.tickets ?: emptyList()
        val failed = body.failed ?: emptyList()

        if (tickets.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Aucun ticket créé"
            )
            return
        }

        // Convert to CreatedTicketInfo for printing
        val createdTickets = tickets.map { t ->
            CreatedTicketInfo(
                ticketId = t.ticketId,
                ticketNo = t.ticketNo,
                tirageId = t.tirageId,
                tirageNom = t.tirageNom,
                totalMise = t.totalMise,
                printed = false,
                isOffline = true,
                signature = t.signature,
                hash = t.hash,
                groupId = body.groupId ?: t.groupId
            )
        }

        val failedCount = failed.size
        val errorMsg = if (failedCount > 0) {
            "⚠️ $failedCount/${requestedIds.size} échecs - ${failed.firstOrNull()?.error ?: ""}"
        } else null

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            createdTickets = createdTickets,
            currentPrintIndex = 0,
            ticketCreated = false, // Keep false while printing!
            error = errorMsg,
            creationProgress = "Impression du ticket..."
        )

        // Start combined printing
        printCombinedTicketMulti()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COMBINED PRINTING FOR MULTI-TIRAGE
    // ═══════════════════════════════════════════════════════════════════════

    private fun printCombinedTicketMulti() {
        viewModelScope.launch {
            val tickets = _uiState.value.createdTickets
            if (tickets.isEmpty()) {
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                printError = null,
                creationProgress = "Impression du ticket..."
            )

            try {
                val apiLines = _uiState.value.lines.flatMap { it.toApiLines() }
                val printData = buildCombinedPrintData(tickets, apiLines)

                val result = printer.printTicket(printData)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        createdTickets = tickets.map { it.copy(printed = true) },
                        creationProgress = null,
                        printError = null,
                        ticketCreated = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        printError = "Impression échouée: ${result.exceptionOrNull()?.message ?: "Erreur"}",
                        creationProgress = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    printError = "Erreur: ${e.message}",
                    creationProgress = null
                )
            }
        }
    }

    fun skipPrintMulti() {
        _uiState.value = _uiState.value.copy(
            creationProgress = null,
            printError = null,
            ticketCreated = true
        )
    }

    fun retryPrintMulti() {
        printCombinedTicketMulti()
    }

    fun toggleFreeMariage(checked: Boolean) {
        _uiState.value = _uiState.value.copy(freeMariageChecked = checked)
    }

    fun resetMultiTirageState() {
        _uiState.value = _uiState.value.copy(
            createdTickets = emptyList(),
            currentPrintIndex = 0,
            printError = null,
            creationProgress = null,
            ticketCreated = false,
            freeMariageChecked = false,
            lines = emptyList() // Added to clear lines on reset
        )
    }

    // ─── Refaire Fiche (Blueprint Pre-fill) ─────────────────────────────────

    private fun prefillFromBlueprint(blueprintLines: List<BlueprintLine>) {
        val newLines = blueprintLines.map { line ->
            TicketLineWithOptions(
                jeu = line.jeu,
                valeur = line.valeur,
                miseBase = line.mise,
                options = emptySet(), // Blueprints are usually simple lines
                useGlobalOptions = false
            )
        }
        _uiState.value = _uiState.value.copy(lines = newLines)
    }

    fun loadBlueprint(ticketId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = ticketRepository.getTicketBlueprint(ticketId)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.lines != null) {
                        prefillFromBlueprint(body.lines)
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = body?.error ?: "Erreur récupération blueprint"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Erreur serveur: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Erreur: ${e.message}"
                )
            }
        }
    }
    
    fun clearPrintError() {
        _uiState.value = _uiState.value.copy(printError = null)
    }
}
