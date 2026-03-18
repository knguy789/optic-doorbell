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

sealed class Screen {
    object Home : Screen()
    object Doorbell : Screen()
    object Enroll : Screen()
}

@Composable
fun OPTICApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (screen) {
        is Screen.Home -> HomeScreen(onStart = { screen = Screen.Doorbell })
        is Screen.Doorbell -> DoorbellScreen(
            onEnroll = { screen = Screen.Enroll }
        )
        is Screen.Enroll -> EnrollScreen(
            onDone = { screen = Screen.Doorbell }
        )
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
                Text("Get Started", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// Shared camera IP state
var sharedCameraIp = "172.20.10.2"

@Composable
fun Esp32CameraStream(

    ip: String,
    onFrameCaptured: ((Bitmap) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusText by remember { mutableStateOf("Connecting...") }
    var fps by remember { mutableStateOf(0) }
    var faces by remember { mutableStateOf<List<Pair<Face, String?>>>(emptyList()) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val detector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
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
                    android.util.Log.d("OPTIC_DEBUG", "Fetching frame from: $url")
                    //val url = URL("http://$ip/capture?t=${System.currentTimeMillis()}")
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
                                onFrameCaptured?.invoke(bitmap)
                            }

                            val inputImage = InputImage.fromBitmap(bitmap, 0)
                            detector.process(inputImage)
                                .addOnSuccessListener { detectedFaces ->
                                    faces = detectedFaces.map { face ->
                                        val name = FaceRecognitionHelper.recognize(face)
                                        face to name
                                    }
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

            // Draw boxes and names
            if (faces.isNotEmpty() && viewSize != IntSize.Zero) {
                val scale = minOf(
                    viewSize.width / bmp.width.toFloat(),
                    viewSize.height / bmp.height.toFloat()
                )
                val offsetX = (viewSize.width - bmp.width * scale) / 2f
                val offsetY = (viewSize.height - bmp.height * scale) / 2f

                Canvas(modifier = Modifier.fillMaxSize()) {
                    faces.forEach { (face, _) ->
                        val box = face.boundingBox
                        val color = Color(0xFFFF4444)
                        drawRect(
                            color = color,
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

                // Draw name labels above each box
                faces.forEach { (face, name) ->
                    if (name != null) {
                        val box = face.boundingBox
                        val labelX = (offsetX + box.left * scale).dp
                        val labelY = (offsetY + box.top * scale - 24).dp
                        Box(
                            modifier = Modifier
                                .offset(x = labelX, y = labelY)
                                .background(Color(0xFFFF4444), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = name,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Text(
                text = "$fps fps",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 10.sp,
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
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun DoorbellScreen(onEnroll: () -> Unit) {
    android.util.Log.d("OPTIC_DEBUG", "sharedCameraIp = $sharedCameraIp")
    android.util.Log.d("OPTIC_DEBUG", "cameraIp state = ${sharedCameraIp}")
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var cameraIp by remember { mutableStateOf(sharedCameraIp) }
    var editingIp by remember { mutableStateOf(sharedCameraIp) }
    var showIpDialog by remember { mutableStateOf(false) }
    val enrolledNames = remember { mutableStateListOf<String>().also { it.addAll(FaceRecognitionHelper.getEnrolledNames()) } }

    val webInterfaceUrl = "http://$cameraIp"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Optic", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(cameraIp, color = Color(0xFF666666), fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Change IP",
                    color = Color(0xFF9370DB),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        editingIp = cameraIp
                        showIpDialog = true
                    }
                )
                Text(
                    text = "+ Enroll",
                    color = Color(0xFF9370DB),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onEnroll() }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Open web interface →",
            color = Color(0xFF666666),
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webInterfaceUrl))
                    context.startActivity(intent)
                }
                .padding(vertical = 4.dp)
        )

        // Show enrolled people
        if (enrolledNames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                enrolledNames.forEach { name ->
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1A1A1A), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .clickable {
                                FaceRecognitionHelper.removeEnrolled(name)
                                enrolledNames.remove(name)
                            }
                    ) {
                        Text(
                            text = "$name ×",
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Live feed",
            color = Color(0xFF888888),
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            Esp32CameraStream(ip = cameraIp, modifier = Modifier.fillMaxSize())
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "http://$cameraIp/capture",
            color = Color(0xFF333333),
            fontSize = 11.sp
        )
    }

    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("Camera IP", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
            text = {
                OutlinedTextField(
                    value = editingIp,
                    onValueChange = { editingIp = it },
                    label = { Text("IP Address", color = Color(0xFF666666), fontSize = 13.sp) },
                    placeholder = { Text("e.g. 192.168.1.100", color = Color(0xFF444444), fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        cameraIp = editingIp
                        sharedCameraIp = editingIp
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
                    sharedCameraIp = editingIp
                    showIpDialog = false
                }) {
                    Text("Connect", color = Color(0xFF9370DB), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("Cancel", color = Color(0xFF666666), fontSize = 14.sp)
                }
            }
        )
    }
}

@Composable
fun EnrollScreen(onDone: () -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var name by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("Point camera at the person's face, then tap Enroll") }
    var lastFrame by remember { mutableStateOf<Bitmap?>(null) }
    var enrolling by remember { mutableStateOf(false) }

    val detector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.2f)
            .build()
        FaceDetection.getClient(options)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Enroll Face", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Cancel",
                color = Color(0xFF666666),
                fontSize = 13.sp,
                modifier = Modifier.clickable { onDone() }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Person's name", color = Color(0xFF666666), fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF9370DB),
                unfocusedBorderColor = Color(0xFF333333)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live preview for enrollment
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            Esp32CameraStream(
                ip = sharedCameraIp,
                onFrameCaptured = { bitmap -> lastFrame = bitmap },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = statusMsg,
            color = Color(0xFF666666),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (name.isBlank()) {
                    statusMsg = "Please enter a name first"
                    return@Button
                }
                val frame = lastFrame
                if (frame == null) {
                    statusMsg = "Waiting for camera feed..."
                    return@Button
                }
                enrolling = true
                statusMsg = "Detecting face..."

                val inputImage = InputImage.fromBitmap(frame, 0)
                detector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        enrolling = false
                        if (faces.isEmpty()) {
                            statusMsg = "No face detected — make sure the face is clearly visible"
                        } else if (faces.size > 1) {
                            statusMsg = "Multiple faces detected — only one person should be in frame"
                        } else {
                            FaceRecognitionHelper.enroll(name, faces[0])
                            statusMsg = "✓ ${name} enrolled successfully!"
                            name = ""
                        }
                    }
                    .addOnFailureListener {
                        enrolling = false
                        statusMsg = "Detection failed — try again"
                    }
            },
            enabled = !enrolling,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9370DB)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (enrolling) "Enrolling..." else "Enroll Face",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onDone) {
            Text("Done", color = Color(0xFF9370DB), fontSize = 14.sp)
        }
    }
}