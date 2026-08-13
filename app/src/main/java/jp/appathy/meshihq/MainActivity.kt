package jp.appathy.meshihq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import jp.appathy.meshihq.ui.nav.MeshiNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as MeshiApp).repository
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                MeshiNavHost(repository)
            }
        }
    }
}
