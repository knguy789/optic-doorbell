package com.example.opticdoorbell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.opticdoorbell.ui.theme.OPTICDoorbellTheme
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OPTICDoorbellTheme {
                OPTICApp()
            }
//            OPTICDoorbellTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
//                }
//            }
        }
    }
}

//@Composable
//fun Greeting(name: String, modifier: Modifier = Modifier) {
//    Text(
//        text = "Hello $name!",
//        modifier = modifier
//    )
//}
//
//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    OPTICDoorbellTheme {
//        Greeting("Android")
//    }
//}

@Composable
fun OPTICApp() {
    var started by remember { mutableStateOf(false)}
    if (!started) {
        HomeScreen(onStart = {started = true})
    } else {
        DoorbellScreen()
    }
}

@Composable
fun HomeScreen(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to OPTIC",
                color = Color.White,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStart
            ) {
                Text("Start")
            }
        }
    }
}

@Composable
fun DoorbellScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("Idle") }
    val streamUrl = "http://192.168.1.100/stream"  // Change to your ESP32/Pi IP
    val webInterfaceUrl = "http://192.168.1.100"   // Main ESP32 web page
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Simple test text
        Text(
            text = "OPTIC DOORBELL",
            color = Color.White,
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
//            text = "Link..................",
//            color = Color(0xFFFFD700),
//            fontSize = 20.sp
            text = "Link..................",
            color = Color(0xFFFFD700),
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // Opens ESP32-CAM web interface in browser
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webInterfaceUrl))
                    context.startActivity(intent)
                }
                .padding(vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Live Video Feed",
            color = Color(0xFF9370DB),
            fontSize = 24.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Video will appear here",
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Status: $statusText",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

//@Composable
//fun DoorbellScreen() {
//    var statusText by remember { mutableStateOf("Idle") }
//
//    val esp32CamUrl = "http://192.168.1.100/stream"
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(Color.Black)
//            .padding(16.dp)
//    ) {
//        // Video Stream Placeholder
//        Box(
//            modifier = Modifier
//                .fillMaxWidth()
//                .weight(1f)
//                .background(Color.DarkGray),
//            contentAlignment = Alignment.Center
//        ) {
//            AndroidView(
//                factory = { context ->
//                    WebView(context).apply {
//                        // settings.javaScriptEnabled = true
//                        settings.loadWithOverviewMode = true
//                        settings.useWideViewPort = true
//                        webViewClient = WebViewClient()
//                        loadUrl(esp32CamUrl)
//                    }
//                },
//                modifier = Modifier.fillMaxSize()
//            )
////            Text(
////                text = "Live Camera Feed",
////                color = Color.White
////            )
//        }
//        Spacer(modifier = Modifier.height(12.dp))
//
//        // Status Indicator
//        Text(
//            text = "Status: $statusText",
//            color = Color.White,
//            style = MaterialTheme.typography.titleMedium
//        )
//        Spacer(modifier = Modifier.height(12.dp))
//    }
// }