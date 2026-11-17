package com.snaplite.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Small pop-up that appears over the other app when a link is shared to SnapLite.
 * It checks the link, lets you pick video/audio + quality and starts the download in the background.
 */
class ShareActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val link = extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
        if (link == null) {
            Toast.makeText(this, "No link found in what you shared", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (savedInstanceState == null) {
            vm.url = link
            vm.analyze()
        }

        setContent {
            SnapTheme {
                ShareSheet(
                    vm = vm,
                    onClose = { finish() },
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_LINK, vm.url)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun ShareSheet(vm: MainViewModel, onClose: () -> Unit, onOpenApp: () -> Unit) {
    val ctx = LocalContext.current
    val outside = remember { MutableInteractionSource() }
    val inside = remember { MutableInteractionSource() }
    val hasResult = vm.info != null || vm.analyzeFailed

    // Tapping the dimmed area closes the sheet.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = outside, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = inside, indication = null) { },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_download),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        "  SnapLite",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (vm.loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(vm.message ?: "Checking link…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (vm.analyzeFailed) {
                    Text(
                        vm.message ?: "Could not read this link",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                vm.info?.let { InfoCard(it) }
                if (hasResult) FormatOptions(vm)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            vm.download()
                            Toast.makeText(ctx, "Download started", Toast.LENGTH_SHORT).show()
                            onClose()
                        },
                        enabled = !vm.loading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (vm.info != null) "Download" else "Download anyway")
                    }
                    TextButton(onClick = onOpenApp) { Text("Open app") }
                }
            }
        }
    }
}
