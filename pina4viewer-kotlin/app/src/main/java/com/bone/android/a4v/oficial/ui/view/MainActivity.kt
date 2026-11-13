package com.bone.android.a4v.oficial.ui.view

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.widget.addTextChangedListener
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bone.android.a4v.oficial.R
import com.bone.android.a4v.oficial.data.model.ChannelItem
import com.bone.android.a4v.oficial.data.model.EventItem
import com.bone.android.a4v.oficial.data.model.SourceType
import com.bone.android.a4v.oficial.data.parser.ArenaVisionParser
import com.bone.android.a4v.oficial.databinding.ActivityMainBinding
import com.bone.android.a4v.oficial.ui.adapter.EventListAdapter
import com.bone.android.a4v.oficial.ui.viewmodel.MainViewModel
import com.bone.android.a4v.oficial.util.AceStreamInstallerHelper
import com.bone.android.a4v.oficial.util.SportFilterMatcher
import com.bone.android.a4v.oficial.util.StreamLauncher
import com.bone.android.a4v.oficial.util.TimeZoneHelper
import com.bone.android.a4v.oficial.util.UpdateHelper
import com.bone.android.a4v.oficial.util.VpnHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var eventAdapter: EventListAdapter
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private var isLandscape = false
    private var vpnActionView: View? = null
    private var overflowButtonView: View? = null
    private var vpnDialog: AlertDialog? = null
    private var pendingToolbarFocus: ToolbarFocusTarget? = null

    private var lastSource: SourceType? = SourceType.OFF_MODE
    private var lastSportFilter: String = ""
    private var lastSearchQuery: String = ""
    private var currentChipsAre247 = false
    private var lastRenderedVpnState: Boolean? = null

    private enum class ToolbarFocusTarget { VPN, OVERFLOW }

    data class DrawerChannel(
        val displayName: String,
        val channelNumber: String,
        val streamHash: String
    )

    private val drawerChannels = mutableListOf<DrawerChannel>()
    private lateinit var drawerAdapter: ArrayAdapter<String>
    private var lastDrawerSignature: Int = 0

    private val drawerBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawers()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            VpnHelper.startBuiltInVpn(this)
            invalidateOptionsMenuPreservingFocus()
            Toast.makeText(this, "🛡️ Escudo VPN Activado • Partidos Desbloqueados", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isLandscape = (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        onBackPressedDispatcher.addCallback(this, drawerBackCallback)

        applyTvOverscanFallbackIfNeeded()
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

    private fun applyTvOverscanFallbackIfNeeded() {
        if (!AceStreamInstallerHelper.isTvDevice(this)) return
        val xmlPadH = resources.getDimensionPixelSize(R.dimen.screen_padding_horizontal)
        if (xmlPadH == 0) {
            val padH = dpToPx(24f)
            val padV = dpToPx(12f)
            binding.mainContentContainer.setPadding(padH, padV, padH, padV)
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }

    private fun initVpnShield() {
        try {
            if (VpnHelper.isExternalVpnActive(this)) {
                invalidateOptionsMenuPreservingFocus()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
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
        drawerToggle.drawerArrowDrawable.color = Color.parseColor("#FFD700")
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.toolbar.setOnMenuItemClickListener { item ->
            onOptionsItemSelected(item)
        }

        binding.toolbar.overflowIcon?.setTint(Color.parseColor("#FFD700"))
        binding.toolbar.post {
            configureActionMenuClipping()
            setupOverflowButtonFocus()
        }
    }

    private fun configureActionMenuClipping() {
        binding.toolbar.clipChildren = false
        binding.toolbar.clipToPadding = false
        for (i in 0 until binding.toolbar.childCount) {
            val child = binding.toolbar.getChildAt(i)
            if (child is ActionMenuView) {
                child.clipChildren = false
                child.clipToPadding = false
            }
        }
    }

    private fun setupRecyclerView() {
        eventAdapter = EventListAdapter(
            onEventClick = { event -> showChannelChooserDialog(event) },
            onChannelClick = { channel -> playChannel(channel) }
        )

        binding.recyclerViewEvents.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = eventAdapter
            setHasFixedSize(false)
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
                (binding.recyclerViewEvents.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                viewModel.selectSource(sourceType)
            }
        }
    }

    private fun activeSourceRadioView(): RadioButton {
        return when {
            binding.rbTv.isChecked -> binding.rbTv
            binding.rbCaido.isChecked -> binding.rbCaido
            else -> binding.rbPina
        }
    }

    private fun activeSportChipView(): RadioButton {
        val candidates = listOf(
            binding.chipSportFutbol,
            binding.chipSportTenis,
            binding.chipSportMotor,
            binding.chipSportBasket,
            binding.chipSportOtros
        )
        return candidates.firstOrNull { it.visibility == View.VISIBLE && it.isChecked }
            ?: binding.chipSportAll
    }

    private fun focusFirstEventOrFooter(): Boolean {
        val lm = binding.recyclerViewEvents.layoutManager as? LinearLayoutManager
        val targetView = lm?.findViewByPosition(0)
            ?: lm?.findFirstVisibleItemPosition()
                ?.takeIf { it != androidx.recyclerview.widget.RecyclerView.NO_POSITION }
                ?.let { lm.findViewByPosition(it) }
            ?: binding.recyclerViewEvents.getChildAt(0)
        return if (targetView != null) {
            targetView.requestFocus()
        } else {
            binding.tvFooterWarning.requestFocus()
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }

        binding.etSearch.setOnEditorActionListener { v, actionId, event ->
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isSearchAction) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                activeSportChipView().requestFocus()
                true
            } else {
                false
            }
        }

        binding.etSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        activeSportChipView().requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        activeSourceRadioView().requestFocus()
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
        val allChips = listOf(
            binding.chipSportAll,
            binding.chipSportFutbol,
            binding.chipSportTenis,
            binding.chipSportMotor,
            binding.chipSportBasket,
            binding.chipSportOtros
        )

        updateChipsForSource(SourceType.OFF_MODE)

        allChips.forEach { chip ->
            chip.setOnClickListener {
                val filterKey = (chip.tag as? String).orEmpty()
                (binding.recyclerViewEvents.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                viewModel.setSportFilter(filterKey)
            }

            chip.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusFirstEventOrFooter()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            binding.etSearch.requestFocus()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
        }
    }

    private fun updateChipsForSource(source: SourceType) {
        val is247 = (source == SourceType.SEARCH)
        currentChipsAre247 = is247

        if (is247) {
            binding.chipSportAll.apply { text = "🏆 Todos"; tag = SportFilterMatcher.FILTER_ALL }
            binding.chipSportFutbol.apply { text = "📺 Deportes TV"; tag = SportFilterMatcher.FILTER_TV_DEPORTES }
            binding.chipSportTenis.apply { text = "🏟️ ArenaVision"; tag = SportFilterMatcher.FILTER_TV_ARENAVISION }
            binding.chipSportMotor.apply { text = "📡 TDT Oficial"; tag = SportFilterMatcher.FILTER_TV_TDT }
            binding.chipSportBasket.visibility = View.GONE
            binding.chipSportOtros.visibility = View.GONE
        } else {
            binding.chipSportAll.apply { text = "🏆 Todos"; tag = SportFilterMatcher.FILTER_ALL }
            binding.chipSportFutbol.apply { text = "⚽ Fútbol"; tag = SportFilterMatcher.FILTER_FUTBOL }
            binding.chipSportTenis.apply { text = "🎾 Tenis / Pádel"; tag = SportFilterMatcher.FILTER_TENIS_PADEL }
            binding.chipSportMotor.apply { text = "🏎️ Motor"; tag = SportFilterMatcher.FILTER_MOTOR }
            binding.chipSportBasket.apply {
                text = "🏀 Basket"
                tag = SportFilterMatcher.FILTER_BASKET
                visibility = View.VISIBLE
            }
            binding.chipSportOtros.apply {
                text = "🥊 Otros"
                tag = SportFilterMatcher.FILTER_OTROS
                visibility = View.VISIBLE
            }
        }
    }

    private fun syncCheckedChipWithFilter(sportFilter: String, source: SourceType) {
        val norm = SportFilterMatcher.normalize(sportFilter)
        val targetChip: RadioButton = if (source == SourceType.SEARCH) {
            when (norm) {
                "" -> binding.chipSportAll
                SportFilterMatcher.FILTER_TV_DEPORTES -> binding.chipSportFutbol
                SportFilterMatcher.FILTER_TV_ARENAVISION -> binding.chipSportTenis
                SportFilterMatcher.FILTER_TV_TDT -> binding.chipSportMotor
                else -> binding.chipSportAll
            }
        } else {
            when (norm) {
                "" -> binding.chipSportAll
                SportFilterMatcher.FILTER_FUTBOL -> binding.chipSportFutbol
                SportFilterMatcher.FILTER_TENIS_PADEL, "TENIS", "PADEL" -> binding.chipSportTenis
                SportFilterMatcher.FILTER_MOTOR -> binding.chipSportMotor
                SportFilterMatcher.FILTER_BASKET, "BALONCESTO" -> binding.chipSportBasket
                else -> binding.chipSportOtros
            }
        }
        if (!targetChip.isChecked) {
            targetChip.isChecked = true
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.gold_primary, R.color.gold_dark)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        binding.tvFooterWarning.setOnClickListener {
            TvDialogHelper.show(
                this,
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Cannot get transport file")
                    .setMessage(
                        "¿Por qué sale este mensaje en AceStream?\n\n" +
                            "1. 📡 El canal aún no está emitiendo: los emisores encienden la señal unos 15 minutos antes del inicio del evento.\n\n" +
                            "2. 🔄 Prueba otra opción: en el selector de canales del evento, elige Opción 2, Opción 3 u otra fuente alternativa.\n\n" +
                            "3. 🧹 Limpia la caché: en Ajustes de Android > Aplicaciones > Ace Stream > Almacenamiento > Borrar caché.\n\n" +
                            "4. 🛡️ Fuga de DNS: activa Psiphon VPN o en Ajustes de Red de Android pon DNS privado en 'dns.google'."
                    )
                    .setPositiveButton("Entendido", null),
                preferPositiveFocus = true,
                onShowExtra = { d ->
                    d.setOnDismissListener { binding.tvFooterWarning.requestFocus() }
                }
            )
        }

        binding.tvFooterWarning.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                val lm = binding.recyclerViewEvents.layoutManager as? LinearLayoutManager
                val lastVisiblePos = lm?.findLastVisibleItemPosition() ?: RecyclerView.NO_POSITION
                val targetView = if (lastVisiblePos >= 0) lm?.findViewByPosition(lastVisiblePos) else null
                if (targetView != null) {
                    targetView.requestFocus()
                } else {
                    activeSportChipView().requestFocus()
                }
                true
            } else {
                false
            }
        }
    }

    private fun buildDrawerChannelList(): List<DrawerChannel> {
        val streamMap = ArenaVisionParser.cachedStreams
        val sortedKeys = streamMap.keys.mapNotNull { it.toIntOrNull() }.distinct().sorted()
        val numbers = if (sortedKeys.isNotEmpty()) {
            sortedKeys
        } else {
            ((1..103) + (105..141) + (150..155) + 160).distinct().sorted()
        }

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
        lastDrawerSignature = drawerChannels.hashCode()

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

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                drawerBackCallback.isEnabled = true
                updateDrawerChannelsIfChanged()
                binding.drawerChannelList.post {
                    binding.drawerChannelList.requestFocus()
                    if (binding.drawerChannelList.selectedItemPosition < 0 && drawerChannels.isNotEmpty()) {
                        binding.drawerChannelList.setSelection(0)
                    }
                }
            }

            override fun onDrawerClosed(drawerView: View) {
                drawerBackCallback.isEnabled = false
                activeSourceRadioView().requestFocus()
            }
        })

        binding.drawerChannelList.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                binding.drawerLayout.closeDrawers()
                activeSourceRadioView().requestFocus()
                true
            } else {
                false
            }
        }
    }

    private fun updateDrawerChannelsIfChanged() {
        if (!::drawerAdapter.isInitialized) return
        val newItems = buildDrawerChannelList()
        val newSig = newItems.hashCode()
        if (newSig != lastDrawerSignature) {
            lastDrawerSignature = newSig
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
                    VpnHelper.vpnStateFlow.collect {
                        invalidateOptionsMenuPreservingFocus()
                    }
                }

                viewModel.uiState.collect { state ->
                    updateDrawerChannelsIfChanged()
                    binding.swipeRefresh.isRefreshing = state.isLoading

                    if (currentChipsAre247 != (state.currentSource == SourceType.SEARCH)) {
                        updateChipsForSource(state.currentSource)
                    }

                    val shouldScrollToTop = (state.currentSource != lastSource) ||
                        (state.sportFilter != lastSportFilter) ||
                        (state.searchQuery != lastSearchQuery)

                    lastSource = state.currentSource
                    lastSportFilter = state.sportFilter
                    lastSearchQuery = state.searchQuery

                    val focusedInsideList = currentFocus?.let {
                        binding.recyclerViewEvents.findContainingItemView(it)
                    }
                    val focusedEventId = focusedInsideList?.tag as? String
                    val focusedAdapterPos = focusedInsideList?.let {
                        binding.recyclerViewEvents.getChildAdapterPosition(it)
                    } ?: RecyclerView.NO_POSITION

                    eventAdapter.submitList(state.filteredEvents) {
                        val lm = binding.recyclerViewEvents.layoutManager as? LinearLayoutManager ?: return@submitList
                        if (shouldScrollToTop) {
                            lm.scrollToPositionWithOffset(0, 0)
                        } else if (focusedEventId != null && state.filteredEvents.isNotEmpty()) {
                            val matchingView = binding.recyclerViewEvents.findViewWithTag<View>(focusedEventId)
                            if (matchingView != null) {
                                matchingView.requestFocus()
                            } else if (focusedAdapterPos != RecyclerView.NO_POSITION) {
                                val safePos = focusedAdapterPos.coerceIn(0, state.filteredEvents.lastIndex)
                                lm.findViewByPosition(safePos)?.requestFocus()
                            }
                        }
                    }

                    syncCheckedChipWithFilter(state.sportFilter, state.currentSource)

                    val count = state.filteredEvents.size
                    val unitLabel = if (state.currentSource == SourceType.SEARCH) {
                        if (count == 1) "canal disponible" else "canales disponibles"
                    } else {
                        if (count == 1) "evento disponible" else "eventos disponibles"
                    }
                    val activeFilterSuffix = if (state.sportFilter.isNotBlank() && state.currentSource != SourceType.SEARCH) {
                        " • Filtro: ${SportFilterMatcher.normalize(state.sportFilter)}"
                    } else {
                        ""
                    }

                    if (state.isOffMode) {
                        binding.tvUserCount.text = "[OFF-MODE] • $count $unitLabel$activeFilterSuffix"
                        binding.tvUserCount.setTextColor(Color.parseColor("#FFD700"))
                    } else {
                        binding.tvUserCount.text = "📡 $count $unitLabel$activeFilterSuffix"
                        binding.tvUserCount.setTextColor(Color.parseColor("#E0E0E0"))
                    }

                    if (state.lastUpdated.isNotEmpty()) {
                        val tzBadge = if (state.selectedTimezoneId != TimeZoneHelper.DEFAULT_TIMEZONE_ID) {
                            val shortName = if (state.selectedTimezoneId == TimeZoneHelper.SYSTEM_TIMEZONE_ID) {
                                "Local"
                            } else {
                                state.selectedTimezoneId.substringAfterLast('/').replace('_', ' ')
                            }
                            "\n($shortName)"
                        } else {
                            ""
                        }
                        binding.tvFooterZone.text = "${state.lastUpdated}$tzBadge"
                    }

                    binding.tvEmpty.visibility = if (!state.isLoading && state.filteredEvents.isEmpty()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                    state.errorMessage?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        viewModel.consumeErrorMessage()
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
        val adapter = ArrayAdapter(
            this,
            R.layout.item_dialog_channel,
            R.id.tvChannelChoice,
            channelNames
        )

        val builder = AlertDialog.Builder(this)
            .setTitle("Canales disponibles")
            .setAdapter(adapter) { dialog, which ->
                dialog.dismiss()
                event.channels.getOrNull(which)?.let { playChannel(it) }
            }
            .setNegativeButton("Cancelar", null)

        TvDialogHelper.show(this, builder, preferPositiveFocus = false)
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

    private fun openAppMenu() {
        val opened = binding.toolbar.showOverflowMenu()
        if (!opened) {
            openOptionsMenu()
        }
    }

    private fun findOverflowButton(toolbar: Toolbar): View? {
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is ActionMenuView) {
                for (j in 0 until child.childCount) {
                    val menuChild = child.getChildAt(j)
                    if (menuChild.javaClass.simpleName.contains("OverflowMenuButton", ignoreCase = true)) {
                        return menuChild
                    }
                }
                for (j in 0 until child.childCount) {
                    val menuChild = child.getChildAt(j)
                    val desc = menuChild.contentDescription?.toString() ?: ""
                    if (desc.contains("opcion", ignoreCase = true) || desc.contains("option", ignoreCase = true)) {
                        return menuChild
                    }
                }
                for (j in 0 until child.childCount) {
                    val menuChild = child.getChildAt(j)
                    if (menuChild != vpnActionView && menuChild is ImageView) {
                        return menuChild
                    }
                }
            }
        }
        return null
    }

    private fun setupOverflowButtonFocus() {
        binding.toolbar.post {
            configureActionMenuClipping()
            val overflowBtn = findOverflowButton(binding.toolbar) ?: return@post
            overflowButtonView = overflowBtn
            overflowBtn.isFocusable = true
            overflowBtn.background = ContextCompat.getDrawable(this, R.drawable.selector_dialog_item_focus)
            overflowBtn.setOnFocusChangeListener { v, hasFocus ->
                v.animate().cancel()
                val scale = if (hasFocus) 1.15f else 1.0f
                v.animate().scaleX(scale).scaleY(scale).setDuration(130).start()
            }
            overflowBtn.setOnClickListener {
                openAppMenu()
            }
            if (pendingToolbarFocus == ToolbarFocusTarget.OVERFLOW) {
                pendingToolbarFocus = null
                overflowBtn.requestFocus()
            }
        }
    }

    private fun invalidateOptionsMenuPreservingFocus() {
        pendingToolbarFocus = when {
            vpnActionView?.hasFocus() == true -> ToolbarFocusTarget.VPN
            overflowButtonView?.hasFocus() == true -> ToolbarFocusTarget.OVERFLOW
            else -> null
        }
        invalidateOptionsMenu()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        binding.drawerLayout.closeDrawers()
                    } else {
                        openAppMenu()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (vpnActionView?.hasFocus() == true) {
                        handleVpnButtonClick()
                        return true
                    }
                    if (overflowButtonView?.hasFocus() == true) {
                        openAppMenu()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (vpnActionView?.hasFocus() == true || overflowButtonView?.hasFocus() == true) {
                        activeSourceRadioView().requestFocus()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (vpnActionView?.hasFocus() == true || overflowButtonView?.hasFocus() == true) {
                        return true
                    }
                    if (binding.rbTv.hasFocus() || binding.rbCaido.hasFocus()) {
                        val target = vpnActionView ?: binding.toolbar.findViewById(R.id.btnToolbarVpn)
                        if (target != null) {
                            vpnActionView = target
                            target.requestFocus()
                        } else {
                            openAppMenu()
                        }
                        return true
                    }
                    if (binding.rbPina.hasFocus()) {
                        binding.drawerLayout.openDrawer(GravityCompat.START)
                        return true
                    }
                    if (binding.etSearch.hasFocus()) {
                        activeSourceRadioView().requestFocus()
                        return true
                    }
                    val focused = currentFocus
                    if (focused != null) {
                        val containing = binding.recyclerViewEvents.findContainingItemView(focused)
                        if (containing != null) {
                            val pos = binding.recyclerViewEvents.getChildAdapterPosition(containing)
                            if (pos == 0) {
                                activeSportChipView().requestFocus()
                                return true
                            }
                        }
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (overflowButtonView?.hasFocus() == true) {
                        val target = vpnActionView ?: binding.toolbar.findViewById(R.id.btnToolbarVpn)
                        if (target != null) {
                            vpnActionView = target
                            target.requestFocus()
                        }
                        return true
                    }
                    if (vpnActionView?.hasFocus() == true || binding.rbPina.hasFocus()) {
                        binding.drawerLayout.openDrawer(GravityCompat.START)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (vpnActionView?.hasFocus() == true) {
                        val overflow = overflowButtonView ?: findOverflowButton(binding.toolbar)
                        if (overflow != null) {
                            overflowButtonView = overflow
                            overflow.requestFocus()
                        } else {
                            openAppMenu()
                        }
                        return true
                    }
                    if (overflowButtonView?.hasFocus() == true) {
                        return true
                    }
                    if (binding.rbCaido.hasFocus()) {
                        val target = vpnActionView ?: binding.toolbar.findViewById(R.id.btnToolbarVpn)
                        if (target != null) {
                            vpnActionView = target
                            target.requestFocus()
                        } else {
                            openAppMenu()
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
        try {
            VpnHelper.updateState(this)
            val isVpnNow = VpnHelper.isVpnActive(this)
            if (lastRenderedVpnState != isVpnNow) {
                invalidateOptionsMenuPreservingFocus()
            }
            viewModel.reapplyFilters()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        binding.toolbar.overflowIcon?.setTint(Color.parseColor("#FFD700"))
        setupVpnActionView(menu)
        setupOverflowButtonFocus()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        setupVpnActionView(menu)
        val isVpnOn = VpnHelper.isVpnActive(this)
        lastRenderedVpnState = isVpnOn
        val vpnSettingsItem = menu?.findItem(R.id.menu_vpn_settings)
        if (vpnSettingsItem != null) {
            val installedVpn = VpnHelper.getInstalledVpnApp(this)
            vpnSettingsItem.title = when {
                installedVpn != null && isVpnOn -> "🛡️ Abrir ${installedVpn.name} (Conectada)"
                installedVpn != null -> "🛡️ Abrir ${installedVpn.name}"
                isVpnOn -> "🛡️ VPN: Conectada (Ajustes)"
                else -> "🛡️ VPN: Desconectada (Instalar)"
            }
        }
        menu?.findItem(R.id.menu_filter_sport)?.isVisible =
            viewModel.uiState.value.currentSource != SourceType.SEARCH
        menu?.findItem(R.id.menu_orientation)?.isVisible = !AceStreamInstallerHelper.isTvDevice(this)
        setupOverflowButtonFocus()
        return super.onPrepareOptionsMenu(menu)
    }

    private fun setupVpnActionView(menu: Menu? = null) {
        try {
            val targetMenu = menu ?: try { binding.toolbar.menu } catch (_: Throwable) { null }
            val actionView = targetMenu?.findItem(R.id.menu_vpn)?.actionView
                ?: binding.toolbar.findViewById(R.id.btnToolbarVpn)
                ?: return
            vpnActionView = actionView

            actionView.isFocusable = true
            actionView.isClickable = true

            val ivIcon = actionView.findViewById<ImageView>(R.id.ivVpnStatusIcon)
            val tvText = actionView.findViewById<TextView>(R.id.tvVpnStatusText)

            val isVpnOn = VpnHelper.isVpnActive(this)

            if (isVpnOn) {
                ivIcon?.setImageResource(R.drawable.ic_shield_check)
                tvText?.text = "VPN ON"
                tvText?.setTextColor(Color.parseColor("#00E676"))
            } else {
                ivIcon?.setImageResource(R.drawable.ic_shield_off)
                tvText?.text = "VPN OFF"
                tvText?.setTextColor(Color.parseColor("#FF5252"))
            }

            actionView.setOnClickListener {
                handleVpnButtonClick()
            }

            actionView.setOnFocusChangeListener { v, hasFocus ->
                v.animate().cancel()
                val scale = if (hasFocus) 1.1f else 1.0f
                v.animate().scaleX(scale).scaleY(scale).setDuration(130).start()
            }

            if (pendingToolbarFocus == ToolbarFocusTarget.VPN) {
                pendingToolbarFocus = null
                actionView.post { actionView.requestFocus() }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_refresh -> {
                Toast.makeText(this, "🔄 Actualizando agenda...", Toast.LENGTH_SHORT).show()
                viewModel.refresh()
                true
            }
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
                AceStreamInstallerHelper.promptInstallDialog(this, force = true)
                true
            }
            R.id.menu_software -> {
                com.bone.android.a4v.oficial.util.WiseplayInstallerHelper.promptInstallDialog(this, force = true)
                true
            }
            R.id.menu_vpn, R.id.menu_vpn_settings -> {
                handleVpnButtonClick()
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
            val updateInfo = UpdateHelper.checkForUpdate(this@MainActivity)
            if (updateInfo != null && updateInfo.hasUpdate) {
                UpdateHelper.promptUpdateDialog(this@MainActivity, updateInfo)
            } else if (manual) {
                val curVer = UpdateHelper.getAppVersionName(this@MainActivity)
                Toast.makeText(this@MainActivity, "Estás en la última versión disponible (v$curVer)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSportFilterDialog() {
        val options = if (viewModel.uiState.value.currentSource == SourceType.SEARCH) {
            listOf(
                SportFilterMatcher.SportMenuOption("Todos (Sin filtro)", SportFilterMatcher.FILTER_ALL),
                SportFilterMatcher.SportMenuOption("📺 Deportes TV", SportFilterMatcher.FILTER_TV_DEPORTES),
                SportFilterMatcher.SportMenuOption("🏟️ ArenaVision", SportFilterMatcher.FILTER_TV_ARENAVISION),
                SportFilterMatcher.SportMenuOption("📡 TDT Oficial", SportFilterMatcher.FILTER_TV_TDT)
            )
        } else {
            SportFilterMatcher.MENU_SPORTS
        }
        val labels = options.map { it.label }.toTypedArray()

        val adapter = ArrayAdapter(
            this,
            R.layout.item_dialog_channel,
            R.id.tvChannelChoice,
            labels
        )

        val builder = AlertDialog.Builder(this)
            .setTitle("Seleccionar filtro de deporte:")
            .setAdapter(adapter) { dialog, which ->
                dialog.dismiss()
                val chosen = options.getOrNull(which) ?: return@setAdapter
                (binding.recyclerViewEvents.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                viewModel.setSportFilter(chosen.filterKey)
            }
            .setNegativeButton("Cancelar", null)

        TvDialogHelper.show(this, builder, preferPositiveFocus = false)
    }

    private fun showTimezoneDialog() {
        val options = TimeZoneHelper.OPTIONS
        val currentId = viewModel.uiState.value.selectedTimezoneId
        val labels = options.map { option ->
            if (option.id == currentId) "✓ ${option.label}" else option.label
        }.toTypedArray()

        val adapter = ArrayAdapter(
            this,
            R.layout.item_dialog_channel,
            R.id.tvChannelChoice,
            labels
        )

        val builder = AlertDialog.Builder(this)
            .setTitle("Configuración de Zona Horaria")
            .setAdapter(adapter) { dialog, which ->
                dialog.dismiss()
                val option = options.getOrNull(which) ?: return@setAdapter
                viewModel.setTimezone(option.id)
                Toast.makeText(this, "Zona horaria aplicada: ${option.label}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null)

        TvDialogHelper.show(this, builder, preferPositiveFocus = false)
    }

    private fun toggleOrientation() {
        isLandscape = !isLandscape
        requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun handleVpnButtonClick() {
        if (!VpnHelper.openVpnAppOrSettings(this)) {
            showVpnDialog()
        }
    }

    private fun showVpnDialog() {
        try {
            if (vpnDialog?.isShowing == true) return

            if (VpnHelper.isBuiltInVpnActive()) {
                VpnHelper.stopBuiltInVpn(this)
            }

            val isVpnConnected = VpnHelper.isVpnActive(this)
            val installedVpn = VpnHelper.getInstalledVpnApp(this)
            val isPsiphonInstalled = VpnHelper.isPsiphonInstalled(this)
            val vpnName = installedVpn?.name ?: "VPN"

            val dialogBuilder = AlertDialog.Builder(this)

            if (isVpnConnected) {
                dialogBuilder.setTitle("🛡️ VPN Conectada y Activa")
                    .setMessage(
                        "🟢 TU CONEXIÓN ESTÁ PROTEGIDA FUERA DE ESPAÑA\n\n" +
                            "• Tu dispositivo está conectado mediante $vpnName.\n" +
                            "• Los bloqueos de operadoras y de LaLiga en AceStream están saltados con éxito.\n" +
                            "• Ya puedes reproducir cualquier partido en directo sin el error 'Cannot get transport file'."
                    )
                    .setPositiveButton("Entendido", null)

                if (installedVpn != null) {
                    dialogBuilder.setNeutralButton("Abrir ${installedVpn.name}") { _, _ ->
                        VpnHelper.launchVpnApp(this, installedVpn)
                    }
                } else {
                    dialogBuilder.setNeutralButton("Ajustes VPN") { _, _ ->
                        VpnHelper.openSystemVpnSettings(this)
                    }
                }
            } else {
                if (installedVpn != null) {
                    val isPsiphon = installedVpn.packageName == VpnHelper.PSIPHON.packageName ||
                        installedVpn.packageName == VpnHelper.PSIPHON_PLAY.packageName

                    val descText = if (isPsiphon) {
                        "Tienes instalada Psiphon.\n\nPulsa 'Abrir Psiphon' y dale a 'Iniciar'. Conexión automática sin cuentas ni contraseñas."
                    } else {
                        "Tienes instalada ${installedVpn.name}.\n\nPulsa 'Abrir ${installedVpn.name}' y conéctate a cualquier país fuera de España para saltar el bloqueo de las operadoras."
                    }

                    dialogBuilder.setTitle("🛡️ Conectar ${installedVpn.name}")
                        .setMessage(
                            "⚠️ Para ver partidos en AceStream sin bloqueos ('Cannot get transport file'), tu conexión debe salir por otro país (ej. Reino Unido / Países Bajos).\n\n" +
                                descText
                        )
                        .setPositiveButton("🚀 Abrir ${installedVpn.name}") { _, _ ->
                            VpnHelper.launchVpnApp(this, installedVpn)
                        }

                    if (!isPsiphon && !isPsiphonInstalled) {
                        dialogBuilder.setNeutralButton("📥 Instalar Psiphon") { _, _ ->
                            VpnHelper.installPsiphonDirect(this)
                        }
                    } else {
                        dialogBuilder.setNeutralButton("Ajustes VPN") { _, _ ->
                            VpnHelper.openSystemVpnSettings(this)
                        }
                    }
                    dialogBuilder.setNegativeButton("Cerrar", null)
                } else {
                    dialogBuilder.setTitle("🛡️ Desbloquear AceStream (Sin Cuentas)")
                        .setMessage(
                            "⚠️ En España las operadoras bloquean los enlaces de AceStream durante los partidos provocando el error 'Cannot get transport file'.\n\n" +
                                "Para saltar el bloqueo necesitas una VPN que envíe tu conexión fuera de España.\n\n" +
                                "Recomendamos Psiphon:\n" +
                                "• 100% Gratis e ilimitada\n" +
                                "• CERO cuentas, CERO correos y CERO contraseñas\n" +
                                "• Descarga directa e instalación en 1 clic\n" +
                                "• Apta para abuelas: solo pulsar 'Iniciar' y listo."
                        )
                        .setPositiveButton("📥 Instalar Psiphon (Descarga Directa)") { _, _ ->
                            VpnHelper.installPsiphonDirect(this)
                        }
                        .setNeutralButton("Ajustes VPN Android") { _, _ ->
                            VpnHelper.openSystemVpnSettings(this)
                        }
                        .setNegativeButton("Cerrar", null)
                }
            }

            vpnDialog = TvDialogHelper.show(
                activity = this,
                builder = dialogBuilder,
                preferPositiveFocus = true,
                onShowExtra = { shownDialog ->
                    shownDialog.setOnDismissListener {
                        vpnDialog = null
                        vpnActionView?.requestFocus()
                    }
                }
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun showAboutDialog() {
        val verName = UpdateHelper.getAppVersionName(this)
        TvDialogHelper.show(
            this,
            AlertDialog.Builder(this)
                .setTitle("🍍 Piña4Viewer v$verName")
                .setMessage(
                    "Versión Moderna Nativa en Kotlin\n\n" +
                        "• Versión Instalada: v$verName\n" +
                        "• Arquitectura MVVM con Corrutinas\n" +
                        "• Filtros en tiempo real\n" +
                        "• Soporte universal Phone / Tablet / TV\n" +
                        "• Desbloqueo VPN Anti-Bloqueos (Psiphon 100% sin cuentas)\n" +
                        "• Sin publicidad invasiva"
                )
                .setPositiveButton("Aceptar", null),
            preferPositiveFocus = true
        )
    }
}
