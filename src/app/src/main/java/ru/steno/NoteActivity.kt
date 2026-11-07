package ru.steno

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import ru.steno.domain.*
import ru.steno.presentation.note.*

class NoteActivity : ComponentActivity() {
    private val appTheme: ru.steno.presentation.app.AppViewModel by viewModel()
    private val vm: NoteViewModel by viewModel { parametersOf(intent.getLongExtra(EXTRA_NOTE_ID,-1)) }
    private val save = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onIntent(NoteIntent.ExportDestinationPicked(it.data?.data?.takeIf { _ -> it.resultCode==RESULT_OK }?.toString()))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val s by vm.state.collectAsState()
            LaunchedEffect(vm) { vm.effects.collect { when(it) {
                NoteEffect.NavigateBack -> finish()
                is NoteEffect.ShowMessage -> message(it.message)
                is NoteEffect.PickExportDestination -> save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType(it.mimeType).putExtra(Intent.EXTRA_TITLE,it.suggestedName))
            } } }
            HexTheme(appTheme.state.collectAsState().value.themeMode) {
                NoteScreen(s, vm::onIntent, ::finish)
            }
        }
    }
}
