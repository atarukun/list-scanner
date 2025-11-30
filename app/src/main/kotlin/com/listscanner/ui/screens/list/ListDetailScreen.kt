package com.listscanner.ui.screens.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.listscanner.data.entity.Item
import com.listscanner.ui.gesture.rememberDirectionalSwipeState
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListDetailScreen(
    viewModel: ListDetailViewModel,
    onBackClick: () -> Unit,
    onListDeleted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val hideChecked by viewModel.hideChecked.collectAsState()
    val editingItemId by viewModel.editingItemId.collectAsState()
    val isAddingItem by viewModel.isAddingItem.collectAsState()
    val itemPendingDeletion by viewModel.itemPendingDeletion.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isRenaming by viewModel.isRenaming.collectAsState()
    val isDeletingList by viewModel.isDeletingList.collectAsState()
    val listDeleted by viewModel.listDeleted.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearErrorMessage()
        }
    }

    // Navigate when list is deleted
    LaunchedEffect(listDeleted) {
        if (listDeleted) {
            onListDeleted()
        }
    }

    // Delete list confirmation dialog
    if (isDeletingList && uiState is ListDetailViewModel.UiState.Success) {
        val state = uiState as ListDetailViewModel.UiState.Success
        DeleteListConfirmationDialog(
            listName = state.list.name,
            onConfirm = { viewModel.confirmDeleteList() },
            onDismiss = { viewModel.cancelDeleteList() }
        )
    }

    // Delete item confirmation dialog
    itemPendingDeletion?.let { item ->
        val displayText = if (item.text.length > 50) {
            "${item.text.take(25)}...${item.text.takeLast(22)}"
        } else {
            item.text
        }
        DeleteConfirmationDialog(
            itemText = displayText,
            onConfirm = { viewModel.confirmDeleteItem() },
            onDismiss = { viewModel.cancelDeleteItem() }
        )
    }

    // Rename list dialog
    if (isRenaming && uiState is ListDetailViewModel.UiState.Success) {
        val state = uiState as ListDetailViewModel.UiState.Success
        RenameListDialog(
            currentName = state.list.name,
            onConfirm = { newName -> viewModel.renameList(newName) },
            onDismiss = { viewModel.cancelRenaming() }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            enabled = uiState is ListDetailViewModel.UiState.Success
                        ) { viewModel.startRenaming() }
                    ) {
                        Text(
                            text = when (val state = uiState) {
                                is ListDetailViewModel.UiState.Success -> state.list.name
                                else -> "List Detail"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState is ListDetailViewModel.UiState.Success) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename list",
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleHideChecked() }) {
                        Icon(
                            imageVector = if (hideChecked) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (hideChecked) "Show all items" else "Hide checked items",
                            tint = if (hideChecked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    // Overflow menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete list") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.requestDeleteList()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            val showFab = uiState is ListDetailViewModel.UiState.Success && !isAddingItem
            if (showFab) {
                FloatingActionButton(
                    onClick = { viewModel.startAddingItem() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add item"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = uiState) {
                    is ListDetailViewModel.UiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is ListDetailViewModel.UiState.Success -> {
                        when {
                            state.items.isEmpty() && hideChecked && state.totalItemCount > 0 -> {
                                AllCheckedContent(
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            state.items.isEmpty() -> {
                                EmptyListContent(
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            else -> {
                                val reorderState = rememberReorderableLazyListState(
                                    onMove = { from, to ->
                                        viewModel.onItemMove(from.index, to.index)
                                    },
                                    onDragEnd = { _, _ ->
                                        viewModel.onDragEnd()
                                    }
                                )

                                LazyColumn(
                                    state = reorderState.listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .reorderable(reorderState),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(
                                        items = state.items,
                                        key = { item -> item.id }
                                    ) { item ->
                                        ReorderableItem(
                                            reorderableState = reorderState,
                                            key = item.id
                                        ) { isDragging ->
                                            ListItemCard(
                                                item = item,
                                                onCheckedChange = { viewModel.toggleItemChecked(item.id) },
                                                isEditing = editingItemId == item.id,
                                                onStartEdit = { viewModel.startEditing(item.id) },
                                                onSaveEdit = { text -> viewModel.updateItemText(item.id, text) },
                                                onCancelEdit = { viewModel.cancelEditing() },
                                                onRequestDelete = { viewModel.requestDeleteItem(item) },
                                                itemPendingDeletion = itemPendingDeletion,
                                                isDragging = isDragging,
                                                reorderState = reorderState,
                                                modifier = Modifier.animateItemPlacement()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is ListDetailViewModel.UiState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (isAddingItem) {
                AddItemTextField(
                    onSave = { text -> viewModel.addItem(text) },
                    onCancel = { viewModel.cancelAddingItem() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
private fun ListItemCard(
    item: Item,
    onCheckedChange: (Boolean) -> Unit,
    isEditing: Boolean,
    onStartEdit: () -> Unit,
    onSaveEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    itemPendingDeletion: Item?,
    isDragging: Boolean = false,
    reorderState: ReorderableLazyListState? = null,
    modifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        label = "dragElevation"
    )
    val textColor by animateColorAsState(
        targetValue = if (item.isChecked) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "textColorAnimation"
    )

    var editText by remember(item.id, isEditing) { mutableStateOf(item.text) }
    val focusRequester = remember { FocusRequester() }
    var showError by remember { mutableStateOf(false) }
    var hasFocusedOnce by remember(item.id, isEditing) { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onRequestDelete()
                false // Don't complete the dismiss, show dialog first
            } else {
                false
            }
        }
    )

    // Reset swipe state when dialog is dismissed
    LaunchedEffect(itemPendingDeletion) {
        if (itemPendingDeletion == null) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    // Get modifier that resets swipe when vertical scroll is detected
    val directionalModifier = rememberDirectionalSwipeState(dismissState, density)

    Box(
        modifier = modifier.then(directionalModifier)
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            },
            enableDismissFromEndToStart = true,
            enableDismissFromStartToEnd = false
        ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation)
                .combinedClickable(
                    onClick = { if (!isEditing) onStartEdit() },
                    onLongClick = { showContextMenu = true }
                )
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag handle - only this triggers reorder
                    if (reorderState != null) {
                        Box(
                            modifier = Modifier
                                .detectReorderAfterLongPress(reorderState)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = { /* consume click to prevent Card's onClick */ }
                                )
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Checkbox(
                        checked = item.isChecked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.size(48.dp)
                    )

                    if (isEditing) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp, end = 16.dp)
                        ) {
                            TextField(
                                value = editText,
                                onValueChange = { newText ->
                                    if (newText.length <= 200) {
                                        editText = newText
                                        showError = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.hasFocus) {
                                            hasFocusedOnce = true
                                        } else if (hasFocusedOnce && isEditing) {
                                            if (editText.trim().isNotEmpty()) {
                                                onSaveEdit(editText)
                                            } else {
                                                showError = true
                                            }
                                        }
                                    },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (editText.trim().isNotEmpty()) {
                                            onSaveEdit(editText)
                                        } else {
                                            showError = true
                                        }
                                    }
                                ),
                                singleLine = false,
                                isError = showError
                            )

                            if (editText.length > 180) {
                                Text(
                                    text = "${editText.length}/200",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (editText.length >= 195) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            if (showError) {
                                Text(
                                    text = "Item text cannot be empty",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        Text(
                            text = item.text,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp, end = 16.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = textColor,
                            maxLines = Int.MAX_VALUE,
                            overflow = TextOverflow.Visible
                        )
                    }
                }

                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showContextMenu = false
                            onRequestDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun EmptyListContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.List,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "No items in this list",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun AllCheckedContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "All items checked!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun AddItemTextField(
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var showError by remember { mutableStateOf(false) }
    var hasFocusedOnce by remember { mutableStateOf(false) }
    var hasSaved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { newText ->
                    if (newText.length <= 200) {
                        text = newText
                        showError = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.hasFocus) {
                            hasFocusedOnce = true
                        } else if (hasFocusedOnce && !hasSaved) {
                            if (text.trim().isNotEmpty()) {
                                hasSaved = true
                                onSave(text)
                            } else {
                                onCancel()
                            }
                        }
                    },
                placeholder = { Text("New item...") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (text.trim().isNotEmpty() && !hasSaved) {
                            hasSaved = true
                            onSave(text)
                        } else if (text.trim().isEmpty()) {
                            showError = true
                        }
                    }
                ),
                singleLine = false,
                isError = showError
            )

            if (text.length > 180) {
                Text(
                    text = "${text.length}/200",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (text.length >= 195) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (showError) {
                Text(
                    text = "Item text cannot be empty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    itemText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete item?") },
        text = { Text("Delete '$itemText'? This cannot be undone") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RenameListDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editedName by remember { mutableStateOf(currentName) }
    val isValidName = editedName.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename List") },
        text = {
            Column {
                TextField(
                    value = editedName,
                    onValueChange = { if (it.length <= 50) editedName = it },
                    singleLine = true,
                    placeholder = { Text("List name") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (editedName.length > 40) {
                    Text(
                        text = "${editedName.length}/50",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (editedName.length >= 48)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(editedName) },
                enabled = isValidName
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteListConfirmationDialog(
    listName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete list?") },
        text = { Text("Delete '$listName'? This will also delete all items and cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
