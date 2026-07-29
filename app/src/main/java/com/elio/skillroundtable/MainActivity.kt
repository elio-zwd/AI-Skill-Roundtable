package com.elio.skillroundtable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elio.skillroundtable.ui.CardBg
import com.elio.skillroundtable.ui.MainAppContent
import com.elio.skillroundtable.ui.PrimaryAccent
import com.elio.skillroundtable.ui.SecondaryAccent
import com.elio.skillroundtable.ui.SlateBg
import com.elio.skillroundtable.ui.TextPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = PrimaryAccent,
                    secondary = SecondaryAccent,
                    background = SlateBg,
                    surface = CardBg,
                    onPrimary = Color.White,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SlateBg
                ) {
                    MainAppContent()
                }
            }
        }
    }
}
