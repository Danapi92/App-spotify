package com.example.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.model.AudioQuality
import com.example.data.model.MusicTrack
import com.example.data.model.UiState
import com.example.data.remote.NetworkModule
import com.example.data.repository.DownloadStatus
import com.example.data.repository.FirestoreUserRepository
import com.example.data.repository.MusicCatalogRepository
import com.example.data.repository.OfflineDownloadManager
import com.example.data.repository.SettingsRepository
import com.example.player.PlaybackController
import com.example.ui.auth.signOutUser
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.MiniPlayerBar
import com.example.ui.localization.LocalStrings
import com.example.ui.screens.EqualizerSheet
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.PlaylistDetailViewModel
import com.example.ui.viewmodel.SearchViewModel
import com.example.ui.viewmodel.StreamMainViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object SearchRoute
@Serializable object ExploreRoute
@Serializable object LibraryRoute
@Serializable object ProfileRoute
@Serializable object SettingsRoute
@Serializable object PlaylistDetailRoute

private data class BottomNavTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AuthenticatedStreamApp(
    currentUserId: String,
    settingsRepo: SettingsRepository
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val mainViewModel: StreamMainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                val userRepo = FirestoreUserRepository(db)
                val catalogRepo = MusicCatalogRepository(
                    audiusApi = NetworkModule.createAudiusService(app),
                    deezerApi = NetworkModule.createDeezerService(app),
                    lrclibApi = NetworkModule.createLrclibService(app),
                    db = db
                )
                val downloadManager = OfflineDownloadManager(app)
                lateinit var vmRef: StreamMainViewModel
                val playbackController = PlaybackController(app, downloadManager) { track ->
                    vmRef.onActiveTrackChanged(track)
                }
                vmRef = StreamMainViewModel(
                    currentUserId = currentUserId,
                    userRepo = userRepo,
                    catalogRepo = catalogRepo,
                    settingsRepo = settingsRepo,
                    downloadManager = downloadManager,
                    playbackController = playbackController
                )
                vmRef
            }
        }
    )

    val searchViewModel: SearchViewModel = viewModel(
        key = "search_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                SearchViewModel(
                    currentUserId = currentUserId,
                    catalogRepo = MusicCatalogRepository(
                        audiusApi = NetworkModule.createAudiusService(app),
                        deezerApi = NetworkModule.createDeezerService(app),
                        lrclibApi = NetworkModule.createLrclibService(app),
                        db = db
                    ),
                    userRepo = FirestoreUserRepository(db)
                )
            }
        }
    )

    val playlistDetailViewModel: PlaylistDetailViewModel = viewModel(
        key = "pl_detail_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                PlaylistDetailViewModel(
                    currentUserId = currentUserId,
                    catalogRepo = MusicCatalogRepository(
                        audiusApi = NetworkModule.createAudiusService(app),
                        deezerApi = NetworkModule.createDeezerService(app),
                        lrclibApi = NetworkModule.createLrclibService(app),
                        db = db
                    ),
                    userRepo = FirestoreUserRepository(db)
                )
            }
        }
    )

    LaunchedEffect(mainViewModel) {
        mainViewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val playback by mainViewModel.playbackState.collectAsStateWithLifecycle()
    val userProfileState by mainViewModel.userProfile.collectAsStateWithLifecycle()
    val homeCatalogState by mainViewModel.homeCatalog.collectAsStateWithLifecycle()
    val favoritesState by mainViewModel.favorites.collectAsStateWithLifecycle()
    val historyState by mainViewModel.history.collectAsStateWithLifecycle()
    val myPlaylistsState by mainViewModel.myPlaylists.collectAsStateWithLifecycle()
    val publicPlaylistsState by mainViewModel.publicPlaylists.collectAsStateWithLifecycle()
    val devicesState by mainViewModel.devices.collectAsStateWithLifecycle()
    val downloadedTracks by mainViewModel.downloadedTracks.collectAsStateWithLifecycle()
    val downloadStates by mainViewModel.downloadStates.collectAsStateWithLifecycle()
    val selectedGenre by mainViewModel.selectedGenre.collectAsStateWithLifecycle()
    val genreTracksState by mainViewModel.genreTracks.collectAsStateWithLifecycle()
    val historyRecommendations by mainViewModel.historyRecommendations.collectAsStateWithLifecycle()
    val activeLyrics by mainViewModel.activeLyrics.collectAsStateWithLifecycle()
    val lyricsLoading by mainViewModel.lyricsLoading.collectAsStateWithLifecycle()

    val favoriteIds = remember(favoritesState) {
        (favoritesState as? UiState.Success)?.data?.map { it.trackId }?.toSet() ?: emptySet()
    }
    val myPlaylists = (myPlaylistsState as? UiState.Success)?.data.orEmpty()

    var showNowPlaying by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var trackForPlaylistSheet by remember { mutableStateOf<MusicTrack?>(null) }

    val navTabs = listOf(
        BottomNavTab(HomeRoute, strings.navHome, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
        BottomNavTab(SearchRoute, strings.navSearch, Icons.Rounded.Search, Icons.Outlined.Search, "nav_search"),
        BottomNavTab(ExploreRoute, strings.navExplore, Icons.Rounded.Explore, Icons.Outlined.Explore, "nav_explore"),
        BottomNavTab(LibraryRoute, strings.navLibrary, Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic, "nav_library"),
        BottomNavTab(ProfileRoute, strings.navProfile, Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile")
    )

    val navigateTab: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail && !showNowPlaying) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        if (playback.currentTrack != null) {
                            MiniPlayerBar(
                                playback = playback,
                                isFavorite = playback.currentTrack?.id in favoriteIds,
                                onOpenNowPlaying = { showNowPlaying = true },
                                onTogglePlayPause = { mainViewModel.playbackController.togglePlayPause() },
                                onSkipNext = { mainViewModel.playbackController.skipNext() },
                                onToggleFavorite = {
                                    playback.currentTrack?.let { mainViewModel.toggleFavorite(it) }
                                }
                            )
                        }
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            navTabs.forEach { tab ->
                                val selected = currentDest?.hasRoute(tab.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && !showNowPlaying) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        navTabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute
                    ) {
                        composable<HomeRoute> {
                            HomeScreen(
                                userProfile = userProfileState,
                                homeCatalog = homeCatalogState,
                                historyState = historyState,
                                historyRecommendations = historyRecommendations,
                                selectedGenre = selectedGenre,
                                genreTracksState = genreTracksState,
                                favoriteIds = favoriteIds,
                                playback = playback,
                                onRetryCatalog = mainViewModel::refreshHomeCatalog,
                                onSelectGenre = mainViewModel::selectGenre,
                                onPlayTrack = mainViewModel::playTrack,
                                onOpenCollection = { col ->
                                    playlistDetailViewModel.loadRemoteCollection(col)
                                    navController.navigate(PlaylistDetailRoute)
                                },
                                onPlayCollection = mainViewModel::playPlaylistCollection,
                                onToggleFavorite = mainViewModel::toggleFavorite,
                                onAddToPlaylist = { trackForPlaylistSheet = it },
                                onAddToQueue = { mainViewModel.playbackController.addToQueue(it) },
                                onOpenSettings = { navController.navigate(SettingsRoute) }
                            )
                        }

                        composable<SearchRoute> {
                            val query by searchViewModel.query.collectAsStateWithLifecycle()
                            val filter by searchViewModel.selectedFilter.collectAsStateWithLifecycle()
                            val results by searchViewModel.resultsState.collectAsStateWithLifecycle()
                            val searchHistory by searchViewModel.searchHistory.collectAsStateWithLifecycle()
                            val pubPlaylists = (publicPlaylistsState as? UiState.Success)?.data.orEmpty()

                            SearchScreen(
                                query = query,
                                selectedFilter = filter,
                                resultsState = results,
                                searchHistoryState = searchHistory,
                                publicPlaylists = pubPlaylists,
                                favoriteIds = favoriteIds,
                                playback = playback,
                                onQueryChange = searchViewModel::onQueryChange,
                                onSearchSubmit = searchViewModel::onSearchSubmit,
                                onFilterChange = searchViewModel::setFilter,
                                onSelectHistory = searchViewModel::selectHistoryQuery,
                                onDeleteHistory = searchViewModel::deleteHistoryItem,
                                onClearHistory = searchViewModel::clearHistory,
                                onPlayTrack = mainViewModel::playTrack,
                                onOpenCollection = { col ->
                                    playlistDetailViewModel.loadRemoteCollection(col)
                                    navController.navigate(PlaylistDetailRoute)
                                },
                                onPlayCollection = { col -> mainViewModel.playPlaylistCollection(col, false) },
                                onOpenCloudPlaylist = { pl ->
                                    playlistDetailViewModel.observeCloudPlaylist(pl)
                                    navController.navigate(PlaylistDetailRoute)
                                },
                                onToggleFavorite = mainViewModel::toggleFavorite,
                                onAddToPlaylist = { trackForPlaylistSheet = it },
                                onAddToQueue = { mainViewModel.playbackController.addToQueue(it) }
                            )
                        }

                        composable<ExploreRoute> {
                            ExploreScreen(
                                selectedGenre = selectedGenre,
                                genreTracksState = genreTracksState,
                                publicPlaylistsState = publicPlaylistsState,
                                favoriteIds = favoriteIds,
                                playback = playback,
                                onSelectGenre = mainViewModel::selectGenre,
                                onPlayTrack = mainViewModel::playTrack,
                                onOpenCloudPlaylist = { pl ->
                                    playlistDetailViewModel.observeCloudPlaylist(pl)
                                    navController.navigate(PlaylistDetailRoute)
                                },
                                onToggleFavorite = mainViewModel::toggleFavorite,
                                onAddToPlaylist = { trackForPlaylistSheet = it },
                                onAddToQueue = { mainViewModel.playbackController.addToQueue(it) }
                            )
                        }

                        composable<LibraryRoute> {
                            LibraryScreen(
                                favoritesState = favoritesState,
                                playlistsState = myPlaylistsState,
                                downloadedTracks = downloadedTracks,
                                historyState = historyState,
                                favoriteIds = favoriteIds,
                                playback = playback,
                                onCreatePlaylistClick = { showCreatePlaylist = true },
                                onOpenCloudPlaylist = { pl ->
                                    playlistDetailViewModel.observeCloudPlaylist(pl)
                                    navController.navigate(PlaylistDetailRoute)
                                },
                                onPlayTrack = mainViewModel::playTrack,
                                onToggleFavorite = mainViewModel::toggleFavorite,
                                onAddToPlaylist = { trackForPlaylistSheet = it },
                                onAddToQueue = { mainViewModel.playbackController.addToQueue(it) },
                                onDeleteDownload = mainViewModel::deleteDownload
                            )
                        }

                        composable<ProfileRoute> {
                            val favCount = (favoritesState as? UiState.Success)?.data?.size ?: 0
                            ProfileScreen(
                                userProfileState = userProfileState,
                                devicesState = devicesState,
                                favoritesCount = favCount,
                                playlistsCount = myPlaylists.size,
                                downloadsCount = downloadedTracks.size,
                                onEditName = mainViewModel::updateDisplayName,
                                onPhotoSelected = mainViewModel::updateProfilePhoto,
                                onTogglePremium = mainViewModel::togglePremiumTier,
                                onRegisterDevice = mainViewModel::registerCurrentDevice,
                                onRemoveDevice = mainViewModel::removeDevice,
                                onOpenSettings = { navController.navigate(SettingsRoute) },
                                onOpenEqualizer = { showEqualizer = true },
                                onSignOut = {
                                    signOutUser(credentialManager, scope)
                                }
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                preferences = preferences,
                                onBack = { navController.popBackStack() },
                                onToggleDarkTheme = mainViewModel::setDarkTheme,
                                onSelectLanguage = mainViewModel::setLanguage,
                                onSelectQuality = mainViewModel::setAudioQuality,
                                onToggleNotifications = mainViewModel::setNotificationsEnabled,
                                onOpenEqualizer = { showEqualizer = true }
                            )
                        }

                        composable<PlaylistDetailRoute> {
                            val remoteCol by playlistDetailViewModel.remoteCollection.collectAsStateWithLifecycle()
                            val remoteTracks by playlistDetailViewModel.remoteTracks.collectAsStateWithLifecycle()
                            val cloudPl by playlistDetailViewModel.cloudPlaylist.collectAsStateWithLifecycle()
                            val cloudTracks by playlistDetailViewModel.cloudTracks.collectAsStateWithLifecycle()

                            PlaylistDetailScreen(
                                currentUserId = currentUserId,
                                remoteCollection = remoteCol,
                                remoteTracksState = remoteTracks,
                                cloudPlaylist = cloudPl,
                                cloudTracksState = cloudTracks,
                                favoriteIds = favoriteIds,
                                playback = playback,
                                onBack = { navController.popBackStack() },
                                onPlayTrack = mainViewModel::playTrack,
                                onToggleFavorite = mainViewModel::toggleFavorite,
                                onAddToPlaylist = { trackForPlaylistSheet = it },
                                onAddToQueue = { mainViewModel.playbackController.addToQueue(it) },
                                onToggleVisibility = { pl ->
                                    playlistDetailViewModel.toggleCloudVisibility(pl) { }
                                },
                                onRemoveCloudTrack = playlistDetailViewModel::removeCloudTrack,
                                onDeleteCloudPlaylist = { id ->
                                    playlistDetailViewModel.deleteCloudPlaylist(id) {
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }
                    }

                    if (useNavRail && !showNowPlaying && playback.currentTrack != null) {
                        MiniPlayerBar(
                            playback = playback,
                            isFavorite = playback.currentTrack?.id in favoriteIds,
                            onOpenNowPlaying = { showNowPlaying = true },
                            onTogglePlayPause = { mainViewModel.playbackController.togglePlayPause() },
                            onSkipNext = { mainViewModel.playbackController.skipNext() },
                            onToggleFavorite = {
                                playback.currentTrack?.let { mainViewModel.toggleFavorite(it) }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        )
                    }
                }
            }
        }

        // Fullscreen Now Playing Overlay
        AnimatedVisibility(
            visible = showNowPlaying && playback.currentTrack != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            val activeId = playback.currentTrack?.id.orEmpty()
            NowPlayingScreen(
                playback = playback,
                isFavorite = activeId in favoriteIds,
                downloadStatus = downloadStates[activeId] ?: DownloadStatus.NotDownloaded,
                lyrics = activeLyrics,
                lyricsLoading = lyricsLoading,
                onCollapse = { showNowPlaying = false },
                onTogglePlayPause = { mainViewModel.playbackController.togglePlayPause() },
                onSkipNext = { mainViewModel.playbackController.skipNext() },
                onSkipPrevious = { mainViewModel.playbackController.skipPrevious() },
                onSeekTo = { mainViewModel.playbackController.seekTo(it) },
                onToggleShuffle = { mainViewModel.playbackController.toggleShuffle() },
                onCycleRepeat = { mainViewModel.playbackController.cycleRepeatMode() },
                onToggleFavorite = {
                    playback.currentTrack?.let { mainViewModel.toggleFavorite(it) }
                },
                onVolumeChange = { mainViewModel.playbackController.setVolume(it) },
                onDownloadClick = mainViewModel::downloadTrack,
                onAddToPlaylist = { trackForPlaylistSheet = it },
                onOpenEqualizer = { showEqualizer = true },
                onCycleQuality = {
                    val next = when (playback.audioQuality) {
                        AudioQuality.AUTO -> AudioQuality.STANDARD
                        AudioQuality.STANDARD -> AudioQuality.HIGH
                        AudioQuality.HIGH -> AudioQuality.AUTO
                    }
                    mainViewModel.setAudioQuality(next)
                },
                onJumpQueue = { mainViewModel.playbackController.jumpToQueueIndex(it) },
                onRemoveFromQueue = { mainViewModel.playbackController.removeFromQueue(it) },
                onDismissError = { mainViewModel.playbackController.clearError() }
            )
        }
    }

    if (showEqualizer) {
        EqualizerSheet(
            state = playback.equalizer,
            onDismiss = { showEqualizer = false },
            onToggleEnabled = { mainViewModel.playbackController.setEqualizerEnabled(it) },
            onSelectPreset = { mainViewModel.playbackController.selectEqualizerPreset(it) },
            onBandChange = { band, level -> mainViewModel.playbackController.setEqualizerBand(band, level) }
        )
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylist = false },
            onCreate = mainViewModel::createPlaylist
        )
    }

    trackForPlaylistSheet?.let { track ->
        AddToPlaylistSheet(
            track = track,
            playlists = myPlaylists,
            onDismiss = { trackForPlaylistSheet = null },
            onSelectPlaylist = { pl -> mainViewModel.addTrackToPlaylist(pl, track) },
            onCreateNewPlaylist = { showCreatePlaylist = true }
        )
    }
}
