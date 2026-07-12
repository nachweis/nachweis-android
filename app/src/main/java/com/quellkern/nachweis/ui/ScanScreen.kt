package com.quellkern.nachweis.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.quellkern.nachweis.ui.components.PrimaryButton
import com.quellkern.nachweis.ui.components.SecondaryButton
import com.quellkern.nachweis.ui.theme.Ink
import com.quellkern.nachweis.ui.theme.Paper
import com.quellkern.nachweis.ui.theme.nachweisColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Camera QR scanner. On the first decoded QR it invokes [onScanned] once with the raw value
 * (an OpenID4VCI offer URI). Camera permission is requested inline; if denied, the screen
 * explains and offers a retry rather than silently failing. No visual polish — the scanning
 * chrome is C-late's concern.
 */
@Composable
fun ScanScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        Box(modifier = modifier.fillMaxSize()) {
            CameraPreview(onScanned = onScanned, modifier = Modifier.fillMaxSize())
            ScannerOverlay(onCancel = onCancel, modifier = Modifier.fillMaxSize())
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Camera access is needed to scan an issuer's QR code.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            PrimaryButton(
                label = "Allow camera",
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 16.dp),
            )
            SecondaryButton(
                label = "Back",
                onClick = onCancel,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * The camera-free scanning chrome drawn over the live [CameraPreview]: a Paper-bordered
 * viewfinder frame carrying the house hard shadow, an instruction strip, and a Cancel action
 * wired to [onCancel]. It holds no camera or analyzer state, so it renders and is tested on its
 * own; the viewfinder centre is transparent so the camera shows through it.
 */
@Composable
internal fun ScannerOverlay(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shadow = nachweisColors.shadowOffset
    val weight = nachweisColors.borderWeight
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Instruction strip: Paper text on an Ink fill (design-tokens contrast law), the inverse
        // of the paper ground so it reads clearly over an arbitrary camera image.
        Text(
            text = "Point the camera at the issuer's QR code",
            color = Paper,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink, RectangleShape)
                .border(weight, Ink, RectangleShape)
                .padding(12.dp),
        )

        // Viewfinder frame: a hollow square with an offset ink shadow behind a Paper border, so
        // the house neo-brutal treatment frames the live camera without covering it.
        Box(
            modifier = Modifier
                .size(240.dp)
                .semantics { contentDescription = "Scanning viewfinder" },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = shadow, y = shadow)
                    .border(weight, Ink, RectangleShape),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Transparent, RectangleShape)
                    .border(weight, Paper, RectangleShape),
            )
        }

        SecondaryButton(
            label = "Cancel",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    var handled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    Box(modifier = modifier.semantics { contentDescription = "Camera viewfinder for scanning a QR code" }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { proxy ->
                        val image = proxy.image
                        if (image == null || handled) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        val input = InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)
                        scanner.process(input)
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstOrNull { it.valueType == Barcode.TYPE_URL || it.rawValue != null }?.rawValue
                                if (value != null && !handled) {
                                    handled = true
                                    onScanned(value)
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )
    }
}
