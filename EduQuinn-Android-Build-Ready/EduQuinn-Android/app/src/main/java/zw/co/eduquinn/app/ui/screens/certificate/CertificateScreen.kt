package zw.co.eduquinn.app.ui.screens.certificate

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.Certificate
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.theme.Gold
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
    courseId: String,
) {
    val viewModel: CertificateViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CertificateViewModel(courseId, app.services.certificateRepository, app.services.courseRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    fun shareCertificate() {
        scope.launch {
            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
            val uri = saveAndGetShareUri(context, bitmap)
            uri?.let {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, it)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share certificate"))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Certificate") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.certificate != null) {
                        IconButton(onClick = { shareCertificate() }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share certificate")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            uiState.certificate == null -> ErrorState(
                uiState.error ?: "This certificate is not available yet.",
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        },
                ) {
                    CertificateCard(
                        certificate = uiState.certificate!!,
                        courseTitle = uiState.courseTitle,
                        instructorName = uiState.instructorName,
                        studentName = uiState.studentName,
                    )
                }
                Button(
                    onClick = { shareCertificate() },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(" Share certificate", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun CertificateCard(
    certificate: Certificate,
    courseTitle: String,
    instructorName: String?,
    studentName: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .padding(4.dp)
            .background(MaterialTheme.colorScheme.background)
            .border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(12.dp))
            .padding(10.dp)
            .border(BorderStroke(1.dp, Gold), RoundedCornerShape(6.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "CERTIFICATE OF COMPLETION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
            Text("This certifies that", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(
                studentName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text("has successfully completed", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(
                courseTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            instructorName?.let {
                Text("Instructor: $it", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 12.dp)) {
                Text("CERTIFICATE ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(certificate.certificateCode, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    formatIssuedDate(certificate.issuedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatIssuedDate(isoDate: String): String = try {
    val parsed = java.time.Instant.parse(isoDate)
    SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date.from(parsed))
} catch (_: Exception) {
    isoDate
}

private fun saveAndGetShareUri(context: android.content.Context, bitmap: android.graphics.Bitmap): android.net.Uri? = try {
    val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
    val file = File(dir, "certificate_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
} catch (_: Exception) {
    null
}
