package com.etaris.astrahomework

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.etaris.astrahomework.ui.theme.AstraHomeworkTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::setFolder)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AstraHomeworkTheme { HomeworkApp(viewModel) { picker.launch(null) } }
        }
    }
}
