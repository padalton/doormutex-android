package net.padalton.doormutex.android.ui.pairing

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.padalton.doormutex.android.R
import net.padalton.doormutex.android.data.HubException
import net.padalton.doormutex.android.data.HubUrl
import net.padalton.doormutex.android.data.PairCheck
import net.padalton.doormutex.android.data.Role
import net.padalton.doormutex.android.repository
import net.padalton.doormutex.android.ui.LocalNetworkHint
import net.padalton.doormutex.android.ui.rememberLocalNetworkAccess
import net.padalton.doormutex.android.widget.RefreshWorker
import net.padalton.doormutex.android.widget.redrawWidgets

sealed interface PairState {
    data object Editing : PairState

    data object Checking : PairState

    data class Failed(
        val message: Int,
    ) : PairState

    data class Found(
        val check: PairCheck,
    ) : PairState
}

class PairingViewModel(
    app: Application,
) : AndroidViewModel(app) {
    var url by mutableStateOf("")
    var token by mutableStateOf("")
    var cleartextAccepted by mutableStateOf(false)
    var state by mutableStateOf<PairState>(PairState.Editing)
        private set

    val parsedUrl get() = HubUrl.parse(url)

    fun edited() {
        state = PairState.Editing
    }

    fun check() {
        val ok = parsedUrl as? HubUrl.Ok ?: return
        if (token.isBlank()) return
        state = PairState.Checking
        viewModelScope.launch {
            state =
                try {
                    PairState.Found(getApplication<Application>().repository.check(ok.url, token))
                } catch (e: HubException) {
                    PairState.Failed(
                        when (e) {
                            is HubException.Unreachable -> R.string.pair_error_unreachable
                            is HubException.Unauthorized -> R.string.pair_error_unauthorized
                            is HubException.Forbidden -> R.string.pair_error_forbidden
                            else -> R.string.pair_error_other
                        },
                    )
                }
        }
    }

    fun save(onDone: () -> Unit) {
        val found = state as? PairState.Found ?: return
        viewModelScope.launch {
            val app = getApplication<Application>()
            app.repository.save(found.check)
            RefreshWorker.schedule(app)
            redrawWidgets(app)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    vm: PairingViewModel = viewModel(),
) {
    val localNetwork = rememberLocalNetworkAccess()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.pair_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.pair_intro), style = MaterialTheme.typography.bodyMedium)

            val parsed = vm.parsedUrl
            val urlError =
                when {
                    vm.url.isBlank() -> null
                    parsed is HubUrl.Invalid -> R.string.pair_invalid_url
                    parsed is HubUrl.CleartextNotPrivate -> R.string.pair_cleartext_public
                    else -> null
                }
            OutlinedTextField(
                value = vm.url,
                onValueChange = {
                    vm.url = it
                    vm.cleartextAccepted = false
                    vm.edited()
                },
                label = { Text(stringResource(R.string.pair_url)) },
                placeholder = { Text(stringResource(R.string.pair_url_hint)) },
                isError = urlError != null,
                supportingText = urlError?.let { { Text(stringResource(it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag("pair-url"),
            )
            OutlinedTextField(
                value = vm.token,
                onValueChange = {
                    vm.token = it
                    vm.edited()
                },
                label = { Text(stringResource(R.string.pair_token)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().testTag("pair-token"),
            )

            val cleartext = (parsed as? HubUrl.Ok)?.cleartext == true
            if (cleartext) {
                Text(
                    stringResource(R.string.pair_cleartext_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vm.cleartextAccepted, onCheckedChange = { vm.cleartextAccepted = it })
                    Text(stringResource(R.string.pair_cleartext_accept))
                }
            }

            val canCheck =
                parsed is HubUrl.Ok && vm.token.isNotBlank() && (!cleartext || vm.cleartextAccepted) &&
                    vm.state !is PairState.Checking
            OutlinedButton(
                onClick = { localNetwork.request(vm::check) },
                enabled = canCheck,
                modifier = Modifier.testTag("pair-check"),
            ) {
                Text(stringResource(R.string.pair_check))
            }

            if (localNetwork.denied) LocalNetworkHint(localNetwork, onGranted = vm::check)

            when (val s = vm.state) {
                PairState.Editing -> {}

                PairState.Checking -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.pair_checking))
                    }
                }

                is PairState.Failed -> {
                    Text(stringResource(s.message), color = MaterialTheme.colorScheme.error)
                }

                is PairState.Found -> {
                    Found(s.check) { vm.save(onPaired) }
                }
            }
        }
    }
}

@Composable
private fun Found(
    check: PairCheck,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            pluralStringResource(R.plurals.pair_found, check.profiles.size, check.profiles.size),
            style = MaterialTheme.typography.titleMedium,
        )
        check.profiles.forEach { Text("• ${it.name}") }
        Text(
            stringResource(
                when (check.me?.role) {
                    Role.READ -> R.string.pair_role_read
                    Role.FULL -> R.string.pair_role_full
                    else -> R.string.pair_role_unknown
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onSave, modifier = Modifier.testTag("pair-save")) { Text(stringResource(R.string.pair_save)) }
    }
}
