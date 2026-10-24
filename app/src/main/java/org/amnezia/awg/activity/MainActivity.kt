/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.activity

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.Observable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import org.amnezia.awg.fragment.AppListDialogFragment
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.model.ServerCategory
import org.amnezia.awg.model.ServerItem
import org.amnezia.awg.model.ServerRepository
import org.amnezia.awg.model.ServerType
import org.amnezia.awg.preference.ServerPreferences
import org.amnezia.awg.util.ErrorMessages
import java.util.Locale

class MainActivity : BaseActivity() {

    private enum class ActiveScreen { HOME, SETTINGS, ABOUT, SERVER_SELECT }
    private var currentScreen: ActiveScreen = ActiveScreen.HOME

    private lateinit var layoutHome: View
    private lateinit var layoutSettings: View
    private lateinit var layoutAbout: View
    private lateinit var layoutServerSelect: View

    private lateinit var tvTimer: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnPower: FrameLayout
    private lateinit var ivPower: ImageView
    private lateinit var btnServerSelect: LinearLayout
    private lateinit var tvServerName: TextView

    private lateinit var bottomNavContainer: FrameLayout
    private lateinit var btnNavHome: FrameLayout
    private lateinit var btnNavSettings: FrameLayout
    private lateinit var ivNavHome: ImageView
    private lateinit var ivNavSettings: ImageView

    // Server selection views
    private lateinit var btnServerSelectBack: FrameLayout
    private lateinit var tabRegular: FrameLayout
    private lateinit var tabSpecial: FrameLayout
    private lateinit var tvTabRegular: TextView
    private lateinit var tvTabSpecial: TextView
    private lateinit var rvServersList: RecyclerView

    private var currentServerCategory = ServerCategory.REGULAR
    private var timerJob: Job? = null
    private var connectedStartTime: Long = 0L

    private var tunnelCallback: Observable.OnPropertyChangedCallback? = null

