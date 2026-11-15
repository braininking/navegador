package com.example.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.local.LocalBrowserConfig
import com.example.data.remote.TranslationAiRepository
import com.example.security.AegisVpnService
import com.example.security.VpnStatus
import com.example.ui.AegisUiState
import com.example.ui.AegisViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberIndigo
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ShieldEmerald
import com.google.firebase.auth.FirebaseUser

@Composable
fun ShieldProfileScreen(
    config: LocalBrowserConfig,
    vpnStatus: VpnStatus,
    translationCacheCount: Int,
    currentUser: FirebaseUser?,
    uiState: AegisUiState,
    viewModel: AegisViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var langMenuExpanded by remember { mutableStateOf(false) }
    var searchMenuExpanded by remember { mutableStateOf(false) }
    var quickTranslateInput by remember { mutableStateOf("") }
    var isSigningIn by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AegisVpnService.start(context, config.vpnRegion)
            viewModel.showToast("VPN AegisX conectada e criptografada!")
        } else {
            viewModel.showToast("Permissão VPN negada pelo sistema.")
        }
    }

    fun toggleVpn() {
        if (vpnStatus.isConnected) {
            AegisVpnService.stop(context)
            viewModel.showToast("VPN desconectada.")
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                AegisVpnService.start(context, config.vpnRegion)
                viewModel.showToast("VPN AegisX conectada em ${vpnStatus.activeNode.label}!")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Escudo, VPN & Perfil", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "VPN gratuita criptografada, idioma automático e sincronização",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // VPN Free Encrypted Tunnel Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        if (vpnStatus.isConnected) ShieldEmerald else CyberCyan.copy(alpha = 0.5f),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (vpnStatus.isConnected) "VPN GRATUITA ATIVA" else "VPN GRATUITA PROTEGIDA",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (vpnStatus.isConnected) ShieldEmerald else CyberCyan
                            )
                            Text(
                                text = if (vpnStatus.isConnected) "Conectado • ${vpnStatus.activeNode.label}" else "Toque para criptografar seu tráfego",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "${vpnStatus.cipherSuite} • ${vpnStatus.encryptedDns}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        if (vpnStatus.isConnected) listOf(ShieldEmerald, CyberCyan)
                                        else listOf(CyberCyan.copy(alpha = 0.25f), CyberIndigo.copy(alpha = 0.25f))
                                    )
                                )
                                .clickable { toggleVpn() }
                                .testTag("vpn_power_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PowerSettingsNew,
                                contentDescription = "Ligar ou desligar VPN",
                                tint = if (vpnStatus.isConnected) ObsidianVoid else CyberCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VpnStatus.AVAILABLE_NODES.forEach { node ->
                            val selected = config.vpnRegion == node.id
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.updateSecuritySettings(vpnRegion = node.id)
                                    if (vpnStatus.isConnected) {
                                        AegisVpnService.start(context, node.id)
                                    }
                                },
                                label = { Text("${node.label} (${node.latencyMs}ms)") }
                            )
                        }
                    }
                }
            }
        }

        // Editable Language & Automatic Translation Preferences
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Translate, contentDescription = null, tint = CyberCyan)
                        Text("Idioma do Perfil & Tradução Automática", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Idioma Principal", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${config.preferredLanguageLabel} (${config.preferredLanguage.uppercase()})",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberCyan
                            )
                        }
                        Box {
                            OutlinedButton(
                                onClick = { langMenuExpanded = true },
                                modifier = Modifier.testTag("change_language_button")
                            ) {
                                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Alterar Idioma")
                            }
                            DropdownMenu(
                                expanded = langMenuExpanded,
                                onDismissRequest = { langMenuExpanded = false }
                            ) {
                                TranslationAiRepository.SUPPORTED_LANGUAGES.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text("${lang.nativeLabel} (${lang.code.uppercase()})") },
                                        onClick = {
                                            langMenuExpanded = false
                                            viewModel.updateLanguagePreferences(
                                                langCode = lang.code,
                                                langLabel = lang.label,
                                                autoTranslate = config.autoTranslateEnabled
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Traduzir sites estrangeiros automaticamente", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Cache offline ativo ($translationCacheCount traduções salvas)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.autoTranslateEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateLanguagePreferences(
                                    langCode = config.preferredLanguage,
                                    langLabel = config.preferredLanguageLabel,
                                    autoTranslate = enabled
                                )
                            }
                        )
                    }

                    if (translationCacheCount > 0) {
                        TextButton(onClick = { viewModel.clearTranslationCache() }) {
                            Text("Limpar cache offline de traduções")
                        }
                    }

                    // Instant Translator Box
                    OutlinedTextField(
                        value = quickTranslateInput,
                        onValueChange = { quickTranslateInput = it },
                        label = { Text("Tradutor Rápido (IA + Cache Offline)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.quickTranslateText(quickTranslateInput) },
                        enabled = quickTranslateInput.isNotBlank() && !uiState.isQuickTranslating,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isQuickTranslating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ObsidianVoid)
                        } else {
                            Text("Traduzir para ${config.preferredLanguageLabel}", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (uiState.quickTranslateOutput.isNotBlank()) {
                        Text(
                            text = uiState.quickTranslateOutput,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ShieldEmerald,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        )
                    }
                }
            }
        }

        // Privacy & Browser Engine Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Motor Chromium & Criptografia", style = MaterialTheme.typography.titleMedium)

                    SettingSwitchRow(
                        icon = Icons.Filled.Shield,
                        title = "Bloqueio de Anúncios Nativo",
                        subtitle = "${config.adsBlockedTotal} anúncios interceptados",
                        checked = config.adBlockEnabled,
                        onCheckedChange = { viewModel.updateSecuritySettings(adBlockEnabled = it) }
                    )
                    SettingSwitchRow(
                        icon = Icons.Filled.EnhancedEncryption,
                        title = "Bloqueio Estrito de Rastreadores & Cookies 3P",
                        subtitle = "Criptografia local AES-256-GCM Keystore ativa",
                        checked = config.strictTrackerBlock,
                        onCheckedChange = { viewModel.updateSecuritySettings(strictTrackerBlock = it) }
                    )
                    SettingSwitchRow(
                        icon = Icons.Filled.DesktopWindows,
                        title = "Modo Computador (Desktop) por Padrão",
                        subtitle = "Solicitar versão desktop dos sites",
                        checked = config.desktopModeDefault,
                        onCheckedChange = { viewModel.updateSecuritySettings(desktopModeDefault = it) }
                    )
                    SettingSwitchRow(
                        icon = Icons.Filled.DarkMode,
                        title = "Tema Obsidian Dark Futurista",
                        subtitle = "Alternar entre Obsidian Dark e Glacier Light",
                        checked = config.darkThemeEnabled,
                        onCheckedChange = { viewModel.updateSecuritySettings(darkThemeEnabled = it) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Buscador Privado Padrão", style = MaterialTheme.typography.bodyMedium)
                        Box {
                            OutlinedButton(onClick = { searchMenuExpanded = true }) {
                                Text(config.searchEngine)
                            }
                            DropdownMenu(
                                expanded = searchMenuExpanded,
                                onDismissRequest = { searchMenuExpanded = false }
                            ) {
                                listOf("DuckDuckGo", "Brave", "Startpage", "Google").forEach { engine ->
                                    DropdownMenuItem(
                                        text = { Text(engine) },
                                        onClick = {
                                            searchMenuExpanded = false
                                            viewModel.updateSecuritySettings(searchEngine = engine)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Personal Account & Optional Cross-Device Sync Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentUser != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = ShieldEmerald)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUser.displayName?.takeIf { it.isNotBlank() } ?: "Conta Pessoal AegisX",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = currentUser.email ?: "Sincronização ativa entre dispositivos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { viewModel.forceCloudSync() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sincronizar", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    signOutAegis(credentialManager, scope) {
                                        viewModel.showToast("Modo local ativo. Você foi desconectado.")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Desconectar")
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = CyberCyan)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Modo Local Privado (Sem Login)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "O navegador funciona 100% sem conta. Entre com Google apenas se quiser sincronizar abas, favoritos e extensões entre dispositivos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (authErrorMessage != null) {
                            Text(
                                text = authErrorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                authErrorMessage = null
                                isSigningIn = true
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isSigningIn = false
                                        viewModel.showToast("Conta Google conectada! Sincronizando dados...")
                                    },
                                    onAuthError = { msg ->
                                        isSigningIn = false
                                        authErrorMessage = msg
                                    },
                                    scope = scope,
                                    onAuthCancelled = {
                                        isSigningIn = false
                                    }
                                )
                            },
                            enabled = !isSigningIn,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("google_sign_in_button")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = ObsidianVoid,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Conectar Google para Sincronizar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
