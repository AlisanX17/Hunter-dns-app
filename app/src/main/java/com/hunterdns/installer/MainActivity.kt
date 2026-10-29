package com.hunterdns.installer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

private val Bg = Color(0xFF0B0D10)
private val Panel = Color(0xFF171A20)
private val Panel2 = Color(0xFF222630)
private val Accent = Color(0xFFFF6A1A)
private val MainText = Color(0xFFF5F5F5)
private val Muted = Color(0xFF9BA3AF)
private val Green = Color(0xFF4ADE80)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { InstallerApp() }
    }
}

@Composable
private fun InstallerApp() {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("root") }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("single") }
    var transport by remember { mutableStateOf("BackPack") }
    var profile by remember { mutableStateOf("balance") }
    var enableBbr by remember { mutableStateOf(true) }
    var enableUpdates by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Not connected") }
    var logs by remember { mutableStateOf("Hunter DNS Installer ready.\\nEnter VPS SSH details to begin.") }
    var showConfirm by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun appendLog(s: String) { logs = (logs + "\\n" + s).takeLast(14000) }

    suspend fun runRemote(command: String): String = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "Server IP or hostname is required." }
        require(username.isNotBlank()) { "SSH username is required." }
        val sshPort = port.toIntOrNull() ?: throw IllegalArgumentException("SSH port must be a number.")
        require(sshPort in 1..65535) { "SSH port must be between 1 and 65535." }
        require(password.isNotEmpty()) { "Enter the SSH password." }

        val jsch = JSch()
        val session = jsch.getSession(username, host.trim(), sshPort)
        session.setPassword(password)
        // First-version convenience mode. For production use, pin and verify the SSH host key.
        session.setConfig("StrictHostKeyChecking", "no")
        session.timeout = 15000
        session.connect(15000)
        try {
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.setInputStream(null)
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            channel.setOutputStream(stdout)
            channel.setErrStream(stderr)
            channel.connect(10000)
            val started = System.currentTimeMillis()
            while (!channel.isClosed) {
                if (System.currentTimeMillis() - started > 20 * 60 * 1000) {
                    channel.disconnect()
                    throw IllegalStateException("Remote command timed out after 20 minutes.")
                }
                kotlinx.coroutines.delay(250)
            }
            val exit = channel.exitStatus
            val out = stdout.toString("UTF-8")
            val err = stderr.toString("UTF-8")
            channel.disconnect()
            val combined = (out + if (err.isNotBlank()) "\\n[stderr]\\n$err" else "").trim()
            if (exit != 0) throw IllegalStateException("Remote command exited with code $exit\\n$combined")
            combined.ifBlank { "Command completed successfully (exit 0)." }
        } finally {
            session.disconnect()
        }
    }

    fun startAction(label: String, commandBuilder: () -> String) {
        if (busy) return
        if (host.isBlank() || username.isBlank() || password.isBlank()) {
            appendLog("ERROR: Enter server, SSH username and password first.")
            status = "Missing SSH details"
            return
        }
        showConfirm = label
        pendingAction = {
            scope.launch {
                busy = true
                status = "$label running…"
                appendLog("▶ $label")
                try {
                    val result = runRemote(commandBuilder())
                    appendLog(result)
                    status = "$label completed"
                } catch (e: Exception) {
                    appendLog("ERROR: ${e.message ?: e.javaClass.simpleName}")
                    status = "Failed"
                } finally {
                    busy = false
                }
            }
        }
    }

    val installCommand = remember(role, domain, transport, profile, enableBbr, enableUpdates) {
        val roleArg = when(role) {
            "Relay" -> "relay"
            "Exit" -> "exit"
            "Single" -> "single"
            else -> "node"
        }
        val envs = mutableListOf("ASSUME_YES=1", "INSTALL_ROLE=$roleArg", "TRANSPORT=${shellQuote(transport.lowercase())}", "PROFILE=${shellQuote(profile)}")
        if (domain.isNotBlank()) envs.add("PANEL_DOMAIN=${shellQuote(domain.trim())}")
        if (enableBbr) envs.add("ENABLE_BBR=1")
        if (!enableUpdates) envs.add("DISABLE_AUTO_UPDATE=1")
        // This runs the user's own installer script on the VPS. Validate/review the script URL before using it.
        "sudo env ${envs.joinToString(" ")} bash -lc 'curl -fsSLO https://raw.githubusercontent.com/AlisanX17/Hunter-dns/main/hunter-dns.sh && bash hunter-dns.sh'"
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel, onBackground = MainText, onSurface = MainText)) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().background(Panel).padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).background(Accent, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Build, null, tint = Color.Black, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("HUNTER DNS", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MainText)
                        Text("VPS Panel Installer · Android", fontSize = 12.sp, color = Muted)
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Accent, strokeWidth = 2.dp)
                }
            }
        ) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StatusCard(status, busy)
                SectionTitle("SSH SERVER", "Enter the VPS connection details")
                AppCard {
                    AppField(host, { host = it }, "Server IP / hostname", Icons.Default.Dns)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(0.8f)) { AppField(port, { port = it.filter(Char::isDigit).take(5) }, "SSH port", Icons.Default.SettingsEthernet) }
                        Box(Modifier.weight(1.2f)) { AppField(username, { username = it }, "SSH username", Icons.Default.Person) }
                    }
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text("SSH password") },
                        leadingIcon = { Icon(Icons.Default.Key, null) }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        colors = fieldColors()
                    )
                    Text("Password stays in app memory for this session and is not saved to disk. Host-key verification must be hardened before public release.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                    Button(
                        onClick = { startAction("SSH connection test") { "id && hostname && (command -v bash) && echo 'SSH test OK'" } },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = MainText)
                    ) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(8.dp)); Text("TEST SSH CONNECTION") }
                }

                SectionTitle("INSTALL CONFIGURATION", "Choose how to install Hunter DNS")
                AppCard {
                    Text("Server role", color = Muted, fontSize = 12.sp)
                    ChoiceChips(listOf("Relay", "Exit", "Single", "Node"), role) { role = it }
                    AppField(domain, { domain = it }, "Panel domain (optional)", Icons.Default.Language)
                    Text("Transport", color = Muted, fontSize = 12.sp)
                    ChoiceChips(listOf("BackPack", "TCP"), transport) { transport = it }
                    Text("Profile", color = Muted, fontSize = 12.sp)
                    ChoiceChips(listOf("turbo", "balance"), profile) { profile = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable BBR", fontWeight = FontWeight.SemiBold)
                            Text("Apply BBR if supported by the installer", color = Muted, fontSize = 11.sp)
                        }
                        Switch(enableBbr, { enableBbr = it }, colors = SwitchDefaults.colors(checkedThumbColor = Accent))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow installer updates", fontWeight = FontWeight.SemiBold)
                            Text("Keep installer components updated", color = Muted, fontSize = 11.sp)
                        }
                        Switch(enableUpdates, { enableUpdates = it }, colors = SwitchDefaults.colors(checkedThumbColor = Accent))
                    }
                    Button(
                        onClick = { startAction("Install Hunter DNS") { installCommand } },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)
                    ) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("INSTALL PANEL", fontWeight = FontWeight.Bold) }
                }

                SectionTitle("PANEL MANAGEMENT", "Run maintenance commands on your VPS")
                AppCard {
                    ManagementButton("Check installation", Icons.Default.FactCheck, !busy) {
                        startAction("Check installation") { "sudo bash hunter-dns.sh --check" }
                    }
                    ManagementButton("Show installer version", Icons.Default.Info, !busy) {
                        startAction("Show version") { "sudo bash hunter-dns.sh --version" }
                    }
                    ManagementButton("Reconfigure tunnel", Icons.Default.Sync, !busy) {
                        startAction("Reconfigure tunnel") { "sudo bash hunter-dns.sh --tunnel" }
                    }
                    ManagementButton("Update installer", Icons.Default.SystemUpdateAlt, !busy) {
                        startAction("Update installer") { "sudo bash -lc 'curl -fsSLO https://raw.githubusercontent.com/AlisanX17/Hunter-dns/main/hunter-dns.sh && bash hunter-dns.sh --check'" }
                    }
                    ManagementButton("Uninstall panel", Icons.Default.DeleteForever, !busy, danger = true) {
                        startAction("UNINSTALL Hunter DNS") { "sudo bash hunter-dns.sh --uninstall" }
                    }
                }

                SectionTitle("COMMAND LOG", "Latest output from the VPS")
                AppCard {
                    Text(logs, color = Color(0xFFD1D5DB), fontSize = 11.sp, lineHeight = 16.sp)
                    OutlinedButton(onClick = { logs = "Hunter DNS Installer ready." }, enabled = !busy) {
                        Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Clear logs")
                    }
                }
                Text("Security: only connect to VPS instances you administer. Review the installer source before running it. This first build disables strict SSH host-key checking for convenience; do not use it with untrusted networks until host-key pinning is implemented.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }

    if (showConfirm != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = null; pendingAction = null },
            containerColor = Panel,
            title = { Text("Confirm action", color = MainText) },
            text = { Text("Run “$showConfirm” on $host:$port as $username? This executes commands on that VPS.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = null
                    val action = pendingAction
                    pendingAction = null
                    action?.invoke()
                }) { Text("Run", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = null; pendingAction = null }) { Text("Cancel", color = Muted) }
            }
        )
    }
}

