package com.dev.alex.portfolio.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.alex.portfolio.Screen
import com.dev.alex.portfolio.data.api.PortfolioDto
import com.dev.alex.portfolio.ui.components.Avatar
import com.dev.alex.portfolio.ui.components.Divider
import com.dev.alex.portfolio.ui.components.FpIcon
import com.dev.alex.portfolio.ui.components.FpLabel
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.Semantic
import kotlinx.coroutines.launch

/** Everything the shell needs from the app: where we are, where we can go. */
class ShellNav(
    val current: Screen,
    val portfolios: List<PortfolioDto>,
    val username: String,
    val offline: Boolean,
    val dark: Boolean,
    val colorOf: (String) -> Long,
    val open: (Screen) -> Unit,
    /** a form over the current page */
    val push: (Screen) -> Unit,
    val back: () -> Unit,
    val toggleTheme: () -> Unit,
    val signOut: () -> Unit,
    val rememberPortfolioOrder: (List<String>) -> Unit,
) {
    fun portfolioName(portfolioId: String): String =
        portfolios.firstOrNull { it.portfolioId == portfolioId }?.portfolioName ?: "Portfolio"
}

/** Pages of one portfolio the app implements, in the desktop sidebar's order. */
private data class PageLink(val label: String, val icon: ImageVector, val make: (String) -> Screen)

private val PAGES = listOf(
    PageLink("Dashboard", FpIcons.Home) { Screen.Dashboard(it) },
    PageLink("Holdings", FpIcons.Pie) { Screen.Holdings(it) },
    PageLink("Ownership", FpIcons.Diamond) { Screen.Ownership(it) },
    PageLink("Self-Funding", FpIcons.Target) { Screen.SelfFunding(it) },
    PageLink("Watchlist", FpIcons.Eye) { Screen.Watchlist(it) },
    PageLink("Statistics", FpIcons.Hash) { Screen.Statistics(it) },
    PageLink("Transactions", FpIcons.Rows) { Screen.Transactions(it) },
    PageLink("Dividends", FpIcons.Coins) { Screen.Dividends(it) },
    PageLink("Dividend Calendar", FpIcons.Calendar) { Screen.Calendar(it) },
)

/**
 * The mobile frame of every page: 56dp top bar, and the desktop sidebar as the drawer —
 * so every entry matches the web app. Pages without a mobile design are left out rather
 * than linked to nothing.
 */
@Composable
fun MobileShell(
    nav: ShellNav,
    title: String,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val colors = Fp.colors

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.sidebar,
                drawerShape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                modifier = Modifier.width(284.dp),
            ) {
                DrawerContent(nav) { screen ->
                    scope.launch { drawer.close() }
                    nav.open(screen)
                }
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.pageBg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
            ) {
                IconAction(FpIcons.Menu, "Open menu") { scope.launch { drawer.open() } }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions()
                IconAction(if (nav.dark) FpIcons.Moon else FpIcons.Sun, "Switch theme", onClick = nav.toggleTheme)
                Avatar(nav.username.ifBlank { "?" }, size = 26.dp, color = Semantic.Purple)
            }
            Divider()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = content,
            )
        }
    }
}

@Composable
fun IconAction(icon: ImageVector, description: String, tint: Color = Fp.colors.textMuted, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClickLabel = description, onClick = onClick),
    ) {
        FpIcon(icon, size = 18.dp, tint = tint)
    }
}

@Composable
private fun DrawerContent(nav: ShellNav, go: (Screen) -> Unit) {
    val colors = Fp.colors
    // ModalDrawerSheet already insets its content from the status bar
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Brush.linearGradient(listOf(Brand.Primary, Semantic.Purple))),
            ) {
                Text("F", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text("FinancePortfolio", color = colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            NavRow(
                label = "All Portfolios",
                icon = FpIcons.Folder,
                active = nav.current == Screen.Portfolios,
                onClick = { go(Screen.Portfolios) },
            )
            FpLabel(
                "Your portfolios",
                color = colors.textSubtle,
                modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
            )
            nav.portfolios.forEach { portfolio ->
                val selected = nav.current.portfolioId == portfolio.portfolioId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) activeBackground() else Color.Transparent)
                        .clickable { go(Screen.Dashboard(portfolio.portfolioId)) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Avatar(portfolio.portfolioName.ifBlank { "?" }, size = 22.dp, color = Color(nav.colorOf(portfolio.portfolioId)))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        portfolio.portfolioName,
                        color = if (selected) Brand.Primary else colors.textMuted,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    Column(Modifier.padding(bottom = 6.dp)) {
                        PAGES.forEach { page ->
                            val target = page.make(portfolio.portfolioId)
                            NavRow(
                                label = page.label,
                                icon = page.icon,
                                active = nav.current == target,
                                sub = true,
                                onClick = { go(target) },
                            )
                        }
                    }
                }
            }
        }

        Divider()
        if (nav.offline) {
            Text(
                "Offline — showing saved data",
                color = Semantic.Warning,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, top = 10.dp),
            )
        }
        Box(Modifier.padding(12.dp)) {
            NavRow(label = "Log out", icon = FpIcons.Logout, active = false, onClick = nav.signOut)
        }
    }
}

@Composable
private fun activeBackground(): Color = if (Fp.colors.isDark) Brand.Primary.copy(alpha = 0.20f) else Brand.Primary50

@Composable
private fun NavRow(label: String, icon: ImageVector, active: Boolean, sub: Boolean = false, onClick: () -> Unit) {
    val colors = Fp.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) activeBackground() else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(
                start = if (sub) 38.dp else 12.dp,
                end = 12.dp,
                top = if (sub) 7.dp else 9.dp,
                bottom = if (sub) 7.dp else 9.dp,
            ),
    ) {
        FpIcon(icon, size = if (sub) 14.dp else 16.dp, tint = if (active) Brand.Primary else colors.textMuted)
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (active) Brand.Primary else colors.textMuted,
            fontSize = if (sub) 12.5.sp else 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
