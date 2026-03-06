package com.example.opticdoorbell


import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opticdoorbell.ui.theme.OPTICDoorbellTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OPTICDoorbellTheme {
                OPTICApp()
            }
        }
    }
}


@Composable
fun OPTICApp() {
    var started by remember { mutableStateOf(false) }
    if (!started) {
        HomeScreen(onStart = { started = true })
    } else {
        DoorbellScreen()
    }
}


@Composable
fun HomeScreen(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Optic",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Light
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Smart Doorbell",
                color = Color(0xFF888888),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(36.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9370DB)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Get Started",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


@Composable
fun Esp32CameraStream(ip: String, modifier: Modifier = Modifier) {


    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusText by remember { mutableStateOf("Connecting...") }
    var fps by remember { mutableStateOf(0) }
    var faces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }


    val detector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }


    val scope = rememberCoroutineScope()


    LaunchedEffect(ip) {
        scope.launch(Dispatchers.IO) {
            var frameCount = 0
            var lastSecond = System.currentTimeMillis()


            while (isActive) {
                try {
                    val url = URL("http://$ip/capture?t=${System.currentTimeMillis()}")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.requestMethod = "GET"
                    conn.connect()


                    if (conn.responseCode == 200) {
                        val bytes = conn.inputStream.readBytes()
                        conn.disconnect()


                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            withContext(Dispatchers.Main) {
                                imageBitmap = bitmap
                                statusText = ""
                            }


                            val inputImage = InputImage.fromBitmap(bitmap, 0)
                            detector.process(inputImage)
                                .addOnSuccessListener { detectedFaces ->
                                    faces = detectedFaces
                                }


                            frameCount++
                            val now = System.currentTimeMillis()
                            if (now - lastSecond >= 1000) {
                                val currentFps = frameCount
                                withContext(Dispatchers.Main) { fps = currentFps }
                                frameCount = 0
                                lastSecond = now
                            }
                        }
                    } else {
                        conn.disconnect()
                        withContext(Dispatchers.Main) { statusText = "Error ${conn.responseCode}" }
                        delay(500)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { statusText = "Reconnecting..." }
                    delay(500)
                }
            }
        }
    }


    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { viewSize = it }
    ) {
        if (imageBitmap != null) {
            val bmp = imageBitmap!!


            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Camera Feed",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )


            // Draw red box around each detected face
            if (faces.isNotEmpty() && viewSize != IntSize.Zero) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scale = minOf(size.width / bmp.width.toFloat(), size.height / bmp.height.toFloat())
                    val offsetX = (size.width - bmp.width * scale) / 2f
                    val offsetY = (size.height - bmp.height * scale) / 2f


                    faces.forEach { face ->
                        val box = face.boundingBox
                        drawRect(
                            color = Color(0xFFFF4444),
                            topLeft = Offset(
                                x = offsetX + box.left * scale,
                                y = offsetY + box.top * scale
                            ),
                            size = Size(
                                width = box.width() * scale,
                                height = box.height() * scale
                            ),
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }


            // FPS counter
            Text(
                text = "$fps fps",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )


        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF9370DB),
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusText,
                    color = Color(0xFF666666),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}


@Composable
fun DoorbellScreen() {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current


    var cameraIp by remember { mutableStateOf("172.20.10.12") }
    var editingIp by remember { mutableStateOf("172.20.10.12") }
    var showIpDialog by remember { mutableStateOf(false) }


    val webInterfaceUrl = "http://$cameraIp"


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {


        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Optic",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = cameraIp,
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Change IP",
                    color = Color(0xFF9370DB),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.clickable {
                        editingIp = cameraIp
                        showIpDialog = true
                    }
                )
            }
        }


        Spacer(modifier = Modifier.height(12.dp))


        // Open web interface link
        Text(
            text = "Open web interface →",
            color = Color(0xFF666666),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webInterfaceUrl))
                    context.startActivity(intent)
                }
                .padding(vertical = 4.dp)
        )


        Spacer(modifier = Modifier.height(12.dp))


        Text(
            text = "Live feed",
            color = Color(0xFF888888),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.fillMaxWidth()
        )


        Spacer(modifier = Modifier.height(8.dp))


        // Camera view
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            Esp32CameraStream(
                ip = cameraIp,
                modifier = Modifier.fillMaxSize()
            )
        }


        Spacer(modifier = Modifier.height(12.dp))


        Text(
            text = "http://$cameraIp/capture",
            color = Color(0xFF333333),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
    }


    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            containerColor = Color(0xFF1A1A1A),
            title = {
                Text(
                    "Camera IP",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            text = {
                OutlinedTextField(
                    value = editingIp,
                    onValueChange = { editingIp = it },
                    label = {
                        Text(
                            "IP Address",
                            color = Color(0xFF666666),
                            fontSize = 13.sp
                        )
                    },
                    placeholder = {
                        Text(
                            "e.g. 192.168.1.100",
                            color = Color(0xFF444444),
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        cameraIp = editingIp
                        showIpDialog = false
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF9370DB),
                        unfocusedBorderColor = Color(0xFF333333)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    cameraIp = editingIp
                    showIpDialog = false
                }) {
                    Text(
                        "Connect",
                        color = Color(0xFF9370DB),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text(
                        "Cancel",
                        color = Color(0xFF666666),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        )
    }
}
