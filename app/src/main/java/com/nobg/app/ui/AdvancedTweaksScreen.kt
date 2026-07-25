package com.nobg.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobg.app.data.NobgRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedTweaksScreen(
    repo: NobgRepository,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isFreeformEnabled by remember { mutableStateOf(repo.isForceFreeformEnabled()) }
    var isSafeVolumeDisabled by remember { mutableStateOf(repo.isDisableSafeVolumeEnabled()) }
    var isCellularSaverEnabled by remember { mutableStateOf(repo.isDisableCellularAlwaysOnEnabled()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ Cấu Hình Nâng Cao (Hidden Settings)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CARD 1: FORCE RESIZABLE & FREEFORM (OPPO FIND N3 & FOLDABLES)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "📱 ĐẶC BIỆT DÀNH CHO OPPO FIND N3 & MÀN GẬP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "📐 ÉP CỬA SỔ TỰ DO (FREEFORM) & CHIA ĐÔI MÀN HÌNH",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Bắt buộc TẤT CẢ ứng dụng (Instagram, Zalo, Ngân hàng, Game...) phải hỗ trợ chia đôi màn hình và mở cửa sổ nổi Freeform trên ColorOS / OxygenOS & Android.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = isFreeformEnabled,
                            onCheckedChange = { enabled ->
                                isFreeformEnabled = enabled
                                scope.launch {
                                    repo.setForceResizableAndFreeform(enabled)
                                    val msg = if (enabled) "📐 Đã BẬT Cửa sổ nổi Freeform & Chia đôi cho Oppo Find N3!" else "Đã TẮT Ép cửa sổ tự do"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // CARD 2: DISABLE SAFE VOLUME WARNING
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🔊 TẮT GIỚI HẠN CẢNH BÁO ÂM LƯỢNG TAI NGHE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tắt thông báo cảnh báo 60% âm lượng phiền phức khi kết nối tai nghe hoặc loa Bluetooth.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = isSafeVolumeDisabled,
                            onCheckedChange = { enabled ->
                                isSafeVolumeDisabled = enabled
                                scope.launch {
                                    repo.setDisableSafeVolume(enabled)
                                    val msg = if (enabled) "🔊 Đã TẮT Giới hạn cảnh báo âm lượng tai nghe" else "Đã BẬT Giới hạn âm lượng tai nghe"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // CARD 3: CELLULAR STANDBY SAVER (DISABLE 4G/5G ON WI-FI)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "📶 TẮT GIỮ SÓNG 4G/5G KHÔNG CẦN THIẾT KHI DÙNG WI-FI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Ngắt kết nối Modem 4G/5G ngầm khi đang bắt Wi-Fi để tiết kiệm 15% dung lượng pin chờ và giảm nóng máy.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = isCellularSaverEnabled,
                            onCheckedChange = { enabled ->
                                isCellularSaverEnabled = enabled
                                scope.launch {
                                    repo.setDisableCellularAlwaysOn(enabled)
                                    val msg = if (enabled) "📶 Đã TẮT Duy trì 4G/5G ngầm khi có Wi-Fi (Tiết kiệm pin)" else "Đã BẬT Duy trì 4G/5G ngầm"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
