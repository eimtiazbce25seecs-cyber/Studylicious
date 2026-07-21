package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.StudyRepository
import com.example.ui.StudyViewModel
import com.example.ui.StudyViewModelFactory
import com.example.ui.StudyliciousApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Room Database, DAOs, and Repository
    val database = AppDatabase.getDatabase(this)
    val repository = StudyRepository(
      taskDao = database.taskDao(),
      streakDao = database.streakDao(),
      weakTopicDao = database.weakTopicDao(),
      chatDao = database.chatDao(),
      todoDao = database.todoDao()
    )
    
    // Instantiate ViewModel using custom Factory
    val viewModel = ViewModelProvider(
      this,
      StudyViewModelFactory(repository, this)
    )[StudyViewModel::class.java]

    enableEdgeToEdge()
    setContent {
      val appTheme = viewModel.appTheme.collectAsState().value
      val isDarkTheme = appTheme == "Cosmic Candy"
      MyApplicationTheme(darkTheme = isDarkTheme, dynamicColor = false) {
        StudyliciousApp(viewModel = viewModel)
      }
    }
  }
}
