package be.anton.curatedphone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.anton.curatedphone.BuildConfig
import be.anton.curatedphone.data.*
import java.text.DateFormat
import java.util.Date

private val palette = lightColorScheme(primary = Color(0xFF176D79), onPrimary = Color.White, primaryContainer = Color(0xFFD5EFF1), onPrimaryContainer = Color(0xFF123C46), secondary = Color(0xFF526781), background = Color(0xFFF2F6FB), surface = Color.White, surfaceVariant = Color(0xFFE6EDF5), onSurface = Color(0xFF172D49), onBackground = Color(0xFF172D49), error = Color(0xFF9E3440))
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CuratedPhoneApp(vm: PhoneViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Status") }
    val tabs = listOf("Status", "Apps", "Websites", "Guardians", "Review", "Codes", "History", "Removal")
    MaterialTheme(colorScheme = palette, typography = Typography(bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp))) {
        Scaffold(topBar = { Column { TopAppBar(title = { Text("Curated Phone", fontWeight = FontWeight.SemiBold) }, actions = { TextButton(onClick = vm::sync, enabled = ui.paired && !ui.busy) { Text("Sync") } }); PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(tab), edgePadding = 12.dp, containerColor = palette.background) { tabs.forEach { title -> Tab(selected = title == tab, onClick = { tab = title }, text = { Text(title) }) } }; if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) } }, bottomBar = { if (ui.draft != null) Surface(tonalElevation = 2.dp) { Row(Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Your draft", fontWeight = FontWeight.SemiBold); Text("Active settings stay in place", style = MaterialTheme.typography.bodySmall) }; Button(onClick = { tab = "Review" }) { Text("Review changes") } } } }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ui.message?.let { Surface(color = palette.errorContainer, shape = RoundedCornerShape(12.dp)) { Text(it, Modifier.padding(16.dp), color = palette.onErrorContainer) } }
                when (tab) {
                    "Status" -> StatusScreen(ui, vm) { tab = it }
                    "Apps" -> AppsScreen(ui, vm)
                    "Websites" -> WebsitesScreen(ui, vm)
                    "Guardians" -> GuardiansScreen(ui, vm)
                    "Review" -> ReviewScreen(ui, vm) { tab = "Codes" }
                    "Codes" -> OtpScreen(ui, vm)
                    "History" -> HistoryScreen(ui)
                    "Removal" -> RemovalScreen(ui, vm)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
@Composable private fun Title(title: String, detail: String) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold); Text(detail, color = palette.secondary) } }
@Composable private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(18.dp), color = palette.surface) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() } } }
@Composable private fun Fact(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { Text(label, Modifier.weight(1f), color = palette.secondary); Text(value, Modifier.weight(1f), fontWeight = FontWeight.Medium) } }
private fun date(time: Long?) = time?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "Not yet"
@Composable private fun StatusScreen(ui: PhoneUiState, vm: PhoneViewModel, navigate: (String) -> Unit) {
    Title("A phone with intention.", "Keep the familiar Samsung experience. Change your boundaries with a guardian's approval.")
    Surface(color = palette.primaryContainer, shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(if (ui.state?.status == "retired") "Management has ended" else if (ui.journal != null) "Approved, awaiting application" else if (ui.localVersion > 0) "Approved settings on this phone" else "Let's set your starting boundaries", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(if (ui.state?.status == "retired") "Approved cleanup and ownership release are complete. Ordinary uninstall can now proceed." else if (ui.localVersion > 0) "${ui.active?.policy?.blockedAppPackageIds?.size ?: 0} blocked packages. All other apps remain allowed." else "The starting draft includes 200 exact app packages. It takes approval and device enrolment to enforce them."); ui.journal?.let { Text("Step: ${it.stage}"); it.failure?.let { failure -> Text(failure) }; Button(onClick = vm::retry, enabled = !ui.busy) { Text("Retry approved operation") } } } }
    ui.enforcementIssue?.let { issue -> Panel("Policy needs attention") { Text(issue, color = palette.error); OutlinedButton(onClick = vm::retry, enabled = !ui.busy) { Text("Retry approved settings") } } }
    if (!ui.paired) {
        var code by remember { mutableStateOf("") }
        Panel("Connect this phone") { Text("Create a one-time pairing code from the backend setup command. Pairing fixes the two guardian addresses for onboarding."); OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Pairing code") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation()); Button(onClick = { vm.pair(code); code = "" }, enabled = code.isNotBlank() && !ui.busy) { Text("Pair phone") } }
    }
    Panel("Enforcement status") { Fact("Device owner", if (ui.owner) "Enrolled" else "Not enrolled"); Fact("Samsung Knox", ui.knox); Fact("Approved version", (ui.state?.approvedPolicyVersion ?: 0).toString()); Fact("Applied locally", ui.localVersion.toString()); Fact("Server applied version", (ui.state?.appliedPolicyVersion ?: 0).toString()); Fact("Website rules", ui.ruleCount.toString()); Fact("Last successful sync", date(ui.lastSync)); if (!ui.owner) Text("Enrolment needs an eligible test phone. Installing the APK alone does not grant management.", style = MaterialTheme.typography.bodySmall) }
    ui.state?.pending?.let { pending -> Panel("Approval request") { Text("${pending.purpose.replace('_', ' ')} · ${pending.status}"); Text("${pending.diff.size} exact changes"); Button(onClick = { navigate("Codes") }) { Text("Open approval codes") } } }
    if (ui.owner) { var licence by remember { mutableStateOf("") }; Panel("Knox licence") { Text("Activate a suitable Samsung licence. The key stays out of the repository and APK."); OutlinedTextField(value = licence, onValueChange = { licence = it }, label = { Text("Licence key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedButton(onClick = { vm.activateKnox(licence); licence = "" }, enabled = licence.isNotBlank() && !ui.busy) { Text("Request activation") } } }
    Text("Category filtering, Samsung licensing, production enrolment, and approved removal need the device acceptance checks before daily use.", style = MaterialTheme.typography.bodySmall, color = palette.secondary)
}
@Composable private fun AppsScreen(ui: PhoneUiState, vm: PhoneViewModel) {
    Title("Blocked apps", "All apps are allowed by default. These exact package IDs are the exceptions in your draft.")
    val draft = ui.draft ?: return Text("Pair your phone to create a draft.")
    var search by rememberSaveable { mutableStateOf("") }; var packageId by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search apps or package IDs") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Panel("Add a verified package") { Text("You can block a package before it is installed. Verify its exact ID first.", style = MaterialTheme.typography.bodySmall); OutlinedTextField(value = packageId, onValueChange = { packageId = it }, label = { Text("com.publisher.app") }, modifier = Modifier.fillMaxWidth(), singleLine = true); OutlinedButton(onClick = { vm.toggleApp(packageId.trim()); packageId = "" }, enabled = packageId.isNotBlank()) { Text("Change draft blocklist") } }
    val installed = ui.apps.associateBy { it.packageId }
    val ids = (draft.policy.blockedAppPackageIds + installed.keys).distinct().filter { search.isBlank() || it.contains(search, true) || installed[it]?.name?.contains(search, true) == true }.sortedWith(compareByDescending<String> { it in draft.policy.blockedAppPackageIds }.thenBy { installed[it]?.name ?: it })
    Text("${draft.policy.blockedAppPackageIds.size} blocked in draft · ${ids.size} matching packages", color = palette.secondary)
    ids.take(250).forEach { id -> Surface(shape = RoundedCornerShape(12.dp)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(installed[id]?.name ?: id.substringAfterLast('.'), fontWeight = FontWeight.Medium); Text(id, style = MaterialTheme.typography.bodySmall, color = palette.secondary); val active = ui.state?.status != "retired" && id in ui.active?.policy?.blockedAppPackageIds.orEmpty(); Text(if (installed[id]?.protected == true) "Protected" else if (active) "Active: blocked" else "Active: allowed", style = MaterialTheme.typography.labelSmall) }; Switch(checked = id in draft.policy.blockedAppPackageIds, onCheckedChange = { vm.toggleApp(id) }, enabled = installed[id]?.protected != true) } } }
}
private val categoryNames = mapOf("social" to "Social networks", "streaming" to "Video and streaming", "gambling" to "Gambling", "adult" to "Adult content")
@Composable private fun WebsitesScreen(ui: PhoneUiState, vm: PhoneViewModel) {
    Title("Websites in Chrome", "Block domains and their subdomains. Other apps keep their normal network connection.")
    val draft = ui.draft ?: return Text("Pair your phone to create a draft.")
    Panel("Category boundaries") {
        categoryNames.forEach { (id, label) -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium); Switch(checked = id in draft.policy.categories, onCheckedChange = { vm.toggleCategory(id) }) }
            val feed = draft.policy.feeds.firstOrNull { it.category == id }
            if (id in draft.policy.categories) {
                var url by rememberSaveable(id, feed?.url) { mutableStateOf(feed?.url ?: "") }; var licence by rememberSaveable(id, feed?.licence) { mutableStateOf(feed?.licence ?: "") }
                Text(if (feed == null) "Needs a tested category feed before submission" else "Approved source changes require a new batch", style = MaterialTheme.typography.bodySmall, color = if (feed == null) palette.error else palette.secondary)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("HTTPS domain-list URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = licence, onValueChange = { licence = it }, label = { Text("Feed licence") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { vm.setFeed(id, url, licence) }, enabled = url.isNotBlank() && licence.isNotBlank()) { Text("Save source in draft") }
            }
        } }
    }
    var host by rememberSaveable { mutableStateOf("") }
    Panel("Custom domains") { OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("example.org") }, modifier = Modifier.fillMaxWidth(), singleLine = true); OutlinedButton(onClick = { vm.addDomain(host); host = "" }, enabled = host.isNotBlank()) { Text("Add domain to draft") }; draft.policy.customDomains.forEach { value -> Row(verticalAlignment = Alignment.CenterVertically) { Text(value, Modifier.weight(1f)); TextButton(onClick = { vm.removeDomain(value) }) { Text("Remove") } } } }
    Panel("Filter diagnostics") { Fact("Applied rule count", ui.ruleCount.toString()); ui.state?.feeds.orEmpty().filter { f -> ui.state?.snapshot?.policy?.feeds.orEmpty().any { it.id == f.id && it.url == f.url } }.forEach { feed -> Text("${categoryNames[feed.category]}: ${feed.fetchStatus}; updated ${date(feed.fetchedAt)}", style = MaterialTheme.typography.bodySmall); Text("${feed.url}\n${feed.licence}\nSHA-256: ${feed.checksum}", style = MaterialTheme.typography.bodySmall) }; Text("Managed Chrome supports at most 1,000 rules. Larger lists fail visibly; they are never truncated. Large-category enforcement remains a device prototype gate."); Text("Feed contents refresh without a new approval. Sources, categories, and custom domains require approval. Failed downloads keep the last usable list.", style = MaterialTheme.typography.bodySmall) }
}
@Composable private fun GuardiansScreen(ui: PhoneUiState, vm: PhoneViewModel) {
    Title("Your guardians", "Two separate inboxes, with different responsibilities. Neither inbox should be accessible to you.")
    val draft = ui.draft ?: return Text("Pairing sets the initial guardian addresses.")
    Panel("Primary guardian") { Text("Approves settings, guardian replacements, and removal."); Text("Active: ${ui.active?.primaryEmail ?: draft.primaryEmail}", style = MaterialTheme.typography.bodySmall); OutlinedTextField(value = draft.primaryEmail, onValueChange = { vm.setEmail(true, it) }, label = { Text("Proposed primary email") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true); if (ui.active != null) Button(onClick = { vm.submit("replace_primary") }, enabled = !ui.busy && draft.primaryEmail != ui.active.primaryEmail) { Text("Request primary replacement") } }
    Panel("Recovery guardian") { Text("Can only approve replacement of an inaccessible primary inbox. Cannot change restrictions or approve removal."); Text("Active: ${ui.active?.backupEmail ?: draft.backupEmail}", style = MaterialTheme.typography.bodySmall); OutlinedTextField(value = draft.backupEmail, onValueChange = { vm.setEmail(false, it) }, label = { Text("Proposed recovery email") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true); if (ui.active != null) Button(onClick = { vm.submit("replace_backup") }, enabled = !ui.busy && draft.backupEmail != ui.active.backupEmail) { Text("Request recovery replacement") } }
    if (ui.active != null) Panel("Lost primary inbox") { Text("The registered recovery guardian approves only the proposed primary address above. The new inbox must also be verified."); OutlinedButton(onClick = { vm.submit("replace_primary", true) }, enabled = !ui.busy && draft.primaryEmail != ui.active.primaryEmail) { Text("Ask recovery guardian") } }
    Text("Guardian replacement requests contain only the address change, even when your draft also has app or website edits.", style = MaterialTheme.typography.bodySmall, color = palette.secondary)
}
private fun localDiff(active: Snapshot?, draft: Snapshot): List<String> {
    val result = mutableListOf<String>()
    fun changes(before: List<String>, after: List<String>, add: String, remove: String) { (after - before.toSet()).forEach { result += "$add: $it" }; (before - after.toSet()).forEach { result += "$remove: $it" } }
    changes(active?.policy?.blockedAppPackageIds.orEmpty(), draft.policy.blockedAppPackageIds, "Block app", "Unblock app")
    changes(active?.policy?.categories.orEmpty(), draft.policy.categories, "Block category", "Stop blocking category")
    changes(active?.policy?.customDomains.orEmpty(), draft.policy.customDomains, "Block domain", "Unblock domain")
    val oldFeeds = active?.policy?.feeds.orEmpty()
    draft.policy.feeds.filter { it !in oldFeeds }.forEach { result += "Use feed: ${it.category}; ${it.url}; ${it.licence}" }
    oldFeeds.filter { it !in draft.policy.feeds }.forEach { result += "Remove feed: ${it.category}; ${it.url}" }
    if (active?.primaryEmail != draft.primaryEmail) result += "Primary guardian: ${draft.primaryEmail}"
    if (active?.backupEmail != draft.backupEmail) result += "Recovery guardian: ${draft.backupEmail}"
    return result
}
@Composable private fun ReviewScreen(ui: PhoneUiState, vm: PhoneViewModel, openCodes: () -> Unit) {
    Title("Review the whole batch", "The guardian receives these changes and one code for this exact request. Edits never unlock active settings.")
    val draft = ui.draft ?: return Text("Pair your phone to create a draft.")
    val changes = localDiff(ui.active, draft)
    Panel("${changes.size} draft changes") { if (changes.isEmpty()) Text("Your draft matches the approved settings.") else changes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } }
    Panel("Request approval") { Fact("Recipient", ui.active?.primaryEmail ?: draft.primaryEmail); Text("The server validates and computes the final email diff. Submitting replaces any previous pending batch. Guardian address changes have their own requests.", style = MaterialTheme.typography.bodySmall); Button(onClick = { vm.submit(); openCodes() }, enabled = !ui.busy && changes.isNotEmpty()) { Text(if (ui.active == null) "Request initial approval" else "Send batch for approval") }; if (ui.active != null) TextButton(onClick = vm::discardDraft, enabled = !ui.busy) { Text("Restore draft to approved settings") } }
}
@Composable private fun OtpScreen(ui: PhoneUiState, vm: PhoneViewModel) {
    Title("Approval codes", "Ask the guardian to review the email before sharing its code. Each inbox verification stays tied to this request.")
    val pending = ui.state?.pending ?: return Text("No request is pending. Review your draft to send one.")
    Panel("Exact submitted request") { Text("${pending.purpose.replace('_', ' ')} · ${pending.status}", fontWeight = FontWeight.Medium); Text(pending.requestId, style = MaterialTheme.typography.bodySmall); pending.diff.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } }
    ui.state.challenges.forEach { challenge ->
        var code by remember(challenge.channel, challenge.generation) { mutableStateOf("") }
        Panel(if (challenge.channel == "authorize") "Guardian authorization" else "Verify proposed inbox") { Text(challenge.email); Text(if (challenge.verified) "This inbox has verified its code" else "Generation ${challenge.generation} · expires ${date(challenge.expiresAt)}\nDelivery: ${challenge.delivery}\n${5 - challenge.attempts} attempts remaining", style = MaterialTheme.typography.bodySmall)
            if (!challenge.verified && pending.status == "pending") { OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(8) }, label = { Text("8-digit code") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true); Button(onClick = { vm.verify(challenge, code); code = "" }, enabled = code.length == 8 && !ui.busy) { Text("Verify this code") }; TextButton(onClick = { vm.resend(challenge) }, enabled = !ui.busy && System.currentTimeMillis() >= challenge.resendAt) { Text("Send a new code") } }
        }
    }
    if (pending.status in setOf("authorized", "failed")) Button(onClick = vm::retry, enabled = !ui.busy) { Text("Apply authorized settings") }
    if (pending.status == "pending") OutlinedButton(onClick = vm::cancel, enabled = !ui.busy) { Text("Cancel pending request") }
}
@Composable private fun HistoryScreen(ui: PhoneUiState) {
    Title("Your change history", "Approval and native application are separate steps. Codes and credentials are never kept here.")
    val history = ui.state?.history.orEmpty()
    if (history.isEmpty()) Text("Your first submitted batch will appear here.")
    history.forEach { request -> Panel(request.purpose.replace('_', ' ').replaceFirstChar(Char::uppercase)) { Fact("Status", request.status); Fact("Submitted", date(request.createdAt)); Text(request.requestId, style = MaterialTheme.typography.bodySmall); Text("${request.diff.size} changes"); request.failure?.let { Text(it, color = palette.error) } } }
}
@Composable private fun RemovalScreen(ui: PhoneUiState, vm: PhoneViewModel) {
    Title("End management", "A standalone request to your primary guardian. Approval ends restrictions only through a verified cleanup and release.")
    Panel("What will happen") { Text("Website filtering stops. Apps suspended by this controller are unsuspended. Knox installation rules and controller protections are removed, then device ownership is released."); Text("No factory reset is automatically attempted. A recovery guardian cannot approve this request."); Fact("Samsung removal route", if (BuildConfig.SAMSUNG_REMOVAL_VALIDATED) "Validated build configuration" else "Awaiting real-device validation") }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = confirmed, onCheckedChange = { confirmed = it }); Text("I understand that approved removal will end these restrictions.", Modifier.weight(1f)) }
    Button(onClick = { vm.submit("remove_management") }, enabled = confirmed && ui.active != null && !ui.busy, colors = ButtonDefaults.buttonColors(containerColor = palette.error)) { Text("Request management removal") }
}
