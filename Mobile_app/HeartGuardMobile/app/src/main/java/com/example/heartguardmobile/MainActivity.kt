package com.example.heartguardmobile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// --- CONFIGURAÇÃO ---
const val BASE_URL = "https://drawlingly-precurricular-eugena.ngrok-free.dev/" // <--- CONFIRA SEU LINK!

// --- MODELOS ---
data class PatientStatus(
    val Paciente: String,
    val ECG: String,
    val PA: String,
    val SpO2: String,
    val Analise_IA: String,
    val Medicacao_Cadastrada: String? = "sua medicação"
)

data class PatientConfig(
    val name: String,
    val phone: String,
    val target_sys: Int,
    val target_dia: Int,
    val target_spo2: Int,
    val medication: String
)

data class ApiResult(val status: String, val message: String)

// --- API ---
interface ApiService {
    @GET("latest_status")
    suspend fun getStatus(): PatientStatus

    @POST("register_patient")
    suspend fun registerPatient(@Body config: PatientConfig): ApiResult
}

object RetrofitClient {
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}

// --- NAVEGAÇÃO ---
@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf("form") }
    var userName by remember { mutableStateOf("") }

    if (currentScreen == "form") {
        RegistrationScreen(
            onRegistered = { name ->
                userName = name
                currentScreen = "dashboard"
            }
        )
    } else {
        FamilyDashboard(
            userName = userName,
            onEditSettings = { currentScreen = "form" } // Volta para o cadastro
        )
    }
}