private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

@Composable
private fun StatusCard(status: String, busy: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).border(1.dp, Panel2, RoundedCornerShape(16.dp)).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(if (busy) Accent else if (status.contains("completed", true)) Green else Muted, RoundedCornerShape(50)))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("STATUS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(status, color = MainText, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun AppCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).border(1.dp, Panel2, RoundedCornerShape(18.dp)).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun AppField(value: String, onValueChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        leadingIcon = { Icon(icon, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        colors = fieldColors()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Panel2,
    focusedLabelColor = Accent,
    cursorColor = Accent,
    focusedTextColor = MainText,
    unfocusedTextColor = MainText,
    focusedLeadingIconColor = Accent,
    unfocusedLeadingIconColor = Muted
)

@Composable
private fun ChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            val active = selected == option
            Surface(
                onClick = { onSelect(option) },
                color = if (active) Accent.copy(alpha = .16f) else Panel2,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Accent else Panel2),
                modifier = Modifier.weight(1f)
            ) {
                Text(option, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), color = if (active) Accent else MainText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ManagementButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) Color(0xFFFF5555) else MainText),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (danger) Color(0xFF7F1D1D) else Panel2)
    ) {
        Icon(icon, null); Spacer(Modifier.width(8.dp)); Text(title); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null)
    }
}
