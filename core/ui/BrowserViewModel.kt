package com.astralx.browser.presentation.browser

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astralx.browser.domain.model.Tab
import com.astralx.browser.domain.repository.TabRepository
import com.astralx.browser.domain.repository.HistoryRepository
import com.astralx.browser.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the modern browser fragment
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {
    
    private val _currentUrl = MutableLiveData<String>()
    val currentUrl: LiveData<String> = _currentUrl
    
    private val _isPrivacyMode = MutableLiveData<Boolean>(false)
    val isPrivacyMode: LiveData<Boolean> = _isPrivacyMode
    
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs
    
    private val _currentTabId = MutableStateFlow<String?>(null)
    val currentTabId: StateFlow<String?> = _currentTabId
    
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _progress = MutableLiveData<Int>(0)
    val progress: LiveData<Int> = _progress
    
    init {
        loadTabs()
    }
    
    /**
     * Load all tabs
     */
    private fun loadTabs() {
        viewModelScope.launch {
            try {
                tabRepository.getAllTabs().collect { tabList ->
                    _tabs.value = tabList
                    
                    // Set current tab if not set
                    if (_currentTabId.value == null && tabList.isNotEmpty()) {
                        _currentTabId.value = tabList.first().id
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading tabs")
            }
        }
    }
    
    /**
     * Create a new tab
     */
    fun createNewTab(url: String = "https://www.google.com") {
        viewModelScope.launch {
            try {
                val newTab = Tab(
                    url = url,
                    title = "New Tab",
                    isPrivate = _isPrivacyMode.value ?: false
                )
                
                val tabId = tabRepository.createTab(newTab)
                _currentTabId.value = tabId
                _currentUrl.value = url
                
                Timber.d("Created new tab: $tabId")
            } catch (e: Exception) {
                Timber.e(e, "Error creating new tab")
            }
        }
    }
    
    /**
     * Switch to a different tab
     */
    fun switchToTab(tabId: String) {
        viewModelScope.launch {
            try {
                val tab = tabRepository.getTab(tabId)
                if (tab != null) {
                    _currentTabId.value = tabId
                    _currentUrl.value = tab.url
                    _isPrivacyMode.value = tab.isPrivate
                    
                    Timber.d("Switched to tab: $tabId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error switching tab")
            }
        }
    }
    
    /**
     * Close a tab
     */
    fun closeTab(tabId: String) {
        viewModelScope.launch {
            try {
                tabRepository.deleteTab(tabId)
                
                // If closing current tab, switch to another
                if (_currentTabId.value == tabId) {
                    val remainingTabs = _tabs.value.filter { it.id != tabId }
                    if (remainingTabs.isNotEmpty()) {
                        switchToTab(remainingTabs.first().id)
                    } else {
                        // Create new tab if no tabs left
                        createNewTab()
                    }
                }
                
                Timber.d("Closed tab: $tabId")
            } catch (e: Exception) {
                Timber.e(e, "Error closing tab")
            }
        }
    }
    
    /**
     * Update current URL
     */
    fun updateUrl(url: String) {
        _currentUrl.value = url
        
        // Update tab URL
        _currentTabId.value?.let { tabId ->
            viewModelScope.launch {
                try {
                    tabRepository.updateTabUrl(tabId, url)
                } catch (e: Exception) {
                    Timber.e(e, "Error updating tab URL")
                }
            }
        }
        
        // Add to history if not in privacy mode
        if (_isPrivacyMode.value != true) {
            addToHistory(url)
        }
    }
    
    /**
     * Update page title
     */
    fun updateTitle(title: String) {
        _currentTabId.value?.let { tabId ->
            viewModelScope.launch {
                try {
                    tabRepository.updateTabTitle(tabId, title)
                } catch (e: Exception) {
                    Timber.e(e, "Error updating tab title")
                }
            }
        }
    }
    
    /**
     * Toggle privacy mode
     */
    fun togglePrivacyMode() {
        val newMode = !(_isPrivacyMode.value ?: false)
        _isPrivacyMode.value = newMode
        
        // Update current tab privacy mode
        _currentTabId.value?.let { tabId ->
            viewModelScope.launch {
                try {
                    tabRepository.updateTabPrivacy(tabId, newMode)
                } catch (e: Exception) {
                    Timber.e(e, "Error updating tab privacy")
                }
            }
        }
        
        Timber.d("Privacy mode: $newMode")
    }
    
    /**
     * Add URL to history
     */
    private fun addToHistory(url: String) {
        viewModelScope.launch {
            try {
                historyRepository.addToHistory(url)
            } catch (e: Exception) {
                Timber.e(e, "Error adding to history")
            }
        }
    }
    
    /**
     * Check if URL is bookmarked
     */
    fun isBookmarked(url: String): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        viewModelScope.launch {
            try {
                result.value = bookmarkRepository.isBookmarked(url)
            } catch (e: Exception) {
                Timber.e(e, "Error checking bookmark status")
                result.value = false
            }
        }
        return result
    }
    
    /**
     * Toggle bookmark for current URL
     */
    fun toggleBookmark() {
        _currentUrl.value?.let { url ->
            viewModelScope.launch {
                try {
                    if (bookmarkRepository.isBookmarked(url)) {
                        bookmarkRepository.removeBookmark(url)
                    } else {
                        bookmarkRepository.addBookmark(
                            url = url,
                            title = _tabs.value.find { it.id == _currentTabId.value }?.title ?: "Bookmark"
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error toggling bookmark")
                }
            }
        }
    }
    
    /**
     * Update loading state
     */
    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }
    
    /**
     * Update progress
     */
    fun setProgress(progress: Int) {
        _progress.value = progress
    }
}