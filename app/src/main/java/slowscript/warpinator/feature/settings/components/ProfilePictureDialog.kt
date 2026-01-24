package slowscript.warpinator.feature.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import slowscript.warpinator.core.utils.ProfilePicturePainter

/**
 * A dialog composable that allows the user to view and select a profile picture.
 *
 * It provides a grid of predefined profile avatars and an option to select a custom image.
 * The currently selected image is displayed prominently at the top.
 *
 * @param currentKey The key representing the currently active profile picture (e.g., "1", "2", or "profilePic.png").
 * @param onDismiss Callback invoked when the user attempts to close the dialog (e.g., clicks the close button or back).
 * @param onSelectKey Callback invoked when the user confirms their selection by clicking "Save". Passes the selected image key.
 * @param onSelectCustom Callback invoked when the user clicks the button to add a custom photo.
 * @param imageSignature A signature (timestamp/hash) used to force a refresh of the image painter when the underlying custom image file changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePictureDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSelectKey: (String) -> Unit,
    onSelectCustom: () -> Unit,
    imageSignature: Long,
) {
    var selectedKey by remember { mutableStateOf<String>(currentKey) }
    val context = LocalContext.current

    LaunchedEffect(imageSignature) {
        if (imageSignature > 0 && selectedKey != "profilePic.png") {
            selectedKey = "profilePic.png"
        }
    }

    Dialog(
        onDismissRequest = onDismiss, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Change profile picture", maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                onSelectKey(selectedKey)
                            }, modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text("Save")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier
                        .padding(
                            top = 160.dp, start = 16.dp, end = 16.dp, bottom = 32.dp
                        )
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    LazyVerticalGrid(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        columns = GridCells.Adaptive(64.dp),
                        contentPadding = PaddingValues(
                            top = 96.dp, start = 16.dp, end = 16.dp, bottom = 16.dp
                        )
                    ) {
                        item {
                            Box(contentAlignment = Alignment.Center) {
                                FilledIconButton(
                                    onClick = onSelectCustom,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .height(64.dp)
                                        .aspectRatio(1f)
                                ) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Add Custom Profile",
                                    )
                                }
                            }
                        }

                        items(ProfilePicturePainter.colorsLength) { index ->
                            val key = index.toString()
                            val isSelected = (key == selectedKey)
                            val bmp = remember(key) {
                                ProfilePicturePainter.getProfilePicture(
                                    key, context
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .clickable { selectedKey = key }) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Profile $index",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .border(
                                                    4.dp,
                                                    MaterialTheme.colorScheme.tertiary,
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }

                    }
                }
                Box(
                    modifier = Modifier.offset(y = (80).dp), contentAlignment = Alignment.Center
                ) {
                    val currentBitmap = remember(selectedKey, imageSignature) {
                        ProfilePicturePainter.getProfilePicture(selectedKey, context, true)
                    }

                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(160.dp)
                            .border(
                                8.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape
                            )
                            .padding(8.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ProfilePictureDialogPreview() {

    ProfilePictureDialog(
        currentKey = "1",
        onDismiss = {},
        onSelectKey = {},
        onSelectCustom = {},
        imageSignature = 0
    )

}