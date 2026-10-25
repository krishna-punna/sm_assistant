package com.smassistant.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.FakeCipher
import com.smassistant.app.linkedin.LinkedInAuthClient
import com.smassistant.app.linkedin.LinkedInAuthStore
import com.smassistant.app.linkedin.LinkedInOAuthApp
import com.smassistant.app.linkedin.LinkedInToken
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LinkedInAuthViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings get() = ApiSettingsStore(context, FakeCipher())
    private val authStore get() = LinkedInAuthStore(context, FakeCipher())

    private var exchange: ApiResult<LinkedInToken> = ApiResult.Ok(LinkedInToken("new-token", 60))
    private var codes = mutableListOf<String>()
    private val client = object : LinkedInAuthClient() {
        override suspend fun exchangeCode(app: LinkedInOAuthApp, code: String): ApiResult<LinkedInToken> {
            codes += code
            return exchange
        }

        override suspend fun memberName(token: String): ApiResult<String> = ApiResult.Ok("Punna Krishna")
    }

    @Before
    fun clear() {
        context.getSharedPreferences("api_settings", Context.MODE_PRIVATE).edit().clear().commit()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LinkedInAuthViewModel(authStore, settings, client, now = { 1_000L }, newState = { "state-1" })

    private fun signedIn(): LinkedInAuthViewModel = viewModel().apply {
        onClientIdChange(" client-1 ")
        onClientSecretChange("secret-1")
        signIn()
        assertTrue(onBrowserNavigate("${LinkedInOAuthApp.DEFAULT_REDIRECT_URI}?code=code-1&state=state-1"))
    }

    @Test
    fun `sign in needs the client id and secret`() {
        val vm = viewModel()
        vm.signIn()
        assertEquals("Enter the Client ID from your LinkedIn app", vm.state.value.clientIdError)
        assertEquals("Enter the Client Secret from your LinkedIn app", vm.state.value.clientSecretError)
        assertNull(vm.state.value.authorizationUrl)
    }

    @Test
    fun `sign in saves the app and opens LinkedIn`() {
        val vm = viewModel()
        vm.onClientIdChange(" client-1 ")
        vm.onClientSecretChange("secret-1")
        vm.onRedirectUriChange("")
        vm.signIn()
        assertTrue(vm.state.value.authorizationUrl!!.startsWith("https://www.linkedin.com/oauth/v2/authorization?"))
        assertEquals(LinkedInOAuthApp("client-1", "secret-1", LinkedInOAuthApp.DEFAULT_REDIRECT_URI), authStore.app())
        assertFalse(vm.onBrowserNavigate("https://www.linkedin.com/login"))
        assertNotNull(vm.state.value.authorizationUrl)
    }

    @Test
    fun `the redirect saves the token and who signed in`() {
        val vm = signedIn()
        assertEquals(listOf("code-1"), codes)
        assertNull(vm.state.value.authorizationUrl)
        assertEquals("new-token", settings.load(ApiService.LINKEDIN).key)
        assertEquals("Punna Krishna", vm.state.value.session?.name)
        assertEquals(61_000L, vm.state.value.session?.expiresAt)
        assertEquals(1, vm.state.value.tokenVersion)
        assertEquals("Signed in to LinkedIn as Punna Krishna", vm.state.value.message)
        // Reopening the screen still shows the session.
        assertEquals("Punna Krishna", viewModel().state.value.session?.name)
    }

    @Test
    fun `a redirect with another state is refused`() {
        val vm = viewModel()
        vm.onClientIdChange("client-1")
        vm.onClientSecretChange("secret-1")
        vm.signIn()
        assertTrue(vm.onBrowserNavigate("${LinkedInOAuthApp.DEFAULT_REDIRECT_URI}?code=code-1&state=other"))
        assertTrue(codes.isEmpty())
        assertEquals("Sign-in didn't match this request. Try again.", vm.state.value.message)
        assertEquals("", settings.load(ApiService.LINKEDIN).key)
    }

    @Test
    fun `a rejected exchange explains what to check`() {
        exchange = ApiResult.Failed(ApiError.InvalidKey)
        val vm = signedIn()
        assertNull(vm.state.value.session)
        assertTrue(vm.state.value.message!!.startsWith("LinkedIn didn't accept the sign-in."))
    }

    @Test
    fun `a pasted token hides the signed in member`() {
        signedIn()
        settings.save(ApiService.LINKEDIN, "", "pasted-token")
        assertNull(viewModel().state.value.session)
    }

    @Test
    fun `sign out removes the token`() {
        val vm = signedIn()
        vm.signOut()
        assertNull(vm.state.value.session)
        assertEquals("", settings.load(ApiService.LINKEDIN).key)
        assertNull(authStore.session())
        assertEquals("client-1", authStore.app().clientId)
    }
}
