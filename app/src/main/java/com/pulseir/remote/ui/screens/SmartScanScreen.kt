package com.pulseir.remote.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulseir.remote.R
import com.pulseir.remote.data.CatalogBrand
import com.pulseir.remote.data.CatalogCategory
import com.pulseir.remote.data.CatalogRemote
import com.pulseir.remote.data.CatalogRepository
import com.pulseir.remote.data.ScanCandidate
import com.pulseir.remote.data.UserRepository
import com.pulseir.remote.ir.ButtonRole
import com.pulseir.remote.ir.IrButton
import com.pulseir.remote.ir.IrTransmitter
import com.pulseir.remote.ui.components.ALL_ROOMS
import com.pulseir.remote.ui.components.IrLedStatusPill
import com.pulseir.remote.ui.components.KeepScreenOnEffect
import com.pulseir.remote.ui.components.TactileRemoteButton
import com.pulseir.remote.ui.components.categoryIcon
import com.pulseir.remote.ui.components.roomDisplayName
import com.pulseir.remote.ui.theme.PulseColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartScanScreen(
    catalogRepository: CatalogRepository,
    userRepository: UserRepository,
    irTransmitter: IrTransmitter,
    onDeviceSaved: (String) -> Unit,
) {
    val isArabic = LocalConfiguration.current.locales[0]?.language == "ar"
    val lastPulse by irTransmitter.lastPulse.collectAsState()

    var categories by remember { mutableStateOf<List<CatalogCategory>>(emptyList()) }
    var selectedCategoryId by remember { mutableStateOf("tv") }
    var brands by remember { mutableStateOf<List<CatalogBrand>>(emptyList()) }
    var selectedBrand by remember { mutableStateOf<String?>(null) }
    var brandDropdownExpanded by remember { mutableStateOf(false) }

    var candidates by remember { mutableStateOf<List<ScanCandidate>>(emptyList()) }
    var inActiveSession by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isScanning by remember { mutableStateOf(false) }
    var scanDelayMs by remember { mutableLongStateOf(450L) }

    // Keep screen awake during an active scan session so screen never locks mid-blast
    KeepScreenOnEffect(enabled = inActiveSession && isScanning)

    // Verification sheet state (tracks candidate index so user can rescue 1-step late reactions!)
    var verifyingIndex by remember { mutableStateOf<Int?>(null) }
    var matchingRemotes by remember { mutableStateOf<List<CatalogRemote>>(emptyList()) }
    var verifyModelIndex by remember { mutableIntStateOf(0) }
    var verifyButtons by remember { mutableStateOf<List<IrButton>>(emptyList()) }
    var saveName by remember { mutableStateOf("") }
    var saveRoom by remember { mutableStateOf("living") }

    LaunchedEffect(Unit) {
        categories = catalogRepository.getCategories()
    }

    LaunchedEffect(selectedCategoryId) {
        isScanning = false
        inActiveSession = false
        selectedBrand = null
        brands = catalogRepository.getBrands(selectedCategoryId)
        candidates = catalogRepository.getScanCandidates(selectedCategoryId, null)
        currentIndex = 0
    }

    LaunchedEffect(selectedCategoryId, selectedBrand) {
        isScanning = false
        candidates = catalogRepository.getScanCandidates(selectedCategoryId, selectedBrand)
        currentIndex = 0
    }

    // Auto-scan coroutine loop
    LaunchedEffect(isScanning, candidates, scanDelayMs) {
        if (!isScanning || candidates.isEmpty()) return@LaunchedEffect
        while (isScanning && currentIndex < candidates.size) {
            val cand = candidates[currentIndex]
            irTransmitter.transmit(
                signal = cand.powerButton.signal,
                label = "${cand.sampleBrand}: ${cand.powerButton.name}",
                performHaptic = true,
            )
            delay(scanDelayMs)
            if (!isScanning) break
            if (currentIndex + 1 < candidates.size) {
                currentIndex++
            } else {
                isScanning = false
            }
        }
    }

    // Load matching remotes when verifying a hit (or stepping back to previous candidate)
    LaunchedEffect(verifyingIndex) {
        val idx = verifyingIndex ?: return@LaunchedEffect
        val cand = candidates.getOrNull(idx) ?: return@LaunchedEffect
        matchingRemotes = catalogRepository.getRemotesByPowerFingerprint(
            categoryId = selectedCategoryId,
            fingerprint = cand.fingerprint,
            brandFilter = selectedBrand,
        )
        verifyModelIndex = 0
    }

    LaunchedEffect(matchingRemotes, verifyModelIndex) {
        val r = matchingRemotes.getOrNull(verifyModelIndex) ?: return@LaunchedEffect
        verifyButtons = catalogRepository.getButtons(r.id)
        saveName = "${r.brand} ${r.model}".trim()
    }

    AnimatedContent(
        targetState = inActiveSession,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        label = "scanPhase",
    ) { active ->
        if (!active) {
            // PHASE 1: Calm, Zero-Clutter Setup
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.smart_scan_title),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        IrLedStatusPill(hasIrEmitter = irTransmitter.hasIrEmitter, lastPulse = lastPulse)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.smart_scan_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Category chips
                item {
                    Text(
                        text = stringResource(R.string.select_category),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (cat in categories) {
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                leadingIcon = {
                                    Icon(categoryIcon(cat.id), contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(cat.localizedName(isArabic)) },
                            )
                        }
                    }
                }

                // Optional Brand Filter
                item {
                    ExposedDropdownMenuBox(
                        expanded = brandDropdownExpanded,
                        onExpandedChange = { brandDropdownExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedBrand ?: stringResource(R.string.all_brands),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.select_brand)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandDropdownExpanded) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = brandDropdownExpanded,
                            onDismissRequest = { brandDropdownExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.all_brands)) },
                                onClick = {
                                    selectedBrand = null
                                    brandDropdownExpanded = false
                                },
                            )
                            for (b in brands) {
                                DropdownMenuItem(
                                    text = { Text("${b.name} (${b.remoteCount})") },
                                    onClick = {
                                        selectedBrand = b.name
                                        brandDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Speed Selector
                item {
                    Text(
                        text = stringResource(R.string.scan_speed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = scanDelayMs == 250L,
                            onClick = { scanDelayMs = 250L },
                            label = { Text(stringResource(R.string.speed_turbo), maxLines = 1) },
                        )
                        FilterChip(
                            selected = scanDelayMs == 450L,
                            onClick = { scanDelayMs = 450L },
                            label = { Text(stringResource(R.string.speed_fast), maxLines = 1) },
                        )
                        FilterChip(
                            selected = scanDelayMs == 1000L,
                            onClick = { scanDelayMs = 1000L },
                            label = { Text(stringResource(R.string.speed_normal), maxLines = 1) },
                        )
                        FilterChip(
                            selected = scanDelayMs == 2000L,
                            onClick = { scanDelayMs = 2000L },
                            label = { Text(stringResource(R.string.speed_slow), maxLines = 1) },
                        )
                    }
                }

                // Launch Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            RadarEmitterBadge(active = false)
                            if (candidates.isNotEmpty()) {
                                Text(
                                    text = "${candidates.size} ${stringResource(R.string.power)} · ${selectedBrand ?: stringResource(R.string.all_brands)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PulseColors.Cyan,
                                )
                                Button(
                                    onClick = {
                                        currentIndex = 0
                                        inActiveSession = true
                                        isScanning = true
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Crimson),
                                ) {
                                    Icon(Icons.Rounded.Radar, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.start_scan), style = MaterialTheme.typography.titleMedium)
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.no_power_codes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // PHASE 2: Immersive Zero-Distraction Active Radar
            val activeCandidate = candidates.getOrNull(currentIndex)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top status strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            isScanning = false
                            inActiveSession = false
                        },
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.scan_stop))
                    }
                    IrLedStatusPill(hasIrEmitter = irTransmitter.hasIrEmitter, lastPulse = lastPulse)
                }

                // Center Radar + Live Telemetry
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadarEmitterBadge(active = isScanning, sizeDp = 152)

                    Text(
                        text = if (isScanning) stringResource(R.string.scanning_now) else stringResource(R.string.scan_paused),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isScanning) PulseColors.Crimson else PulseColors.Amber,
                    )

                    if (activeCandidate != null) {
                        Text(
                            text = stringResource(R.string.scanning_code, currentIndex + 1, candidates.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = PulseColors.Cyan,
                        )
                        LinearProgressIndicator(
                            progress = { (currentIndex + 1).toFloat() / candidates.size.coerceAtLeast(1).toFloat() },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = PulseColors.Crimson,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = activeCandidate.sampleBrand,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "${activeCandidate.sampleModel}  ·  ${activeCandidate.powerButton.signal.shortBadge()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Bottom Thumb-Reachable Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Giant impossible-to-miss IT WORKED! button right under the thumb
                    Button(
                        onClick = {
                            isScanning = false
                            verifyingIndex = currentIndex
                        },
                        enabled = activeCandidate != null,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Emerald),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.it_worked),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }

                    // Micro-controls: Prev / Pause-Resume / Re-blast / Next
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                isScanning = false
                                if (currentIndex > 0) {
                                    currentIndex--
                                    val c = candidates[currentIndex]
                                    irTransmitter.transmit(c.powerButton.signal, c.sampleBrand)
                                }
                            },
                            enabled = currentIndex > 0,
                            modifier = Modifier.weight(0.8f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null)
                        }

                        Button(
                            onClick = {
                                if (!isScanning && currentIndex >= candidates.lastIndex) currentIndex = 0
                                isScanning = !isScanning
                            },
                            modifier = Modifier.weight(1.4f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) PulseColors.Amber else PulseColors.Crimson,
                            ),
                        ) {
                            Icon(if (isScanning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isScanning) stringResource(R.string.pause_scan) else stringResource(R.string.resume_scan),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                activeCandidate?.let { irTransmitter.transmit(it.powerButton.signal, it.sampleBrand) }
                            },
                            modifier = Modifier.weight(0.9f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.reblast))
                        }

                        OutlinedButton(
                            onClick = {
                                isScanning = false
                                if (currentIndex + 1 < candidates.size) {
                                    currentIndex++
                                    val c = candidates[currentIndex]
                                    irTransmitter.transmit(c.powerButton.signal, c.sampleBrand)
                                }
                            },
                            enabled = currentIndex + 1 < candidates.size,
                            modifier = Modifier.weight(0.8f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }

    // Verification & Reaction-Time Rescue Sheet
    val vIdx = verifyingIndex
    if (vIdx != null) {
        val remote = matchingRemotes.getOrNull(verifyModelIndex)
        ModalBottomSheet(
            onDismissRequest = { verifyingIndex = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.verify_match_title), style = MaterialTheme.typography.titleLarge)

                // Last 5 blasted codes rescue strip (essential for Turbo 0.25s & Fast 0.45s speeds!)
                if (vIdx > 0) {
                    val recentIndices = remember(vIdx) {
                        val start = (vIdx - 4).coerceAtLeast(0)
                        (vIdx downTo start).toList()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.recent_blasted_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = PulseColors.Amber,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (idx in recentIndices) {
                                val cand = candidates.getOrNull(idx) ?: continue
                                FilterChip(
                                    selected = idx == vIdx,
                                    onClick = {
                                        verifyingIndex = idx
                                        currentIndex = idx
                                        irTransmitter.transmit(cand.powerButton.signal, cand.sampleBrand)
                                    },
                                    label = { Text("#${idx + 1} ${cand.sampleBrand}", maxLines = 1) },
                                )
                            }
                        }
                    }
                }

                Text(
                    stringResource(R.string.verify_match_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (remote != null) {
                    if (matchingRemotes.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { verifyModelIndex = (verifyModelIndex - 1 + matchingRemotes.size) % matchingRemotes.size },
                                shape = RoundedCornerShape(12.dp),
                            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null) }

                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(
                                    "${remote.brand} · ${remote.model}",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(R.string.model_of, verifyModelIndex + 1, matchingRemotes.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PulseColors.Cyan,
                                )
                            }

                            OutlinedButton(
                                onClick = { verifyModelIndex = (verifyModelIndex + 1) % matchingRemotes.size },
                                shape = RoundedCornerShape(12.dp),
                            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }
                        }
                    }

                    val verifyRoles = listOf(ButtonRole.VOL_UP, ButtonRole.VOL_DOWN, ButtonRole.MUTE, ButtonRole.MENU, ButtonRole.CH_UP, ButtonRole.POWER)
                    val sampleBtns = remember(verifyButtons) {
                        val list = verifyRoles.mapNotNull { r -> verifyButtons.firstOrNull { it.role == r } }.distinctBy { it.id }.toMutableList()
                        for (b in verifyButtons) {
                            if (list.size >= 4) break
                            if (list.none { it.id == b.id }) list.add(b)
                        }
                        list.take(4)
                    }
                    sampleBtns.chunked(2).forEach { rowBtns ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (btn in rowBtns) {
                                TactileRemoteButton(
                                    onPress = { irTransmitter.transmit(btn.signal, btn.name) },
                                    label = btn.name,
                                    icon = if (btn.role == ButtonRole.POWER) Icons.Rounded.PowerSettingsNew else null,
                                    modifier = Modifier.weight(1f),
                                    height = 48.dp,
                                )
                            }
                            if (rowBtns.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }

                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text(stringResource(R.string.device_name_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (rm in ALL_ROOMS) {
                            FilterChip(
                                selected = saveRoom == rm,
                                onClick = { saveRoom = rm },
                                label = { Text(roomDisplayName(rm)) },
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val saved = userRepository.addDevice(
                                name = saveName,
                                room = saveRoom,
                                categoryId = remote.categoryId,
                                brand = remote.brand,
                                model = remote.model,
                                catalogRemoteId = remote.id,
                            )
                            verifyingIndex = null
                            inActiveSession = false
                            onDeviceSaved(saved.id)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Emerald),
                    ) {
                        Text(stringResource(R.string.save_this_remote), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun RadarEmitterBadge(active: Boolean, sizeDp: Int = 112) {
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radarPulse",
    )

    Box(modifier = Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = (size.minDimension / 2f) * pulse
                drawCircle(
                    color = PulseColors.Crimson.copy(alpha = (1f - pulse) * 0.72f),
                    radius = r,
                    style = Stroke(width = 4f),
                )
                val p2 = (pulse + 0.5f) % 1f
                drawCircle(
                    color = PulseColors.Cyan.copy(alpha = (1f - p2) * 0.55f),
                    radius = (size.minDimension / 2f) * p2,
                    style = Stroke(width = 3f),
                )
            }
        }
        Box(
            modifier = Modifier
                .size((sizeDp * 0.54f).dp)
                .clip(CircleShape)
                .background(if (active) PulseColors.Crimson.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Radar,
                contentDescription = null,
                tint = if (active) PulseColors.Crimson else PulseColors.Cyan,
                modifier = Modifier.size((sizeDp * 0.28f).dp),
            )
        }
    }
}
