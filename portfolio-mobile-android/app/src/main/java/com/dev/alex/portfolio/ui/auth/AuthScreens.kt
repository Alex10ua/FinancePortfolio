package com.dev.alex.portfolio.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.alex.portfolio.AuthState
import com.dev.alex.portfolio.ui.components.FpIcon
import com.dev.alex.portfolio.ui.components.FpTextField
import com.dev.alex.portfolio.ui.components.PrimaryButton
import com.dev.alex.portfolio.ui.components.VSpace
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.shell.IconAction
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.Semantic

private val White85 = Color.White.copy(alpha = 0.85f)

/** The login mockup's indigo radial wash, deeper in dark mode. */
@Composable
private fun AuthBackground(content: @Composable ColumnScope.() -> Unit) {
    val dark = Fp.colors.isDark
    val stops = if (dark) {
        arrayOf(0f to Color(0xFF312E81), 0.6f to Color(0xFF0F0E2A), 1f to Color(0xFF020617))
    } else {
        arrayOf(0f to Brand.Indigo400, 0.35f to Brand.Primary, 1f to Brand.Indigo800)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        *stops,
                        center = Offset(size.width / 2f, 0f),
                        radius = size.maxDimension,
                    ),
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 24.dp),
        content = content,
    )
}

@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
        ) {
            Text("F", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text("FinancePortfolio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun Heading(eyebrow: String, title: String, body: String) {
    Column(Modifier.padding(top = 30.dp, bottom = 24.dp)) {
        Text(eyebrow.uppercase(), color = White85, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        VSpace(8.dp)
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
        VSpace(6.dp)
        Text(body, color = White85, fontSize = 13.sp)
    }
}

@Composable
private fun ErrorLine(message: String?) {
    if (message.isNullOrBlank()) return
    Text(
        message,
        color = Semantic.Danger,
        fontSize = 12.5.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Semantic.Danger.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/**
 * Sign-in with the fields the backend actually supports. The mockup's "Forgot password?",
 * "Continue with Google" and "Create an account" are left out — there is no backend for
 * them (CLAUDE.md, "Not ported: auth.jsx"). "Remember me" becomes the fingerprint option,
 * and the server address is added: the app has no fixed host to talk to.
 */
@Composable
fun SignInScreen(
    state: AuthState.SignIn,
    onSubmit: (server: String, username: String, password: String, rememberWithFingerprint: Boolean) -> Unit,
) {
    val colors = Fp.colors
    var server by rememberSaveable { mutableStateOf(state.server) }
    var username by rememberSaveable { mutableStateOf(state.username) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var remember by rememberSaveable { mutableStateOf(state.biometricAvailable) }
    val canSubmit = !state.busy && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
    val submit = { if (canSubmit) onSubmit(server, username, password, remember && state.biometricAvailable) }

    AuthBackground {
        Wordmark()
        Heading("Welcome back", "Sign in to your account", "Continue tracking your portfolios.")
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .padding(20.dp),
        ) {
            FpTextField(
                value = server,
                onValueChange = { server = it },
                label = "Server",
                placeholder = "my-pc.your-tailnet.ts.net",
                icon = FpIcons.Globe,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            FpTextField(
                value = username,
                onValueChange = { username = it },
                label = "Username",
                placeholder = "alex",
                icon = FpIcons.User,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            )
            FpTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "••••••••••••",
                icon = FpIcons.Lock,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailing = {
                    IconAction(FpIcons.Eye, if (showPassword) "Hide password" else "Show password") {
                        showPassword = !showPassword
                    }
                },
            )
            if (state.biometricAvailable) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { remember = !remember },
                ) {
                    Checkbox(
                        checked = remember,
                        onCheckedChange = { remember = it },
                        colors = CheckboxDefaults.colors(checkedColor = Brand.Primary),
                    )
                    Text("Unlock with fingerprint next time", color = colors.textMuted, fontSize = 12.5.sp)
                }
            }
            ErrorLine(state.message)
            if (state.busy) {
                Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand.Primary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else {
                PrimaryButton("Sign In", onClick = submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth())
            }
        }
        VSpace(18.dp)
        Text(
            "Reach the server over Tailscale — the backend is never exposed to the internet.",
            color = White85,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One touch to open: the prompt opens by itself, the button reopens it after a cancel. */
@Composable
fun LockScreen(state: AuthState.Locked, onUnlock: () -> Unit, onUsePassword: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    AuthBackground {
        Wordmark()
        Heading("Welcome back", "Unlock ${state.username}", "Your saved sign-in opens with your fingerprint.")
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Fp.colors.surface)
                .padding(24.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brand.Primary.copy(alpha = 0.10f))
                    .clickable(enabled = !state.busy, onClick = onUnlock),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(color = Brand.Primary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                } else {
                    FpIcon(FpIcons.Fingerprint, size = 34.dp, tint = Brand.Primary)
                }
            }
            VSpace(12.dp)
            Text(
                if (state.busy) "Signing in…" else "Touch to unlock",
                color = Fp.colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!state.message.isNullOrBlank()) {
                VSpace(14.dp)
                ErrorLine(state.message)
            }
            VSpace(18.dp)
            Text(
                "Use password instead",
                color = Brand.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !state.busy, onClick = onUsePassword)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}