    private var pendingTunnel: ObservableTunnel? = null
    private var pendingTargetState: Tunnel.State = Tunnel.State.TOGGLE
    private val permissionActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val tunnel = pendingTunnel
        val targetState = pendingTargetState
        pendingTunnel = null
        if (result.resultCode == RESULT_OK) {
            if (tunnel != null) {
                setTunnelStateWithPermissionsResult(tunnel, targetState)
            }
        } else {
            Toast.makeText(this, "Разрешение на VPN отклонено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        initViews()
        setupNavigation()
        setupSettingsItems()
        setupAboutScreen()
        setupServerSelectScreen()
        setupVpnActions()
        showHomeScreen()

        onBackPressedDispatcher.addCallback(this) {
            when (currentScreen) {
                ActiveScreen.SERVER_SELECT -> showHomeScreen()
                ActiveScreen.ABOUT -> showSettingsScreen()
                ActiveScreen.SETTINGS -> showHomeScreen()
                ActiveScreen.HOME -> finish()
            }
        }

        lifecycleScope.launch {
            ensureBuiltInServers()
            refreshTunnels()
        }
    }

    private fun initViews() {
        layoutHome = findViewById(R.id.layout_home)
        layoutSettings = findViewById(R.id.layout_settings)
        layoutAbout = findViewById(R.id.layout_about)
        layoutServerSelect = findViewById(R.id.layout_server_select)

        tvTimer = findViewById(R.id.tv_timer)
        tvStatus = findViewById(R.id.tv_status)
        btnPower = findViewById(R.id.btn_power)
        ivPower = findViewById(R.id.iv_power)
        btnServerSelect = findViewById(R.id.btn_server_select)
        tvServerName = findViewById(R.id.tv_server_name)

        bottomNavContainer = findViewById(R.id.bottom_nav_container)
        btnNavHome = findViewById(R.id.btn_nav_home)
        btnNavSettings = findViewById(R.id.btn_nav_settings)
        ivNavHome = findViewById(R.id.iv_nav_home)
        ivNavSettings = findViewById(R.id.iv_nav_settings)

        btnServerSelectBack = findViewById(R.id.btn_server_select_back)
        tabRegular = findViewById(R.id.tab_regular)
        tabSpecial = findViewById(R.id.tab_special)
        tvTabRegular = findViewById(R.id.tv_tab_regular)
        tvTabSpecial = findViewById(R.id.tv_tab_special)
        rvServersList = findViewById(R.id.rv_servers_list)

        val unbounded = ResourcesCompat.getFont(this, R.font.unbounded)
        if (unbounded != null) {
            findViewById<TextView>(R.id.tv_title)?.typeface = unbounded
            findViewById<TextView>(R.id.tv_version)?.typeface = unbounded
            tvTimer.typeface = unbounded
            tvStatus.typeface = unbounded
            tvServerName.typeface = unbounded
            findViewById<TextView>(R.id.tv_settings_title)?.typeface = unbounded
            findViewById<TextView>(R.id.tv_item_appearance)?.typeface = unbounded
            findViewById<TextView>(R.id.tv_item_comfort)?.typeface = unbounded
            findViewById<TextView>(R.id.tv_item_exceptions)?.typeface = unbounded
            findViewById<TextView>(R.id.tv_item_hotspot)?.typeface = unbounded
            findViewById<TextView>(R.id.tv_item_about)?.typeface = unbounded
            findViewById<TextView>(R.id.tv_settings_footer_version)?.typeface = unbounded

            findViewById<TextView>(R.id.tv_about_title)?.typeface = unbounded
            findViewById<TextView>(R.id.tv_btn_check_updates)?.typeface = unbounded
            findViewById<TextView>(R.id.tv_app_version)?.typeface = unbounded
            findViewById<TextView>(R.id.tv_btn_telegram)?.typeface = unbounded

            findViewById<TextView>(R.id.tv_server_select_title)?.typeface = unbounded
            tvTabRegular.typeface = unbounded
            tvTabSpecial.typeface = unbounded
        }

        findViewById<TextView>(R.id.tv_app_version)?.text =
            "Версия приложения: v${org.amnezia.awg.BuildConfig.VERSION_NAME}"
        findViewById<TextView>(R.id.tv_version)?.text =
            "v${org.amnezia.awg.BuildConfig.VERSION_NAME}"
    }

    private fun setupNavigation() {
        btnNavHome.setOnClickListener {
            showHomeScreen()
        }
        btnNavSettings.setOnClickListener {
            showSettingsScreen()
        }
    }

    private fun showHomeScreen() {
        currentScreen = ActiveScreen.HOME
        layoutHome.visibility = View.VISIBLE
        layoutSettings.visibility = View.GONE
        layoutAbout.visibility = View.GONE
        layoutServerSelect.visibility = View.GONE
        bottomNavContainer.visibility = View.VISIBLE

        btnNavHome.setBackgroundResource(R.drawable.bg_nav_tab_active)
        ivNavHome.setColorFilter(Color.WHITE)

        btnNavSettings.background = null
        ivNavSettings.setColorFilter(ContextCompat.getColor(this, R.color.zaza_nav_inactive))

        btnPower.requestFocus()
    }

    private fun showSettingsScreen() {
        currentScreen = ActiveScreen.SETTINGS
        layoutHome.visibility = View.GONE
        layoutSettings.visibility = View.VISIBLE
        layoutAbout.visibility = View.GONE
        layoutServerSelect.visibility = View.GONE
        bottomNavContainer.visibility = View.VISIBLE

        btnNavSettings.setBackgroundResource(R.drawable.bg_nav_tab_active)
        ivNavSettings.setColorFilter(Color.WHITE)

        btnNavHome.background = null
        ivNavHome.setColorFilter(ContextCompat.getColor(this, R.color.zaza_nav_inactive))

        findViewById<View>(R.id.item_appearance)?.requestFocus()
    }

    private fun showAboutScreen() {
        currentScreen = ActiveScreen.ABOUT
        layoutHome.visibility = View.GONE
        layoutSettings.visibility = View.GONE
        layoutAbout.visibility = View.VISIBLE
        layoutServerSelect.visibility = View.GONE
        bottomNavContainer.visibility = View.GONE

        findViewById<View>(R.id.btn_about_back)?.requestFocus()
    }

    private fun showServerSelectScreen() {
        currentScreen = ActiveScreen.SERVER_SELECT
        layoutHome.visibility = View.GONE
        layoutSettings.visibility = View.GONE
        layoutAbout.visibility = View.GONE
        layoutServerSelect.visibility = View.VISIBLE
        bottomNavContainer.visibility = View.GONE
        updateServerList()

        tabRegular.requestFocus()
    }

    private fun setupServerSelectScreen() {
        btnServerSelectBack.setOnClickListener {
            showHomeScreen()
        }

        tabRegular.setOnClickListener {
            currentServerCategory = ServerCategory.REGULAR
            tabRegular.setBackgroundResource(R.drawable.bg_tab_pill_active)
            tvTabRegular.setTextColor(ContextCompat.getColor(this, R.color.zaza_purple))
            tabSpecial.background = null
            tvTabSpecial.setTextColor(ContextCompat.getColor(this, R.color.zaza_tab_inactive_text))
            updateServerList()
        }

        tabSpecial.setOnClickListener {
            currentServerCategory = ServerCategory.SPECIAL
            tabSpecial.setBackgroundResource(R.drawable.bg_tab_pill_active)
            tvTabSpecial.setTextColor(ContextCompat.getColor(this, R.color.zaza_purple))
            tabRegular.background = null
            tvTabRegular.setTextColor(ContextCompat.getColor(this, R.color.zaza_tab_inactive_text))
            updateServerList()
        }

        rvServersList.layoutManager = LinearLayoutManager(this)
    }

    private fun updateServerList() {
        val filteredServers = ServerRepository.SERVERS.filter { it.category == currentServerCategory }
        val sortedServers = filteredServers.sortedWith(
            compareByDescending<ServerItem> { ServerPreferences.isPinned(this, it.id) }
        )

        rvServersList.adapter = ServerCardAdapter(
            items = sortedServers,
            currentSelectedId = selectedTunnel?.name,
            onSelect = { server ->
                onServerClicked(server)
            },
            onInfo = { server ->
                showServerInfoSheet(server)
            }
        )
    }

    private fun onServerClicked(server: ServerItem) {
        if (server.isCountry && !ServerPreferences.isSpeedWarningSuppressed(this, server.id)) {
            showSpeedWarningSheet(server)
        } else {
            selectAndActivateTunnel(server, connect = false)
            showHomeScreen()
        }
    }

    private fun selectAndActivateTunnel(server: ServerItem, connect: Boolean) {
        lifecycleScope.launch {
            val manager = Application.getTunnelManager()
            val tunnels = manager.getTunnels()
            var currentTunnel = tunnels[server.id]
            if (currentTunnel == null) {
                try {
                    assets.open("tunnels/${server.assetFileName}").use { stream ->
                        val config = Config.parse(stream)
                        currentTunnel = manager.create(server.id, config)
                    }
                } catch (e: Throwable) {
                    val error = ErrorMessages[e]
                    Toast.makeText(this@MainActivity, "Ошибка конфигурации: $error", Toast.LENGTH_LONG).show()
                    Log.e("MainActivity", "Failed to load tunnel ${server.id}", e)
                    return@launch
                }
            }

            val targetTunnel = currentTunnel ?: return@launch
            selectedTunnel = targetTunnel
            tvServerName.text = server.displayName
            bindTunnelState(targetTunnel)

            if (connect) {
                setTunnelStateWithPermissions(targetTunnel, Tunnel.State.UP)
            }
        }
    }

    private fun showSpeedWarningSheet(server: ServerItem) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_speed_warning_sheet, null)
        dialog.setContentView(view)

