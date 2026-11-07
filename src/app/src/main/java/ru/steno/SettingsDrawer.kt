package ru.steno

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ru.steno.domain.*
import ru.steno.presentation.settings.*

@Composable internal fun SettingsDrawer(vm: SettingsViewModel, modifier: Modifier = Modifier) {
    val s by vm.state.collectAsState()
    var advanced by rememberSaveable { mutableStateOf(false) }
    SettingsDrawerContent(s,vm::onIntent,modifier) {advanced=true}
    if(advanced) SettingsDialog(vm) {advanced=false}
}
@Composable internal fun SettingsDrawerContent(s: SettingsState, send: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier, advanced: () -> Unit = {}) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(start=20.dp,end=28.dp,top=50.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(21.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top) {
            HexImage(R.drawable.hex_431_4963_vector,width=47,height=60)
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color=Color.White,fontSize=20.sp)){append("Turri")}
                withStyle(SpanStyle(color=Color(0xff2f6bff),fontSize=24.sp)){append("Tell")}
                withStyle(SpanStyle(color=Color.White,fontSize=20.sp)){append("a")}
            },fontWeight=FontWeight.SemiBold)
        }
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                HexImage(R.drawable.hex_431_4963_brain)
                Text("Выберите модель:",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Medium)
            }
            Column {
                DrawerChoice("Whisper",s.asrModel==AsrModelType.WHISPER) {
                    if(s.whisper is WhisperModelState.Installed) send(SettingsIntent.AsrModelSelected(AsrModelType.WHISPER)) else advanced()
                }
                DrawerChoice("GigaAM",s.asrModel==AsrModelType.GIGA_AM) {send(SettingsIntent.AsrModelSelected(AsrModelType.GIGA_AM))}
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                HexImage(R.drawable.hex_431_4963_palette)
                Text("Тема:",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Medium)
            }
            Column {
                listOf(ThemeMode.LIGHT to "Светлая",ThemeMode.DARK to "Тёмная",ThemeMode.SYSTEM to "Системная").forEach { (mode,label) ->
                    DrawerChoice(label,s.themeMode==mode) {send(SettingsIntent.ThemeSelected(mode))}
                }
            }
        }
        TextButton(advanced) {Text("Обработка и экспорт",color=Color(0xff9aa7c0))}
    }
}
@Composable private fun DrawerChoice(label: String, selected: Boolean, action: () -> Unit) {
    Surface(onClick=action,shape=RoundedCornerShape(10.dp),color=if(selected) Color(0xffe9ecef) else Color.Transparent,modifier=Modifier.fillMaxWidth()) {
        Text(label,Modifier.padding(10.dp),color=if(selected) Color(0xff131a26) else Color.White,fontSize=16.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium)
    }
}
