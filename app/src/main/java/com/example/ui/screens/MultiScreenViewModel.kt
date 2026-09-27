package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.data.model.IPTVItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MultiScreenLayout {
    SELECT_TEMPLATE,
    DUAL_SIDE_BY_SIDE,
    DUAL_TOP_BOTTOM,
    QUAD_GRID
}

class MultiScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentLayout = MutableStateFlow(MultiScreenLayout.SELECT_TEMPLATE)
    val currentLayout: StateFlow<MultiScreenLayout> = _currentLayout.asStateFlow()

    private val _slotItems = MutableStateFlow<List<IPTVItem?>>(listOf(null, null, null, null))
    val slotItems: StateFlow<List<IPTVItem?>> = _slotItems.asStateFlow()

    private val _focusedSlotIndex = MutableStateFlow(0)
    val focusedSlotIndex: StateFlow<Int> = _focusedSlotIndex.asStateFlow()

    private val _activeSelectorSlot = MutableStateFlow<Int?>(null)
    val activeSelectorSlot: StateFlow<Int?> = _activeSelectorSlot.asStateFlow()

    fun selectLayout(layout: MultiScreenLayout) {
        _currentLayout.value = layout
        _slotItems.value = listOf(null, null, null, null)
        _focusedSlotIndex.value = 0
    }

    fun setChannelForSlot(slotIndex: Int, item: IPTVItem) {
        val newList = _slotItems.value.toMutableList()
        if (slotIndex in newList.indices) {
            newList[slotIndex] = item
            _slotItems.value = newList
            _focusedSlotIndex.value = slotIndex
        }
        _activeSelectorSlot.value = null
    }

    fun removeChannelFromSlot(slotIndex: Int) {
        val newList = _slotItems.value.toMutableList()
        if (slotIndex in newList.indices) {
            newList[slotIndex] = null
            _slotItems.value = newList
        }
    }

    fun setAudioFocus(slotIndex: Int) {
        if (slotIndex in 0..3) {
            _focusedSlotIndex.value = slotIndex
        }
    }

    fun openChannelSelector(slotIndex: Int) {
        _activeSelectorSlot.value = slotIndex
    }

    fun closeChannelSelector() {
        _activeSelectorSlot.value = null
    }

    fun resetToTemplateSelection() {
        _currentLayout.value = MultiScreenLayout.SELECT_TEMPLATE
        _slotItems.value = listOf(null, null, null, null)
        _focusedSlotIndex.value = 0
    }
}
