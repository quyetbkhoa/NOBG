package com.nobg.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val userSystemFilters by viewModel.userSystemFilters.collectAsState()
    val disabledFilters by viewModel.disabledFilters.collectAsState()
    val powerStateFilters by viewModel.powerStateFilters.collectAsState()
    val nobgStateFilters by viewModel.nobgStateFilters.collectAsState()
    val frozenShelfFilters by viewModel.frozenShelfFilters.collectAsState()
    val hiddenFilter by viewModel.hiddenFilter.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = PremiumDimens.ContentMaxWidth)
                .padding(horizontal = PremiumDimens.ScreenGutter)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bộ lọc",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Đóng")
                }
            }

            // SECTION 1: LOẠI ỨNG DỤNG (MULTI-CHOICE)
            FilterSectionTitle("Loại ứng dụng")
            MultiChoiceSegmentedRow(
                options = UserSystemFilterOption.values().toList(),
                selectedSet = userSystemFilters,
                onToggle = { opt ->
                    val current = viewModel.userSystemFilters.value
                    viewModel.userSystemFilters.value = if (opt in current) current - opt else current + opt
                },
                getLabel = { it.label }
            )

            // SECTION 2: TRẠNG THÁI VÔ HIỆU HÓA (MULTI-CHOICE)
            FilterSectionTitle("Trạng thái vô hiệu hóa")
            MultiChoiceSegmentedRow(
                options = DisabledFilterOption.values().toList(),
                selectedSet = disabledFilters,
                onToggle = { opt ->
                    val current = viewModel.disabledFilters.value
                    viewModel.disabledFilters.value = if (opt in current) current - opt else current + opt
                },
                getLabel = { it.label }
            )

            // SECTION 3: CHẾ ĐỘ PIN ANDROID (MULTI-CHOICE)
            FilterSectionTitle("Chế độ pin Android")
            MultiChoiceSegmentedRow(
                options = PowerStateFilterOption.values().toList(),
                selectedSet = powerStateFilters,
                onToggle = { opt ->
                    val current = viewModel.powerStateFilters.value
                    viewModel.powerStateFilters.value = if (opt in current) current - opt else current + opt
                },
                getLabel = { it.label }
            )

            // SECTION 4: TRẠNG THÁI NOBG (MULTI-CHOICE)
            FilterSectionTitle("Trạng thái NOBG")
            MultiChoiceSegmentedRow(
                options = NobgStateFilterOption.values().toList(),
                selectedSet = nobgStateFilters,
                onToggle = { opt ->
                    val current = viewModel.nobgStateFilters.value
                    viewModel.nobgStateFilters.value = if (opt in current) current - opt else current + opt
                },
                getLabel = { it.label }
            )

            // SECTION 5: KỆ ĐÓNG BẰNG (MULTI-CHOICE)
            FilterSectionTitle("Kệ Đóng Băng")
            MultiChoiceSegmentedRow(
                options = FrozenShelfFilterOption.values().toList(),
                selectedSet = frozenShelfFilters,
                onToggle = { opt ->
                    val current = viewModel.frozenShelfFilters.value
                    viewModel.frozenShelfFilters.value = if (opt in current) current - opt else current + opt
                },
                getLabel = { it.label }
            )

            // SECTION 6: APP ĐÃ ẨN (3-CHOICE)
            FilterSectionTitle("Ứng dụng ẩn")
            SingleChoiceRow(
                options = HiddenFilterOption.values().toList(),
                selected = hiddenFilter,
                onSelected = { viewModel.hiddenFilter.value = it },
                getLabel = { it.label }
            )

            // ACTION BUTTONS: RESET ALL & APPLY
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.clearAllFilters()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Xóa tất cả", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Áp dụng", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FilterSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun <T> MultiChoiceSegmentedRow(
    options: List<T>,
    selectedSet: Set<T>,
    onToggle: (T) -> Unit,
    getLabel: (T) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { opt ->
                    val isSelected = opt in selectedSet
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggle(opt) },
                        label = { Text(getLabel(opt)) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowOptions.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun <T> SingleChoiceRow(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    getLabel: (T) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { opt ->
            val isSelected = opt == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(opt) },
                label = { Text(getLabel(opt)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
