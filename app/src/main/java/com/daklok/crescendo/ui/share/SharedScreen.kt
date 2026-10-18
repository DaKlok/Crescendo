package com.daklok.crescendo.ui.share

import android.app.Activity
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PieceEntity
import com.daklok.crescendo.data.backup.UnauthorizedException
import com.daklok.crescendo.data.share.SharedDriveClient
import com.daklok.crescendo.data.share.SharedDriveException
import com.daklok.crescendo.data.share.SharedGroup
import com.daklok.crescendo.data.share.SharedMember
import com.daklok.crescendo.data.share.SharedPiece
import com.daklok.crescendo.data.share.SharedStatus
import com.daklok.crescendo.ui.common.IconBadge
import com.daklok.crescendo.ui.common.NotificationHost
import com.daklok.crescendo.ui.common.NotificationType
import com.daklok.crescendo.ui.common.TonalIconButton
import com.daklok.crescendo.ui.common.rememberNotifier
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Spoločné noty: skupiny (zdieľané priečinky na Google Drive) s učiteľom alebo kamarátmi.
 * Každý člen si skladbu stiahne do knižnice; zmeny (poznámky, nahrávky…) odošle ako novú verziu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedScreen(onBack: () -> Unit, onOpenPiece: (Long) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    val repo = app.shared
    val scope = rememberCoroutineScope()
    val notifier = rememberNotifier()
    val authClient = remember { Identity.getAuthorizationClient(context) }

    var token by remember { mutableStateOf<String?>(null) }
    var needsAuth by remember { mutableStateOf(false) }
    var myEmail by remember { mutableStateOf("") }
    var myPermissionId by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<SharedGroup>?>(null) }
    var group by remember { mutableStateOf<SharedGroup?>(null) }
    var pieces by remember { mutableStateOf<List<SharedPiece>?>(null) }
    var members by remember { mutableStateOf<List<SharedMember>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    // po každej operácii sa stav skladieb prepočíta
    var revision by remember { mutableStateOf(0) }

    var newGroupOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var inviteOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var deleteGroupOpen by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<SharedPiece?>(null) }
    var removing by remember { mutableStateOf<SharedPiece?>(null) }

    val library by app.database.pieceDao().observeAll().collectAsState(initial = emptyList())
    val byId = remember(library) { library.associateBy { it.id } }

    val msgError = stringResource(R.string.sh_error)
    val msgNoAccess = stringResource(R.string.sh_no_access)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        runCatching { authClient.getAuthorizationResultFromIntent(res.data) }.getOrNull()?.accessToken?.let {
            token = it
            needsAuth = false
        }
    }

    fun authorize() {
        authClient.authorize(repo.authorizationRequest)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pi = result.pendingIntent ?: return@addOnSuccessListener
                    launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    token = result.accessToken
                    needsAuth = false
                }
            }
            .addOnFailureListener { needsAuth = true }
    }

    /** Spustí prácu s Drive; chyby oznámi, pri vypršanom tokene požiada o nový. */
    fun drive(label: String?, block: suspend (String) -> Unit) {
        val t = token ?: run { authorize(); return }
        if (busy != null) return
        busy = label ?: ""
        progress = null
        scope.launch {
            try {
                block(t)
            } catch (e: UnauthorizedException) {
                withContext(Dispatchers.IO) { runCatching { GoogleAuthUtil.clearToken(app, t) } }
                token = null
                authorize()
            } catch (e: SharedDriveException) {
                notifier.show(if (e.code == 403 || e.code == 404) msgNoAccess else (e.message ?: msgError), NotificationType.ERROR)
            } catch (e: Exception) {
                notifier.show(e.message?.takeIf { it.isNotBlank() } ?: msgError, NotificationType.ERROR)
            } finally {
                busy = null
                progress = null
                revision++
            }
        }
    }

    fun loadGroups() = drive(null) { t ->
        val client = SharedDriveClient(t)
        if (myEmail.isBlank()) client.me().let { (e, p) -> myEmail = e; myPermissionId = p }
        val hidden = repo.hiddenGroups()
        groups = client.listGroups().filter { it.id !in hidden }
    }

    fun loadGroup(g: SharedGroup) = drive(null) { t ->
        val client = SharedDriveClient(t)
        pieces = client.listPieces(g.id, myEmail)
        members = runCatching { client.members(g.id) }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) {
        val t = repo.silentToken()
        if (t == null) needsAuth = true else token = t
    }
    LaunchedEffect(token) { if (token != null && groups == null) loadGroups() }

    BackHandler {
        if (group != null) {
            group = null
            pieces = null
            members = emptyList()
            loadGroups()
        } else onBack()
    }

    val msgShared = stringResource(R.string.sh_msg_shared)
    val msgPulled = stringResource(R.string.sh_msg_pulled)
    val msgPushed = stringResource(R.string.sh_msg_pushed)
    val msgInvited = stringResource(R.string.sh_msg_invited)
    val labelUpload = stringResource(R.string.sh_uploading)
    val labelDownload = stringResource(R.string.sh_downloading)
    val inviteMessage = stringResource(R.string.sh_invite_message)

    fun pull(g: SharedGroup, p: SharedPiece) = drive(labelDownload) { t ->
        repo.pull(t, g.id, p) { progress = it }
        notifier.show(msgPulled, NotificationType.SUCCESS)
        pieces = SharedDriveClient(t).listPieces(g.id, myEmail)
    }

    fun push(g: SharedGroup, pieceId: Long) = drive(labelUpload) { t ->
        val piece = app.library.getPiece(pieceId) ?: return@drive
        repo.push(t, piece) { progress = it }
        notifier.show(msgPushed, NotificationType.SUCCESS)
        pieces = SharedDriveClient(t).listPieces(g.id, myEmail)
    }

    val g = group
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        TonalIconButton(
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            onClick = {
                                if (group != null) {
                                    group = null
                                    pieces = null
                                    loadGroups()
                                } else onBack()
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    },
                    title = {
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(g?.name ?: stringResource(R.string.sh_title), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when {
                                    g == null -> stringResource(R.string.sh_subtitle)
                                    g.ownedByMe -> stringResource(R.string.sh_my_group)
                                    else -> stringResource(R.string.sh_from, g.ownerName.ifBlank { g.ownerEmail })
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        if (token != null) {
                            TonalIconButton(
                                icon = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.sh_refresh),
                                onClick = { if (g != null) loadGroup(g) else loadGroups() },
                            )
                        }
                        if (g != null) {
                            Spacer(Modifier.width(8.dp))
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                TonalIconButton(Icons.Outlined.MoreVert, stringResource(R.string.more), { menu = true })
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                                    if (g.canShare) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sh_invite)) },
                                            leadingIcon = { Icon(Icons.Outlined.PersonAdd, null) },
                                            onClick = { menu = false; inviteOpen = true },
                                        )
                                    }
                                    if (g.ownedByMe) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sh_rename)) },
                                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                            onClick = { menu = false; renameOpen = true },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sh_delete_group)) },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                            onClick = { menu = false; deleteGroupOpen = true },
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sh_leave)) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                                            onClick = {
                                                menu = false
                                                drive(null) { t ->
                                                    runCatching { SharedDriveClient(t).removeMember(g.id, myPermissionId) }
                                                    repo.hideGroup(g.id)
                                                    group = null
                                                    pieces = null
                                                    groups = groups?.filter { it.id != g.id }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                AnimatedVisibility(visible = busy != null) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        val p = progress
                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        val label = busy
                        if (!label.isNullOrBlank()) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (token != null && busy == null) {
                if (g == null) {
                    ExtendedFloatingActionButton(
                        onClick = { newGroupOpen = true },
                        icon = { Icon(Icons.Outlined.Add, null) },
                        text = { Text(stringResource(R.string.sh_new_group)) },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                } else if (g.canEdit) {
                    ExtendedFloatingActionButton(
                        onClick = { addOpen = true },
                        icon = { Icon(Icons.Outlined.LibraryMusic, null) },
                        text = { Text(stringResource(R.string.sh_add_piece)) },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                when {
                    needsAuth && token == null -> ConnectCard { authorize() }
                    g == null -> GroupList(groups, onOpen = { picked ->
                        group = picked
                        pieces = null
                        loadGroup(picked)
                    })
                    else -> GroupDetail(
                        group = g,
                        pieces = pieces,
                        members = members,
                        myEmail = myEmail,
                        revision = revision,
                        statusOf = { p -> repo.status(p, byId) },
                        onPull = { p -> pull(g, p) },
                        onPush = { id -> push(g, id) },
                        onConflict = { conflict = it },
                        onOpen = onOpenPiece,
                        onRemove = { removing = it },
                        onRemoveMember = { m ->
                            drive(null) { t ->
                                SharedDriveClient(t).removeMember(g.id, m.permissionId)
                                members = SharedDriveClient(t).members(g.id)
                            }
                        },
                        onInvite = { inviteOpen = true },
                    )
                }
            }
            NotificationHost(
                notifier,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
            )
        }
    }

    // ───────────── dialógy ─────────────

    if (newGroupOpen) {
        NameDialog(
            title = stringResource(R.string.sh_new_group),
            initial = "",
            onDismiss = { newGroupOpen = false },
            onConfirm = { name ->
                newGroupOpen = false
                drive(null) { t ->
                    val client = SharedDriveClient(t)
                    val id = client.createGroup(name)
                    groups = client.listGroups().filter { it.id !in repo.hiddenGroups() }
                    groups?.firstOrNull { it.id == id }?.let { created ->
                        group = created
                        pieces = emptyList()
                        members = runCatching { client.members(id) }.getOrDefault(emptyList())
                    }
                }
            },
        )
    }
    if (renameOpen && g != null) {
        NameDialog(
            title = stringResource(R.string.sh_rename),
            initial = g.name,
            onDismiss = { renameOpen = false },
            onConfirm = { name ->
                renameOpen = false
                drive(null) { t ->
                    SharedDriveClient(t).renameGroup(g.id, name)
                    group = g.copy(name = name)
                }
            },
        )
    }
    if (inviteOpen && g != null) {
        InviteDialog(
            onDismiss = { inviteOpen = false },
            onInvite = { email, canEdit ->
                inviteOpen = false
                drive(null) { t ->
                    val client = SharedDriveClient(t)
                    client.invite(g.id, email, canEdit, inviteMessage)
                    members = runCatching { client.members(g.id) }.getOrDefault(members)
                    notifier.show(msgInvited, NotificationType.SUCCESS)
                }
            },
        )
    }
    if (addOpen && g != null) {
        AddPieceDialog(
            pieces = library,
            alreadyShared = remember(revision, library) { repo.links().filter { it.groupId == g.id }.map { it.pieceId }.toSet() },
            onDismiss = { addOpen = false },
            onPick = { piece ->
                addOpen = false
                drive(labelUpload) { t ->
                    repo.share(t, g.id, piece) { progress = it }
                    notifier.show(msgShared, NotificationType.SUCCESS)
                    pieces = SharedDriveClient(t).listPieces(g.id, myEmail)
                }
            },
        )
    }
    if (deleteGroupOpen && g != null) {
        AlertDialog(
            onDismissRequest = { deleteGroupOpen = false },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(R.string.sh_delete_group)) },
            text = { Text(stringResource(R.string.sh_delete_group_desc, g.name)) },
            confirmButton = {
                Button(onClick = {
                    deleteGroupOpen = false
                    drive(null) { t ->
                        SharedDriveClient(t).trash(g.id)
                        group = null
                        pieces = null
                        groups = groups?.filter { it.id != g.id }
                    }
                }) { Text(stringResource(R.string.sh_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteGroupOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(R.string.sh_remove_piece)) },
            text = { Text(stringResource(R.string.sh_remove_piece_desc, p.title)) },
            confirmButton = {
                Button(onClick = {
                    removing = null
                    val grp = g ?: return@Button
                    drive(null) { t ->
                        SharedDriveClient(t).trashPiece(p.id)
                        repo.links().filter { it.fileId == p.id }.forEach { repo.unlink(it.pieceId) }
                        pieces = SharedDriveClient(t).listPieces(grp.id, myEmail)
                    }
                }) { Text(stringResource(R.string.sh_delete)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    conflict?.let { p ->
        val grp = g
        val pieceId = repo.status(p, byId).second
        AlertDialog(
            onDismissRequest = { conflict = null },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Outlined.SyncProblem, null) },
            title = { Text(stringResource(R.string.sh_conflict_title)) },
            text = { Text(stringResource(R.string.sh_conflict_desc, p.modifiedBy)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        conflict = null
                        if (grp != null) pull(grp, p)
                    }) { Text(stringResource(R.string.sh_conflict_take_theirs)) }
                    if (grp?.canEdit == true && pieceId != null) {
                        TextButton(onClick = {
                            conflict = null
                            push(grp, pieceId)
                        }) { Text(stringResource(R.string.sh_conflict_keep_mine)) }
                    }
                    TextButton(onClick = { conflict = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

// ───────────────────────── časti ─────────────────────────

@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(Icons.Outlined.Groups, size = 72.dp)
        Spacer(Modifier.size(20.dp))
        Text(stringResource(R.string.sh_connect_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.sh_connect_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn480(),
        )
        Spacer(Modifier.size(20.dp))
        Button(onClick = onConnect, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.sh_connect)) }
    }
}

private fun Modifier.widthIn480(): Modifier = this.then(Modifier.fillMaxWidth(0.9f))

@Composable
private fun GroupList(groups: List<SharedGroup>?, onOpen: (SharedGroup) -> Unit) {
    if (groups == null) return
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.sh_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        if (groups.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.sh_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp),
                )
            }
        }
        items(groups, key = { it.id }) { g ->
            Card(
                onClick = { onOpen(g) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Groups, active = g.ownedByMe)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (g.ownedByMe) stringResource(R.string.sh_my_group)
                            else stringResource(R.string.sh_from, g.ownerName.ifBlank { g.ownerEmail }),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupDetail(
    group: SharedGroup,
    pieces: List<SharedPiece>?,
    members: List<SharedMember>,
    myEmail: String,
    revision: Int,
    statusOf: (SharedPiece) -> Pair<SharedStatus, Long?>,
    onPull: (SharedPiece) -> Unit,
    onPush: (Long) -> Unit,
    onConflict: (SharedPiece) -> Unit,
    onOpen: (Long) -> Unit,
    onRemove: (SharedPiece) -> Unit,
    onRemoveMember: (SharedMember) -> Unit,
    onInvite: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // členovia
        item(key = "members") {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.sh_members, members.size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (group.canShare) {
                            FilledTonalButton(onClick = onInvite, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Outlined.PersonAdd, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sh_invite))
                            }
                        }
                    }
                    members.forEach { m ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val me = m.email.equals(myEmail, ignoreCase = true)
                                Text(
                                    (m.name.ifBlank { m.email }) + if (me) " " + stringResource(R.string.sh_me) else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(
                                        when (m.role) {
                                            "owner" -> R.string.sh_role_owner
                                            "writer", "organizer", "fileOrganizer" -> R.string.sh_role_writer
                                            else -> R.string.sh_role_reader
                                        },
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (group.ownedByMe && m.role != "owner") {
                                IconButton(onClick = { onRemoveMember(m) }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.sh_remove_member))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (pieces != null && pieces.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(if (group.canEdit) R.string.sh_no_pieces else R.string.sh_no_pieces_reader),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp),
                )
            }
        }
        items(pieces.orEmpty(), key = { it.id }) { p ->
            val (status, pieceId) = remember(p, revision) { statusOf(p) }
            PieceRow(
                piece = p,
                status = status,
                canEdit = group.canEdit && p.canEdit,
                onMain = {
                    when (status) {
                        SharedStatus.NEW, SharedStatus.REMOTE_NEWER -> onPull(p)
                        SharedStatus.LOCAL_CHANGES -> if (group.canEdit && pieceId != null) onPush(pieceId) else onConflict(p)
                        SharedStatus.CONFLICT -> onConflict(p)
                        SharedStatus.SYNCED -> pieceId?.let(onOpen)
                    }
                },
                onOpen = pieceId?.let { id -> { onOpen(id) } },
                onRemove = { onRemove(p) },
            )
        }
    }
}

@Composable
private fun PieceRow(
    piece: SharedPiece,
    status: SharedStatus,
    canEdit: Boolean,
    onMain: () -> Unit,
    onOpen: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (status == SharedStatus.SYNCED) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(piece.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (piece.composer.isNotBlank()) {
                    Text(piece.composer, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val time = DateUtils.getRelativeTimeSpanString(piece.modifiedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                Text(
                    stringResource(
                        R.string.sh_changed_by,
                        if (piece.modifiedByMe) stringResource(R.string.sh_you) else piece.modifiedBy,
                        time,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        when (status) {
                            SharedStatus.NEW -> R.string.sh_st_new
                            SharedStatus.SYNCED -> R.string.sh_st_synced
                            SharedStatus.REMOTE_NEWER -> R.string.sh_st_remote
                            SharedStatus.LOCAL_CHANGES -> R.string.sh_st_local
                            SharedStatus.CONFLICT -> R.string.sh_st_conflict
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (status == SharedStatus.CONFLICT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            val (icon, label) = when (status) {
                SharedStatus.NEW -> Icons.Outlined.CloudDownload to R.string.sh_download
                SharedStatus.REMOTE_NEWER -> Icons.Outlined.CloudDownload to R.string.sh_update
                SharedStatus.LOCAL_CHANGES -> if (canEdit) Icons.Outlined.CloudUpload to R.string.sh_push else Icons.Outlined.Sync to R.string.sh_resolve
                SharedStatus.CONFLICT -> Icons.Outlined.SyncProblem to R.string.sh_resolve
                SharedStatus.SYNCED -> Icons.Outlined.OpenInNew to R.string.sh_open
            }
            Button(onClick = onMain, shape = RoundedCornerShape(16.dp)) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(label))
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                    if (onOpen != null && status != SharedStatus.SYNCED) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sh_open)) },
                            leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) },
                            onClick = { menu = false; onOpen() },
                        )
                    }
                    if (canEdit) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sh_remove_piece)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; onRemove() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Outlined.Groups, null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                singleLine = true,
                label = { Text(stringResource(R.string.sh_group_name)) },
                placeholder = { Text(stringResource(R.string.sh_group_name_hint)) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(if (initial.isEmpty()) R.string.create else R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun InviteDialog(onDismiss: () -> Unit, onInvite: (String, Boolean) -> Unit) {
    var email by remember { mutableStateOf("") }
    var canEdit by remember { mutableStateOf(true) }
    val valid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Outlined.PersonAdd, null) },
        title = { Text(stringResource(R.string.sh_invite)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.sh_invite_desc), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sh_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().clickable { canEdit = !canEdit }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sh_can_edit), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (canEdit) R.string.sh_can_edit_on else R.string.sh_can_edit_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = canEdit, onCheckedChange = { canEdit = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onInvite(email.trim(), canEdit) }, enabled = valid) { Text(stringResource(R.string.sh_send_invite)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AddPieceDialog(
    pieces: List<PieceEntity>,
    alreadyShared: Set<Long>,
    onDismiss: () -> Unit,
    onPick: (PieceEntity) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val list = remember(pieces, q) {
        pieces.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.composer.lowercase().contains(q) }
            .sortedBy { it.title.lowercase() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Outlined.LibraryMusic, null) },
        title = { Text(stringResource(R.string.sh_add_piece)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.search)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(list, key = { it.id }) { p ->
                        val shared = p.id in alreadyShared
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !shared) { onPick(p) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (shared) stringResource(R.string.sh_already_shared) else p.composer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Text(stringResource(R.string.sh_add_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
