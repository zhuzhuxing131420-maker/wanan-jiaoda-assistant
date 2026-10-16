package com.wananjiaoda.assistant.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wananjiaoda.assistant.ui.navigation.AppDestination

@Composable
fun AppBottomBar(currentRoute: String?, onDestinationSelected: (AppDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        AppDestination.entries.filter { it.showInBottomBar }.forEach { destination ->
            val glyph = when (destination) {
                AppDestination.HOME -> ToolGlyphKind.HOME
                AppDestination.SETTINGS -> ToolGlyphKind.SETTINGS
                else -> ToolGlyphKind.HISTORY
            }
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onDestinationSelected(destination) },
                icon = { ToolGlyph(glyph) },
                label = { Text(destination.label) },
            )
        }
    }
}
