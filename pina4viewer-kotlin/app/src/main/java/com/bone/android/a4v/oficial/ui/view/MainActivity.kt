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

    private val directChannels = (1..30).map { i ->
        val quality = if (i <= 6 || i in 11..16) "1080p" else "720p"
        "AV$i ($quality)"
    }

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
        setupSwipeRefresh()
        setupDrawerChannels()
        observeState()
        initVpnShield()
        checkAppUpdate(manual = false)
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
            binding.rbIn to SourceType.SERVER_IN,
            binding.rbOffMode to SourceType.OFF_MODE,
            binding.rbCool to SourceType.SERVER_COOL,
            binding.rbTop to SourceType.SERVER_TOP,
            binding.rbSearch to SourceType.SEARCH,
            binding.rbPl to SourceType.SERVER_PL,
            binding.rbCoIn to SourceType.SERVER_CO_IN,
            binding.rbInfo to SourceType.SERVER_INFO,
            binding.rbLv to SourceType.SERVER_LV,
            binding.rbCaido to SourceType.CAIDO
        )

        val allRadios = radioMap.keys.toList()
        allRadios.forEach { rb -> rb.isChecked = (rb == binding.rbIn) }

        radioMap.forEach { (radioButton, sourceType) ->
            radioButton.setOnClickListener {
                allRadios.forEach { rb -> rb.isChecked = (rb == radioButton) }
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
                        val firstChild = binding.recyclerViewEvents.layoutManager?.findViewByPosition(0)
                        if (firstChild != null) {
                            firstChild.requestFocus()
                        } else {
                            binding.recyclerViewEvents.requestFocus()
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        binding.rbPl.requestFocus()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.gold_primary, R.color.gold_dark)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun setupDrawerChannels() {
        val drawerAdapter = ArrayAdapter(
            this,
            R.layout.item_drawer_channel,
            R.id.tvDrawerItem,
            directChannels
        )
        binding.drawerChannelList.adapter = drawerAdapter

        binding.drawerChannelList.setOnItemClickListener { _, _, position, _ ->
            val channelNumber = position + 1
            val channelName = "AV$channelNumber"
            val streamHash = ArenaVisionParser.cachedStreams[channelNumber.toString()] ?: channelName
            binding.drawerLayout.closeDrawers()
            playChannel(ChannelItem(name = channelName, streamId = streamHash))
        }

        binding.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: android.view.View) {
                binding.drawerChannelList.requestFocus()
            }
            override fun onDrawerClosed(drawerView: android.view.View) {
                binding.rbIn.requestFocus()
            }
        })

        binding.drawerChannelList.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                binding.drawerLayout.closeDrawers()
                binding.rbIn.requestFocus()
                true
            } else {
                false
            }
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
                    binding.swipeRefresh.isRefreshing = state.isLoading
                    eventAdapter.submitList(state.filteredEvents)

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

        val channelOptions = event.channels.map { "${it.name}(ACESTREAM)" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Canales(players)")
            .setItems(channelOptions) { _, which ->
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
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (binding.etSearch.hasFocus()) {
                        val firstChild = binding.recyclerViewEvents.layoutManager?.findViewByPosition(0)
                        if (firstChild != null) {
                            firstChild.requestFocus()
                            return true
                        }
                    }
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    if (binding.rbIn.hasFocus() || binding.rbOffMode.hasFocus() || binding.rbCool.hasFocus() || binding.rbTop.hasFocus() || binding.rbSearch.hasFocus()) {
                        if (binding.rbIn.hasFocus() || binding.rbOffMode.hasFocus()) {
                            binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
                        } else {
                            openOptionsMenu()
                        }
                        return true
                    }
                    if (binding.rbPl.hasFocus()) {
                        binding.rbIn.requestFocus()
                        return true
                    }
                    if (binding.rbCoIn.hasFocus()) {
                        binding.rbOffMode.requestFocus()
                        return true
                    }
                    if (binding.rbInfo.hasFocus()) {
                        binding.rbCool.requestFocus()
                        return true
                    }
                    if (binding.rbLv.hasFocus()) {
                        binding.rbTop.requestFocus()
                        return true
                    }
                    if (binding.rbCaido.hasFocus()) {
                        binding.rbSearch.requestFocus()
                        return true
                    }
                    if (binding.etSearch.hasFocus()) {
                        binding.rbPl.requestFocus()
                        return true
                    }
                    val focused = currentFocus
                    if (focused != null) {
                        val containing = binding.recyclerViewEvents.findContainingItemView(focused)
                        if (containing != null) {
                            val pos = binding.recyclerViewEvents.getChildAdapterPosition(containing)
                            if (pos == 0) {
                                binding.etSearch.requestFocus()
                                return true
                            }
                        }
                    }
                }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (binding.rbIn.hasFocus() || binding.rbPl.hasFocus()) {
                        binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (binding.rbSearch.hasFocus() || binding.rbCaido.hasFocus()) {
                        openOptionsMenu()
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
        return super.onPrepareOptionsMenu(menu)
    }

    private fun setupVpnActionView(menu: Menu?) {
        val vpnMenuItem = menu?.findItem(R.id.menu_vpn) ?: return
        val actionView = vpnMenuItem.actionView ?: return
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
            R.id.menu_software -> {
                showSoftwareDialog()
                true
            }
            R.id.menu_vpn -> {
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
            "Descargar AceStream Engine (Recomendado)",
            "Descargar Wiseplay Player",
            "Descargar MX Player",
            "Descargar SopCast"
        )

        AlertDialog.Builder(this)
            .setTitle("Software necesario para reproducción")
            .setItems(apps) { _, which ->
                when (which) {
                    0 -> openUrl("http://wiki.acestream.org/wiki/index.php/Download")
                    1 -> openUrl("market://details?id=com.wiseplay")
                    2 -> openUrl("market://details?id=com.mxtech.videoplayer.ad")
                    3 -> openUrl("http://download.sopcast.com/download/SopCast.apk")
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showVpnDialog() {
        val isExternal = com.bone.android.a4v.oficial.util.VpnHelper.isExternalVpnActive(this)
        val isBuiltIn = com.bone.android.a4v.oficial.util.VpnHelper.isBuiltInVpnActive()

        if (isExternal) {
            AlertDialog.Builder(this)
                .setTitle("🛡️ Estado de Conexión VPN")
                .setMessage("🟢 VPN EXTERNA CONECTADA (ej. Proton VPN)\n\n• Tu dispositivo ya cuenta con un túnel VPN activo a nivel de sistema.\n• Las transmisiones de AceStream y los servidores de ArenaVision ya están protegidos contra bloqueos de operadoras.\n• En Android solo puede haber una única VPN activa a la vez; por tanto, no necesitas activar el escudo interno de Piña4Viewer.")
                .setPositiveButton("Entendido", null)
                .setNeutralButton("Ajustes VPN Android") { _, _ ->
                    com.bone.android.a4v.oficial.util.VpnHelper.openSystemVpnSettings(this)
                }
                .show()
            return
        }

        val isVpnOn = isBuiltIn
        val statusText = if (isVpnOn) {
            "🟢 ESCUDO VPN: CONECTADO\n\n• Túnel Cifrado Anti-Bloqueos (Quad9 Suiza 9.9.9.9 + Google + AdGuard)\n• Inmune a bloqueos de operadoras en España\n• AceStream y transmisiones desbloqueadas\n• 100% velocidad de tu conexión sin límites"
        } else {
            "🔴 ESCUDO VPN: DESCONECTADO\n\nLas operadoras pueden bloquear las emisiones de AceStream. Pulsa el botón de abajo para activar el escudo integrado."
        }

        val toggleButtonTitle = if (isVpnOn) "Desactivar Escudo" else "Activar Escudo (1-Clic)"

        AlertDialog.Builder(this)
            .setTitle("🛡️ Escudo Anti-Bloqueos Integrado")
            .setMessage(statusText)
            .setPositiveButton(toggleButtonTitle) { _, _ ->
                com.bone.android.a4v.oficial.util.VpnHelper.toggleBuiltInVpn(this) { intent ->
                    vpnLauncher.launch(intent)
                }
                binding.root.postDelayed({ invalidateOptionsMenu() }, 500)
            }
            .setNegativeButton("Cerrar", null)
            .show()
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
