package com.feerlessleadr.embidextrous.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect

/** The Server group's first screen: the same Server and Plugins cards as on Home, with room to grow into the plugin manager. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerOverviewScreen(vm: AdminHomeViewModel) {
    LifecycleStartEffect(vm) {
        vm.load()
        onStopOrDispose { }
    }
    Scaffold(topBar = { TopAppBar(title = { if (!com.feerlessleadr.embidextrous.ui.LocalInTabbedGroup.current) Text("Server") }) }) { padding ->
        PullToRefreshBox(isRefreshing = false, onRefresh = { vm.load(force = true) }, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(12.dp)) {
                item { ServerCard(vm) }
                item { PluginsCard(vm) }
            }
        }
    }
}
