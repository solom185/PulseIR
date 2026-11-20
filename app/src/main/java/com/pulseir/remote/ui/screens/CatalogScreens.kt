package com.pulseir.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulseir.remote.R
import com.pulseir.remote.data.CatalogBrand
import com.pulseir.remote.data.CatalogCategory
import com.pulseir.remote.data.CatalogRemote
import com.pulseir.remote.data.CatalogRepository
import com.pulseir.remote.data.UserRepository
import com.pulseir.remote.ir.AcMode
import com.pulseir.remote.ir.AcProtocolEngine
import com.pulseir.remote.ir.AcState
import com.pulseir.remote.ir.ButtonRole
import com.pulseir.remote.ir.IrButton
import com.pulseir.remote.ir.IrTransmitter
import com.pulseir.remote.ir.SmartAcProtocol
import com.pulseir.remote.ui.components.ALL_ROOMS
import com.pulseir.remote.ui.components.IrLedStatusPill
import com.pulseir.remote.ui.components.TactileRemoteButton
import com.pulseir.remote.ui.components.categoryAccentColor
import com.pulseir.remote.ui.components.categoryIcon
import com.pulseir.remote.ui.components.roomDisplayName
import com.pulseir.remote.ui.theme.PulseColors

private enum class CatalogStep { CATEGORIES, BRANDS, TEST_MODEL, TEST_SMART_AC }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogBrowserScreen(
    catalogRepository: CatalogRepository,
    userRepository: UserRepository,
    irTransmitter: IrTransmitter,
    onBack: () -> Unit,
    onDeviceSaved: (String) -> Unit,
) {
    val isArabic = LocalConfiguration.current.locales[0]?.language == "ar"
    val lastPulse by irTransmitter.lastPulse.collectAsState()

    var step by remember { mutableStateOf(CatalogStep.CATEGORIES) }
    var categories by remember { mutableStateOf<List<CatalogCategory>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<CatalogCategory?>(null) }
    var globalQuery by remember { mutableStateOf("") }
    var globalResults by remember { mutableStateOf<List<CatalogRemote>>(emptyList()) }

    var brandQuery by remember { mutableStateOf("") }
    var brands by remember { mutableStateOf<List<CatalogBrand>>(emptyList()) }
    var selectedBrand by remember { mutableStateOf("") }

    var remotes by remember { mutableStateOf<List<CatalogRemote>>(emptyList()) }
    var modelIndex by remember { mutableIntStateOf(0) }
    var currentButtons by remember { mutableStateOf<List<IrButton>>(emptyList()) }

    var selectedSmartAc by remember { mutableStateOf<SmartAcProtocol?>(null) }

    var deviceName by remember { mutableStateOf("") }
    var selectedRoom by remember { mutableStateOf("living") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        categories = catalogRepository.getCategories()
        loading = false
    }

    LaunchedEffect(globalQuery) {
        globalResults = if (globalQuery.trim().length >= 2) {
            catalogRepository.searchRemotes(globalQuery, 40)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(selectedCategory, brandQuery, step) {
        if (step == CatalogStep.BRANDS && selectedCategory != null) {
            brands = catalogRepository.getBrands(selectedCategory!!.id, brandQuery)
        }
    }

    LaunchedEffect(remotes, modelIndex, step) {
        if (step == CatalogStep.TEST_MODEL && remotes.isNotEmpty()) {
            val r = remotes[modelIndex.coerceIn(0, remotes.lastIndex)]
            currentButtons = catalogRepository.getButtons(r.id)
            deviceName = "${r.brand} ${r.model}".trim()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (step) {
                            CatalogStep.CATEGORIES -> stringResource(R.string.select_category)
                            CatalogStep.BRANDS -> selectedCategory?.localizedName(isArabic) ?: stringResource(R.string.select_brand)
                            CatalogStep.TEST_MODEL -> selectedBrand.ifBlank { stringResource(R.string.select_model) }
                            CatalogStep.TEST_SMART_AC -> stringResource(R.string.smart_ac_section_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (step) {
                            CatalogStep.CATEGORIES -> onBack()
                            CatalogStep.BRANDS -> step = CatalogStep.CATEGORIES
                            CatalogStep.TEST_MODEL, CatalogStep.TEST_SMART_AC -> {
                                step = if (selectedCategory != null) CatalogStep.BRANDS else CatalogStep.CATEGORIES
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IrLedStatusPill(
                        hasIrEmitter = irTransmitter.hasIrEmitter,
                        lastPulse = lastPulse,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PulseColors.Crimson)
            }
            return@Scaffold
        }

        when (step) {
            CatalogStep.CATEGORIES -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                ) {
                    OutlinedTextField(
                        value = globalQuery,
                        onValueChange = { globalQuery = it },
                        placeholder = { Text(stringResource(R.string.search)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )

                    if (globalQuery.trim().length >= 2) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            items(globalResults, key = { it.id }) { r ->
                                Surface(
                                    onClick = {
                                        remotes = listOf(r)
                                        selectedBrand = r.brand
                                        modelIndex = 0
                                        step = CatalogStep.TEST_MODEL
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            categoryIcon(r.categoryId),
                                            contentDescription = null,
                                            tint = categoryAccentColor(r.categoryId),
                                            modifier = Modifier.size(24.dp),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("${r.brand} · ${r.model}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                stringResource(R.string.buttons_count, r.buttonCount),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(categories, key = { it.id }) { cat ->
                                val accent = categoryAccentColor(cat.id)
                                Surface(
                                    onClick = {
                                        selectedCategory = cat
                                        brandQuery = ""
                                        step = CatalogStep.BRANDS
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(accent.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(categoryIcon(cat.id), contentDescription = null, tint = accent)
                                        }
                                        Text(
                                            text = cat.localizedName(isArabic),
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${stringResource(R.string.brands_count, cat.brandCount)} · ${stringResource(R.string.remotes_count, cat.remoteCount)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            CatalogStep.BRANDS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                ) {
                    OutlinedTextField(
                        value = brandQuery,
                        onValueChange = { brandQuery = it },
                        placeholder = { Text(stringResource(R.string.select_brand)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        // Show Smart Full-Thermostat AC engines at top when browsing AC!
                        if (selectedCategory?.id == "ac" && brandQuery.isBlank()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = PulseColors.Cyan.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, PulseColors.Cyan.copy(alpha = 0.4f)),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = PulseColors.Cyan, modifier = Modifier.size(18.dp))
                                            Text(stringResource(R.string.smart_ac_section_title), style = MaterialTheme.typography.titleMedium, color = PulseColors.Cyan)
                                        }
                                        Text(
                                            text = stringResource(R.string.smart_ac_section_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        for (proto in SmartAcProtocol.entries) {
                                            Surface(
                                                onClick = {
                                                    selectedSmartAc = proto
                                                    deviceName = proto.localizedName(isArabic)
                                                    step = CatalogStep.TEST_SMART_AC
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(Icons.Rounded.AcUnit, contentDescription = null, tint = PulseColors.Cyan, modifier = Modifier.size(22.dp))
                                                    Spacer(Modifier.width(10.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(proto.localizedName(isArabic), style = MaterialTheme.typography.titleMedium)
                                                        Text(
                                                            proto.compatibleBrands,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        items(brands, key = { it.name }) { brand ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        selectedBrand = brand.name
                                        modelIndex = 0
                                        step = CatalogStep.TEST_MODEL
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(brand.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = stringResource(R.string.remotes_count, brand.remoteCount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            CatalogStep.TEST_SMART_AC -> {
                val proto = selectedSmartAc ?: SmartAcProtocol.COOLIX
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, PulseColors.Cyan.copy(alpha = 0.45f)),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(proto.localizedName(isArabic), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    proto.compatibleBrands,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PulseColors.Cyan,
                                )
                                Text(
                                    stringResource(R.string.point_and_test),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TactileRemoteButton(
                                        onPress = {
                                            irTransmitter.transmit(
                                                AcProtocolEngine.encode(proto, AcState(powerOn = true, mode = AcMode.COOL, tempC = 24)),
                                                "Cool 24°C ON",
                                            )
                                        },
                                        icon = Icons.Rounded.PowerSettingsNew,
                                        label = "${stringResource(R.string.ac_cool)} 24°C",
                                        containerColor = PulseColors.Emerald,
                                        contentColor = Color.White,
                                        height = 60.dp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TactileRemoteButton(
                                        onPress = {
                                            irTransmitter.transmit(
                                                AcProtocolEngine.encode(proto, AcState(powerOn = false)),
                                                "AC OFF",
                                            )
                                        },
                                        icon = Icons.Rounded.PowerSettingsNew,
                                        label = stringResource(R.string.power_off),
                                        containerColor = PulseColors.Crimson,
                                        contentColor = Color.White,
                                        height = 60.dp,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedTextField(
                                    value = deviceName,
                                    onValueChange = { deviceName = it },
                                    label = { Text(stringResource(R.string.device_name_label)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    for (room in ALL_ROOMS) {
                                        FilterChip(
                                            selected = selectedRoom == room,
                                            onClick = { selectedRoom = room },
                                            label = { Text(roomDisplayName(room)) },
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        val saved = userRepository.addDevice(
                                            name = deviceName,
                                            room = selectedRoom,
                                            categoryId = "ac",
                                            brand = proto.name,
                                            model = "Smart Thermostat",
                                            catalogRemoteId = null,
                                            smartAcProtocolId = proto.id,
                                        )
                                        onDeviceSaved(saved.id)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Emerald),
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.save_this_remote))
                                }
                            }
                        }
                    }
                }
            }

            CatalogStep.TEST_MODEL -> {
                LaunchedEffect(selectedCategory, selectedBrand) {
                    if (selectedCategory != null && selectedBrand.isNotBlank() && remotes.none { it.brand == selectedBrand && it.categoryId == selectedCategory!!.id }) {
                        remotes = catalogRepository.getRemotes(selectedCategory!!.id, selectedBrand)
                        modelIndex = 0
                    }
                }

                val currentRemote = remotes.getOrNull(modelIndex)
                if (currentRemote == null) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val priorityRoles = listOf(
                        ButtonRole.POWER, ButtonRole.POWER_ON, ButtonRole.POWER_OFF,
                        ButtonRole.MUTE, ButtonRole.VOL_UP, ButtonRole.VOL_DOWN,
                        ButtonRole.CH_UP, ButtonRole.MENU, ButtonRole.INPUT,
                    )
                    val testButtons = remember(currentButtons) {
                        val picked = priorityRoles.mapNotNull { role -> currentButtons.firstOrNull { it.role == role } }.distinctBy { it.id }.toMutableList()
                        for (b in currentButtons) {
                            if (picked.size >= 6) break
                            if (picked.none { it.id == b.id }) picked.add(b)
                        }
                        picked.take(6)
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.model_of, modelIndex + 1, remotes.size),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = PulseColors.Cyan,
                                        )
                                        Text(
                                            text = stringResource(R.string.buttons_count, currentRemote.buttonCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = "${currentRemote.brand} — ${currentRemote.model}",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        text = stringResource(R.string.point_and_test),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    testButtons.chunked(2).forEach { rowBtns ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            for (btn in rowBtns) {
                                                val isPower = btn.role in setOf(ButtonRole.POWER, ButtonRole.POWER_ON, ButtonRole.POWER_OFF)
                                                TactileRemoteButton(
                                                    onPress = { irTransmitter.transmit(btn.signal, btn.name) },
                                                    label = btn.name,
                                                    icon = if (isPower) Icons.Rounded.PowerSettingsNew else null,
                                                    containerColor = if (isPower) PulseColors.Crimson else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    contentColor = if (isPower) Color.White else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f),
                                                    height = 58.dp,
                                                )
                                            }
                                            if (rowBtns.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }

                                    if (remotes.size > 1) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            OutlinedButton(
                                                onClick = { modelIndex = (modelIndex - 1 + remotes.size) % remotes.size },
                                                modifier = Modifier.weight(1f).height(46.dp),
                                                shape = RoundedCornerShape(14.dp),
                                            ) {
                                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null)
                                                Text(stringResource(R.string.prev_model))
                                            }
                                            OutlinedButton(
                                                onClick = { modelIndex = (modelIndex + 1) % remotes.size },
                                                modifier = Modifier.weight(1f).height(46.dp),
                                                shape = RoundedCornerShape(14.dp),
                                            ) {
                                                Text(stringResource(R.string.next_model))
                                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedTextField(
                                        value = deviceName,
                                        onValueChange = { deviceName = it },
                                        label = { Text(stringResource(R.string.device_name_label)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = stringResource(R.string.room_label),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        for (room in ALL_ROOMS) {
                                            FilterChip(
                                                selected = selectedRoom == room,
                                                onClick = { selectedRoom = room },
                                                label = { Text(roomDisplayName(room)) },
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            val saved = userRepository.addDevice(
                                                name = deviceName,
                                                room = selectedRoom,
                                                categoryId = currentRemote.categoryId,
                                                brand = currentRemote.brand,
                                                model = currentRemote.model,
                                                catalogRemoteId = currentRemote.id,
                                            )
                                            onDeviceSaved(saved.id)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Emerald),
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.save_this_remote))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
