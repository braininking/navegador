package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.BrowserExtension
import com.example.ui.AegisUiState
import com.example.ui.AegisViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberIndigo
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ShieldEmerald
import org.json.JSONObject

@Composable
fun ExtensionsScreen(
    extensions: List<BrowserExtension>,
    uiState: AegisUiState,
    viewModel: AegisViewModel
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("todas") }
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingExtension by remember { mutableStateOf<BrowserExtension?>(null) }

    val filtered = remember(extensions, selectedFilter) {
        when (selectedFilter) {
            "ativas" -> extensions.filter { it.enabled }
            "desktop" -> extensions.filter { it.category == "desktop" || it.category == "appearance" }
            "privacidade" -> extensions.filter { it.category == "adblock" || it.category == "privacy" }
            "custom" -> extensions.filter { !it.isBuiltIn || it.category == "custom" }
            else -> extensions
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { content ->
                val trimmed = content.trim()
                if (trimmed.startsWith("{")) {
                    runCatching {
                        val json = JSONObject(trimmed)
                        viewModel.saveCustomExtension(
                            name = json.optString("name", "Plugin Importado"),
                            description = json.optString("description", "Extensão importada de arquivo local"),
                            category = json.optString("category", "custom"),
                            matchPattern = json.optString("matchPattern", "*://*/*"),
                            jsCode = json.optString("jsCode", ""),
                            cssCode = json.optString("cssCode", "")
                        )
                    }.onFailure {
                        viewModel.showToast("JSON de extensão inválido.")
                    }
                } else {
                    viewModel.saveCustomExtension(
                        name = "Userscript Importado",
                        description = "Script personalizado importado via arquivo (.user.js)",
                        category = "custom",
                        matchPattern = "*://*/*",
                        jsCode = trimmed,
                        cssCode = ""
                    )
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Central de Extensões", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "Plugins open-source, modo desktop e scripts personalizados",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { importFileLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("import_extension_button")
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Importar")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "todas" to "Todas (${extensions.size})",
                        "ativas" to "Ativas (${extensions.count { it.enabled }})",
                        "privacidade" to "Privacidade",
                        "desktop" to "Desktop & Visual",
                        "custom" to "Personalizadas"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { selectedFilter = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            items(filtered, key = { it.id }) { ext ->
                ExtensionCard(
                    extension = ext,
                    onToggle = { viewModel.toggleExtension(ext) },
                    onEdit = {
                        editingExtension = ext
                        showEditorDialog = true
                    },
                    onDelete = { viewModel.deleteExtension(ext) }
                )
            }

            item { Spacer(Modifier.height(72.dp)) }
        }

        FloatingActionButton(
            onClick = {
                editingExtension = null
                viewModel.clearGeneratedExtensionDraft()
                showEditorDialog = true
            },
            containerColor = CyberCyan,
            contentColor = ObsidianVoid,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .testTag("add_custom_extension_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Criar extensão personalizada")
        }
    }

    if (showEditorDialog) {
        ExtensionEditorDialog(
            initialExtension = editingExtension,
            uiState = uiState,
            onGenerateWithAi = { prompt -> viewModel.generateExtensionWithAi(prompt) },
            onSave = { name, desc, category, pattern, js, css ->
                viewModel.saveCustomExtension(
                    id = editingExtension?.id.orEmpty(),
                    name = name,
                    description = desc,
                    category = category,
                    matchPattern = pattern,
                    jsCode = js,
                    cssCode = css
                )
                showEditorDialog = false
            },
            onDismiss = { showEditorDialog = false }
        )
    }
}

@Composable
private fun ExtensionCard(
    extension: BrowserExtension,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when (extension.category) {
        "adblock", "privacy" -> Icons.Filled.Shield
        "desktop" -> Icons.Filled.Computer
        "appearance" -> Icons.Filled.Palette
        else -> Icons.Filled.Extension
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (extension.enabled) CyberCyan.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (extension.enabled) CyberCyan.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (extension.enabled) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(extension.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "v${extension.version} • ${extension.author} • ${extension.matchPattern}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ShieldEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = extension.enabled,
                    onCheckedChange = { onToggle() }
                )
            }

            if (extension.description.isNotBlank()) {
                Text(
                    text = extension.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar Código")
                }
                if (!extension.isBuiltIn) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remover extensão",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionEditorDialog(
    initialExtension: BrowserExtension?,
    uiState: AegisUiState,
    onGenerateWithAi: (String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var modeTab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(initialExtension?.name ?: "") }
    var description by remember { mutableStateOf(initialExtension?.description ?: "") }
    var category by remember { mutableStateOf(initialExtension?.category ?: "custom") }
    var matchPattern by remember { mutableStateOf(initialExtension?.matchPattern ?: "*://*/*") }
    var jsCode by remember { mutableStateOf(initialExtension?.jsCode ?: "") }
    var cssCode by remember { mutableStateOf(initialExtension?.cssCode ?: "") }
    var aiPrompt by remember { mutableStateOf("") }

    LaunchedEffect(uiState.generatedExtensionDraft) {
        uiState.generatedExtensionDraft?.let { draft ->
            name = draft.name
            description = draft.description
            category = draft.category
            matchPattern = draft.matchPattern
            jsCode = draft.jsCode
            cssCode = draft.cssCode
            modeTab = 0
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialExtension == null) "Nova Extensão / Plugin" else "Editar Extensão")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initialExtension == null) {
                    TabRow(selectedTabIndex = modeTab) {
                        Tab(
                            selected = modeTab == 0,
                            onClick = { modeTab = 0 },
                            text = { Text("Manual JS/CSS") },
                            icon = { Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = modeTab == 1,
                            onClick = { modeTab = 1 },
                            text = { Text("Criar com IA") },
                            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                if (modeTab == 1) {
                    Text(
                        text = "Descreva o plugin que deseja e o Gemini gera o script pronto:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        label = { Text("Ex: Aumentar fonte dos artigos e ocultar barras laterais") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onGenerateWithAi(aiPrompt) },
                        enabled = !uiState.isGeneratingExtension && aiPrompt.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberIndigo),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isGeneratingExtension) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Gerar Userscript com Gemini")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome do Plugin") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descrição") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = matchPattern,
                        onValueChange = { matchPattern = it },
                        label = { Text("URL Match Pattern (*://*/*)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = jsCode,
                        onValueChange = { jsCode = it },
                        label = { Text("JavaScript (Userscript)") },
                        textStyle = MaterialTheme.typography.labelSmall,
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cssCode,
                        onValueChange = { cssCode = it },
                        label = { Text("CSS Opcional") },
                        textStyle = MaterialTheme.typography.labelSmall,
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description, category, matchPattern, jsCode, cssCode) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid)
            ) {
                Text("Salvar Extensão", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
