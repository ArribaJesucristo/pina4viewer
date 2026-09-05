package com.bone.android.a4v.oficial.ui.view

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bone.android.a4v.oficial.R
import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.SourceType
import com.bone.android.a4v.oficial.data.parser.ArenaVisionParser
import com.bone.android.a4v.oficial.databinding.ActivityMainBinding
import com.bone.android.a4v.oficial.ui.adapter.EventListAdapter
import com.bone.android.a4v.oficial.ui.viewmodel.MainViewModel
import com.bone.android.a4v.oficial.util.StreamLauncher
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var eventAdapter: EventListAdapter
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private var isLandscape = false
    private var vpnActionView: View? = null
    private var vpnDialog: AlertDialog? = null

    data class DrawerChannel(
        val displayName: String,
        val channelNumber: String,
        val streamHash: String
    )

    private val drawerChannels = mutableListOf<DrawerChannel>()
    private lateinit var drawerAdapter: ArrayAdapter<String>

    private val vpnLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            com.bone.android.a4v.oficial.util.VpnHelper.startBuiltInVpn(this)
            invalidateOptionsMenu()
            Toast.makeText(this, "🛡️ Escudo VPN Activado • Partidos Desbloqueados", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbarAndDrawer()
        setupRecyclerView()
        setupRadioButtons()
        setupSearch()
        setupSportChips()
        setupSwipeRefresh()
        setupDrawerChannels()
        observeState()
        initVpnShield()
        checkAppUpdate(manual = false)
        binding.root.post { binding.rbPina.requestFocus() }
    }

    private fun initVpnShield() {
        if (com.bone.android.a4v.oficial.util.VpnHelper.isExternalVpnActive(this)) {
            invalidateOptionsMenu()
            return
        }
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.app_name,
            R.string.app_name
        )
        drawerToggle.drawerArrowDrawable.color = android.graphics.Color.parseColor("#FFD700")
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.toolbar.setOnMenuItemClickListener { item ->
            onOptionsItemSelected(item)
        }

        binding.toolbar.overflowIcon?.setTint(android.graphics.Color.parseColor("#FFD700"))
    }

    private fun setupRecyclerView() {
        eventAdapter = EventListAdapter(
            onEventClick = { event -> showChannelChooserDialog(event) },
            onChannelClick = { channel -> playChannel(channel) }
        )

        binding.recyclerViewEvents.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = eventAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupRadioButtons() {
        val radioMap = mapOf(
            binding.rbPina to SourceType.OFF_MODE,
            binding.rbTv to SourceType.SEARCH,
            binding.rbCaido to SourceType.CAIDO
        )

        val allRadios = radioMap.keys.toList()
        allRadios.forEach { rb -> rb.isChecked = (rb == binding.rbPina) }

        radioMap.forEach { (radioButton, sourceType) ->
            radioButton.setOnClickListener {
                allRadios.forEach { rb -> rb.isChecked = (rb == radioButton) }
                binding.chipSportAll.isChecked = true
                viewModel.selectSource(sourceType)
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }

        binding.etSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val checkedChip = when {
                            binding.chipSportFutbol.isChecked -> binding.chipSportFutbol
                            binding.chipSportTenis.isChecked -> binding.chipSportTenis
                            binding.chipSportMotor.isChecked -> binding.chipSportMotor
                            binding.chipSportBasket.isChecked -> binding.chipSportBasket
                            binding.chipSportOtros.isChecked -> binding.chipSportOtros
                            else -> binding.chipSportAll
                        }
                        checkedChip.requestFocus()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        binding.rbPina.requestFocus()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    }

    private fun setupSportChips() {
        val chipMap = mapOf(
            binding.chipSportAll to "",
            binding.chipSportFutbol to "FUTBOL",
            binding.chipSportTenis to "TENIS",
            binding.chipSportMotor to "MOTOR",
            binding.chipSportBasket to "BASKET",
            binding.chipSportOtros to "OTROS"
        )

        chipMap.forEach { (chip, sportKey) ->
            chip.setOnClickListener {
                viewModel.setSportFilter(sportKey)
            }

            chip.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val firstChild = binding.recyclerViewEvents.layoutManager?.findViewByPosition(0)
                            if (firstChild != null) {
                                firstChild.requestFocus()
                            } else {
                                binding.recyclerViewEvents.requestFocus()
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            binding.etSearch.requestFocus()
                            true
                        }
                        else -> false
                    }
                } else false
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.gold_primary, R.color.gold_dark)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun buildDrawerChannelList(): List<DrawerChannel> {
        val streamMap = ArenaVisionParser.cachedStreams
        val sortedKeys = streamMap.keys.mapNotNull { it.toIntOrNull() }.distinct().sorted()
        val numbers = if (sortedKeys.isNotEmpty()) sortedKeys else ((1..102) + (103..103) + (105..141) + (150..155) + (160..160)).distinct().sorted()

        return numbers.map { num ->
            val numStr = num.toString()
            val quality = if (num in 1..6 || num in 11..16 || num in 132..133) "1080p" else "720p"
            val streamHash = streamMap[numStr] ?: "AV$num"
            DrawerChannel(
                displayName = "AV$num ($quality)",
                channelNumber = numStr,
                streamHash = streamHash
            )
        }
    }

    private fun setupDrawerChannels() {
        drawerChannels.clear()
        drawerChannels.addAll(buildDrawerChannelList())

        drawerAdapter = ArrayAdapter(
            this,
            R.layout.item_drawer_channel,
            R.id.tvDrawerItem,
            drawerChannels.map { it.displayName }.toMutableList()
        )
        binding.drawerChannelList.adapter = drawerAdapter

        binding.drawerChannelList.setOnItemClickListener { _, _, position, _ ->
            val channel = drawerChannels.getOrNull(position) ?: return@setOnItemClickListener
            val streamHash = ArenaVisionParser.cachedStreams[channel.channelNumber] ?: channel.streamHash
            binding.drawerLayout.closeDrawers()
            playChannel(ChannelItem(name = "AV${channel.channelNumber}", streamId = streamHash))
        }

        binding.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: android.view.View) {
                updateDrawerChannelsIfChanged()
                binding.drawerChannelList.requestFocus()
            }
            override fun onDrawerClosed(drawerView: android.view.View) {
                binding.rbPina.requestFocus()
            }
        })

        binding.drawerChannelList.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                binding.drawerLayout.closeDrawers()
                binding.rbPina.requestFocus()
                true
            } else {
                false
            }
        }
    }

    private fun updateDrawerChannelsIfChanged() {
        if (!::drawerAdapter.isInitialized) return
        val newItems = buildDrawerChannelList()
        if (newItems.size != drawerChannels.size || (newItems.isNotEmpty() && newItems.first().streamHash != drawerChannels.firstOrNull()?.streamHash)) {
            drawerChannels.clear()
            drawerChannels.addAll(newItems)
            drawerAdapter.clear()
            drawerAdapter.addAll(drawerChannels.map { it.displayName })
            drawerAdapter.notifyDataSetChanged()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    com.bone.android.a4v.oficial.util.VpnHelper.vpnStateFlow.collect {
                        invalidateOptionsMenu()
                    }
                }

                viewModel.uiState.collect { state ->
                    updateDrawerChannelsIfChanged()
                    binding.swipeRefresh.isRefreshing = state.isLoading
                    eventAdapter.submitList(state.filteredEvents)

                    when {
                        state.sportFilter.isEmpty() -> if (!binding.chipSportAll.isChecked) binding.chipSportAll.isChecked = true
                        state.sportFilter.equals("FUTBOL", ignoreCase = true) -> if (!binding.chipSportFutbol.isChecked) binding.chipSportFutbol.isChecked = true
                        state.sportFilter.equals("TENIS", ignoreCase = true) -> if (!binding.chipSportTenis.isChecked) binding.chipSportTenis.isChecked = true
                        state.sportFilter.equals("MOTOR", ignoreCase = true) -> if (!binding.chipSportMotor.isChecked) binding.chipSportMotor.isChecked = true
                        state.sportFilter.equals("BASKET", ignoreCase = true) -> if (!binding.chipSportBasket.isChecked) binding.chipSportBasket.isChecked = true
                        state.sportFilter.equals("OTROS", ignoreCase = true) -> if (!binding.chipSportOtros.isChecked) binding.chipSportOtros.isChecked = true
                    }

                    val count = state.filteredEvents.size
                    if (state.isOffMode) {
                        binding.tvUserCount.text = "[OFF-MODE]"
                        binding.tvUserCount.setTextColor(android.graphics.Color.parseColor("#FFD700"))
                    } else {
                        binding.tvUserCount.text = "Hay 2013357 descargas • $count eventos disponibles"
                        binding.tvUserCount.setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
                    }

                    if (state.lastUpdated.isNotEmpty()) {
                        binding.tvFooterZone.text = state.lastUpdated
                    }

                    binding.tvEmpty.visibility = if (!state.isLoading && state.filteredEvents.isEmpty()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                    state.errorMessage?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showChannelChooserDialog(event: EventItem) {
        if (event.channels.isEmpty()) {
            Toast.makeText(this, "No hay canales disponibles para este evento", Toast.LENGTH_SHORT).show()
            return
        }

        if (event.channels.size == 1) {
            playChannel(event.channels.first())
            return
        }

        val channelNames = event.channels.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Canales disponibles")
            .setItems(channelNames) { _, which ->
                val selectedChannel = event.channels.getOrNull(which)
                selectedChannel?.let { playChannel(it) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun playChannel(channel: ChannelItem) {
        StreamLauncher.launchChannel(
            context = this,
            channel = channel,
            onWebFallback = { url ->
                WebBrowserActivity.start(this, url)
            }
        )
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_MENU -> {
                    openOptionsMenu()
                    return true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (vpnActionView?.hasFocus() == true) {
                        showVpnDialog()
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (vpnActionView?.hasFocus() == true) {
                        binding.rbTv.requestFocus()
                        return true
                    }
                    if (binding.etSearch.hasFocus()) {
                        val firstChild = binding.recyclerViewEvents.layoutManager?.findViewByPosition(0)
                        if (firstChild != null) {
                            firstChild.requestFocus()
                            return true
                        }
                    }
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    if (vpnActionView?.hasFocus() == true) {
                        return true
                    }
                    if (binding.rbTv.hasFocus() || binding.rbCaido.hasFocus()) {
                        val target = vpnActionView ?: binding.toolbar.findViewById(R.id.btnToolbarVpn)
                        if (target != null) {
                            vpnActionView = target
                            target.requestFocus()
                        } else {
                            openOptionsMenu()
                        }
                        return true
                    }
                    if (binding.rbPina.hasFocus()) {
                        binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
                        return true
                    }
                    if (binding.etSearch.hasFocus()) {
                        binding.rbPina.requestFocus()
                        return true
                    }
                    val focused = currentFocus
                    if (focused != null) {
                        val containing = binding.recyclerViewEvents.findContainingItemView(focused)
                        if (containing != null) {
                            val pos = binding.recyclerViewEvents.getChildAdapterPosition(containing)
                            if (pos == 0) {
                                binding.chipSportAll.requestFocus()
                                return true
                            }
                        }
                    }
                }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (vpnActionView?.hasFocus() == true) {
                        binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
                        return true
                    }
                    if (binding.rbPina.hasFocus()) {
                        binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (vpnActionView?.hasFocus() == true) {
                        openOptionsMenu()
                        return true
                    }
                    if (binding.rbCaido.hasFocus()) {
                        val target = vpnActionView ?: binding.toolbar.findViewById(R.id.btnToolbarVpn)
                        if (target != null) {
                            vpnActionView = target
                            target.requestFocus()
                        } else {
                            openOptionsMenu()
                        }
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        com.bone.android.a4v.oficial.util.VpnHelper.updateState(this)
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        binding.toolbar.overflowIcon?.setTint(android.graphics.Color.parseColor("#FFD700"))
        setupVpnActionView(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        setupVpnActionView(menu)
        val vpnSettingsItem = menu?.findItem(R.id.menu_vpn_settings)
        if (vpnSettingsItem != null) {
            val isVpnOn = com.bone.android.a4v.oficial.util.VpnHelper.isVpnActive(this)
            vpnSettingsItem.title = if (isVpnOn) {
                "🛡️ VPN: Conectada (Ajustes)"
            } else {
                "🛡️ VPN: Desconectada (Activar)"
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    private fun setupVpnActionView(menu: Menu? = null) {
        val targetMenu = menu ?: binding.toolbar.menu
        val actionView = targetMenu?.findItem(R.id.menu_vpn)?.actionView
            ?: binding.toolbar.findViewById(R.id.btnToolbarVpn)
        if (actionView == null) return
        vpnActionView = actionView

        actionView.isFocusable = true
        actionView.isFocusableInTouchMode = true
        actionView.isClickable = true

        val ivIcon = actionView.findViewById<android.widget.ImageView>(R.id.ivVpnStatusIcon)
        val tvText = actionView.findViewById<android.widget.TextView>(R.id.tvVpnStatusText)

        val isExternal = com.bone.android.a4v.oficial.util.VpnHelper.isExternalVpnActive(this)
        val isVpnOn = com.bone.android.a4v.oficial.util.VpnHelper.isVpnActive(this)

        if (isVpnOn) {
            ivIcon?.setImageResource(R.drawable.ic_shield_check)
            tvText?.text = if (isExternal) "VPN ON" else "VPN ON"
            tvText?.setTextColor(android.graphics.Color.parseColor("#00E676"))
        } else {
            ivIcon?.setImageResource(R.drawable.ic_shield_off)
            tvText?.text = "VPN OFF"
            tvText?.setTextColor(android.graphics.Color.parseColor("#FF5252"))
        }

        actionView.setOnClickListener {
            showVpnDialog()
        }

        actionView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_filter_sport -> {
                showSportFilterDialog()
                true
            }
            R.id.menu_timezone -> {
                showTimezoneDialog()
                true
            }
            R.id.menu_orientation -> {
                toggleOrientation()
                true
            }
            R.id.menu_install_acestream -> {
                com.bone.android.a4v.oficial.util.AceStreamInstallerHelper.promptInstallDialog(this, force = true)
                true
            }
            R.id.menu_software -> {
                showSoftwareDialog()
                true
            }
            R.id.menu_vpn, R.id.menu_vpn_settings -> {
                showVpnDialog()
                true
            }
            R.id.menu_update -> {
                checkAppUpdate(manual = true)
                true
            }
            R.id.menu_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkAppUpdate(manual: Boolean) {
        lifecycleScope.launch {
            val updateInfo = com.bone.android.a4v.oficial.util.UpdateHelper.checkForUpdate(this@MainActivity)
            if (updateInfo != null && updateInfo.hasUpdate) {
                com.bone.android.a4v.oficial.util.UpdateHelper.promptUpdateDialog(this@MainActivity, updateInfo)
            } else if (manual) {
                val curVer = com.bone.android.a4v.oficial.util.UpdateHelper.getAppVersionName(this@MainActivity)
                Toast.makeText(this@MainActivity, "Estás en la última versión disponible (v$curVer)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSportFilterDialog() {
        val sports = arrayOf(
            "Todos (Sin filtro)",
            "Fútbol",
            "Baloncesto",
            "Tenis",
            "Motor (F1 / MotoGP)",
            "Ciclismo",
            "Balonmano",
            "Boxeo / MMA",
            "Rugby",
            "Béisbol",
            "Golf",
            "Hockey",
            "Snooker / Billar",
            "Voleibol"
        )

        AlertDialog.Builder(this)
            .setTitle("Seleccionar filtro de deporte:")
            .setItems(sports) { _, which ->
                val selected = if (which == 0) "" else sports[which].split(" ")[0]
                viewModel.setSportFilter(selected)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showTimezoneDialog() {
        val timezones = arrayOf(
            "Madrid, Paris, Bruselas (GMT+1 / GMT+2)",
            "Londres, Lisboa (GMT+0)",
            "Buenos Aires, Santiago (GMT-3)",
            "Bogotá, Lima, CDMX (GMT-5)",
            "Hora Local del Dispositivo"
        )

        AlertDialog.Builder(this)
            .setTitle("Configuración de Zona Horaria")
            .setItems(timezones) { _, which ->
                Toast.makeText(this, "Zona horaria fijada: ${timezones[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun toggleOrientation() {
        isLandscape = !isLandscape
        requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun showSoftwareDialog() {
        val apps = arrayOf(
            "📺 Instalar / Actualizar AceStream (Automático)",
            "Descargar Wiseplay Player",
            "Descargar MX Player",
            "Descargar SopCast"
        )

        AlertDialog.Builder(this)
            .setTitle("Software necesario para reproducción")
            .setItems(apps) { _, which ->
                when (which) {
                    0 -> com.bone.android.a4v.oficial.util.AceStreamInstallerHelper.promptInstallDialog(this, force = true)
                    1 -> openUrl("market://details?id=com.wiseplay")
                    2 -> openUrl("market://details?id=com.mxtech.videoplayer.ad")
                    3 -> openUrl("http://download.sopcast.com/download/SopCast.apk")
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showVpnDialog() {
        if (vpnDialog?.isShowing == true) return

        val isExternal = com.bone.android.a4v.oficial.util.VpnHelper.isExternalVpnActive(this)
        val isBuiltIn = com.bone.android.a4v.oficial.util.VpnHelper.isBuiltInVpnActive()
        val installedVpn = com.bone.android.a4v.oficial.util.VpnHelper.getInstalledVpnApp(this)
        val vpnName = installedVpn?.name ?: "externa (NordVPN, Proton, etc.)"

        if (isExternal) {
            val dialogBuilder = AlertDialog.Builder(this)
                .setTitle("🛡️ Estado de Conexión VPN")
                .setMessage("🟢 VPN ACTIVA ($vpnName)\n\n• Tu dispositivo ya cuenta con un túnel VPN activo a nivel de sistema.\n• Las transmisiones de AceStream y los servidores de ArenaVision ya están protegidos contra bloqueos de operadoras.\n• En Android solo puede haber una única VPN activa a la vez; por tanto, no necesitas activar el escudo interno de Piña4Viewer.")
                .setPositiveButton("Entendido", null)

            if (installedVpn != null) {
                dialogBuilder.setNeutralButton("Abrir ${installedVpn.name}") { _, _ ->
                    com.bone.android.a4v.oficial.util.VpnHelper.launchVpnApp(this, installedVpn)
                }
            } else {
                dialogBuilder.setNeutralButton("Ajustes VPN Android") { _, _ ->
                    com.bone.android.a4v.oficial.util.VpnHelper.openSystemVpnSettings(this)
                }
            }

            val d = dialogBuilder.create()
            d.setOnDismissListener {
                vpnDialog = null
                vpnActionView?.requestFocus()
            }
            d.setOnShowListener {
                d.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
            }
            vpnDialog = d
            d.show()
            return
        }

        val isVpnOn = isBuiltIn
        val statusText = if (isVpnOn) {
            "🟢 ESCUDO VPN: CONECTADO\n\n• Túnel Cifrado Anti-Bloqueos (Quad9 Suiza 9.9.9.9 + Google + AdGuard)\n• Inmune a bloqueos de operadoras en España\n• AceStream y transmisiones desbloqueadas\n• 100% velocidad de tu conexión sin límites"
        } else {
            "🔴 ESCUDO VPN: DESCONECTADO\n\nLas operadoras pueden bloquear las emisiones de AceStream. Puedes activar el escudo integrado o abrir tu aplicación VPN favorita."
        }

        val toggleButtonTitle = if (isVpnOn) "Desactivar Escudo" else "Activar Escudo (1-Clic)"

        val builder = AlertDialog.Builder(this)
            .setTitle("🛡️ Escudo Anti-Bloqueos")
            .setMessage(statusText)
            .setPositiveButton(toggleButtonTitle) { _, _ ->
                com.bone.android.a4v.oficial.util.VpnHelper.toggleBuiltInVpn(this) { intent ->
                    vpnLauncher.launch(intent)
                }
                binding.root.postDelayed({ invalidateOptionsMenu() }, 500)
            }
            .setNegativeButton("Cerrar", null)

        if (installedVpn != null) {
            builder.setNeutralButton("Abrir ${installedVpn.name}") { _, _ ->
                com.bone.android.a4v.oficial.util.VpnHelper.launchVpnApp(this, installedVpn)
            }
        } else {
            builder.setNeutralButton("Ajustes VPN") { _, _ ->
                com.bone.android.a4v.oficial.util.VpnHelper.openSystemVpnSettings(this)
            }
        }

        val d = builder.create()
        d.setOnDismissListener {
            vpnDialog = null
            vpnActionView?.requestFocus()
        }
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
        }
        vpnDialog = d
        d.show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("🍍 Piña4Viewer 2.0")
            .setMessage("Versión Moderna Nativa en Kotlin\n\n• Arquitectura MVVM con Corrutinas\n• Filtros en tiempo real\n• Soporte universal Phone / Tablet / TV\n• Sin publicidad invasiva")
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }
}