// --- TELA 1: CADASTRO/CONFIGURAÇÃO ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(onRegistered: (String) -> Unit) {
    var name by remember { mutableStateOf("Sr. João (Ribeirinho)") }
    var phone by remember { mutableStateOf("+55") }
    var medication by remember { mutableStateOf("Losartana 50mg") }
    var sys by remember { mutableStateOf("130") }
    var dia by remember { mutableStateOf("80") }
    var spo2 by remember { mutableStateOf("92") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⚙️ Configuração", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF006400))
        Text("Cadastre ou edite os dados do paciente", fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome Completo") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefone Emergência") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = medication, onValueChange = { medication = it }, label = { Text("Medicação de Controle") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
        Text("Valores Alvo", fontSize = 14.sp, color = Color.Gray)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedTextField(value = sys, onValueChange = { sys = it }, label = { Text("Sis") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = dia, onValueChange = { dia = it }, label = { Text("Dia") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = spo2, onValueChange = { spo2 = it }, label = { Text("SpO2") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = {
                scope.launch {
                    try {
                        val config = PatientConfig(name, phone, sys.toInt(), dia.toInt(), spo2.toInt(), medication)
                        val res = RetrofitClient.api.registerPatient(config)
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        onRegistered(name)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erro de Conexão", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400))
        ) {
            Text("Salvar Configurações")
        }
    }
}

// --- TELA 2: DASHBOARD ---
// ... (Mantenha os imports e classes de cima iguais)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyDashboard(userName: String, onEditSettings: () -> Unit) {
    var status by remember { mutableStateOf(PatientStatus("Carregando...", "...", "--", "--", "", "medicamento")) }
    var isDanger by remember { mutableStateOf(false) }

    // Estados do Alerta
    var showDialog by remember { mutableStateOf(false) }
    var dialogStep by remember { mutableStateOf(0) }
    var alertDismissed by remember { mutableStateOf(false) }

    // NOVO: Temporizador de Segurança (60 segundos)
    var timeRemaining by remember { mutableIntStateOf(60) }

    val context = LocalContext.current

    // Loop de Monitoramento de Dados (Fica rodando sempre)
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val novoStatus = RetrofitClient.api.getStatus()
                status = novoStatus

                val perigoDetectado = "PERIGO" in novoStatus.ECG || "VENTRICULAR" in novoStatus.ECG || "ANORMALIDADE" in novoStatus.ECG

                if (perigoDetectado && !alertDismissed) {
                    if (!showDialog) { // Se o alerta acabou de aparecer
                        isDanger = true
                        showDialog = true
                        timeRemaining = 60 // Reinicia o timer para 1 minuto
                    }
                }
                else if (!perigoDetectado) {
                    isDanger = false
                    alertDismissed = false
                    showDialog = false
                    dialogStep = 0
                }
            } catch (e: Exception) {}
            delay(2000)
        }
    }

    // NOVO: Lógica do "Homem Morto" (Countdown)
    // Esse bloco roda apenas quando o Dialog aparece
    LaunchedEffect(showDialog) {
        if (showDialog) {
            while (timeRemaining > 0) {
                delay(1000) // Espera 1 segundo
                timeRemaining-- // Diminui o tempo
            }

            // SE O TEMPO ACABAR (CHEGAR A 0):
            if (showDialog) { // Verifica se ainda está aberto
                showDialog = false
                alertDismissed = true
                // Dispara o alerta automaticamente
                Toast.makeText(context, "⏳ TEMPO ESGOTADO: Socorro acionado automaticamente!", Toast.LENGTH_LONG).show()
                // Nota Técnica: O Python já enviou o SMS quando detectou o perigo (lado servidor),
                // mas aqui o App confirma para quem estiver perto que a ação foi tomada.
            }
        }
    }

    // --- POP-UP INTELIGENTE COM TIMER ---
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { /* Bloqueado */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (dialogStep == 0) "⚠️ ALERTA DE SAÚDE" else "🆘 AÇÃO NECESSÁRIA")
                }
            },
            text = {
                Column {
                    if (dialogStep == 0) {
                        Text("Detectamos uma alteração cardíaca grave em $userName.")
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("O paciente já tomou o medicamento '${status.Medicacao_Cadastrada}' hoje?", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Por favor, informe se consegue tomar o medicamento agora.\n\nSe precisar de socorro, clique em AJUDA.")
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // BARRA DE PROGRESSO DO TEMPO (Visual de Urgência)
                    Text("Acionamento automático em: ${timeRemaining}s", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = timeRemaining / 60f,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Red,
                        trackColor = Color.Red.copy(alpha = 0.2f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        alertDismissed = true
                        Toast.makeText(context, "Registro: Medicação Tomada. Recomendado repouso.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400))
                ) {
                    Text("SIM, TOMEI")
                }
            },
            dismissButton = {
                if (dialogStep == 0) {
                    Button(
                        onClick = { dialogStep = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("NÃO")
                    }
                } else {
                    Button(
                        onClick = {
                            showDialog = false
                            alertDismissed = true
                            Toast.makeText(context, "🚨 ALERTA MANUAL ENVIADO!", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                    ) {
                        Text("PEDIR AJUDA AGORA")
                    }
                }
            }
        )
    }

    // ... (O RESTO DO CÓDIGO DO SCAFFOLD/LAYOUT CONTINUA IGUAL AO ANTERIOR) ...
    // Vou repetir a parte do Scaffold para garantir que você tenha o bloco completo:

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HeartGuard Family", color = Color.White, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onEditSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configurações", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        val bgGradient = if (isDanger) Brush.verticalGradient(listOf(Color(0xFF8B0000), Color(0xFF1A0000)))
        else Brush.verticalGradient(listOf(Color(0xFF006400), Color(0xFF001A00)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Monitorando: $userName", color = Color.LightGray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(30.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(150.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isDanger) "🚨 PERIGO" else "✅ ESTÁVEL", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(status.ECG, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InfoCard("Pressão", status.PA, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(16.dp))
                InfoCard("Oxigenação", status.SpO2, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Orientação IA (RAG):", color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f)) {
                Text(text = status.Analise_IA, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
@Composable
fun InfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)), modifier = modifier.height(100.dp)) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.LightGray, fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}