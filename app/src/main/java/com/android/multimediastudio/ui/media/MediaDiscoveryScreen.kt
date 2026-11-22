package com.android.multimediastudio.ui.media

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GraphicEq
import com.android.multimediastudio.ui.components.AnimatedWaveformIcon
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import com.android.multimediastudio.ui.settings.SettingsScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.content.res.Configuration
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.multimediastudio.data.model.MediaFilter
import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import com.android.multimediastudio.util.VideoThumbnailData
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.util.lerp
import com.android.multimediastudio.ui.player.AudioPlayerContent
import com.android.multimediastudio.ui.player.FloatingVideoPip
import com.android.multimediastudio.ui.player.LoopMode
import com.android.multimediastudio.ui.player.MiniPlayerBar
import com.android.multimediastudio.ui.player.PlaybackSpeedSelector
import com.android.multimediastudio.ui.player.PlayerScreen
import com.android.multimediastudio.ui.player.PlayerUiState
import com.android.multimediastudio.ui.player.PlayerViewModel
import com.android.multimediastudio.ui.player.VideoPlayerSurface
import com.android.multimediastudio.ui.player.findActivity
import com.android.multimediastudio.ui.theme.MultimediaStudioTheme
import com.android.multimediastudio.util.PermissionUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MediaDiscoveryScreen(
    viewModel: MediaViewModel,
    playerViewModel: PlayerViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val uiState by viewModel.uiState.collectAsState()
    val playerLayoutState by playerViewModel.playerLayoutState.collectAsState()
    val playerUiState by playerViewModel.uiState.collectAsState()

    if (playerLayoutState.isInPipMode) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            VideoPlayerSurface(
                player = playerViewModel.getPlayer(),
                onSurfaceClick = { },
                onRewind = { },
                onForward = { },
                isScreenLocked = false,
                enableGestures = false,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val configuration = LocalConfiguration.current
    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCompactHeight = configuration.screenHeightDp < 500

    val currentDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val isPlayerExpanded = playerLayoutState.isExpanded || uiState.selectedMediaItem != null

    val isPlayerFullOrLandscape = isPlayerExpanded &&
            (playerLayoutState.isLandscape || playerLayoutState.isFullScreen || isDeviceLandscape)

    // On phones in landscape (compact height) or whenever the player is expanded, use full width (single pane)
    val shouldForceSinglePane = isPlayerFullOrLandscape || (isDeviceLandscape && isCompactHeight)

    val scaffoldDirective = if (shouldForceSinglePane) {
        currentDirective.copy(maxHorizontalPartitions = 1)
    } else {
        currentDirective
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<MediaItem>(
        scaffoldDirective = scaffoldDirective
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.selectedMediaItem) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    BackHandler(enabled = uiState.isSettingsOpen || navigator.canNavigateBack() || isPlayerExpanded || uiState.isSelectionMode) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (uiState.isSettingsOpen) {
            viewModel.closeSettings()
        } else if (uiState.isSelectionMode) {
            viewModel.exitSelectionMode()
        } else if (isPlayerExpanded) {
            val wasInLandscape = playerViewModel.uiState.value.playedFromLandscape
            if (wasInLandscape) {
                context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            playerViewModel.collapsePlayer()
            playerViewModel.setOrientation(false)
            playerViewModel.setFullScreen(false)
            if (navigator.canNavigateBack()) {
                coroutineScope.launch {
                    navigator.navigateBack()
                }
            }
            viewModel.selectMediaItem(null)
        } else if (navigator.canNavigateBack()) {
            coroutineScope.launch {
                navigator.navigateBack()
            }
        }
    }

    var playerProgress by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isSettingsOpen) {
            SettingsScreen(
                themeMode = uiState.themeMode,
                appThemeColor = uiState.appThemeColor,
                onThemeModeSelected = { mode -> viewModel.setThemeMode(mode) },
                onAppThemeColorSelected = { color -> viewModel.setAppThemeColor(color) },
                hideSocialMedia = uiState.hideSocialMedia,
                onHideSocialMediaChanged = { hide -> viewModel.setHideSocialMedia(hide) },
                onBack = { viewModel.closeSettings() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ListDetailPaneScaffold(
            directive = scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                AnimatedPane {
                    MediaGalleryPane(
                        viewModel = viewModel,
                        playerViewModel = playerViewModel,
                        playerProgress = playerProgress,
                        onItemClick = { item ->
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            playerViewModel.initializePlayer(context)
                            if (item.mediaType == MediaType.VIDEO) {
                                val isLandscapeStage = isDeviceLandscape ||
                                        (context.findActivity()?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE)
                                playerViewModel.playMedia(
                                    mediaItem = item,
                                    queue = viewModel.uiState.value.filteredMediaItems,
                                    playedFromLandscape = isLandscapeStage
                                )
                                playerViewModel.setMediaQueue(viewModel.uiState.value.filteredMediaItems)
                                playerViewModel.expandPlayer()
                                viewModel.selectMediaItem(item)
                                if (scaffoldDirective.maxHorizontalPartitions > 1) {
                                    coroutineScope.launch {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, item)
                                    }
                                }
                            } else {
                                if (playerLayoutState.currentMediaItem?.id == item.id) {
                                    playerViewModel.togglePlayPause()
                                } else {
                                    playerViewModel.playMedia(item, viewModel.uiState.value.filteredMediaItems)
                                    playerViewModel.setMediaQueue(viewModel.uiState.value.filteredMediaItems)
                                    if (!playerLayoutState.isPlaying) {
                                        playerViewModel.play()
                                    }
                                }
                                viewModel.selectMediaItem(null)
                            }
                        }
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val selectedItem = navigator.currentDestination?.contentKey ?: uiState.selectedMediaItem
                    if (selectedItem != null) {
                        val activeItem = playerLayoutState.currentMediaItem ?: selectedItem
                        val currentQueue = playerUiState.mediaQueue
                        val effectiveQueue = when {
                            uiState.filteredMediaItems.any { it.id == activeItem.id } -> uiState.filteredMediaItems
                            currentQueue.any { it.id == activeItem.id } -> currentQueue
                            else -> listOf(activeItem)
                        }
                        PlayerScreen(
                            mediaItem = activeItem,
                            viewModel = playerViewModel,
                            mediaQueue = effectiveQueue,
                            onNext = {
                                playerViewModel.playNext()
                                playerViewModel.uiState.value.currentMediaItem?.let { newItem ->
                                    viewModel.selectMediaItem(newItem)
                                }
                            },
                            onPrevious = {
                                playerViewModel.playPrevious()
                                playerViewModel.uiState.value.currentMediaItem?.let { newItem ->
                                    viewModel.selectMediaItem(newItem)
                                }
                            },
                            onClose = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                val wasInLandscape = playerViewModel.uiState.value.playedFromLandscape
                                if (wasInLandscape) {
                                    context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                                playerViewModel.collapsePlayer()
                                playerViewModel.setOrientation(false)
                                playerViewModel.setFullScreen(false)
                                if (navigator.canNavigateBack()) {
                                    coroutineScope.launch {
                                        navigator.navigateBack()
                                    }
                                }
                                viewModel.selectMediaItem(null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        MediaDetailPlaceholder()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        val activePlayerItem = playerLayoutState.currentMediaItem ?: uiState.selectedMediaItem
        val isPlayerFullscreen = playerLayoutState.isExpanded || uiState.selectedMediaItem != null

        if (activePlayerItem != null && isPlayerFullscreen && scaffoldDirective.maxHorizontalPartitions == 1) {
            val currentQueue = playerUiState.mediaQueue
            val effectiveQueue = when {
                uiState.filteredMediaItems.any { it.id == activePlayerItem.id } -> uiState.filteredMediaItems
                currentQueue.any { it.id == activePlayerItem.id } -> currentQueue
                else -> listOf(activePlayerItem)
            }
            PlayerScreen(
                mediaItem = activePlayerItem,
                viewModel = playerViewModel,
                mediaQueue = effectiveQueue,
                onNext = {
                    playerViewModel.playNext()
                    playerViewModel.uiState.value.currentMediaItem?.let { newItem ->
                        viewModel.selectMediaItem(newItem)
                    }
                },
                onPrevious = {
                    playerViewModel.playPrevious()
                    playerViewModel.uiState.value.currentMediaItem?.let { newItem ->
                        viewModel.selectMediaItem(newItem)
                    }
                },
                onClose = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    val wasInLandscape = playerViewModel.uiState.value.playedFromLandscape
                    if (wasInLandscape) {
                        context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                    playerViewModel.collapsePlayer()
                    playerViewModel.setOrientation(false)
                    playerViewModel.setFullScreen(false)
                    if (navigator.canNavigateBack()) {
                        coroutineScope.launch {
                            navigator.navigateBack()
                        }
                    }
                    viewModel.selectMediaItem(null)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (activePlayerItem != null && !isPlayerFullscreen && activePlayerItem.mediaType == MediaType.VIDEO) {
            DiscoveryFloatingVideoPip(
                playerViewModel = playerViewModel,
                mediaItem = activePlayerItem,
                onExpand = {
                    playerViewModel.expandPlayer()
                    if (isDeviceLandscape && !activePlayerItem.isReelOrShort()) {
                        playerViewModel.setOrientation(true)
                        playerViewModel.setPlayedFromLandscape(true)
                    }
                    viewModel.selectMediaItem(activePlayerItem)
                },
                onClose = {
                    playerViewModel.clearActiveTrack()
                    viewModel.selectMediaItem(null)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
}

@Composable
private fun DiscoveryFloatingVideoPip(
    playerViewModel: PlayerViewModel,
    mediaItem: MediaItem,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerUiState by playerViewModel.uiState.collectAsState()
    FloatingVideoPip(
        mediaItem = mediaItem,
        isPlaying = playerUiState.isPlaying,
        player = playerViewModel.getPlayer(),
        currentPositionMs = playerUiState.currentPositionMs,
        durationMs = playerUiState.durationMs,
        onPlayPauseClick = { playerViewModel.togglePlayPause() },
        onExpandClick = onExpand,
        onCloseClick = onClose,
        modifier = modifier
    )
}

@Composable
private fun GalleryMiniPlayer(
    playerViewModel: PlayerViewModel,
    onExpand: (MediaItem) -> Unit
) {
    val playerUiState by playerViewModel.uiState.collectAsState()
    val currentItem = playerUiState.currentMediaItem
    if (currentItem != null && !playerUiState.isExpanded && currentItem.mediaType == MediaType.AUDIO) {
        MiniPlayerBar(
            mediaItem = currentItem,
            isPlaying = playerUiState.isPlaying,
            currentPositionMs = playerUiState.currentPositionMs,
            durationMs = playerUiState.durationMs,
            onPlayPauseClick = { playerViewModel.togglePlayPause() },
            onClick = {
                playerViewModel.expandPlayer()
                onExpand(currentItem)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryPane(
    viewModel: MediaViewModel,
    playerViewModel: PlayerViewModel = viewModel(),
    playerProgress: Float = 0f,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val uiState by viewModel.uiState.collectAsState()
    val activeMediaItemId by playerViewModel.activeMediaItemId.collectAsState()
    val isPlaybackActive by playerViewModel.isPlaybackActive.collectAsState()
    val playerUiState by playerViewModel.uiState.collectAsState()
    val isMiniPlayerActive = playerUiState.currentMediaItem != null &&
            !playerUiState.isExpanded &&
            playerUiState.currentMediaItem?.mediaType == MediaType.AUDIO

    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.setSearchQuery("")
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(uiState.isSelectionMode) {
        if (uiState.isSelectionMode) {
            isSearchExpanded = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val hasMedia = PermissionUtils.hasMediaPermissions(context)
        viewModel.onPermissionChanged(hasMedia)
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeleteConfirmed()
        } else {
            viewModel.clearDeleteIntentSender()
        }
    }

    LaunchedEffect(uiState.pendingDeleteIntentSender) {
        val sender = uiState.pendingDeleteIntentSender
        if (sender != null) {
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    val hasPermission = remember { PermissionUtils.hasMediaPermissions(context) }
    LaunchedEffect(hasPermission) {
        viewModel.onPermissionChanged(hasPermission)
    }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text("Delete Selected Files?") },
            text = { Text("Are you sure you want to delete ${uiState.selectedItemKeys.size} selected file(s)? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmationDialog = false
                        viewModel.deleteSelectedItems()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${uiState.selectedItemKeys.size} selected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close Selection Mode"
                            )
                        }
                    },
                    actions = {
                        val isAllSelected = uiState.selectedItemKeys.size == uiState.filteredMediaItems.size && uiState.filteredMediaItems.isNotEmpty()
                        IconButton(onClick = {
                            if (isAllSelected) {
                                viewModel.deselectAll()
                            } else {
                                viewModel.selectAll()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = if (isAllSelected) "Deselect All" else "Select All",
                                tint = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirmationDialog = true },
                            enabled = uiState.selectedItemKeys.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete Selected",
                                tint = if (uiState.selectedItemKeys.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            } else {
                Crossfade(
                    targetState = isSearchExpanded,
                    label = "TopBarSearchTransition"
                ) { expanded ->
                    if (expanded) {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = {
                                    isSearchExpanded = false
                                    viewModel.setSearchQuery("")
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Close Search"
                                    )
                                }
                            },
                            title = {
                                TextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { query -> viewModel.setSearchQuery(query) },
                                    placeholder = {
                                        Text(
                                            text = "Search title, artist, album...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                )
                            },
                            actions = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        viewModel.setSearchQuery("")
                                    }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = "Clear Search"
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.toggleLayoutMode() }) {
                                    Icon(
                                        imageVector = if (uiState.isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                        contentDescription = if (uiState.isGridView) "Switch to List View" else "Switch to Grid View"
                                    )
                                }
                                IconButton(onClick = { viewModel.openSettings() }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = "Settings"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    } else {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.PermMedia,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(if (isDeviceLandscape) 22.dp else 28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(if (isDeviceLandscape) 8.dp else 12.dp))
                                    Column {
                                        Text(
                                            text = "Media Studio",
                                            style = if (isDeviceLandscape) MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ) else MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        if (!isDeviceLandscape) {
                                            Text(
                                                text = "Local Gallery & Player",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            },
                            actions = {
                                IconButton(onClick = { isSearchExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = "Search"
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleLayoutMode() }) {
                                    Icon(
                                        imageVector = if (uiState.isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                        contentDescription = if (uiState.isGridView) "Switch to List View" else "Switch to Grid View"
                                    )
                                }
                                IconButton(onClick = { viewModel.openSettings() }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = "Settings"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    }
                }
            }
        },
        bottomBar = {
            if ((uiState.permissionGranted || !uiState.isPermissionChecked) && !uiState.isSelectionMode && isMiniPlayerActive) {
                val bottomBarHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                            )
                        )
                        .graphicsLayer {
                            translationY = playerProgress * bottomBarHeightPx
                        }
                ) {
                    GalleryMiniPlayer(
                        playerViewModel = playerViewModel,
                        onExpand = { currentItem ->
                            viewModel.selectMediaItem(currentItem)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
            if (uiState.isPermissionChecked && !uiState.permissionGranted) {
                val scrollState = rememberScrollState()
                val pullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = {
                        if (scrollState.value == 0) {
                            viewModel.refresh()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    state = pullToRefreshState,
                    indicator = {
                        if (uiState.isRefreshing || pullToRefreshState.distanceFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        PermissionRequiredCard(
                            onRequestPermission = {
                                permissionLauncher.launch(PermissionUtils.getRequiredPermissions())
                            }
                        )
                    }
                }
            } else {

                val gridState = rememberLazyGridState()
                val listState = rememberLazyListState()

                val pullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = {
                        val atTop = if (uiState.isGridView) {
                            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
                        } else {
                            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                        }
                        if (atTop) {
                            viewModel.refresh()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = pullToRefreshState,
                    indicator = {
                        if (uiState.isRefreshing || pullToRefreshState.distanceFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) {
                    val isInitialLoading = uiState.isLoading || (uiState.allMediaItems.isEmpty() && uiState.isRefreshing)
                    when {
                        isInitialLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Scanning MediaStore for local files...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        uiState.errorMessage != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                ErrorCard(
                                    message = uiState.errorMessage ?: "Unknown error",
                                    onRetry = { viewModel.loadMedia() }
                                )
                            }
                        }

                        uiState.filteredMediaItems.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                EmptyMediaCard(
                                    selectedFilter = uiState.selectedFilter,
                                    searchQuery = uiState.searchQuery
                                )
                            }
                        }

                        else -> {
                            val showMediaTypeChip = uiState.selectedFilter == MediaFilter.ALL
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Found ${uiState.filteredMediaItems.size} items",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (uiState.isGridView) "Grid View" else "List View",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val onRowItemClick: (MediaItem) -> Unit = remember(uiState.isSelectionMode, onItemClick) {
                                    { item ->
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleItemSelection(item)
                                        } else {
                                            onItemClick(item)
                                        }
                                    }
                                }

                                val onRowItemLongClick: (MediaItem) -> Unit = remember(uiState.isSelectionMode) {
                                    { item ->
                                        if (!uiState.isSelectionMode) {
                                            viewModel.enterSelectionMode(item)
                                        } else {
                                            viewModel.toggleItemSelection(item)
                                        }
                                    }
                                }

                                val onRowPlayClick: (MediaItem, Boolean) -> Unit = remember(onItemClick) {
                                    { item, isCurrent ->
                                        playerViewModel.initializePlayer(context)
                                        if (item.mediaType == MediaType.AUDIO) {
                                            if (isCurrent) {
                                                playerViewModel.play()
                                            } else {
                                                playerViewModel.playMedia(item, viewModel.uiState.value.filteredMediaItems)
                                            }
                                        } else {
                                            onItemClick(item)
                                        }
                                    }
                                }

                                val onRowPauseClick: () -> Unit = remember {
                                    { playerViewModel.pause() }
                                }

                                @OptIn(ExperimentalFoundationApi::class)
                                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                                    if (uiState.isGridView) {
                                        LazyVerticalGrid(
                                            state = gridState,
                                            columns = GridCells.Adaptive(minSize = 150.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(bottom = if (isDeviceLandscape) 64.dp else 80.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(
                                                items = uiState.filteredMediaItems,
                                                key = { if (it.mediaType == MediaType.VIDEO) it.id else -it.id - 1L },
                                                contentType = { it.mediaType }
                                            ) { item ->
                                                val isSelected = uiState.isSelectionMode && uiState.isItemSelected(item)
                                                val isCurrentItem = activeMediaItemId == item.id
                                                val isItemPlaying = isCurrentItem && isPlaybackActive

                                                MediaItemGridCard(
                                                    item = item,
                                                    isSelectionMode = uiState.isSelectionMode,
                                                    isSelected = isSelected,
                                                    isPlaying = isItemPlaying,
                                                    isCurrentItem = isCurrentItem,
                                                    showMediaTypeChip = showMediaTypeChip,
                                                    onItemClick = remember(item, onRowItemClick) { { onRowItemClick(item) } },
                                                    onItemLongClick = remember(item, onRowItemLongClick) { { onRowItemLongClick(item) } },
                                                    onPlayClick = remember(item, isCurrentItem, onRowPlayClick) { { onRowPlayClick(item, isCurrentItem) } },
                                                    onPauseClick = onRowPauseClick
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            state = listState,
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(bottom = if (isDeviceLandscape) 64.dp else 80.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(
                                                items = uiState.filteredMediaItems,
                                                key = { if (it.mediaType == MediaType.VIDEO) it.id else -it.id - 1L },
                                                contentType = { it.mediaType }
                                            ) { item ->
                                                val isSelected = uiState.isSelectionMode && uiState.isItemSelected(item)
                                                val isCurrentItem = activeMediaItemId == item.id
                                                val isItemPlaying = isCurrentItem && isPlaybackActive

                                                MediaItemRow(
                                                    item = item,
                                                    isSelectionMode = uiState.isSelectionMode,
                                                    isSelected = isSelected,
                                                    isPlaying = isItemPlaying,
                                                    isCurrentItem = isCurrentItem,
                                                    showMediaTypeChip = showMediaTypeChip,
                                                    onItemClick = remember(item, onRowItemClick) { { onRowItemClick(item) } },
                                                    onItemLongClick = remember(item, onRowItemLongClick) { { onRowItemLongClick(item) } },
                                                    onPlayClick = remember(item, isCurrentItem, onRowPlayClick) { { onRowPlayClick(item, isCurrentItem) } },
                                                    onPauseClick = onRowPauseClick
                                                )
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

            if ((uiState.permissionGranted || !uiState.isPermissionChecked) && !uiState.isSelectionMode) {
                val targetPillBottom = if (isMiniPlayerActive) {
                    innerPadding.calculateBottomPadding() + if (isDeviceLandscape) 6.dp else 10.dp
                } else {
                    innerPadding.calculateBottomPadding() + if (isDeviceLandscape) 12.dp else 20.dp
                }
                val animatedPillBottom by animateDpAsState(
                    targetValue = targetPillBottom,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "PillBottomAnimation"
                )

                FloatingMediaFilterBar(
                    selectedFilter = uiState.selectedFilter,
                    onFilterSelected = { filter -> viewModel.setFilter(filter) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = animatedPillBottom)
                )
            }
        }
    }
}

@Composable
fun FloatingMediaFilterBar(
    selectedFilter: MediaFilter,
    onFilterSelected: (MediaFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val pillBackgroundColor = if (isDarkTheme) Color(0xFF131315) else Color.White
    val borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.18f)

    Surface(
        shape = CircleShape,
        color = pillBackgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingFilterItem(
                selected = selectedFilter == MediaFilter.ALL,
                onClick = { onFilterSelected(MediaFilter.ALL) },
                icon = Icons.Rounded.PermMedia,
                label = "All",
                contentDescription = "All Media",
                isDarkTheme = isDarkTheme
            )
            FloatingFilterItem(
                selected = selectedFilter == MediaFilter.VIDEO,
                onClick = { onFilterSelected(MediaFilter.VIDEO) },
                icon = Icons.Rounded.VideoLibrary,
                label = "Videos",
                contentDescription = "Videos",
                isDarkTheme = isDarkTheme
            )
            FloatingFilterItem(
                selected = selectedFilter == MediaFilter.AUDIO,
                onClick = { onFilterSelected(MediaFilter.AUDIO) },
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                label = "Audio",
                contentDescription = "Audio",
                isDarkTheme = isDarkTheme
            )
        }
    }
}

@Composable
private fun FloatingFilterItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    contentDescription: String,
    isDarkTheme: Boolean = true
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "FloatingFilterBg"
    )
    val unselectedColor = if (isDarkTheme) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.7f)
    val contentTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else unselectedColor,
        animationSpec = tween(durationMillis = 150),
        label = "FloatingFilterTint"
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentTint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = contentTint,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaItemRow(
    item: MediaItem,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isPlaying: Boolean = false,
    isCurrentItem: Boolean = false,
    showMediaTypeChip: Boolean = true,
    onItemClick: () -> Unit = {},
    onItemLongClick: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    onPauseClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val cardBorder = remember(isCurrentItem, isPlaying, isSelected, primaryColor, outlineColor, tertiaryColor) {
        when {
            isCurrentItem && isPlaying -> BorderStroke(2.dp, primaryColor)
            isCurrentItem -> BorderStroke(1.dp, outlineColor)
            isSelected -> BorderStroke(2.dp, tertiaryColor)
            else -> null
        }
    }

    val metaText = remember(item.id, item.formattedDuration, item.formattedSize, item.resolution) {
        "${item.formattedDuration} • ${item.formattedSize}" + if (item.resolution != null) " • ${item.resolution}" else ""
    }

    val subtitle = remember(item.id, item.artist, item.album) {
        item.getFormattedSubtitle()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                isCurrentItem && isPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                isCurrentItem -> MaterialTheme.colorScheme.surfaceContainerHighest
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onItemClick() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (item.mediaType == MediaType.VIDEO)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.tertiaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Rounded.Movie else Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = if (item.mediaType == MediaType.VIDEO)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(32.dp)
                )

                val imageModel = remember(item.id, item.mediaType, item.thumbnailUri, item.contentUri, item.durationMs) {
                    if (item.mediaType == MediaType.VIDEO) {
                        val videoUri = item.thumbnailUri ?: item.contentUri
                        if (videoUri != null && videoUri != Uri.EMPTY) VideoThumbnailData(videoUri, item.durationMs) else null
                    } else {
                        item.thumbnailUri
                    }
                }

                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (isCurrentItem && isPlaying)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                if (isCurrentItem && isPlaying) onPauseClick() else onPlayClick()
                            }
                    ) {
                        Icon(
                            imageVector = if (isCurrentItem && isPlaying)
                                Icons.Rounded.Pause
                            else
                                Icons.Rounded.PlayArrow,
                            contentDescription = if (isCurrentItem && isPlaying) "Pause" else "Play",
                            tint = if (isCurrentItem && isPlaying)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (isCurrentItem && isPlaying)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier
                    .wrapContentWidth(Alignment.End)
                    .align(Alignment.CenterVertically),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCurrentItem) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        if (isPlaying) {
                            AnimatedWaveformIcon(
                                isAnimating = true,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Pause,
                                contentDescription = "Paused",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else if (showMediaTypeChip) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = if (item.mediaType == MediaType.VIDEO) "VIDEO" else "AUDIO",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaItemGridCard(
    item: MediaItem,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isPlaying: Boolean = false,
    isCurrentItem: Boolean = false,
    showMediaTypeChip: Boolean = true,
    onItemClick: () -> Unit = {},
    onItemLongClick: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    onPauseClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val cardBorder = remember(isCurrentItem, isPlaying, isSelected, primaryColor, outlineColor, tertiaryColor) {
        when {
            isCurrentItem && isPlaying -> BorderStroke(2.dp, primaryColor)
            isCurrentItem -> BorderStroke(1.dp, outlineColor)
            isSelected -> BorderStroke(2.dp, tertiaryColor)
            else -> null
        }
    }

    val subtitle = remember(item.id, item.artist, item.album) {
        item.getFormattedSubtitle()
    }

    val formattedSize = remember(item.id, item.sizeBytes) {
        item.getFormattedSize()
    }

    val formattedDuration = remember(item.id, item.durationMs) {
        item.getFormattedDuration()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                isCurrentItem && isPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                isCurrentItem -> MaterialTheme.colorScheme.surfaceContainerHighest
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        if (item.mediaType == MediaType.VIDEO)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.tertiaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Rounded.Movie else Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = if (item.mediaType == MediaType.VIDEO)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(36.dp)
                )

                val imageModel = remember(item.id, item.mediaType, item.thumbnailUri, item.contentUri, item.durationMs) {
                    if (item.mediaType == MediaType.VIDEO) {
                        val videoUri = item.thumbnailUri ?: item.contentUri
                        if (videoUri != null && videoUri != Uri.EMPTY) VideoThumbnailData(videoUri, item.durationMs) else null
                    } else {
                        item.thumbnailUri
                    }
                }

                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                )

                Surface(
                    shape = CircleShape,
                    color = if (isCurrentItem && isPlaying)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                if (isCurrentItem && isPlaying) onPauseClick() else onPlayClick()
                            }
                    ) {
                        Icon(
                            imageVector = if (isCurrentItem && isPlaying)
                                Icons.Rounded.Pause
                            else
                                Icons.Rounded.PlayArrow,
                            contentDescription = if (isCurrentItem && isPlaying) "Pause" else "Play",
                            tint = if (isCurrentItem && isPlaying)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCurrentItem) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                        ) {
                            if (isPlaying) {
                                AnimatedWaveformIcon(
                                    isAnimating = true,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = "Paused",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else if (showMediaTypeChip) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = if (item.mediaType == MediaType.VIDEO) "VIDEO" else "AUDIO",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onItemClick() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isCurrentItem && isPlaying)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val tag = if (item.mediaType == MediaType.AUDIO) {
                        ""
                    } else {
                        item.resolution ?: item.cleanArtist ?: item.mimeType.substringAfter('/')
                    }
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MediaDetailPlaceholder(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No Media Selected",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select a video or audio file from the gallery list on the left to start playback and inspect media details.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(320.dp)
            )
        }
    }
}

@Composable
fun PermissionRequiredCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Media Access Required",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Media Studio needs permission to access your audio and video files so you can discover, inspect, and edit local media.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Grant Permission")
            }
        }
    }
}

@Composable
fun EmptyMediaCard(
    selectedFilter: MediaFilter,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (searchQuery.isNotEmpty())
                    "No media matching \"$searchQuery\""
                else
                    "No ${selectedFilter.name.lowercase().replaceFirstChar { it.uppercase() }} Files Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (searchQuery.isNotEmpty())
                    "Try adjusting your search query or selecting a different media filter."
                else
                    "No local media matching the selected filter was discovered in device storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error Loading Media",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MediaItemRowPreview() {
    MultimediaStudioTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaItemRow(
                item = MediaItem(
                    id = 1L,
                    title = "Sample Video Clip.mp4",
                    contentUri = Uri.EMPTY,
                    durationMs = 125000L,
                    sizeBytes = 25000000L,
                    mimeType = "video/mp4",
                    dateModified = 1700000000L,
                    thumbnailUri = null,
                    mediaType = MediaType.VIDEO,
                    resolution = "1920x1080"
                )
            )
            MediaItemRow(
                item = MediaItem(
                    id = 2L,
                    title = "Acoustic Song.mp3",
                    contentUri = Uri.EMPTY,
                    durationMs = 210000L,
                    sizeBytes = 8500000L,
                    mimeType = "audio/mpeg",
                    dateModified = 1700000050L,
                    thumbnailUri = null,
                    mediaType = MediaType.AUDIO,
                    artist = "Artist Name",
                    album = "Album Title"
                )
            )
        }
    }
}