        val unbounded = ResourcesCompat.getFont(this, R.font.unbounded)
        if (unbounded != null) {
            view.findViewById<TextView>(R.id.tv_warning_desc)?.typeface = unbounded
            view.findViewById<TextView>(R.id.btn_warning_connect_dont_show)?.typeface = unbounded
        }

        val tvDesc = view.findViewById<TextView>(R.id.tv_warning_desc)
        tvDesc.text = "Сервер ${server.displayName} находится в списке специальных серверов и имеет ограниченную скорость"

        view.findViewById<View>(R.id.btn_warning_connect).setOnClickListener {
            dialog.dismiss()
            selectAndActivateTunnel(server, connect = true)
            showHomeScreen()
        }

        view.findViewById<View>(R.id.btn_warning_connect_dont_show).setOnClickListener {
            ServerPreferences.setSpeedWarningSuppressed(this, server.id, true)
            dialog.dismiss()
            selectAndActivateTunnel(server, connect = true)
            showHomeScreen()
        }

        dialog.show()
    }

    private fun showServerInfoSheet(server: ServerItem) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_server_info_sheet, null)
        dialog.setContentView(view)

        val unbounded = ResourcesCompat.getFont(this, R.font.unbounded)
        val tvTitle = view.findViewById<TextView>(R.id.tv_sheet_title)
        val tvDesc = view.findViewById<TextView>(R.id.tv_sheet_desc)
        val tvPinText = view.findViewById<TextView>(R.id.tv_sheet_pin_text)
        val ivIcon = view.findViewById<ImageView>(R.id.iv_sheet_icon)
        val btnPin = view.findViewById<View>(R.id.btn_sheet_pin)
        val btnDismiss = view.findViewById<View>(R.id.btn_sheet_dismiss)

        if (unbounded != null) {
            tvTitle.typeface = unbounded
            tvDesc.typeface = unbounded
            tvPinText.typeface = unbounded
        }

        tvTitle.text = server.displayName
        tvDesc.text = server.description

        if (server.serverType == ServerType.AI) {
            ivIcon.setImageResource(R.drawable.ic_sparkles)
        } else {
            ivIcon.setImageResource(R.drawable.ic_globe)
        }

        fun updatePinButtonText() {
            val isPinned = ServerPreferences.isPinned(this, server.id)
            tvPinText.text = if (isPinned) "Открепить сервер" else "Закрепить сервер"
        }
        updatePinButtonText()

        btnPin.setOnClickListener {
            val currentlyPinned = ServerPreferences.isPinned(this, server.id)
            ServerPreferences.setPinned(this, server.id, !currentlyPinned)
            updatePinButtonText()
            updateServerList()
        }

        btnDismiss.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupSettingsItems() {
        findViewById<View>(R.id.item_appearance).setOnClickListener {
            showAppearanceDialog()
        }
        findViewById<View>(R.id.item_comfort).setOnClickListener {
            showComfortDialog()
        }
        findViewById<View>(R.id.item_exceptions).setOnClickListener {
            val dialog = AppListDialogFragment()
            dialog.show(supportFragmentManager, "app_list")
        }
        findViewById<View>(R.id.item_hotspot).setOnClickListener {
            showHotspotDialog()
        }
        findViewById<View>(R.id.item_about).setOnClickListener {
            showAboutScreen()
        }
    }

    private fun setupAboutScreen() {
        findViewById<View>(R.id.btn_about_back).setOnClickListener {
            showSettingsScreen()
        }
        findViewById<View>(R.id.btn_check_updates).setOnClickListener {
            Toast.makeText(
                this,
                "Проверка обновлений временно недоступна",
                Toast.LENGTH_SHORT
            ).show()
        }
        findViewById<View>(R.id.btn_app_version).setOnClickListener {
            Toast.makeText(
                this,
                "Версия v${org.amnezia.awg.BuildConfig.VERSION_NAME}",
                Toast.LENGTH_SHORT
            ).show()
        }
        findViewById<View>(R.id.btn_telegram).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+gMbEmCMxwb1hMTRi"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось открыть Telegram: ${e.localizedMessage ?: "неизвестная ошибка"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAppearanceDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Внешний вид")
            .setMessage("Текущая тема: Темная (Zaza Dark Mode).\nИнтерфейс настроен в фирменных цветах Zaza.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showComfortDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Комфорт")
            .setMessage("Настройки комфортного использования:\n• Автоподключение при загрузке\n• Защита от утечек DNS\n• Быстрое переключение тоннелей")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showHotspotDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Точка Доступа с VPN")
            .setMessage("Раздача интернета через защищенный VPN-канал на другие устройства доступна при включенном режиме точки доступа.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupVpnActions() {
        btnPower.setOnClickListener {
            val tunnel = selectedTunnel
            if (tunnel == null) {
                showServerSelectScreen()
                return@setOnClickListener
            }
            setTunnelStateWithPermissions(tunnel, Tunnel.State.TOGGLE)
        }

        btnServerSelect.setOnClickListener {
            showServerSelectScreen()
        }
        btnServerSelect.setOnLongClickListener {
            val server = ServerRepository.findById(selectedTunnel?.name) ?: ServerRepository.SERVERS.firstOrNull()
            if (server != null) {
                showServerInfoSheet(server)
            }
            true
        }
    }

    private fun setTunnelStateWithPermissions(tunnel: ObservableTunnel, state: Tunnel.State) {
        lifecycleScope.launch {
            val shouldCheckPermission = when (state) {
                Tunnel.State.UP -> true
                Tunnel.State.TOGGLE -> tunnel.state != Tunnel.State.UP
                else -> false
            }

            if (shouldCheckPermission && Application.getBackend() is GoBackend) {
                try {
                    val intent = GoBackend.VpnService.prepare(this@MainActivity)
                    if (intent != null) {
                        pendingTunnel = tunnel
                        pendingTargetState = state
                        permissionActivityResultLauncher.launch(intent)
                        return@launch
                    }
                } catch (e: Throwable) {
                    val error = ErrorMessages[e]
                    val message = getString(R.string.error_prepare, error)
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    Log.e("MainActivity", message, e)
                    return@launch
                }
            }
            setTunnelStateWithPermissionsResult(tunnel, state)
        }
    }

    private fun setTunnelStateWithPermissionsResult(tunnel: ObservableTunnel, targetState: Tunnel.State) {
        lifecycleScope.launch {
            try {
                val desiredState = if (targetState == Tunnel.State.TOGGLE) {
                    if (tunnel.state == Tunnel.State.UP) Tunnel.State.DOWN else Tunnel.State.UP
                } else {
                    targetState
                }

                if (desiredState == Tunnel.State.UP) {
                    val allTunnels = Application.getTunnelManager().getTunnels()
                    for (otherTunnel in allTunnels) {
                        if (otherTunnel != tunnel && otherTunnel.state == Tunnel.State.UP) {
                            try {
                                otherTunnel.setStateAsync(Tunnel.State.DOWN)
                            } catch (e: Throwable) {
                                Log.w("MainActivity", "Error taking down other tunnel ${otherTunnel.name}", e)
                            }
                        }
                    }
                }

                tunnel.setStateAsync(desiredState)
            } catch (e: Throwable) {
                val error = ErrorMessages[e]
                val message = if (tunnel.state == Tunnel.State.UP) {
                    getString(R.string.error_down, error)
                } else {
                    getString(R.string.error_up, error)
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                Log.e("MainActivity", message, e)
            }
        }
    }

    private suspend fun ensureBuiltInServers() {
        val manager = Application.getTunnelManager()
        val existingTunnels = manager.getTunnels()
        for (server in ServerRepository.SERVERS) {
            if (!existingTunnels.containsKey(server.id)) {
                try {
                    assets.open("tunnels/${server.assetFileName}").use { stream ->
                        val config = Config.parse(stream)
                        manager.create(server.id, config)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to create built-in tunnel ${server.id}", e)
                }
            }
        }
    }

    override fun onSelectedTunnelChanged(
        oldTunnel: ObservableTunnel?,
        newTunnel: ObservableTunnel?
    ): Boolean {
        oldTunnel?.let { tunnelCallback?.let { cb -> it.removeOnPropertyChangedCallback(cb) } }
        if (newTunnel != null) {
            tvServerName.text = ServerRepository.getDisplayName(newTunnel.name)
            bindTunnelState(newTunnel)
        } else {
            tvServerName.text = "Сервер #8"
            updateUiState(Tunnel.State.DOWN, ObservableTunnel.ConnectionStatus.DISCONNECTED)
        }
        return true
    }

    private suspend fun refreshTunnels() {
        val tunnels = Application.getTunnelManager().getTunnels()
        if (selectedTunnel == null) {
            val lastUsed = Application.getTunnelManager().lastUsedTunnel
            selectedTunnel = lastUsed ?: tunnels["server_8"] ?: tunnels.firstOrNull()
        }
        if (selectedTunnel != null) {
            tvServerName.text = ServerRepository.getDisplayName(selectedTunnel?.name)
            bindTunnelState(selectedTunnel!!)
        } else {
            tvServerName.text = "Сервер #8"
            updateUiState(Tunnel.State.DOWN, ObservableTunnel.ConnectionStatus.DISCONNECTED)
        }
    }

    private fun bindTunnelState(tunnel: ObservableTunnel) {
        tunnelCallback?.let { tunnel.removeOnPropertyChangedCallback(it) }
        val callback = object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                runOnUiThread {
                    updateUiState(tunnel.state, tunnel.connectionStatus)
                }
            }
        }
        tunnel.addOnPropertyChangedCallback(callback)
        tunnelCallback = callback
        updateUiState(tunnel.state, tunnel.connectionStatus)
    }

    private fun updateUiState(state: Tunnel.State, status: ObservableTunnel.ConnectionStatus) {
        if (state == Tunnel.State.UP) {
            tvStatus.text = "Подключено"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.zaza_purple))
            btnPower.setBackgroundResource(R.drawable.bg_power_button_connected)
            ivPower.setColorFilter(Color.WHITE)
            startTimer()
        } else {
            tvStatus.text = "Не подключено"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.zaza_text_secondary))
            btnPower.setBackgroundResource(R.drawable.bg_power_button_disconnected)
            ivPower.setColorFilter(ContextCompat.getColor(this, R.color.zaza_text_secondary))
            stopTimer()
        }
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        connectedStartTime = System.currentTimeMillis()
        timerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsedSec = (System.currentTimeMillis() - connectedStartTime) / 1000
                val hours = elapsedSec / 3600
                val minutes = (elapsedSec % 3600) / 60
                val seconds = elapsedSec % 60
                tvTimer.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        tvTimer.text = "00:00:00"
    }

    private class ServerCardAdapter(
        private val items: List<ServerItem>,
        private val currentSelectedId: String?,
        private val onSelect: (ServerItem) -> Unit,
        private val onInfo: (ServerItem) -> Unit
    ) : RecyclerView.Adapter<ServerCardAdapter.ViewHolder>() {

        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val card: LinearLayout = view.findViewById(R.id.card_server)
            val ivIcon: ImageView = view.findViewById(R.id.iv_server_icon)
            val tvTitle: TextView = view.findViewById(R.id.tv_server_title)
            val tvSubtitle: TextView = view.findViewById(R.id.tv_server_subtitle)
            val ivPin: ImageView = view.findViewById(R.id.iv_server_pin)
            val btnInfo: View = view.findViewById(R.id.btn_server_info)
            val ivCheck: ImageView = view.findViewById(R.id.iv_server_check)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_server_card, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val context = holder.view.context
            val server = items[position]
            val isSelected = server.id == currentSelectedId
            val isPinned = ServerPreferences.isPinned(context, server.id)

            val unbounded = ResourcesCompat.getFont(context, R.font.unbounded)
            if (unbounded != null) {
                holder.tvTitle.typeface = unbounded
                holder.tvSubtitle.typeface = unbounded
            }

            holder.tvTitle.text = server.displayName

            if (server.subtitle != null) {
                holder.tvSubtitle.visibility = View.VISIBLE
                holder.tvSubtitle.text = server.subtitle
            } else {
                holder.tvSubtitle.visibility = View.GONE
            }

            // Icons
            if (server.serverType == ServerType.AI) {
                holder.ivIcon.setImageResource(R.drawable.ic_sparkles)
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_globe)
            }

            if (isSelected) {
                holder.card.setBackgroundResource(R.drawable.bg_server_card_selected)
                holder.ivCheck.visibility = View.VISIBLE
                holder.ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.zaza_purple))
            } else {
                holder.card.setBackgroundResource(R.drawable.bg_server_card_normal)
                holder.ivCheck.visibility = View.GONE
                holder.ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.zaza_text_secondary))
            }

            holder.ivPin.visibility = if (isPinned) View.VISIBLE else View.GONE

            holder.card.setOnClickListener {
                onSelect(server)
            }

            holder.card.setOnLongClickListener {
                onInfo(server)
                true
            }

            holder.btnInfo.setOnClickListener {
                onInfo(server)
            }
        }
    }

    override fun onDestroy() {
        stopTimer()
        tunnelCallback?.let { selectedTunnel?.removeOnPropertyChangedCallback(it) }
        super.onDestroy()
    }
}
